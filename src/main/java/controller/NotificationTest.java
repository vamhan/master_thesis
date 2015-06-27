package controller;

import javax.jms.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class NotificationTest implements javax.jms.MessageListener {
	
	private static String host = "http://localhost:8081/myapp";
	private static String repo_name = "http://localhost:8890/noon";

	/* Receive message from topic subscriber */
	public void onMessage(Message message) {
		try {
			TextMessage textMessage = (TextMessage) message;
			String text = textMessage.getText();
			System.out.println(text);
		} catch (JMSException jmse) {
			jmse.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {
			
			HttpClient client = new DefaultHttpClient();
	    	HttpPost post = new HttpPost(host + "/subscibe?repo_name=" + repo_name + "&subscribe_level=namespace&namespace=http://rdfs.org/sioc/ns#");
			
			String authString = "dba:dba";
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);
			post.setHeader("Authorization", "Basic " + authStringEnc);
			
			HttpResponse APIresponse = client.execute(post);
			HttpEntity entity = APIresponse.getEntity();
	    	String retSrc = EntityUtils.toString(entity); 
	    	System.out.println(retSrc);
	    	JSONObject result = new JSONObject(retSrc);
			HttpEntity enty = APIresponse.getEntity();
	        if (enty != null)
	            enty.consumeContent();
			
            ConnectionFactory myConnFactory = new com.sun.messaging.ConnectionFactory();
            Connection myConn = myConnFactory.createConnection();
            Session mySess = myConn.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
            Topic topic = new com.sun.messaging.Topic(result.getString("topic"));

            MessageConsumer myMsgConsumer = mySess.createConsumer(topic);
            myConn.start();
            
            MessageListener listener = new NotificationTest();
            myMsgConsumer.setMessageListener(listener);

            //mySess.close();
            //myConn.close();

        } catch (Exception jmse) {
            System.out.println("Exception occurred : " + jmse.toString());
            jmse.printStackTrace();
        }
	}
}