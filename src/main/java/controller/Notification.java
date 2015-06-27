package controller;

import javax.jms.*;

public class Notification {
	private Connection connection;
	private ConnectionFactory connFactory;
	private Topic topic;
	private Session session;

	public Notification(String topicName) {
		try {

            /*
             * The following code uses the JNDI File System Service Provider
             * to lookup() Administered Objects that were stored in the
             * Administration Console Tutorial in the Administrator's Guide
             *
             * The following code (in this comment block replaces the
             * statements in Steps 2 and 5 of this example.
             *
             *****/
                /*String MYCF_LOOKUP_NAME = "MyConnectionFactory";
                String MYQUEUE_LOOKUP_NAME = "MyQueue";

                Hashtable env;
                Context ctx = null;

                env = new Hashtable();

                // Store the environment variable that tell JNDI which initial context
                // to use and where to find the provider.

                // For use with the File System JNDI Service Provider
                env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.fscontext.RefFSContextFactory");
                // On Unix, use file:///tmp instead of file:///C:/Temp
                env.put(Context.PROVIDER_URL, "file:///C:/Temp");
 
                // Create the initial context.
                ctx = new InitialContext(env);

                // Lookup my connection factory from the admin object store.
                // The name used here here must match the lookup name
                // used when the admin object was stored.
                myConnFactory = (javax.jms.ConnectionFactory) ctx.lookup(MYCF_LOOKUP_NAME);
      
                // Lookup my queue from the admin object store.
                // The name I search for here must match the lookup name used when
                // the admin object was stored.
                myQueue = (javax.jms.Queue)ctx.lookup(MYQUEUE_LOOKUP_NAME);
            /*****
            *
            */

            //Step 2:
            //Instantiate a Sun Java(tm) System Message Queue ConnectionFactory 
      //administered object.
            //This statement can be eliminated if the JNDI code above is used.
            connFactory = new com.sun.messaging.ConnectionFactory();


            //Step 3:
            //Create a connection to the Sun Java(tm) System Message Queue Message 
      //Service.
            connection = connFactory.createConnection();


            //Step 4:
            //Create a session within the connection.
            session = connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);


            //Step 5:
            //Instantiate a Sun Java(tm) System Message Queue Destination 
      //administered object.
            //This statement can be eliminated if the JNDI code above is used.
            topic = new com.sun.messaging.Topic(topicName);

        } catch (Exception jmse) {
            System.out.println("Exception occurred : " + jmse.toString());
            jmse.printStackTrace();
        }
	}

	/* Create and send message using topic publisher */
	protected void writeMessage(String text) {
        MessageProducer myMsgProducer;
		try {
			myMsgProducer = session.createProducer(topic);
			TextMessage myTextMsg = session.createTextMessage();
	        myTextMsg.setText(text);
	        System.out.println("Sending Message: " + myTextMsg.getText());
	        myMsgProducer.send(myTextMsg);
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/* Close the JMS connection */
	public void close() {
		try {
			session.close();
			connection.close();
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {
            Notification noti = new Notification("http__localhost8890_noon_repository");
            noti.writeMessage("Hello!");
            
            noti.close();

        } catch (Exception jmse) {
            System.out.println("Exception occurred : " + jmse.toString());
            jmse.printStackTrace();
        }
	}
}