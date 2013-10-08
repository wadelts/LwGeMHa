package gemha.servers;

import java.util.logging.*;
import java.io.IOException;

import lw.queues.*;
import lw.utils.LwLogger;
import gemha.support.MessagingException;
import gemha.interfaces.IStoreMesssage;

/**
  * This class saves messages to a queue.
  *
  * @author Liam Wade
  * @version 1.0 30/10/2008
  */
public class StoreMesssageToQueue implements IStoreMesssage {

    private static final Logger logger = Logger.getLogger("gemha");

	public StoreMesssageToQueue(String outputQueueName, String urlJMSserver) {
		this.outputQueueName = outputQueueName;
		this.urlJMSserver = urlJMSserver;
	}

	public StoreMesssageToQueue(String outputQueueName, String urlJMSserver, String replyToQueueName) {
		this.outputQueueName = outputQueueName;
		this.urlJMSserver = urlJMSserver;
		this.replyToQueueName = replyToQueueName;
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

		if (outQueue == null) {
			outQueue = new OutputQueue();
			if (outQueue == null) {
				throw new MessagingException("GenericQueueException encountered creating queue connection for queue " + outputQueueName + " on JMS Server " + urlJMSserver);
			}
		}

		try {
			outQueue.open(outputQueueName, urlJMSserver);
			logger.info("Queue " + outputQueueName + " opened.");
			targetConnectionOpen = true;
		}
		catch (GenericQueueException e) {
			throw new MessagingException("GenericQueueException encountered opening queue " + ": " + e);
		}
	}

	/**
	  * Close any connections
	  *
	  * @throws MessagingException when any error is encountered
	  */
	public void closeStorage()
	 			throws MessagingException {

		if (targetConnectionOpen) {
			outQueue.close(); // doesn't throw any exceptions if problem encountered
			targetConnectionOpen = false;
			if (logger != null) {
				logger.info("Queue " + outputQueueName + " closed.");
			}
		}
	}

	/**
	  * Process a message
	  *
	  * @param message the message to be processed
	  * @param instructions instructions on how the message is to be processed. In this case it may hold an Queue Name for a ReplyToQ
	  *
	  * @throws MessagingException when any error is encountered
	  */
	public void putMessage(String message, String auditKey, String instructions)
																		throws MessagingException {
		if ( ! targetConnectionOpen) {
			// Open targetConnection here
			openStorage();
		}

		try {
			if (instructions == null) { // then no ReplyToQ instructions for this specific message (would override replyToQueueName)
				if (replyToQueueName == null) {
					outQueue.sendMessage(message);
				}
				else { // want to stamp message with reply queue name (and maybe manager name too, but OK if null)
					outQueue.sendReplyMessage(message, replyToQueueName);
				}
			}
			else {
				outQueue.sendReplyMessage(message, instructions);
			}

			logger.info("Response message with AuditKey Value " + auditKey + " placed on response queue.");
		}
		catch (GenericQueueException e) {
			throw new MessagingException("GenericQueueException encountered placing message with AuditKey Value " + auditKey + " on response queue " + outputQueueName + ": " + e);
		}
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performStoreMessageCleanup(LwLogger shutdownLogger) {
		try {
			if (targetConnectionOpen == true) {closeStorage();}

			try {
				shutdownLogger.appendln("LwStoreMesssageToQueue.performCleanup() Shutdown of Queue " + outputQueueName + " completed.");
			}
			catch(IOException e) {
				System.out.println("LwStoreMesssageToQueue.performCleanup(): could not write to shutDownLogFile.");
			}
		}
		catch(MessagingException e) {
			// don't do anything, closing down anyway
		}
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////


	private String outputQueueName = null;
	private String urlJMSserver = null;
	private String replyToQueueName = null;
	private OutputQueue outQueue = null;
	private boolean targetConnectionOpen = false;
}
