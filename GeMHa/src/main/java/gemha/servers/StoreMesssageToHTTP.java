package gemha.servers;

import java.util.Random;
import java.util.logging.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import lw.utils.LwLogger;
import gemha.interfaces.IStoreMesssage;
import gemha.support.*;

/**
  * This class puts messages to a HTTP Server via a post action.
  *
  * @author Liam Wade
  * @version 1.0 09/03/2013
  */
public class StoreMesssageToHTTP implements IStoreMesssage {

    private static final Logger logger = Logger.getLogger("gemha");

    private String serverUrl;			// the URL of the target server
	private String endPointName;		// the target endpoint at the server (e.g. a servlet name)
	
	private boolean HTTPWithBackoff = true;	// should we back off exponentially when trying to connect
	private String dataFormat;			// the format of outgoing messages e.g "XML"

	public StoreMesssageToHTTP(String serverUrl, String endPointName, boolean HTTPWithBackoff, String dataFormat) {
		this.serverUrl = serverUrl;
		this.endPointName = endPointName;
		this.HTTPWithBackoff = HTTPWithBackoff;
		this.dataFormat = dataFormat;
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIStoreMesssage Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Open any connections
	  *
	  * @throws MessagingException when any error is encountered
	  */
	public void openStorage()
	 			throws MessagingException {
	}

	/**
	  * Close any connections
	  *
	  * @throws MessagingException when any error is encountered
	  */
	public void closeStorage()
	 			throws MessagingException {
	}

	/**
	  * Process a message
	  *
	  * @param message the message to be processed
	  * @param instructions instructions on how the message is to be processed (will be implementation-specific). Can be null
	  *
	  * @throws MessagingException when any error is encountered
	  */
	public void putMessage(String message, String auditKey, String instructions) throws MessagingException {

		String endPoint = serverUrl + "/" + endPointName;
		
		if (HTTPWithBackoff) {
			if ( postWithBackoff(endPoint, message)) {
				logger.info("Response message with AuditKey Value " + auditKey + " posted to " + endPoint);
			}
			else {
				// problem posting message!
				logger.severe("Response message with AuditKey Value " + auditKey + " could not be posted to " + endPoint);
				throw new MessagingException("Response message with AuditKey Value " + auditKey + " could not be posted to " + endPoint);
			}
		} else {
			if ( postWithNoBackoff(endPoint, message)) {
				logger.info("Response message with AuditKey Value " + auditKey + " posted to " + endPoint);
			}
			else {
				// problem posting message!
				logger.severe("Response message with AuditKey Value " + auditKey + " could not be posted to " + endPoint);
				throw new MessagingException("Response message with AuditKey Value " + auditKey + " could not be posted to " + endPoint);
			}
		}
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performStoreMessageCleanup(LwLogger shutdownLogger) {

	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIStoreMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	 * Issue a POST request to the server, failing immediately, if a problem encountered.
	 *
	 * @param endpoint POST address.
	 * @param message request body data.
	 * 
	 * @return whether the registration succeeded or not.
	 */
	boolean postWithNoBackoff(String endpoint, String message) {
		try {
			post(endpoint, message);
			return true;
		} catch (IOException e) {
			logger.severe("Failed to register on attempt 1 (only trying once) : " + e);
		}
		
		return false;
	}
	
	/**
	 * Issue a POST request to the server, backing off for a longer period after each fail.
	 *
	 * @param endpoint POST address.
	 * @param message request body data.
	 * 
	 * @return whether the registration succeeded or not.
	 */
	boolean postWithBackoff(String endpoint, String message) {
	    final int MAX_ATTEMPTS = 5;
	    final int BACKOFF_MILLI_SECONDS = 2000;
	    final Random random = new Random();

	    long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);
		// As the server might be down, we will retry it a few times.
		for (int i = 1; i <= MAX_ATTEMPTS; i++) {
			logger.finer("Attempt #" + i + " to register");
			try {
				post(endpoint, message);
				return true;
			} catch (IOException e) {
				// Here we are simplifying and retrying on any error; in a real
				// application, it should retry only on unrecoverable errors
				// (like HTTP error code 503).
				logger.severe("Failed to register on attempt " + i + " : " + e);
				if (i == MAX_ATTEMPTS) {
					break;
				}
				
				try {
					logger.finer("Sleeping for " + backoff + " ms before retry");
					Thread.sleep(backoff);
				} catch (InterruptedException e1) {
					// Activity finished before we complete - exit.
					logger.finer("Thread interrupted: abort remaining retries!");
					Thread.currentThread().interrupt();
					return false;
				}
				// increase backoff exponentially
				backoff *= 2;
			}
		}

		return false;
	}

    /**
	 * Issue a POST request to the server.
	 *
	 * @param endpoint POST address.
	 * @param message request body data.
	 *
	 * @throws IOException propagated from POST.
	 */
	private void post(String endpoint, String message) throws IOException {
		URL url;
		try {
			url = new URL(endpoint);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid url: " + endpoint);
		}
		
		logger.finer("Posting '" + message + "' to " + url);
		byte[] bytes = message.getBytes();
		
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			conn.setFixedLengthStreamingMode(bytes.length);
			conn.setRequestMethod("POST");
			if (dataFormat.equals("XML")) {
				conn.setRequestProperty("Content-Type", "text/xml");
			} else {
				conn.setRequestProperty("Content-Type", "text/plain");
			}
			// post the request
			OutputStream out = conn.getOutputStream();
			out.write(bytes);
			out.close();
			// handle the response
			int status = conn.getResponseCode();
			if (status != 200  && status != 503 && status != 504) {
				throw new IOException("Post failed with error code " + status);
			}
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

}
