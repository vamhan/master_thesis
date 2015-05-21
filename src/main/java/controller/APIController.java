package controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.DBConnector;
import model.EmptyObject;
import model.Instance;
import model.Triple;
import model.UserManager;

import org.openrdf.http.protocol.UnauthorizedException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Link;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;
import controller.TripleStoreConnector;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.riot.RDFDataMgr;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

@RestController
public class APIController {
	
	private static String QUERY_PREFIX = "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>"
			+ "PREFIX psys:<http://proton.semanticweb.org/protonsys#>"
			+ "PREFIX xsd:<http://www.w3.org/2001/XMLSchema#>"
			+ "PREFIX owl:<http://www.w3.org/2002/07/owl#>"
			+ "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
			+ "PREFIX pext:<http://proton.semanticweb.org/protonext#>"
			+ "PREFIX ex:<http://example.org/owlim#>"
			+ "PREFIX virt:<http://www.openlinksw.com/schemas/virtrdf#>"
			+ "PREFIX ns:<http://rdfs.org/sioc/ns#>"
			+ "PREFIX type:<http://rdfs.org/sioc/types#>"
			+ "PREFIX dbpedia-owl:<http://dbpedia.org/ontology/>"
			+ "PREFIX dbpprop:<http://dbpedia.org/property/>"
			+ "PREFIX dbres:<http://dbpedia.org/resource/>";
	
	private static String DEFAULT_GRAPH = "<http://localhost:8890/test>";
	private static String DEFAULT_SCHEMA = "<http://localhost:8890/schema/test>";
	private static String DEFAULT_RULE = "http://localhost:8890/schema/property_rules1";
	

	@RequestMapping("/triples")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> listTriples(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, String level) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> triples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			
			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), username, password);
			//triples = gettingStartedApplication.getAllTriples();
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(triples, status);
			}
			
			String graph = "";
			if (level == null) {
				graph = "from <" + repo_name + "/model>" + " from <" + repo_name + "/instance>";
			} else if (level.equals("model") || level.equals("instance")) {
				graph = "from <" + repo_name + "/" + level + ">";
			} else {
				status = HttpStatus.BAD_REQUEST;
				Map<String, String> map = new HashMap<String, String>();
				map.put("message", "Level parameter can be only model or instance");
				triples.add(map);
				return new ResponseEntity<List<Map<String, String>>>(triples, status);
			}
			
			String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype " + graph + " WHERE {"
						+ "?subject ?predicate ?object"
					+ "}";
			
			log(query);
			
			triples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(triples, status);
	}
	
	@RequestMapping("/triples/search")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> searchTriples(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String keyword) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			keyword = java.net.URLDecoder.decode(keyword, "UTF-8");
			
			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <" + repo_name + "/model> from <" + repo_name + "/instance>  WHERE {"
						+ "{?subject ?predicate ?object ."
							+ "filter regex(str(?subject), '" + keyword + "') "
							//+ (query_prefix != null ? "filter STRSTARTS(STR(?subject), str(" + query_prefix + ":))" : "")
						+ "} UNION"
						+ "{?subject ?predicate ?object ."
							+ "filter regex(str(?predicate), '" + keyword + "')"
							//+ (query_prefix != null ? "filter STRSTARTS(STR(?predicate), str(" + query_prefix + ":))" : "")
						+ "} UNION"
						+ "{?subject ?predicate ?object ."
							+ "filter regex(str(?object), '" + keyword + "')"
							//+ (query_prefix != null ? "filter STRSTARTS(STR(?object), str(" + query_prefix + ":))" : "")
						+ "}"
					+ "}";
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/triples/subject/search")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> searchSubject(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String keyword) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			keyword = java.net.URLDecoder.decode(keyword, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ "{?subject ?predicate ?object ."
						+ "filter regex(str(?subject), '" + keyword + "')}"
					+ "}";
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}  finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/triples/predicate/search")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> searchPredicate(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String keyword) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			keyword = java.net.URLDecoder.decode(keyword, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ "{?subject ?predicate ?object ."
						+ "filter regex(str(?predicate), '" + keyword + "')}"
					+ "}";
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	
	@RequestMapping(value="/triples", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> addTriples(String[] args, @RequestHeader("Authorization") String Authorization, @RequestBody List<Triple> triples, @RequestParam String repo_name, @RequestParam String level, String prefix, boolean notCheckValid) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		String message = "Adding triples fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			/*if (gettingStartedApplication.insert(triples)) {
				status = HttpStatus.OK;
				message  = "Adding triples succeeds";
			}*/
			
			UserManager manager = new UserManager(username, password);
			
			String graph = "";
			if (level.equals("model") || level.equals("instance")) {
				graph = repo_name + "/" + level;
				if (level.equals("model")) {
					if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , true)) {
						status = HttpStatus.FORBIDDEN;
						return new ResponseEntity<String>(message, status);
					}
				} else {
					if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/instance" , true)) {
						status = HttpStatus.FORBIDDEN;
						return new ResponseEntity<String>(message, status);
					}
				}
			} else {
				status = HttpStatus.BAD_REQUEST;
				message = "Level parameter can be only model or instance";
				return new ResponseEntity<String>(message, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "DB.DBA.TTLP('" + QUERY_PREFIX + prefix;
			for (int i = 0; i < triples.size(); i++) {
				Triple triple = triples.get(i);
				query += triple.getSubject() + " " + triple.getPredicate() + " " + convertToUTF8(triple.getObject());
				if (i < triples.size() - 1) {
					query += ". ";
				} else {
					query += ".', '', '" + graph + "')";
				}
				
				HttpEntity<String> result = checkValid(level, QUERY_PREFIX + prefix, repo_name, gettingStartedApplication, triple, notCheckValid);
				if (result != null) {
					return result;
				}
			}
			
			log(query);
			
			gettingStartedApplication.update(query);
			status = HttpStatus.OK;
			message  = "Adding triples succeeds";
			
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	private HttpEntity<String> checkValid(String level, String prefix, String repo_name, TripleStoreConnector gettingStartedApplication, Triple triple, boolean notCheckValid) {
		HttpStatus status;
		String message;
		if (level.equals("model")) {
			String query2 = "SPARQL " + prefix
					+ "SELECT ?instance from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
					+ "?type rdfs:subClassOf* rdfs:Resource ."
					+ "?instance a ?type"
				+ "}";
			List<Map<String, String>> tuples = gettingStartedApplication.queryTuples(query2);
			for (Map<String, String> map : tuples) {
				log("///////////////////////////////////////" + map.get("instance"));
				log("///////////////////////////////////////" + getFullURI(prefix, triple.getSubject()));
				log("///////////////////////////////////////" + getFullURI(prefix, triple.getObject()));
				if (map.get("instance").equals(getFullURI(prefix, triple.getSubject()))) {
					status = HttpStatus.BAD_REQUEST;
					message = triple.getSubject() + " is not in the model level!";
					return new ResponseEntity<String>(message, status);
				} else if (map.get("instance").equals(getFullURI(prefix, triple.getObject()))) {
					status = HttpStatus.BAD_REQUEST;
					message = triple.getObject() + " is not in the model level!";
					return new ResponseEntity<String>(message, status);
				}
			}
		} else {
			String query2 = "SPARQL " + prefix
					+ "SELECT ?class from <" + repo_name + "/model> WHERE { "
						+ "?class rdfs:subClassOf* rdfs:Resource"
					+ "}";
			
			List<Map<String, String>> tuples = gettingStartedApplication.queryTuples(query2);
			for (Map<String, String> map : tuples) {
				log("instance///////////////////////////////////////" + map.get("class"));
				log("instance///////////////////////////////////////" + getFullURI(prefix, triple.getSubject()));
				log("instance///////////////////////////////////////" + getFullURI(prefix, triple.getObject()));
				log(getFullURI(prefix, triple.getPredicate()));
				if (getFullURI(prefix, triple.getPredicate()).equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
					log("instance///////////////////////////////////////type");
					if (map.get("class").equals(getFullURI(prefix, triple.getSubject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getSubject() + " is not in the instance level!";
						return new ResponseEntity<String>(message, status);
					}
				} else if (map.get("class").equals(getFullURI(prefix, triple.getSubject()))) {
					status = HttpStatus.BAD_REQUEST;
					message = triple.getSubject() + " is not in the instance level!";
					return new ResponseEntity<String>(message, status);
				} else if (map.get("class").equals(getFullURI(prefix, triple.getObject()))) {
					status = HttpStatus.BAD_REQUEST;
					message = triple.getObject() + " is not in the instance level!";
					return new ResponseEntity<String>(message, status);
				}
			}
			
			// check domain and range
			if (!notCheckValid && !getFullURI(prefix, triple.getPredicate()).equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>") && !getFullURI(prefix, triple.getPredicate()).equals("<http://www.w3.org/2000/01/rdf-schema#label>") && triple.getObject().indexOf('"') < 0) {
				String query3 = "SPARQL " + prefix
						+ "SELECT ?object from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
						+ triple.getPredicate() + " rdfs:range ?range ."
						+ "?model rdfs:subClassOf* ?range ."
						+ "?object a ?model ."
					+ "}";
				List<Map<String, String>> tuples2 = gettingStartedApplication.queryTuples(query3);
				boolean pass = false;
				for (Map<String, String> map : tuples2) {
					log("conform///////////////////////////////////////" + map.get("object"));
					log("conform///////////////////////////////////////" + getFullURI(prefix, triple.getObject()));
					if (map.get("object").equals(getFullURI(prefix, triple.getObject()))) {
						pass = true;
						break;
					}
				}
				if (!pass && tuples2.size() > 0) {
					status = HttpStatus.BAD_REQUEST;
					message = triple.getPredicate() + " is not conformed to the model (" + triple.getObject() + ")";
					return new ResponseEntity<String>(message, status);
				}
			}
		}
		return null;
	}
	
	@RequestMapping(value="/import", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> importRDF(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam("file") MultipartFile file, @RequestParam String repo_name, @RequestParam String level) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		String message = "Import fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			
			String graph = "";
			if (level.equals("model") || level.equals("instance")) {
				graph = repo_name + "/" + level;
				if (level.equals("model")) {
					if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , true)) {
						status = HttpStatus.FORBIDDEN;
						return new ResponseEntity<String>(message, status);
					}
				} else {
					if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/instance" , true)) {
						status = HttpStatus.FORBIDDEN;
						return new ResponseEntity<String>(message, status);
					}
				}
			} else {
				status = HttpStatus.BAD_REQUEST;
				message = "Level parameter can be only model or instance";
				return new ResponseEntity<String>(message, status);
			}
			
			String extension = FilenameUtils.getExtension(file.getOriginalFilename());
			if (!extension.equals("ttl") && !extension.equals("nt") && !extension.equals("n3") && !extension.equals("rdf") && !extension.equals("xml")) {
				status = HttpStatus.BAD_REQUEST;
				message = "This file type is not supported. Only .ttl, .nt, .n3, .rdf, .xml are allowed";
				return new ResponseEntity<String>(message, status);
			}
			File f = File.createTempFile("tmp", "." + extension);
			FileOutputStream fos = new FileOutputStream(f); 
		    fos.write(file.getBytes());
		    fos.close(); 
			Model model = RDFDataMgr.loadModel(f.getAbsolutePath());
			String prefix = "";
			Set<String> ns = model.getNsPrefixMap().keySet();
			for (String key : ns) {
				prefix += "PREFIX " + key + ":<" + model.getNsPrefixMap().get(key) + ">";
			}
			log(prefix);
			
			StmtIterator stmtIter = model.listStatements();
			while ( stmtIter.hasNext() ) {
				Statement stmt = stmtIter.nextStatement();
		        Triple triple = new Triple();
		        triple.setSubject("<" + stmt.getSubject().getURI() + ">");
		        triple.setPredicate("<" + stmt.getPredicate().getURI() + ">");
		        if (stmt.getObject().isLiteral()) {
	        		if (stmt.getObject().asLiteral().getDatatypeURI() != null) {
	        			triple.setObject('"' + stmt.getObject().asLiteral().getString() + '"' + "^^<" + stmt.getObject().asLiteral().getDatatypeURI() + ">");
	        		} else {
	        			triple.setObject('"' + stmt.getObject().asLiteral().getString() + '"' + "@en");
	        		}
	    			
	    		} else {
	    			triple.setObject("<" + stmt.getObject().asNode().getURI() + ">");
	    		}
		        
		        HttpEntity<String> result = checkValid(level, prefix, repo_name, gettingStartedApplication, triple, false);
				if (result != null) {
					return result;
				}
		    }
			
			String query;
			if (extension.equals("ttl") || extension.equals("nt") || extension.equals("n3")) {
				query = "DB.DBA.TTLP(file_to_string_output('" + f.getAbsolutePath() +"'), '','" + graph + "')";
			} else {
				query = "DB.DBA.RDF_LOAD_RDFXML(file_to_string_output('" + f.getAbsolutePath() +"'), '','" + graph + "')";
			}
			
			gettingStartedApplication.update(query);
			status = HttpStatus.OK;
			message  = "Import succeeds";
			f.delete();
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IOException e) {
			status = HttpStatus.BAD_REQUEST;
			message = "There is something wrong with the imported file!";
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/triples", method=RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<String> deleteTriples(String[] args, @RequestHeader("Authorization") String Authorization, @RequestBody List<Triple> triples, @RequestParam String repo_name, @RequestParam String level, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		String message = "Removing triples fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			/*if (gettingStartedApplication.delete(triples)) {
				status = HttpStatus.OK;
				message  = "Adding triples succeeds";
			}*/
			UserManager manager = new UserManager(username, password);
			String graph = "";
			if (level.equals("model") || level.equals("instance")) {
				graph = "<" + repo_name + "/" + level + ">";
				if (level.equals("model")) {
					if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , true)) {
						status = HttpStatus.FORBIDDEN;
						return new ResponseEntity<String>(message, status);
					}
				} else {
					if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/instance" , true)) {
						status = HttpStatus.FORBIDDEN;
						return new ResponseEntity<String>(message, status);
					}
				}
			} else {
				status = HttpStatus.BAD_REQUEST;
				message = "Level parameter can be only model or instance";
				return new ResponseEntity<String>(message, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "SPARQL " + QUERY_PREFIX + prefix + "DELETE FROM GRAPH " + graph + " {";
			for (int i = 0; i < triples.size(); i++) {
				Triple triple = triples.get(i);
				query += triple.getSubject() + " " + triple.getPredicate() + " " + triple.getObject();
				if (i < triples.size() - 1) {
					query += ". ";
				} else {
					query += "}";
				}
			}
			
			log(query);
			
			gettingStartedApplication.update(query);
			status = HttpStatus.OK;
			message  = "Removing triples succeeds";
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping("/types")
	@ResponseBody
	public HttpEntity<EmptyObject> listTypes(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		EmptyObject emp = new EmptyObject();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<EmptyObject>(emp, status);
			}
			
			String query = "SPARQL " + QUERY_PREFIX
					+ "SELECT DISTINCT ?type from <" + repo_name + "/model> WHERE {"
						+ "?s a ?type ."
					+ "}";
			
			List<Map<String, String>> tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;
				
			/*emp.add(linkTo(methodOn(APIController.class).listTypes(args, Authorization, repo_name)).withSelfRel());
	        for (Map<String, String> type : tuples) {
	        	if (type.getValue().indexOf('#') >= 0) {
	        		String value = type.getValue().substring(type.getValue().indexOf('#') + 1, type.getValue().length() - 1);
		        	String preUrl = type.getValue().substring(1, type.getValue().indexOf('#'));
		        	int index = QUERY_PREFIX.indexOf(preUrl);
		        	if (index >= 0) {
			        	String temp = QUERY_PREFIX.substring(0, index);
			        	int lsindex = temp.lastIndexOf(' ');
			        	String prefix = QUERY_PREFIX.substring(lsindex + 1, index - 1);
			        	type.add(new Link("types/" + prefix + value + "/instances", "instances"));
		        	}
	        	}
	        }*/
	        emp.embedResource("types", tuples);
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<EmptyObject>(emp, status);
	}
	
	@RequestMapping("/types/{type}/instances")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> instances(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String type, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			type = java.net.URLDecoder.decode(type, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "SPARQL " + QUERY_PREFIX + prefix
					+ "SELECT ?instance ?type from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
						+ "?type rdfs:subClassOf* " + type + " . "
						+ "?instance a ?type"
					+ "}";
			
			log(query);
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/types/{type}/hierarchy")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> hierarchy(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String type, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			type = java.net.URLDecoder.decode(type, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "SPARQL " + QUERY_PREFIX + prefix
					+ "SELECT ?class ?type from <" + repo_name + "/model> WHERE { "
						+ "?class rdfs:subClassOf* " + type + " . "
						+ "?class rdfs:subClassOf ?type"
					+ "}";
			
			log(query);
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/instances/{instance}/model_properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> typeProperties(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String instance, @RequestParam String level, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			instance = java.net.URLDecoder.decode(instance, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "";
			if (level.equals("model")) {
				query = "SPARQL " + QUERY_PREFIX + prefix
						+ "SELECT distinct ?predicate from <" + repo_name + "/model> WHERE { "
							+ instance + " rdfs:subClassOf* ?h . "
							+ "?h ?predicate ?o"
						+ "}";
			} else if (level.equals("instance")) {
				query = "SPARQL " + QUERY_PREFIX + prefix
						+ "SELECT distinct ?predicate from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
						   	+ instance + " a ?type ."
							+ "?type rdfs:subClassOf* ?h . "
							+ "?h ?predicate ?o"
						+ "}";
			} else {
				status = HttpStatus.BAD_REQUEST;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			log(query);
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/instances/{instance}/properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> instanceProperties(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String instance, @RequestParam String level, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			instance = java.net.URLDecoder.decode(instance, "UTF-8");
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			
			log(instance);

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "";
			if (level.equals("model")) {
				query = "SPARQL " + QUERY_PREFIX + prefix
						+ "SELECT ?predicate ?object ?name ?type (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
							+ instance +" ?predicate ?object ."
							+ "OPTIONAL {"
								+ "?object rdfs:subClassOf ?type ."
								+ "}"
							+ "OPTIONAL { "
								+ "?object rdfs:label ?name ."
							+ "}"
						+ "}";
			} else if (level.equals("instance")) {
				query = "SPARQL " + QUERY_PREFIX + prefix
						+ "SELECT ?predicate ?object ?name ?type (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
							+ instance +" ?predicate ?object ."
							+ "OPTIONAL {"
								+ "?object a ?type ."
								+ "}"
							+ "OPTIONAL { "
								+ "?object rdfs:label ?name ."
							+ "}"
						+ "}";
			} else {
				status = HttpStatus.BAD_REQUEST;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/instances/{instance}/second_level_properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> instanceSecondLevel(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String instance, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			instance = java.net.URLDecoder.decode(instance, "UTF-8");
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			if (prefix == null) {
				prefix = "";
			}
			
			String query = "SPARQL " + QUERY_PREFIX + prefix
					+ "SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ instance +" ?predicate1 ?subject . "
						+ "?subject ?predicate ?object"
					+ "}";
			
			tuples = gettingStartedApplication.queryTuples(query);
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/ontology")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> ontology(String[] args, @RequestParam String endpointURL, @RequestParam String ontology) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		QueryExecution qExe = null;
		List<Map<String, String>> concepts = new ArrayList<Map<String, String>>();
		
		try {
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			ontology = java.net.URLDecoder.decode(ontology, "UTF-8");
			
			String s1 = "";
			Query query;
			ResultSet results;
			/*if (getAllDescendants) {
				s1 = "{select ?subject ?predicate ?object where {?subject rdfs:subClassOf* <" + ontology + ">. ?subject ?predicate ?object}} "
					+ "union"
					+ "{select ?subject ?predicate ?object where {<" + ontology + "> rdfs:subClassOf* ?subject. ?subject ?predicate ?object}} ";
			} else {
				s1 = "<" + ontology + "> rdfs:subClassOf* ?subject . ?subject ?predicate ?object ";
			}*/
			
			String q1 = QUERY_PREFIX 
					+ "SELECT distinct ?subject ?predicate ?object WHERE { "
						+ "?subject rdfs:subClassOf* " + ontology + ". "
						+ "?subject ?predicate ?object "
						+ "FILTER (?predicate = <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ||"
						+ "?predicate = <http://www.w3.org/2000/01/rdf-schema#subClassOf> ||"
						+ "(?predicate = <http://www.w3.org/2000/01/rdf-schema#label> && langMatches(lang(?object), 'EN')) ||"
						+ "(?predicate = <http://www.w3.org/2000/01/rdf-schema#comment> && langMatches(lang(?object), 'EN')))"
					+ "}";
			log(q1);
			query = QueryFactory.create(q1);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        results = qExe.execSelect();
	        concepts.addAll(prepareNode("", results));
			
	        /*if (getAllDescendants) {
				s1 = "{select ?subject ?predicate ?object where {?subject rdfs:subClassOf* <" + ontology + "> ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?subject ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#range> ?object}} "
					+ "union"
					+ "{select ?subject ?predicate ?object where {<" + ontology + "> rdfs:subClassOf* ?subject ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?subject ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#range> ?object}} ";
			} else {
				s1 = "<" + ontology + "> rdfs:subClassOf* ?subject . "
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?subject ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#range> ?object";
			}*/
			String q2 = QUERY_PREFIX
					+ "select distinct ?subject ?predicate ?object where {"
					+ "?subject rdfs:subClassOf* " + ontology + " ."
					+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?subject ."
					+ "?predicate <http://www.w3.org/2000/01/rdf-schema#range> ?object}";

			log(q2);
	        query = QueryFactory.create(q2);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        results = qExe.execSelect();
	        concepts.addAll(prepareNode("", results));
	        
	        /*if (getAllDescendants) {
				s1 = "{select ?subject ?predicate ?object where {?class rdfs:subClassOf* <" + ontology + "> ."
						+ "?subject <http://www.w3.org/2000/01/rdf-schema#domain> ?class ."
						+ "?subject ?predicate ?object}} "
					+ "union"
					+ "{select ?subject ?predicate ?object where {<" + ontology + "> rdfs:subClassOf* ?class ."
						+ "?subject <http://www.w3.org/2000/01/rdf-schema#domain> ?class ."
						+ "?subject ?predicate ?object}} ";
			} else {
				s1 = "<" + ontology + "> rdfs:subClassOf* ?class . "
						+ "?subject <http://www.w3.org/2000/01/rdf-schema#domain> ?class ."
						+ "?subject ?predicate ?object ";
			}*/
	        String q3 = QUERY_PREFIX
					+ "select distinct ?subject ?predicate ?object where {"
					+ "?class rdfs:subClassOf* " + ontology + " ."
					+ "?subject <http://www.w3.org/2000/01/rdf-schema#domain> ?class ."
					+ "?subject ?predicate ?object "
					+ "FILTER (?predicate = <http://www.w3.org/2000/01/rdf-schema#domain> ||"
					+ "?predicate = <http://www.w3.org/2000/01/rdf-schema#range>)"
					+ "}";

	        log(q3);
	        query = QueryFactory.create(q3);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        results = qExe.execSelect();
	        concepts.addAll(prepareNode("", results));
	        
			status = HttpStatus.OK;
				
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (qExe != null)
				qExe.close();
		}

		return new ResponseEntity<List<Map<String, String>>>(concepts, status);
	}
	
	@RequestMapping("/ontology/resources")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> ontologyResource(String[] args, @RequestParam String endpointURL, String ontology, String resource) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		QueryExecution qExe = null;
		List<Map<String, String>> concepts = new ArrayList<Map<String, String>>();
		
		try {
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			if (ontology != null) {
				ontology = java.net.URLDecoder.decode(ontology, "UTF-8");
			}
			if (resource != null) {
				resource = java.net.URLDecoder.decode(resource, "UTF-8");
			}
			
			String s = "";
			if (ontology == null && resource == null) {
				status = HttpStatus.BAD_REQUEST;
				return new ResponseEntity<List<Map<String, String>>>(concepts, status);
			} else if (resource != null) {
				s = QUERY_PREFIX + "SELECT distinct ?predicate ?object WHERE {"
						//+ "?type rdfs:subClassOf* <" + ontology + "> ."
						//+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?type ."
						+ "values (?v) { (owl:ObjectProperty)(owl:DatatypeProperty) }"
						+ "?predicate a ?v ."
						+ resource + " ?predicate ?object }";
			} else {
				s = QUERY_PREFIX + "SELECT distinct ?subject ?predicate ?object WHERE {"
						//+ "?type rdfs:subClassOf* <" + ontology + "> ."
						//+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?type ."
						+ "values (?v) { (owl:ObjectProperty)(owl:DatatypeProperty) }"
						+ "?subject a " + ontology + " ."
						+ "?predicate a ?v ."
						+ "?subject ?predicate ?object }";
			}

			log(s);
			Query query = QueryFactory.create(s);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        ResultSet results = qExe.execSelect();
	        concepts.addAll(prepareNode(getFullURI(QUERY_PREFIX, resource).replace("<", "").replace(">", ""), results));
			status = HttpStatus.OK;
				
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (qExe != null)
				qExe.close();
		}

		return new ResponseEntity<List<Map<String, String>>>(concepts, status);
	}
	
	
	
	
	@RequestMapping("/sparql")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> sparql(String[] args, @RequestHeader("Authorization") String Authorization, String query) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(args).getParameters(), username, password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.getUserRole(username).toString().equals("superAdmin")) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			query = java.net.URLDecoder.decode(query, "UTF-8");
			log("SPARQL " + QUERY_PREFIX + query);
			tuples = gettingStartedApplication.queryTuples("SPARQL " + QUERY_PREFIX + query);
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	private static SimpleDateFormat logTimestamp = new SimpleDateFormat("HH:mm:ss ");

	private static void log(String message) {
		System.out.println(logTimestamp.format(new Date()) + message);
	}
	
	private String[] getUsernameAndPassword(String secret) {
		String[] result = null;
		if (secret != null && secret.toUpperCase().startsWith("BASIC ")) {
			try {
				String str = new String(Base64.decodeBase64(secret.substring(6)), "UTF-8");
				result = str.split(":");
				System.out.println(result);
			}
			catch (Exception e) { e.printStackTrace(); }
		}
		return result;
	}
	
	/*private static String getPrefix(String defaultPrefix, String url) {
		String preUrl = url.substring(1, url.indexOf("#"));
		int urlIndex = defaultPrefix.indexOf(preUrl);
		if (urlIndex >= 0) { 
			String tempSubString = defaultPrefix.substring(0, urlIndex);
			int pIndex = tempSubString.lastIndexOf("PREFIX");
			return defaultPrefix.substring(pIndex + 7, urlIndex - 2) + ":" + url.substring(url.indexOf("#") + 1, url.length() - 1); 
		}
		return url;
	}*/
	
	private static String getFullURI(String defaultPrefix, String prefixS) {
		if (prefixS != null && prefixS.indexOf(":") > 0) {
			String prefix = prefixS.substring(0, prefixS.indexOf(":"));
			int index = defaultPrefix.indexOf("PREFIX " + prefix + ":");
			if (index >= 0) {
				String tempSubString = defaultPrefix.substring(index, defaultPrefix.length());
				return tempSubString.substring(8 + prefix.length(), tempSubString.indexOf(">")) + prefixS.substring(prefixS.indexOf(":") + 1) + ">";
			}
		}
		return prefixS;
	}
	
	private List<Map<String, String>> prepareNode(String resource, ResultSet results) {
		List<Map<String, String>> concepts = new ArrayList<Map<String,String>>();
        while (results.hasNext()) {
        	QuerySolution sol = results.nextSolution();
        	if (!sol.get("object").isLiteral() || (sol.get("object").isLiteral() && (sol.get("object").asLiteral().getLanguage().equals("en") || sol.get("object").asLiteral().getLanguage().isEmpty()))) {
        		Map<String, String> concept = new HashMap<String, String>();
	        	
	        	//concept.put("subject", query.shortForm(sol.get("class").asNode().getURI()));
	        	//concept.put("predicate", "rdfs:subClassOf");
	        	//concept.put("object", query.shortForm(sol.get("type").asNode().getURI()));
	        	if (sol.get("subject") != null) {
	    			concept.put("subject", "<" + sol.get("subject").asNode().getURI() + ">");
	        	} else {
	        		concept.put("subject", "<" + resource + ">");
	        	}
	    		concept.put("predicate", "<" + sol.get("predicate").asNode().getURI() + ">");
	        	if (sol.get("object").isLiteral()) {
	        		if (sol.get("object").asLiteral().getDatatypeURI() != null) {
	        			concept.put("object", '"' + sol.get("object").asLiteral().getString() + '"' + "^^<" + sol.get("object").asLiteral().getDatatypeURI() + ">");
	        		} else {
	        			concept.put("object", '"' + sol.get("object").asLiteral().getString() + '"' + "@en");
	        		}
	        		/*String lang = "";
	        		if (sol.get("object").asLiteral().getLanguage() != null && !sol.get("object").asLiteral().getLanguage().isEmpty()) {
	        			lang = "@" + sol.get("object").asLiteral().getLanguage();
	        		}*/
	    			
	    		} else {
	    			concept.put("object", "<" + sol.get("object").asNode().getURI() + ">");
	    		}
	        	concepts.add(concept);
        	}
        }
        return concepts;
	}
    
    private static String convertToUTF8(String s) {
        String out = null;
        try {
            out = new String(s.getBytes("UTF-8"), "ISO-8859-1");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
        return out;
    }
	
	public static void main(String[] args) {
		
		/*APIController con = new APIController();
		Triple triple = new Triple();
		triple.setSubject("<http://dbpedia.org/resource/Barcelona>");
		triple.setPredicate("<http://dbpedia.org/ontology/abstract>");
		triple.setObject("\"Barcelona (English /bɑrsɨˈloʊnə/, Catalan: [bərsəˈɫonə], Spanish: [barθeˈlona]) is the capital city of the autonomous community of Catalonia in Spain, and its 2nd largest city, with a population of 1.6 million within its administrative limits.Its urban area extends beyond the administrative city limits with a population of around 4.5 million people, being the sixth-most populous urban area in the European Union after Paris, London, the Ruhr, Madrid and Milan. About five million people live in the Barcelona metropolitan area. It is the largest metropolis on the Mediterranean Sea, located on the coast between the mouths of the rivers Llobregat and Besòs, and bounded to the west by the Serra de Collserola mountain range, the tallest peak of which is 512 metres (1,680 ft) high.Founded as a Roman city, in the Middle Ages Barcelona became the capital of the County of Barcelona. After merging with the Kingdom of Aragon, Barcelona continued to be an important city in the Crown of Aragon. Besieged several times during its history, Barcelona has a rich cultural heritage and is today an important cultural centre and a major tourist destination. Particularly renowned are the architectural works of Antoni Gaudí and Lluís Domènech i Montaner, which have been designated UNESCO World Heritage Sites. The headquarters of the Union for the Mediterranean is located in Barcelona. The city is known for hosting the 1992 Summer Olympics as well as world-class conferences and expositions and also many international sport tournaments.Barcelona is one of the world's leading tourist, economic, trade fair/exhibitions and cultural-sports centres, and its influence in commerce, education, entertainment, media, fashion, science, and the arts all contribute to its status as one of the world's major global cities. It is a major cultural and economic centre in southwestern Europe (Iberian Peninsula), 24th in the world (before Zürich, after Frankfurt) and a financial centre (Diagonal Mar and Gran Via). In 2008 it was the fourth most economically powerful city by GDP in the European Union and 35th in the world with GDP amounting to €177 billion. In 2012 Barcelona had a GDP of $170 billion; it is lagging Spain on both employment and GDP per capita change. In 2009 the city was ranked Europe's third and one of the world's most successful as a city brand. In the same year the city was ranked Europe's fourth best city for business and fastest improving European city, with growth improved by 17% per year, but it has since been in a full recession with declines in both employment and GDP per capita, with some recent signs of the beginning of an economic recovery. Barcelona is a transport hub with one of Europe's principal seaports, an international airport which handles above 35 million passengers per year, an extensive motorway network and a high-speed rail line with a link to France and the rest of Europe.\"@en");
		List<Triple> list = new ArrayList<Triple>();
		list.add(triple);
		con.addTriples(null, "Basic ZGJhOmRiYQ==", list, "http://localhost:8890/noon", "instance", "");*/
		
		Model model = RDFDataMgr.loadModel("./ontology/example.rdf");
		log(model.getNsPrefixMap().toString());
		StmtIterator stmtIter = model.listStatements();
		while ( stmtIter.hasNext() ) {
	        Statement stmt = stmtIter.nextStatement();
	        log(stmt.getSubject().getURI() + " " + stmt.getPredicate().getURI());
	    }
	}
}



