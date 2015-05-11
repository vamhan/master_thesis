package controller;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import model.Triple;
import model.UserManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yaml.snakeyaml.util.UriEncoder;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import static org.junit.Assert.*;

public class Testing {
	
	private static String DEFAULT_GRAPH = "<http://localhost:8890/noon>";
	private static String DEFAULT_SCHEMA = "<http://localhost:8890/schema/test>";
	private static String DEFAULT_RULE = "http://localhost:8890/schema/property_rules1";
	private static String host = "http://localhost:8080";
	
	@Test
	public void testUser() {
		HttpClient client = new DefaultHttpClient();
    	HttpPost post = new HttpPost(host + "/users");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			
			// create user
			String authString = "vamhan:123";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			List<NameValuePair> params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("username", "noon"));
			params.add(new BasicNameValuePair("password", "noon"));
			params.add(new BasicNameValuePair("role", "abc"));
			post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED);
			HttpEntity enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();

			authString = "vamhan:mofnun";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
			
			authString = "dba:dba";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
			
			params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("username", "noon"));
			params.add(new BasicNameValuePair("password", "noon"));
			params.add(new BasicNameValuePair("role", "viewer"));
			post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        UserManager manager = new UserManager("dba", "dba");
	        String noonID = manager.getUserIDFromUsername("noon");
			
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
			if (enty != null)
	            enty.consumeContent();
			
			// edit user role
			HttpPut put = new HttpPut(host + "/users/" + noonID +"/role?role=contributor");
			put.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(put);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent(); 
	        
	        put.setURI(new URI(host + "/users/1000/role?role=contributor"));
			response = client.execute(put);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
			if (enty != null)
	            enty.consumeContent();
			
			// change user password
			/*HttpPut put2 = new HttpPut(host + "/users/113/password");
			put2.setHeader("Authorization", "Basic " + authStringEnc);
			params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("old_password", "abc"));
			params.add(new BasicNameValuePair("new_password", "abc"));
			put2.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			response = client.execute(put2);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent(); 
	        
	        params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("old_password", "noon"));
			params.add(new BasicNameValuePair("new_password", "abc"));
			put2.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			response = client.execute(put2);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
			if (enty != null)
	            enty.consumeContent();*/
			
			//create another user
			params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("username", "nink"));
			params.add(new BasicNameValuePair("password", "nink"));
			params.add(new BasicNameValuePair("role", "viewer"));
			post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        String ninkID = manager.getUserIDFromUsername("nink");
	        
			params = new ArrayList<NameValuePair>(2);
			params.add(new BasicNameValuePair("username", "pui"));
			params.add(new BasicNameValuePair("password", "pui"));
			params.add(new BasicNameValuePair("role", "admin"));
			post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        String puiID = manager.getUserIDFromUsername("pui");
			
			//create repo
			/*post.setURI(new URI(host + "/repositories?repo_name=www.noon.com"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();*/
	        
	        //add initial triples
	        post.setURI(new URI(host + "/triples?repo_name=www.noon.com&level=model"));
	        StringEntity triple = new StringEntity("[{\"subject\":\"<http://example.org/owlim#Woman>\",\"predicate\":\"<http://www.w3.org/2000/01/rdf-schema#subClassOf>\",\"object\":\"<http://example.org/owlim#Human>\"}, {\"subject\":\"<http://example.org/owlim#Woman>\",\"predicate\":\"<http://www.w3.org/2000/01/rdf-schema#subClassOf>\",\"object\":\"<http://noon.com#Animal>\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        post.setURI(new URI(host + "/triples?repo_name=www.noon.com&level=instance"));
	        triple = new StringEntity("[{\"subject\":\"<http://example.org/owlim#Nink>\",\"predicate\":\"<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\",\"object\":\"<http://example.org/owlim#Woman>\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        //set permission
	        post.setURI(new URI(host + "/repositories/permission?repo_name=www.noon.com&userid=" + noonID + "&permission=2"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        post.setURI(new URI(host + "/repositories/permission?repo_name=www.noon.com&userid=" + ninkID + "&permission=1"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        post.setURI(new URI(host + "/repositories/permission?repo_name=www.noon.com&userid=" + puiID + "&permission=0"));
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        // query triple
	        HttpGet get = new HttpGet(host + "/triples?repo_name=www.noon.com");
	        authString = "noon:noon";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        authString = "pui:pui";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        //add triple
	        post.setURI(new URI(host + "/triples?repo_name=www.noon.com&level=model"));
	        authString = "noon:noon";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        authString = "pui:pui";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        post.setURI(new URI(host + "/triples?repo_name=www.noon.com&level=instance"));
	        authString = "noon:noon";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        post.setURI(new URI(host + "/triples?repo_name=www.noon.com&level=instance"));
	        authString = "nink:nink";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
			
	        
	        // delete repo
 			/*HttpDelete delete = new HttpDelete(host + "/repositories?repo_name=www.noon.com");
 			delete.setHeader("Authorization", "Basic " + authStringEnc);
 			response = client.execute(delete);
 			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
 			enty = response.getEntity();
 	        if (enty != null)
 	            enty.consumeContent(); */
			
			// delete user
	        HttpDelete delete = new HttpDelete(host + "/users/" + noonID);
	        authString = "dba:dba";
			authEncBytes = Base64.encodeBase64(authString.getBytes());
			authStringEnc = new String(authEncBytes);
			delete.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(delete);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent(); 
	        
	        delete.setURI(new URI(host + "/users/" + ninkID));
			response = client.execute(delete);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent(); 
	        
	        delete.setURI(new URI(host + "/users/" + puiID));
			response = client.execute(delete);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent(); 
	        
			response = client.execute(delete);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			post.abort();
		}
	}
	
	@Test
    public void testListUsers() {
    	HttpClient client = new DefaultHttpClient();
    	HttpGet get = new HttpGet(host + "/users");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			
			entity = response.getEntity();

	        if (entity != null) {
	        	String retSrc = EntityUtils.toString(entity); 
	        	JSONArray result = new JSONArray(retSrc);
	            
	        	assertTrue(result.length() > 0);
	        	assertTrue(result.length() == 0);
	        }
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			get.abort();
		}
    }
	

    @Test
    public void testListTriples() {
    	HttpClient client = new DefaultHttpClient();
    	HttpGet get = new HttpGet(host + "/triples?repo_name=www.noon");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			HttpEntity enty = response.getEntity();
			if (enty != null) {
	        	String retSrc = EntityUtils.toString(enty); 
	        	JSONArray result = new JSONArray(retSrc);
	            
	        	assertTrue(result.length() == 0);
	        }
	        if (enty != null)
	            enty.consumeContent();
	        
	        get.setURI(new URI(host + "/triples"));
	        response = client.execute(get);
	        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
	        
	        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			get.abort();
		}
    }
    
    @Test
    public void testSearchTriples() {
    	HttpClient client = new DefaultHttpClient();
    	String url = host + "/triples/search?";
    	HttpGet get = new HttpGet(url + "repo_name=www.noon.com&keyword=Nink");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			
			entity = response.getEntity();
        	String retSrc = EntityUtils.toString(entity); 
        	JSONArray result = new JSONArray(retSrc);
        	assertTrue(result.length() > 0);
        	HttpEntity enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        get.setURI(new URI(url + "repo_name=www.noon.com&keyword=" + UriEncoder.encode("http://example.org/owlim#Woman")));
	        response = client.execute(get);
	        entity = response.getEntity();
        	retSrc = EntityUtils.toString(entity); 
        	result = new JSONArray(retSrc);
        	assertTrue(result.length() > 0);
        	enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
        	
        	get.setURI(new URI(url + "repo_name=www.noon.com&keyword=" + UriEncoder.encode("http://dbtune.org/bbc/peel/artist/33")));
	        response = client.execute(get);
	        entity = response.getEntity();
        	retSrc = EntityUtils.toString(entity); 
        	result = new JSONArray(retSrc);
        	assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        	assertTrue(result.length() == 0);
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			get.abort();
		}
    }
    
    @Test
    public void testSearchSubject() {
    	HttpClient client = new DefaultHttpClient();
    	String url = host + "/triples/subject/search?";
    	HttpGet get = new HttpGet(url + "repo_name=www.noon.com&keyword=Nink");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			
			entity = response.getEntity();
        	String retSrc = EntityUtils.toString(entity); 
        	JSONArray result = new JSONArray(retSrc);
        	assertTrue(result.length() > 0);
        	HttpEntity enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        get.setURI(new URI(url + "repo_name=www.noon.com&keyword=" + UriEncoder.encode("http://example.org/owlim#Woman")));
	        response = client.execute(get);
	        entity = response.getEntity();
        	retSrc = EntityUtils.toString(entity); 
        	result = new JSONArray(retSrc);
        	assertTrue(result.length() > 0);
        	enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
        	
        	get.setURI(new URI(url + "repo_name=www.noon.com&keyword=" + UriEncoder.encode("http://dbtune.org/bbc/peel/artist/33")));
	        response = client.execute(get);
	        entity = response.getEntity();
        	retSrc = EntityUtils.toString(entity); 
        	result = new JSONArray(retSrc);
        	assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        	assertTrue(result.length() == 0);
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			get.abort();
		}
    }
    
    @Test
    public void testSearchPredicate() {
    	HttpClient client = new DefaultHttpClient();
    	String url = host + "/triples/predicate/search?";
    	HttpGet get = new HttpGet(url + "repo_name=www.noon.com&keyword=type");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			
			entity = response.getEntity();
        	String retSrc = EntityUtils.toString(entity); 
        	JSONArray result = new JSONArray(retSrc);
        	assertTrue(result.length() > 0);
        	HttpEntity enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        get.setURI(new URI(url + "repo_name=www.noon.com&keyword=" + UriEncoder.encode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")));
	        response = client.execute(get);
	        entity = response.getEntity();
        	retSrc = EntityUtils.toString(entity); 
        	result = new JSONArray(retSrc);
        	assertTrue(result.length() > 0);
        	enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
        	
        	get.setURI(new URI(url + "repo_name=www.noon.com&keyword=" + UriEncoder.encode("http://dbtune.org/bbc/peel/artist/33")));
	        response = client.execute(get);
	        entity = response.getEntity();
        	retSrc = EntityUtils.toString(entity); 
        	result = new JSONArray(retSrc);
        	assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        	assertTrue(result.length() == 0);
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			get.abort();
		}
    }
    
    
    @Test
	public void testAddTriples() {
		HttpClient client = new DefaultHttpClient();
    	HttpPost post = new HttpPost(host + "/triples?repo_name=www.noon.com&level=model");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
	        StringEntity triple = new StringEntity("[{\"subject\":\"<http://example.org/owlim#Nink>\",\"predicate\":\"<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\",\"object\":\"<http://example.org/owlim#Woman>\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			HttpEntity enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        triple = new StringEntity("[{\"subject\":\"<http://example.org/owlim#Nink>\",\"predicate\":\"<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        triple = new StringEntity("[{\"subject\":\"nink\",\"predicate\":\"<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\",\"object\":\"<http://example.org/owlim#Woman>\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        triple = new StringEntity("[{\"subject\":\"<http://example.org/owlim#Nink>\",\"predicate\":\"<http://www.w3.org/2000/01/rdf-schema#label>\",\"object\":\"'nink'\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        triple = new StringEntity("[{\"subject\":\"ex:Nink\",\"predicate\":\"rdfs:label\",\"object\":\"'nink'\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        triple = new StringEntity("[{\"subject\":\"abc:Nink\",\"predicate\":\"rdfs:label\",\"object\":\"'nink'\"}]");
	        triple.setContentType("application/json");
	        post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
			
			post.setURI(new URI(host + "/triples?repo_name=www.noon.com&level=abc"));
			post.setEntity(triple);
			response = client.execute(post);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
			
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED);
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			post.abort();
		}
	}
    
    @Test
    public void testTypeInstances() {
    	HttpClient client = new DefaultHttpClient();
    	HttpGet get = new HttpGet(host + "/types/ex:Human/instances?repo_name=www.noon.com");
    	HttpResponse response;
    	HttpEntity entity;
		try {
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			get.setHeader("Authorization", "Basic " + authStringEnc);
			
			response = client.execute(get);
			assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
			HttpEntity enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();

	        get.setURI(new URI(host + "/types/nn:Animal/instances?repo_name=www.noon.com"));
	        response = client.execute(get);
	        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST);
	        enty = response.getEntity();
	        if (enty != null)
	            enty.consumeContent();
	        
	        String prefix = UriEncoder.encode("PREFIX nn:<http://noon.com#>");
	        get.setURI(new URI(host + "/types/nn:Animal/instances?repo_name=www.noon.com&prefix=" + prefix));
	        response = client.execute(get);
	        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
	        
	        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED);
			
	        
		} catch (Exception e1) {
			e1.printStackTrace();
		} finally {
			get.abort();
		}
    }
}