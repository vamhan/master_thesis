package controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import javax.mail.*;
import javax.mail.internet.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import model.EmptyObject;
import model.Triple;
import model.UserManager;
import model.UserManager.Role;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openrdf.http.protocol.UnauthorizedException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
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
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpResponse;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.atlas.web.auth.SimpleAuthenticator;
import org.apache.jena.riot.RDFDataMgr;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

@RestController
public class APIController {
	
	public static String dba_username = "dba";
	public static String dba_password = "dba";

	@RequestMapping("/triples")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> listTriples(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, String level, String namespace) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> triples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (namespace != null) {
				namespace = java.net.URLDecoder.decode(namespace, "UTF-8");
			}
			
			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			//triples = gettingStartedApplication.getAllTriples();
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(triples, status);
			}
			
			String query = "";
			if (level == null) {
				query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
						+ "from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ "?subject ?predicate ?object";
			} else if (level.equals("metamodel")) {
				query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
						+ "from <" + repo_name + "/metamodel> WHERE {"
						+ "?subject ?predicate ?object";
			} else if (level.equals("model")) {
				query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
						+ "from <" + repo_name + "/model> WHERE {"
						+ "?subject ?predicate ?object";
			} else if (level.equals("instance")) {
				query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
						+ "from <" + repo_name + "/instance> WHERE {"
						+ "?subject ?predicate ?object";
			} else {
				status = HttpStatus.BAD_REQUEST;
				Map<String, String> map = new HashMap<String, String>();
				map.put("message", "Level parameter can be only metamodel, model or instance");
				triples.add(map);
				return new ResponseEntity<List<Map<String, String>>>(triples, status);
			}
			
			if (namespace != null) {
				query += " FILTER(STRSTARTS(STR(?subject), '" + namespace + "'))";
			}
			query += "}";
			
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(triples, status);
	}
	
	@RequestMapping("/triples/search")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> searchTriples(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String keyword) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			keyword = java.net.URLDecoder.decode(keyword, "UTF-8");
			
			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL SELECT distinct ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
					+ "from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance>  WHERE {"
						+ "{?subject ?predicate ?object ."
							+ "filter regex(str(?subject), '" + keyword + "', 'i') "
							//+ (query_prefix != null ? "filter STRSTARTS(STR(?subject), str(" + query_prefix + ":))" : "")
						+ "} UNION"
						+ "{?subject ?predicate ?object ."
							+ "filter regex(str(?predicate), '" + keyword + "', 'i')"
							//+ (query_prefix != null ? "filter STRSTARTS(STR(?predicate), str(" + query_prefix + ":))" : "")
						+ "} UNION"
						+ "{?subject ?predicate ?object ."
							+ "filter regex(str(?object), '" + keyword + "', 'i')"
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/triples/subject/search")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> searchSubject(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String keyword) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			keyword = java.net.URLDecoder.decode(keyword, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
					+ "from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ "{?subject ?predicate ?object ."
						+ "filter regex(str(?subject), '" + keyword + "', 'i')}"
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/triples/predicate/search")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> searchPredicate(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String keyword) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			keyword = java.net.URLDecoder.decode(keyword, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
					+ "from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ "{?subject ?predicate ?object ."
						+ "filter regex(str(?predicate), '" + keyword + "', 'i')}"
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	
	@RequestMapping(value="/triples", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> addTriples(@RequestHeader("Authorization") String Authorization, @RequestBody List<Triple> triples, @RequestParam String repo_name, @RequestParam String level, String prefix, boolean notCheckValid) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		String message = "Adding triples fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			/*if (gettingStartedApplication.insert(triples)) {
				status = HttpStatus.OK;
				message  = "Adding triples succeeds";
			}*/
			
			manager = new UserManager(username, password);
			
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
			
			HttpEntity<String> result = checkValid(level, prefix, repo_name, gettingStartedApplication, triples, notCheckValid);
			if (result != null) {
				return result;
			}
			
			String query = "";
			for (int i = 0; i < triples.size(); i++) {
				Triple triple = triples.get(i);
				query += getFullUrl(prefix, triple.getSubject()) + " " + getFullUrl(prefix, triple.getPredicate()) + " " + getFullUrl(prefix, convertToUTF8(triple.getObject())) + " . ";
			}
			
			if (query.length() > 10000) {
				query = "DB.DBA.TTLP('" + initPrefix(prefix) + query + "', '', '" + graph + "')";
			} else {
				query = "SPARQL " + initPrefix(prefix) + " INSERT INTO graph <" + graph + ">{" + query + "}";
			}
			
			log(query);
			
			gettingStartedApplication.update(query);
			//notification(triples, repo_name, level, prefix);
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	private HttpEntity<String> checkValid(String level, String prefix, String repo_name, TripleStoreConnector gettingStartedApplication, List<Triple> triples, boolean notCheckValid) throws JSONException {
		HttpStatus status;
		String message;
		
		String query1 = "SPARQL " + initPrefix(prefix)
				+ "SELECT distinct ?metamodel from <" + repo_name + "/metamodel> WHERE { "
					+ "?metamodel ?p ?o"
				+ "}";
		List<Map<String, String>> metamodel = gettingStartedApplication.queryTuples(query1);
		
		String query2 = "SPARQL " + initPrefix(prefix)
				+ "SELECT distinct ?class ?type from <" + repo_name + "/metamodel> from <" + repo_name + "/model> WHERE { "
					+ "?class rdf:type ?subtype ."
					+ "?subtype rdfs:subClassOf* ?type ."
					+ "filter(?subtype != <http://www.w3.org/2000/01/rdf-schema#Class> && ?subtype != <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>)"
				+ "}";
		
		List<Map<String, String>> model = gettingStartedApplication.queryTuples(query2);
		
		if (level.equals("model")) {
			query2 = "SPARQL " + initPrefix(prefix)
					+ "SELECT distinct ?instance from <" + repo_name + "/instance> WHERE { "
					+ "?instance ?p ?o"
				+ "}";
			List<Map<String, String>> tuples = gettingStartedApplication.queryTuples(query2);
			
			query2 = "SPARQL " + initPrefix(prefix)
					+ "SELECT distinct ?predicate ?domain ?range from <" + repo_name + "/metamodel> WHERE { "
					+ "?predicate rdf:type rdf:Property ."
					+ "?predicate rdfs:domain ?domain ."
					+ "?predicate rdfs:range ?range ."
				+ "}";
			List<Map<String, String>> predicates = gettingStartedApplication.queryTuples(query2);
			
			for (Triple triple : triples) {
				for (Map<String, String> map : metamodel) {
					if (getFullPrefix(prefix, map.get("metamodel")).equals(getFullPrefix(prefix, triple.getSubject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getSubject() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					} else if (getFullPrefix(prefix, map.get("metamodel")).equals(getFullPrefix(prefix, triple.getObject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getObject() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					} else if (getFullPrefix(prefix, map.get("metamodel")).equals(getFullPrefix(prefix, triple.getPredicate()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getPredicate() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					}
				}
				for (Map<String, String> map : tuples) {
					log("///////////////////////////////////////" + getFullPrefix(prefix, map.get("instance")));
					log("///////////////////////////////////////" + getFullPrefix(prefix, triple.getSubject()));
					log("///////////////////////////////////////" + getFullPrefix(prefix, triple.getObject()));
					if (getFullPrefix(prefix, map.get("instance")).equals(getFullPrefix(prefix, triple.getSubject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getSubject() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					} else if (getFullPrefix(prefix, map.get("instance")).equals(getFullPrefix(prefix, triple.getObject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getObject() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					}
				}
				
				if ((getFullPrefix(prefix, triple.getPredicate()).equals("rdf:type") || triple.getPredicate().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) 
						&& (getFullPrefix(prefix, triple.getObject()).equals("rdf:Property") || triple.getObject().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>"))) {
					String domain = "";
					String range = "";
					for (Triple triple2 : triples) {
						if (getFullPrefix(prefix, triple.getSubject()).equals(getFullPrefix(prefix, triple2.getSubject())) 
								&& (getFullPrefix(prefix, triple2.getPredicate()).equals("rdf:type") || triple2.getPredicate().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"))
								&& !(getFullPrefix(prefix, triple2.getObject()).equals("rdf:Property") || triple2.getObject().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>"))){
							for (Map<String, String> map : predicates) {
								if (getFullPrefix(prefix, map.get("predicate")).equals(getFullPrefix(prefix, triple2.getObject()))) {
									domain = getFullPrefix(prefix, map.get("domain"));
									range = getFullPrefix(prefix, map.get("range"));
								}
							}
						}
					}
					for (Triple triple2 : triples) {
						if (getFullPrefix(prefix, triple.getSubject()).equals(getFullPrefix(prefix, triple2.getSubject())) 
								&& (getFullPrefix(prefix, triple2.getPredicate()).equals("rdfs:domain") || triple2.getPredicate().equals("<http://www.w3.org/2000/01/rdf-schema#domain>"))){
							boolean pass = false;
							for (Map<String, String> map : model) {
								if (getFullPrefix(prefix, map.get("class")).equals(getFullPrefix(prefix, triple2.getObject()))
										&& getFullPrefix(prefix, map.get("type")).equals(domain)) {
									pass = true;
									break;
								}
							}
							if (!pass) {
								status = HttpStatus.BAD_REQUEST;
								message = triple2.getPredicate() + " is not conformed to the metamodel (" + triple2.getSubject() + ")";
								return new ResponseEntity<String>(message, status);
							}
						}
						if (getFullPrefix(prefix, triple.getSubject()).equals(getFullPrefix(prefix, triple2.getSubject())) 
								&& (getFullPrefix(prefix, triple2.getPredicate()).equals("rdfs:range") || triple2.getPredicate().equals("<http://www.w3.org/2000/01/rdf-schema#range>"))){
							boolean pass = false;
							for (Map<String, String> map : model) {
								if (getFullPrefix(prefix, map.get("class")).equals(getFullPrefix(prefix, triple2.getObject()))
										&& getFullPrefix(prefix, map.get("type")).equals(range)) {
									pass = true;
									break;
								}
							}
							if (!pass) {
								status = HttpStatus.BAD_REQUEST;
								message = triple2.getPredicate() + " is not conformed to the metamodel (" + triple2.getObject() + ")";
								return new ResponseEntity<String>(message, status);
							}
						}
					}
				}
			}
		} else {
			
			query2 = "SPARQL " + initPrefix(prefix)
					+ "SELECT distinct ?instance ?type from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
						+ "?instance rdf:type ?subtype ."
						+ "?subtype rdfs:subClassOf* ?type "
					+ "}";
			
			List<Map<String, String>> instances = gettingStartedApplication.queryTuples(query2);
			
			String query3 = "SPARQL " + initPrefix(prefix)
					+ "SELECT ?predicate ?domain ?range from <" + repo_name + "/model> WHERE { "
					+ "?predicate rdfs:domain ?domain ."
					+ "?predicate rdfs:range ?range ."
				+ "}";
			List<Map<String, String>> predicates = gettingStartedApplication.queryTuples(query3);
			
			for (Triple triple : triples) {
				for (Map<String, String> map : metamodel) {
					if (getFullPrefix(prefix, map.get("metamodel")).equals(getFullPrefix(prefix, triple.getSubject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getSubject() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					} else if (getFullPrefix(prefix, map.get("metamodel")).equals(getFullPrefix(prefix, triple.getObject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getObject() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					} else if (getFullPrefix(prefix, map.get("metamodel")).equals(getFullPrefix(prefix, triple.getPredicate()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getPredicate() + " is not in the model level!";
						return new ResponseEntity<String>(message, status);
					}
				}
				
				for (Map<String, String> map : model) {
					log("instance///////////////////////////////////////" + getFullPrefix(prefix, map.get("class")));
					log("instance///////////////////////////////////////" + getFullPrefix(prefix, triple.getSubject()));
					log("instance///////////////////////////////////////" + getFullPrefix(prefix, triple.getObject()));
					log(getFullPrefix(prefix, triple.getPredicate()));
					if (getFullPrefix(prefix, triple.getPredicate()).equals("rdf:type") || triple.getPredicate().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
						log("instance///////////////////////////////////////type");
						if (getFullPrefix(prefix, map.get("class")).equals(getFullPrefix(prefix, triple.getSubject()))) {
							status = HttpStatus.BAD_REQUEST;
							message = triple.getSubject() + " is not in the instance level!";
							return new ResponseEntity<String>(message, status);
						}
					} else if (getFullPrefix(prefix, map.get("class")).equals(getFullPrefix(prefix, triple.getSubject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getSubject() + " is not in the instance level!";
						return new ResponseEntity<String>(message, status);
					} else if (getFullPrefix(prefix, map.get("class")).equals(getFullPrefix(prefix, triple.getObject()))) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getObject() + " is not in the instance level!";
						return new ResponseEntity<String>(message, status);
					}
				}
			
				// check domain and range
				if (!notCheckValid 
						&& !getFullPrefix(prefix, triple.getPredicate()).equals("rdf:type") && !triple.getPredicate().equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>") 
						&& !getFullPrefix(prefix, triple.getPredicate()).equals("rdfs:label") && !triple.getPredicate().equals("<http://www.w3.org/2000/01/rdf-schema#label>") 
						&& triple.getObject().indexOf('"') < 0) {

					boolean pass1 = false;
					boolean pass2 = false;
					String domain = "";
					String range = "";
					for (Map<String, String> map : predicates) {
						if (getFullPrefix(prefix, map.get("predicate")).equals(getFullPrefix(prefix, triple.getPredicate()))) {
							domain = getFullPrefix(prefix, map.get("domain"));
							range = getFullPrefix(prefix, map.get("range"));
						}
					}
					for (Map<String, String> map : instances) {
						if (getFullPrefix(prefix, map.get("instance")).equals(getFullPrefix(prefix, triple.getSubject()))
								&& getFullPrefix(prefix, map.get("type")).equals(domain)) {
							pass1 = true;
						} else if (getFullPrefix(prefix, map.get("instance")).equals(getFullPrefix(prefix, triple.getObject()))
								&& getFullPrefix(prefix, map.get("type")).equals(range)) {
							pass2 = true;
						}
					}
					if (!pass1 || !pass2) {
						status = HttpStatus.BAD_REQUEST;
						message = triple.getPredicate() + " is not conformed to the model (" + triple.getSubject() + " " + triple.getPredicate() + " " + triple.getObject() + ")";
						return new ResponseEntity<String>(message, status);
					}
				}
			}
		}
		return null;
	}
	
	@RequestMapping(value="/triples", method=RequestMethod.DELETE)
	@ResponseBody
	public HttpEntity<String> deleteTriples(@RequestHeader("Authorization") String Authorization, @RequestBody List<Triple> triples, @RequestParam String repo_name, @RequestParam String level, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		String message = "Removing triples fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			/*if (gettingStartedApplication.delete(triples)) {
				status = HttpStatus.OK;
				message  = "Adding triples succeeds";
			}*/
			manager = new UserManager(username, password);
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
			
			String prefixS = initPrefix(prefix);
			
			String query = "SPARQL " + prefixS + "DELETE FROM GRAPH " + graph + " {";
			for (int i = 0; i < triples.size(); i++) {
				Triple triple = triples.get(i);
				query += getFullUrl(prefix, triple.getSubject()) + " " + getFullUrl(prefix, triple.getPredicate()) + " " + getFullUrl(prefix, triple.getObject());
				if (i < triples.size() - 1) {
					query += ". ";
				} else {
					query += "}";
				}
			}
			
			log(query);
			
			gettingStartedApplication.update(query);
			//notification(triples, repo_name, level, prefix);
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/import", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> importRDF(@RequestHeader("Authorization") String Authorization, @RequestParam("file") MultipartFile file, @RequestParam String repo_name, @RequestParam String level) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		String message = "Import fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			
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
			if (!extension.equals("ttl") && !extension.equals("nt") && !extension.equals("n3") && !extension.equals("rdf") && !extension.equals("xml") && !extension.equals("owl")) {
				status = HttpStatus.BAD_REQUEST;
				message = "This file type is not supported. Only .ttl, .nt, .n3, .rdf, .xml and .owl are allowed";
				return new ResponseEntity<String>(message, status);
			}
			File f = File.createTempFile("tmp", "." + extension);
			FileOutputStream fos = new FileOutputStream(f); 
		    fos.write(file.getBytes());
		    fos.close(); 
			/*Model model = RDFDataMgr.loadModel(f.getAbsolutePath());
			Set<String> ns = model.getNsPrefixMap().keySet();
			String prefixS = "[";
			for (String key : ns) {
				prefixS += "{\"prefix\": \"" + key + "\", \"url\": \""+ model.getNsPrefixMap().get(key) +"\"";
			}
			prefixS += "]";
			log(prefixS);
			
			/*StmtIterator stmtIter = model.listStatements();
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
		        
		        HttpEntity<String> result = checkValid(level, prefixS, repo_name, gettingStartedApplication, triple, false);
				if (result != null) {
					return result;
				}
		    }*/
			
			String query;
			if (extension.equals("ttl") || extension.equals("nt") || extension.equals("n3")) {
				query = "DB.DBA.TTLP(file_to_string_output('" + f.getAbsolutePath() +"'), '','" + graph + "')";
			} else {
				query = "DB.DBA.RDF_LOAD_RDFXML(file_to_string_output('" + f.getAbsolutePath() +"'), '','" + graph + "')";
			}
			
			gettingStartedApplication.update(query);
			//notification(triples, repo_name, level, prefixes);
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@ResponseBody
    @RequestMapping ( value = "/export", method = RequestMethod.GET)
	public ResponseEntity<FileSystemResource> exportRDF(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, String level, String namespace, @RequestParam String format, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		UserManager manager = null;
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		FileSystemResource file = null;
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<FileSystemResource>(file, status);
			}
			
			String query = initPrefix(prefix) + "CONSTRUCT {?subject ?predicate ?object} WHERE {";
			if (level == null) {
				query += "{GRAPH <" + repo_name + "/metamodel> {?subject ?predicate ?object} . ";
				if (namespace != null) {
					query += "FILTER(STRSTARTS(STR(?subject),str(" + namespace + ":)))";
				}
				query += "}";
				query += "union {GRAPH <" + repo_name + "/model> {?subject ?predicate ?object} . ";
				if (namespace != null) {
					query += "FILTER(STRSTARTS(STR(?subject),str(" + namespace + ":)))";
				}
				query += "}";
				query += "union {GRAPH <" + repo_name + "/instance> {?subject ?predicate ?object} . ";
				if (namespace != null) {
					query += "FILTER(STRSTARTS(STR(?subject),str(" + namespace + ":)))";
				}
				query += "}}";
			} else if (level.equals("metamodel")) {
				query += "{GRAPH <" + repo_name + "/metamodel> {?subject ?predicate ?object} . ";
				if (namespace != null) {
					query += "FILTER(STRSTARTS(STR(?subject),str(" + namespace + ":)))";
				}
				query += "}}";
			} else if (level.equals("model")) {
				query += "{GRAPH <" + repo_name + "/model> {?subject ?predicate ?object} . ";
				if (namespace != null) {
					query += "FILTER(STRSTARTS(STR(?subject),str(" + namespace + ":)))";
				}
				query += "}}";
			} else if (level.equals("instance")) {
				query += "{GRAPH <" + repo_name + "/instance> {?subject ?predicate ?object} . ";
				if (namespace != null) {
					query += "FILTER(STRSTARTS(STR(?subject),str(" + namespace + ":)))";
				}
				query += "}}";
			} else {
				status = HttpStatus.BAD_REQUEST;
				return new ResponseEntity<FileSystemResource>(file, status);
			}
			
			log(query);
			
			String extension = "";
			String syntax = "";
			if (format.equals("rdfxml")) {
				extension = "xml";
				syntax = "RDF/XML";
			} else if (format.equals("turtle")) {
				extension = "ttl";
				syntax = "TURTLE";
			} else if (format.equals("ntriples")) {
				extension = "nt";
				syntax = "N-TRIPLES";
			} else if (format.equals("n3")) {
				extension = "n3";
				syntax = "N3";
			} else if (format.equals("owl")) {
				extension = "owl";
				syntax = "RDF/XML-ABBREV";
			} else {
				status = HttpStatus.BAD_REQUEST;
				return new ResponseEntity<FileSystemResource>(file, status);
			}
			
			File f = File.createTempFile("tmp", "." + extension);
			FileWriter fw = new FileWriter(f.getAbsoluteFile());
			HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
			Model result = QueryExecutionFactory.sparqlService("http://localhost:8890/sparql-auth", query, authenticator).execConstruct();
			
			JSONArray preAr = new JSONArray(prefix);
			for (int i = 0; i < preAr.length(); i++) {
				JSONObject ob = preAr.getJSONObject(i);
				result.setNsPrefix(ob.getString("prefix"), ob.getString("url"));
			}
			
			result.write(fw, syntax);
            fw.close();
             
            //response = Response.ok((Object) f);
            //response.setHeader("Content-Disposition", "attachment; filename=\"exportRDF."+ extension + "\"");
            
            file = new FileSystemResource(f.getAbsolutePath());
            
            status = HttpStatus.OK;
            f.deleteOnExit();

		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<FileSystemResource>(file, status);
	}
	
	/*@RequestMapping("/types")
	@ResponseBody
	public HttpEntity<EmptyObject> listTypes(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		EmptyObject emp = new EmptyObject();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<EmptyObject>(emp, status);
			}
			
			String query = "SPARQL "
					+ "SELECT DISTINCT ?type from <" + repo_name + "/model> WHERE {"
						+ "?type rdfs:subClassOf* <http://www.w3.org/2000/01/rdf-schema#Resource> ."
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
	        }
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<EmptyObject>(emp, status);
	}*/
	
	@RequestMapping("/types/{type}/instances")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> instances(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String type, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			type = getFullUrl(prefix, java.net.URLDecoder.decode(type, "UTF-8"));
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL " + initPrefix(prefix)
					+ "SELECT ?instance ?type from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/types/{type}/hierarchy")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> hierarchy(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String type, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			type = getFullUrl(prefix, java.net.URLDecoder.decode(type, "UTF-8"));
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}
			

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL " + initPrefix(prefix)
					+ "SELECT ?class ?type from <" + repo_name + "/metamodel> from <" + repo_name + "/model> WHERE { "
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/types/{type}/properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> typeProperties(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String type, String prefix) {
		return instanceProperties(Authorization, repo_name, type, prefix);
	}
	
	@RequestMapping("/instances/{instance}/model_properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> typePredicates(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String instance, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			instance = getFullUrl(prefix, java.net.URLDecoder.decode(instance, "UTF-8"));
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL " + initPrefix(prefix)
						+ "SELECT distinct ?predicate from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE { "
						   	+ instance + " rdf:type ?model ."
							+ "?model rdfs:subClassOf* ?superModel . "
							+ "?predicate rdfs:domain ?superModel ."
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/instances/{instance}/properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> instanceProperties(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String instance, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			instance = getFullUrl(prefix, java.net.URLDecoder.decode(instance, "UTF-8"));
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "";
			query = "SPARQL " + initPrefix(prefix)
					+ "SELECT ?predicate ?object ?name ?type (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
					+ "from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
						+ instance +" ?predicate ?object ."
						/*+ "OPTIONAL {"
							+ "?object a ?type ."
							+ "}"*/
						+ "OPTIONAL { "
							+ "?object rdfs:label ?name ."
						+ "}"
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/instances/{instance}/second_level_properties")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> instanceSecondLevel(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @PathVariable String instance, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			instance = getFullUrl(prefix, java.net.URLDecoder.decode(instance, "UTF-8"));
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			String query = "SPARQL " + initPrefix(prefix)
					+ "SELECT ?subject ?predicate ?object (iri(sql:RDF_DATATYPE_OF_OBJ(?object, 'untyped!'))) as ?datatype "
					+ "from <" + repo_name + "/metamodel> from <" + repo_name + "/model> from <" + repo_name + "/instance> WHERE {"
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(tuples, status);
	}
	
	@RequestMapping("/ontology")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> ontology(@RequestParam String endpointURL, @RequestParam String ontology, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		QueryExecution qExe = null;
		List<Map<String, String>> concepts = new ArrayList<Map<String, String>>();
		
		try {
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			ontology = getFullUrl(prefix, java.net.URLDecoder.decode(ontology, "UTF-8"));
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}
			
			Query query;
			ResultSet results;
			/*if (getAllDescendants) {
				s1 = "{select ?subject ?predicate ?object where {?subject rdfs:subClassOf* <" + ontology + ">. ?subject ?predicate ?object}} "
					+ "union"
					+ "{select ?subject ?predicate ?object where {<" + ontology + "> rdfs:subClassOf* ?subject. ?subject ?predicate ?object}} ";
			} else {
				s1 = "<" + ontology + "> rdfs:subClassOf* ?subject . ?subject ?predicate ?object ";
			}*/
			
			String prefixS = initPrefix(prefix);
			
			String q1 = prefixS
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
			String q2 = prefixS
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
	        String q3 = prefixS
					+ "select distinct ?subject ?predicate ?object where {"
					+ "?class rdfs:subClassOf* " + ontology + " ."
					+ "?subject <http://www.w3.org/2000/01/rdf-schema#domain> ?class ."
					+ "?subject ?predicate ?object "
					+ "FILTER (?predicate = <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ||"
					+ "?predicate = <http://www.w3.org/2000/01/rdf-schema#subPropertyOf> ||"
					+ "?predicate = <http://www.w3.org/2000/01/rdf-schema#domain> ||"
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
		} catch (JSONException e) {
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
	public HttpEntity<List<Map<String, String>>> ontologyResource(@RequestParam String endpointURL, String ontology, String resource, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		QueryExecution qExe = null;
		List<Map<String, String>> concepts = new ArrayList<Map<String, String>>();
		
		try {
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}
			if (ontology != null) {
				ontology = getFullUrl(prefix, java.net.URLDecoder.decode(ontology, "UTF-8"));
			}
			if (resource != null) {
				resource = getFullUrl(prefix, java.net.URLDecoder.decode(resource, "UTF-8"));
			}
			
			String s = "";
			if (ontology == null && resource == null) {
				status = HttpStatus.BAD_REQUEST;
				return new ResponseEntity<List<Map<String, String>>>(concepts, status);
			} else if (resource != null) {
				s = initPrefix(prefix) + "SELECT distinct ?predicate ?object WHERE {"
						//+ "?type rdfs:subClassOf* <" + ontology + "> ."
						//+ "?predicate <http://www.w3.org/2000/01/rdf-schema#domain> ?type ."
						+ "values (?v) { (owl:ObjectProperty)(owl:DatatypeProperty) }"
						+ "?predicate a ?v ."
						+ resource + " ?predicate ?object }";
			} else {
				s = initPrefix(prefix) + "SELECT distinct ?subject ?predicate ?object WHERE {"
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
	        concepts.addAll(prepareNode(resource, results));
			status = HttpStatus.OK;
				
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (qExe != null)
				qExe.close();
		}

		return new ResponseEntity<List<Map<String, String>>>(concepts, status);
	}
	
	
	@RequestMapping(value="/ontology/link", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> linkWithOntology(@RequestHeader("Authorization") String Authorization, @RequestParam String endpointURL, @RequestParam String ontology, @RequestParam String localElement, @RequestParam String repo_name, String level, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		String message = "Linking with ontology fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			endpointURL = java.net.URLDecoder.decode(endpointURL, "UTF-8");
			ontology = getFullUrl(prefix, java.net.URLDecoder.decode(ontology, "UTF-8"));
			localElement = getFullUrl(prefix, java.net.URLDecoder.decode(localElement, "UTF-8"));
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}
			if (level == null) {
				level = "model";
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			/*if (gettingStartedApplication.insert(triples)) {
				status = HttpStatus.OK;
				message  = "Adding triples succeeds";
			}*/
			
			manager = new UserManager(username, password);
			
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
				message = "Level parameter can be only metamodel, model or instance";
				return new ResponseEntity<String>(message, status);
			}
			
			String query = "SPARQL " + initPrefix(prefix) + " INSERT INTO graph <" + graph + ">{"
					+ localElement + " owl:sameAs " + ontology + " . "
					+ ontology + " dm:endpoint " + endpointURL
					+ "}";
			
			log(query);
			
			gettingStartedApplication.update(query);
			status = HttpStatus.OK;
			message  = "Linking with ontology succeeds";
			
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
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping(value="/ontology/resource/link", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> linkWithResource(@RequestHeader("Authorization") String Authorization, @RequestParam String endpointURL, @RequestParam String resource, @RequestParam String localElement, @RequestParam String repo_name, String prefix) {
		return linkWithOntology(Authorization, endpointURL, resource, localElement, repo_name, "instance", prefix);
	}
	
	@RequestMapping(value="/subscibe", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<Map<String, String>> subscibe(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String subscribe_level, String namespace) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		String message = "subscribing fails";
		Map<String, String> tuple = new HashMap<String, String>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			if (namespace == null) {
				namespace = "";
			}
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			namespace = java.net.URLDecoder.decode(namespace, "UTF-8");

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);

			if (subscribe_level.equals("repository") || subscribe_level.equals("namespace") || subscribe_level.equals("instance")) {
				if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
					status = HttpStatus.FORBIDDEN;
					return new ResponseEntity<Map<String, String>>(tuple, status);
				} else {
					tuple.put("topic", repo_name.replace(":", "").replace("/", "_") + "_" + subscribe_level + (subscribe_level.equals("namespace") ? "_" + namespace.replace(":", "").replace("/", "_").replace("#", "") : ""));
				}
			} else {
				status = HttpStatus.BAD_REQUEST;
				message = "Subsciption level parameter can be only repository, namespace or instance";
				tuple.put("message", message);
				return new ResponseEntity<Map<String, String>>(tuple, status);
			}
			
			/*String query = "INSERT INTO DB.DBA.Subscription"
					+ "(U_ID, repo_name, level, namespace, email)"
					+ "VALUES"
					+ "(" + manager.getUserIDFromUsername(username) + ", '" + repo_name + "', '" + subscribe_level + "', '" + namespace + "', '" + email + "')";
			
			log(query);
			
			gettingStartedApplication.update(query);*/
			status = HttpStatus.OK;
			message  = "subscribing succeeds";
			
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
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<Map<String, String>>(tuple, status);
	} 
	
	@RequestMapping(value="/predefine_sparql", method=RequestMethod.POST)
	@ResponseBody
	public HttpEntity<String> predefinedSparql(@RequestHeader("Authorization") String Authorization, @RequestParam String function_name, @RequestParam String query) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		UserManager manager = null;
		String message = "Predefining SPARQL fails";
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			query = java.net.URLDecoder.decode(query, "UTF-8");
			
			manager = new UserManager(username, password);
			Role role = manager.getUserRole(username);
			if (role != Role.superAdmin && role != Role.admin) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<String>(message, status);
			}

			File file = new File("../webapps/predefined_query.json");
			
			log(file.getAbsolutePath());
			 
			String previousQuery = "{}";
			if (!file.exists()) {
				file.createNewFile();
			} else {
				BufferedReader br = new BufferedReader(new FileReader(file));
			    try {
			        StringBuilder sb = new StringBuilder();
			        String line = br.readLine();

			        while (line != null) {
			            sb.append(line);
			            sb.append(System.lineSeparator());
			            line = br.readLine();
			        }
			        previousQuery = sb.toString();
			    } finally {
			        br.close();
			    }
			}
			
			JSONObject ob = new JSONObject(previousQuery);
			ob.put(function_name, query);
 
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(ob.toString());
			bw.close();
			
			status = HttpStatus.OK;
			message  = "Predefining SPARQL succeeds";
			
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (UnsupportedEncodingException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<String>(message, status);
	}
	
	@RequestMapping("/sparql_parser")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> sparql_parser(@RequestHeader("Authorization") String Authorization, @RequestParam String repo_name, @RequestParam String function_name, @RequestParam(value = "param[]") String[] paramValues, String level, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		UserManager manager = null;
		List<Map<String, String>> triples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			repo_name = java.net.URLDecoder.decode(repo_name, "UTF-8");
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}
			
			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			manager = new UserManager(username, password);
			if (!manager.isUserHasPermission(manager.getUserIDFromUsername(username), repo_name + "/model" , false)) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(triples, status);
			}
			
			String graph = "";
			if (level == null) {
				graph = "from <" + repo_name + "/metamodel> from <" + repo_name + "/model>" + " from <" + repo_name + "/instance>";
			} else if (level.equals("metamodel") || level.equals("model") || level.equals("instance")) {
				graph = "from <" + repo_name + "/" + level + ">";
			} else {
				status = HttpStatus.BAD_REQUEST;
				Map<String, String> map = new HashMap<String, String>();
				map.put("message", "Level parameter can be only metamodel, model or instance");
				triples.add(map);
				return new ResponseEntity<List<Map<String, String>>>(triples, status);
			}
			
			String query = "";
			File file = new File("../webapps/predefined_query.json");
			BufferedReader br = new BufferedReader(new FileReader(file));
		    try {
		        StringBuilder sb = new StringBuilder();
		        String line = br.readLine();

		        while (line != null) {
		            sb.append(line);
		            sb.append(System.lineSeparator());
		            line = br.readLine();
		        }
		        String text = sb.toString();
		        JSONObject ob = new JSONObject(text);
		        query = ob.getString(function_name);
		    } catch (JSONException e) {
				e.printStackTrace();
			} finally {
				br.close();
		    }
			
			for (int i = 1; i <= paramValues.length; i++) {
				query = query.replace("$" + i, paramValues[i - 1]);
			}
			
			query = "SPARQL " + initPrefix(prefix) +
					query.substring(0, query.toLowerCase().indexOf("where")) + graph 
					+ query.substring(query.toLowerCase().indexOf("where"));
			
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
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} finally {
			if (gettingStartedApplication != null)
				gettingStartedApplication.shutdown();
			if (manager != null)
				manager.closeConnection();
		}

		return new ResponseEntity<List<Map<String, String>>>(triples, status);
	}
	
	
	/*private static void notification(List<Triple> triples, final String repo_name, final String level, List<Map<String, String>> prefix) {
		String topic = repo_name.replace(":", "").replace("/", "_") + "_repository"; //+ (level.equals("namespace") ? "_" + map.get("namespace") : "");
		Notification noti = new Notification(topic);
        noti.writeMessage("The metadata repository (" + repo_name + ", repository level) has been made changes. Please refer to the new version");
        noti.close();
        
        if (level.equals("instance")) {
        	topic = repo_name.replace(":", "").replace("/", "_") + "_instance"; //+ (level.equals("namespace") ? "_" + map.get("namespace") : "");
			noti = new Notification(topic);
	        noti.writeMessage("The metadata repository (" + repo_name + ", instance level) has been made changes. Please refer to the new version");
	        noti.close();
        } 
        
        List<String> alN = new ArrayList<String>();
        for (Triple triple : triples) {
        	String namespaceS = getPrefix(prefix, triple.getSubject());
        	String namespaceO = getPrefix(prefix, triple.getObject());
			if (namespaceS.indexOf("<") < 0 && !alN.contains(namespaceS)) {
				
				topic = repo_name.replace(":", "").replace("/", "_") + "_namespace_" + namespaceS;
				noti = new Notification(topic);
		        noti.writeMessage("The metadata repository (" + repo_name + ", namespace " + namespaceS + " level) has been made changes. Please refer to the new version");
		        noti.close();
		        alN.add(namespaceS);
			}
			if (triple.getObject().indexOf('"') < 0 && triple.getObject().indexOf('<') < 0 && !alN.contains(namespaceO)) {
		        topic = repo_name.replace(":", "").replace("/", "_") + "_namespace_" + namespaceO;
				noti = new Notification(topic);
		        noti.writeMessage("The metadata repository (" + repo_name + ", namespace " + namespaceO + " level) has been made changes. Please refer to the new version");
		        noti.close();
		        alN.add(namespaceS);
			}
		}
	}*/
	
	private static void sendEmail(String email, String level) {
	      String to = email;
	      String from = "varunya.qt@gmail.com";
	      Properties properties = System.getProperties();
	      properties.put("mail.smtp.auth", "true");
	      properties.put("mail.smtp.starttls.enable", "true");
	      properties.put("mail.smtp.host", "smtp.gmail.com");
	      properties.put("mail.smtp.port", "587");
	      Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
	    	    protected PasswordAuthentication getPasswordAuthentication() {
	    	        return new PasswordAuthentication("varunya.qt@gmail.com", "abc20042012");
	    	    }
	    	});

	      try{
	         MimeMessage message = new MimeMessage(session);
	         message.setFrom(new InternetAddress(from));
	         message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
	         message.setSubject("Repository Change Notification");
	         message.setText("The metadata repository (" + level + " level) has been made changes. Please refer to the new version");
	         Transport.send(message);
	         System.out.println("Sent message successfully....");
	      }catch (MessagingException mex) {
	         mex.printStackTrace();
	      }
	}
	
	
	@RequestMapping("/sparql")
	@ResponseBody
	public HttpEntity<List<Map<String, String>>> sparql(@RequestHeader("Authorization") String Authorization, String query, String prefix) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		TripleStoreConnector gettingStartedApplication = null;
		List<Map<String, String>> tuples = new ArrayList<Map<String, String>>();
		
		String[] us_pass = getUsernameAndPassword(Authorization);
		String username = us_pass[0];
		String password = us_pass[1];
		
		try {
			if (prefix != null) {
				prefix = java.net.URLDecoder.decode(prefix, "UTF-8");
			}

			gettingStartedApplication = new TripleStoreConnector(TripleStoreConnector.initiateParameters(null).getParameters(), dba_username, dba_password);
			
			UserManager manager = new UserManager(username, password);
			if (!manager.getUserRole(username).toString().equals("superAdmin")) {
				status = HttpStatus.FORBIDDEN;
				return new ResponseEntity<List<Map<String, String>>>(tuples, status);
			}
			
			query = java.net.URLDecoder.decode(query, "UTF-8");
			log("SPARQL " + query);
			tuples = gettingStartedApplication.queryTuples("SPARQL " + initPrefix(prefix) + query);
			status = HttpStatus.OK;
				
		} catch (UnauthorizedException e) {
			status = HttpStatus.UNAUTHORIZED;
		} catch (IllegalArgumentException e) {
			status = HttpStatus.BAD_REQUEST;
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			status = HttpStatus.BAD_REQUEST;
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
	
	private static String initPrefix(String defaultPrefix) throws JSONException {
		String prefix = "";
		if (defaultPrefix != null) {
			JSONArray preAr = new JSONArray(defaultPrefix);
			for (int i = 0; i < preAr.length(); i++) {
				JSONObject ob = preAr.getJSONObject(i);
				prefix += "PREFIX " + ob.getString("prefix") + ":<" + ob.getString("url") + ">";
			}
		}
		return prefix;
	}
	
	private static String getFullPrefix(String defaultPrefix, String url) throws JSONException {
		if (defaultPrefix != null && url != null) {
			JSONArray prefixAr = new JSONArray(defaultPrefix);
			url = url.replace("<", "").replace(">", "");
			for (int i = 0; i < prefixAr.length(); i++) {
				JSONObject p = prefixAr.getJSONObject(i);
				int position = url.indexOf(p.getString("url"));
				if (position >= 0) {
					return p.getString("prefix") + ":" + url.substring(position + p.getString("url").length()); 
				}
			}
		}
		return url;
	}
	
	private static String getUrl(String defaultPrefix, String prefixS) throws JSONException {
		if (defaultPrefix != null && prefixS != null && prefixS.indexOf(":") > 0) {
			JSONArray prefixAr = new JSONArray(defaultPrefix);
			String prefix = prefixS.substring(0, prefixS.indexOf(":"));
			for (int i = 0; i < prefixAr.length(); i++) {
				JSONObject p = prefixAr.getJSONObject(i);
				if (prefix.equals(p.getString("prefix"))) {
					return p.getString("url");
				}
			}
		}
		return prefixS;
	}
	
	private static String getFullUrl(String defaultPrefix, String prefixS) throws JSONException {
		if (defaultPrefix != null && prefixS != null && prefixS.indexOf(":") > 0) {
			JSONArray prefixAr = new JSONArray(defaultPrefix);
			String prefix = prefixS.substring(0, prefixS.indexOf(":"));
			for (int i = 0; i < prefixAr.length(); i++) {
				JSONObject p = prefixAr.getJSONObject(i);
				if (prefix.equals(p.getString("prefix"))) {
					return "<" + p.getString("url") + prefixS.substring(prefixS.indexOf(":") + 1) + ">";
				}
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
	        		concept.put("subject", resource);
	        	}
	    		concept.put("predicate", "<" + sol.get("predicate").asNode().getURI() + ">");
	        	if (sol.get("object").isLiteral()) {
	        		if (sol.get("object").asLiteral().getDatatypeURI() != null) {
	        			concept.put("object", '"' + sol.get("object").asLiteral().getString().replace("\"", "") + '"' + "^^<" + sol.get("object").asLiteral().getDatatypeURI() + ">");
	        		} else {
	        			concept.put("object", '"' + sol.get("object").asLiteral().getString().replace("\"", "") + '"' + "@en");
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
	
	public static void main(String[] args) throws JSONException {
		
		APIController con = new APIController();
		List<Triple> list = new ArrayList<Triple>();
		Triple triple = new Triple();
		triple.setSubject("dm:iris");
		triple.setPredicate("dm:hasFeature");
		triple.setObject("dm:dirty_iris");
		list.add(triple);
		
		String prefix = "[{ \"prefix\": \"rdfs\", \"url\": \"http://www.w3.org/2000/01/rdf-schema#\"},"
				  + "{ \"prefix\": \"rdf\", \"url\": \"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"},"
				  + "{ \"prefix\": \"ns\", \"url\": \"http://rdfs.org/sioc/ns#\"},"
				  + "{ \"prefix\": \"dm\", \"url\": \"http://dm.com#\"},"
				  + "{ \"prefix\": \"type\", \"url\": \"http://rdfs.org/sioc/types#\"},"
				  + "{ \"prefix\": \"xsd\", \"url\": \"http://www.w3.org/2001/XMLSchema#\"},"
				  + "{ \"prefix\": \"owl\", \"url\": \"http://www.w3.org/2002/07/owl#\"},"
				  + "{ \"prefix\": \"dbpedia-owl\", \"url\": \"http://dbpedia.org/ontology/\"},"
				  + "{ \"prefix\": \"dbpprop\", \"url\": \"http://dbpedia.org/property/\"},"
				  + "{ \"prefix\": \"dbres\", \"url\": \"http://dbpedia.org/resource/\"}]";
		
		System.out.println(getFullPrefix(prefix, "<http://rdfs.org/sioc/ns#noon>"));
		System.out.println(getFullPrefix(prefix, "<http://dbpedia.org/ontology/Capital>"));
		System.out.println(getFullPrefix(prefix, "http://dbpedia.org/ontology/Capital"));
		System.out.println(getFullPrefix(prefix, "<http://dbpedia.org/resource/Capital>"));
		System.out.println(getFullPrefix(prefix, "ns:DataSet"));
		System.out.println(getFullPrefix(prefix, "db:DataSet"));
		System.out.println(getFullPrefix(prefix, "\"abc\""));
		
		System.out.println(getUrl(prefix, "<http://rdfs.org/sioc/ns#noon>"));
		System.out.println(getUrl(prefix, "<http://dbpedia.org/ontology/Capital>"));
		System.out.println(getUrl(prefix, "http://dbpedia.org/ontology/Capital"));
		System.out.println(getUrl(prefix, "<http://dbpedia.org/resource/Capital>"));
		System.out.println(getUrl(prefix, "ns:DataSet"));
		System.out.println(getUrl(prefix, "dbpedia-owl:DataSet"));
		System.out.println(getUrl(prefix, "db:DataSet"));
		System.out.println(getUrl(prefix, "\"abc\""));
		
		System.out.println(getFullUrl(prefix, "<http://rdfs.org/sioc/ns#noon>"));
		System.out.println(getFullUrl(prefix, "<http://dbpedia.org/ontology/Capital>"));
		System.out.println(getFullUrl(prefix, "http://dbpedia.org/ontology/Capital"));
		System.out.println(getFullUrl(prefix, "<http://dbpedia.org/resource/Capital>"));
		System.out.println(getFullUrl(prefix, "ns:DataSet"));
		System.out.println(getFullUrl(prefix, "dbpedia-owl:DataSet"));
		System.out.println(getFullUrl(prefix, "db:DataSet"));
		System.out.println(getFullUrl(prefix, "\"abc\""));
		
		con.addTriples("Basic ZGJhOmRiYQ==", list, "http://localhost:8890/noon", "instance", prefix, false);
		
		//log(con.sparql_parser("Basic ZGJhOmRiYQ==", "http://localhost:8890/noon", "ns:age4", "ns:hasProperty", "ns:blah", "instance", "").toString());
		
		/*Model model = RDFDataMgr.loadModel("./ontology/example.rdf");
		log(model.getNsPrefixMap().toString());
		StmtIterator stmtIter = model.listStatements();
		while ( stmtIter.hasNext() ) {
	        Statement stmt = stmtIter.nextStatement();
	        log(stmt.getSubject().getURI() + " " + stmt.getPredicate().getURI());
	    }*/
		
		//sendEmail("vam_han@hotmail.com");
	}
}



