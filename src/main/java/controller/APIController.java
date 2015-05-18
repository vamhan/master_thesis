package controller;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicLong;

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
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Link;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;
import controller.TripleStoreConnector;

import org.apache.commons.codec.binary.Base64;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

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
	public HttpEntity<String> addTriples(String[] args, @RequestHeader("Authorization") String Authorization, @RequestBody List<Triple> triples, @RequestParam String repo_name, @RequestParam String level, String prefix) {
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
			
			String query = "SPARQL " + QUERY_PREFIX + prefix + "INSERT INTO GRAPH " + graph + " {";
			for (int i = 0; i < triples.size(); i++) {
				Triple triple = triples.get(i);
				query += triple.getSubject() + " " + triple.getPredicate() + " " + triple.getObject();
				if (i < triples.size() - 1) {
					query += ". ";
				} else {
					query += "}";
				}
				
				// check level members
				if (level.equals("model")) {
					String query2 = "SPARQL " + QUERY_PREFIX + prefix
							+ "SELECT ?instance from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
							+ "?type rdfs:subClassOf* rdfs:Resource ."
							+ "?instance a ?type"
						+ "}";
					List<Map<String, String>> tuples = gettingStartedApplication.queryTuples(query2);
					for (Map<String, String> map : tuples) {
						log("///////////////////////////////////////" + map.get("instance"));
						log("///////////////////////////////////////" + getFullURI(QUERY_PREFIX + prefix, triple.getSubject()));
						log("///////////////////////////////////////" + getFullURI(QUERY_PREFIX + prefix, triple.getObject()));
						if (map.get("instance").equals(getFullURI(QUERY_PREFIX + prefix, triple.getSubject())) || map.get("instance").equals(getFullURI(QUERY_PREFIX + prefix, triple.getObject()))) {
							status = HttpStatus.BAD_REQUEST;
							message = "Some inserted triples are not in the model level!";
							return new ResponseEntity<String>(message, status);
						}
					}
				} else {
					String query2 = "SPARQL " + QUERY_PREFIX + prefix
							+ "SELECT ?class from <" + repo_name + "/model> WHERE { "
								+ "?class rdfs:subClassOf* rdfs:Resource"
							+ "}";
					
					List<Map<String, String>> tuples = gettingStartedApplication.queryTuples(query2);
					for (Map<String, String> map : tuples) {
						log("instance///////////////////////////////////////" + map.get("class"));
						log("instance///////////////////////////////////////" + getFullURI(QUERY_PREFIX + prefix, triple.getSubject()));
						log("instance///////////////////////////////////////" + getFullURI(QUERY_PREFIX + prefix, triple.getObject()));
						log(getFullURI(QUERY_PREFIX + prefix, triple.getPredicate()));
						if (getFullURI(QUERY_PREFIX + prefix, triple.getPredicate()).equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
							log("instance///////////////////////////////////////type");
							if (map.get("class").equals(getFullURI(QUERY_PREFIX + prefix, triple.getSubject()))) {
								status = HttpStatus.BAD_REQUEST;
								message = "Some inserted triples are not in the instance level!";
								return new ResponseEntity<String>(message, status);
							}
						} else if (map.get("class").equals(getFullURI(QUERY_PREFIX + prefix, triple.getSubject())) || map.get("class").equals(getFullURI(QUERY_PREFIX + prefix, triple.getObject()))) {
							status = HttpStatus.BAD_REQUEST;
							message = "Some inserted triples are not in the instance level!";
							return new ResponseEntity<String>(message, status);
						}
					}
					
					// check domain and range
					if (!getFullURI(QUERY_PREFIX + prefix, triple.getPredicate()).equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>") && !getFullURI(QUERY_PREFIX + prefix, triple.getPredicate()).equals("<http://www.w3.org/2000/01/rdf-schema#label>") && triple.getObject().indexOf('"') < 0) {
						String query3 = "SPARQL " + QUERY_PREFIX + prefix
								+ "SELECT ?object from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
								+ triple.getPredicate() + " rdfs:range ?range ."
								+ "?model rdfs:subClassOf* ?range ."
								+ "?object a ?model ."
							+ "}";
						List<Map<String, String>> tuples2 = gettingStartedApplication.queryTuples(query3);
						boolean flag = false;
						for (Map<String, String> map : tuples2) {
							log("conform///////////////////////////////////////" + map.get("object"));
							log("conform///////////////////////////////////////" + getFullURI(QUERY_PREFIX + prefix, triple.getObject()));
							if (map.get("object").equals(getFullURI(QUERY_PREFIX + prefix, triple.getObject()))) {
								flag = true;
							}
						}
						if (!flag) {
							status = HttpStatus.BAD_REQUEST;
							message = "Some inserted triples are not conformed to the model";
							return new ResponseEntity<String>(message, status);
						}
					}
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
	public HttpEntity<List<Map<String, String>>> ontology(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String endpointURL, @RequestParam String ontology) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		QueryExecution qExe = null;
		List<Map<String, String>> concepts = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			UserManager manager = new UserManager(username, password);
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			ontology = java.net.URLDecoder.decode(ontology, "UTF-8");
			
			String s1 = QUERY_PREFIX 
					+ "SELECT distinct ?subject ?predicate ?object WHERE { "
						+ "?subject rdfs:subClassOf* <" + ontology + "> . "
						+ "?subject ?predicate ?object "
						+ "FILTER (?predicate = <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ||"
						+ "?predicate = <http://www.w3.org/2000/01/rdf-schema#subClassOf> ||"
						+ "(?predicate = <http://www.w3.org/2000/01/rdf-schema#label> && langMatches(lang(?object), 'EN')) ||"
						+ "(?predicate = <http://www.w3.org/2000/01/rdf-schema#comment> && langMatches(lang(?object), 'EN')))"
					+ "}";
			log(s1);
			Query query = QueryFactory.create(s1);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        ResultSet results = qExe.execSelect();
	        concepts.addAll(prepareNode("", results));
			
			String s2 = QUERY_PREFIX
					+ "select distinct ?subject ?predicate ?object where {"
					+ "?subject rdfs:subClassOf* <" + ontology + "> . "
					+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?subject ."
					+ "?predicate <http://www.w3.org/2000/01/rdf-schema#range> ?object}";

	        query = QueryFactory.create(s2);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        results = qExe.execSelect();
	        concepts.addAll(prepareNode("", results));
	        
	        String s3 = QUERY_PREFIX
					+ "select distinct ?subject ?predicate ?object where {"
					+ "?class rdfs:subClassOf* <" + ontology + "> . "
					+ "?subject <http://www.w3.org/2000/01/rdf-schema#domain> ?class ."
					+ "?subject ?predicate ?object "
					+ "FILTER (?predicate = <http://www.w3.org/2000/01/rdf-schema#domain> ||"
					+ "?predicate = <http://www.w3.org/2000/01/rdf-schema#range>)"
					+ "}";

	        log(s2);
	        query = QueryFactory.create(s3);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        results = qExe.execSelect();
	        concepts.addAll(prepareNode("", results));
	        
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
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
	public HttpEntity<List<Map<String, String>>> ontologyResource(String[] args, @RequestHeader("Authorization") String Authorization, @RequestParam String endpointURL, @RequestParam String ontology, String resource) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		QueryExecution qExe = null;
		List<Map<String, String>> concepts = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			UserManager manager = new UserManager(username, password);
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			ontology = java.net.URLDecoder.decode(ontology, "UTF-8");
			
			String s = "";
			if (resource != null) {
				s = QUERY_PREFIX + "SELECT distinct ?predicate ?object WHERE {"
						+ "?type rdfs:subClassOf* <" + ontology + "> ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?type ."
						+ "<" + resource + "> ?predicate ?object }";
			} else {
				s = QUERY_PREFIX + "SELECT distinct ?subject ?predicate ?object WHERE {"
						+ "?type rdfs:subClassOf* <" + ontology + "> ."
						+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?type ."
						+ "?subject ?predicate ?object }";
			}

			log(s);
			Query query = QueryFactory.create(s);
	        qExe = QueryExecutionFactory.sparqlService(endpointURL, query );
	        ResultSet results = qExe.execSelect();
	        concepts.addAll(prepareNode(resource, results));
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
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

		/*String s2 = "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX xsd:<http://www.w3.org/2001/XMLSchema#>"
				+ "PREFIX owl:<http://www.w3.org/2002/07/owl#>"
				+ "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>"
				+ "PREFIX dbpprop: <http://dbpedia.org/property/>"
				+ "PREFIX dbres: <http://dbpedia.org/resource/>"
				+ "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>"
				+ "select distinct ?subject ?predicate ?object where {"
				+ "?subject rdfs:subClassOf* <http://dbpedia.org/ontology/Settlement> . "
				+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?subject ."
				+ "?predicate <http://www.w3.org/2000/01/rdf-schema#range> ?object}";

        Query query = QueryFactory.create(s2); //s2 = the query above
        QueryExecution qExe = QueryExecutionFactory.sparqlService( "http://dbpedia.org/sparql", query );
        ResultSet results = qExe.execSelect();
        List<Map<String, String>> concepts = new ArrayList<Map<String,String>>();
        while (results.hasNext()) {
        	QuerySolution sol = results.nextSolution();
        	Map<String, String> concept = new HashMap<String, String>();
        	concept.put("subject", query.shortForm(sol.get("subject").asNode().getURI()));
        	concept.put("predicate", query.shortForm(sol.get("predicate").asNode().getURI()));
        	concept.put("object", query.shortForm(sol.get("object").asNode().getURI()));
        	concepts.add(concept);
        }
        for (Map<String, String> map : concepts) {
			System.out.println(map);
		}
        qExe.close();*/
		
		/*String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype from <http://localhost:8890/noon/instance> WHERE {"
					+ "?subject ?predicate ?object"
				+ "}";
		
		String s = "Здравей' хора";
		String out;
		try {
			out = new String(s.getBytes("UTF-8"), "ISO-8859-1");
			for (int i = 0; i < out.length(); ++i) {
	            System.out.printf("%x ", (int) out.charAt(i));
	        }
			System.out.println();
			String ss = new String(out.getBytes("ISO-8859-1"), "UTF-8");
			System.out.println(ss);
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		DBConnector conn = new DBConnector();
		conn.connect();
		try {
			conn.queryTuples(query);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		log("noon");
		log(getFullURI(QUERY_PREFIX, "dbres:Ballasalla"));
	}
}



