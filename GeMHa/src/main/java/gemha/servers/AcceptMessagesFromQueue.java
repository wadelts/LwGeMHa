package gemha.servers;

import java.util.logging.*;

import gemha.support.MessagingException;
import gemha.interfaces.IAcceptMesssages;
import lw.queues.GenericQueueException;
import lw.queues.InputQueue;
import lw.queues.NoMessageFoundException;
import lw.utils.LwLogger;

/**
  * This class retrieves messages from an MQ queue.
  *
  * @author Liam Wade
  * @version 1.0 30/10/2008
  */
public class AcceptMessagesFromQueue implements IAcceptMesssages {

    private static final Logger logger = Logger.getLogger("gemha");
	
	public AcceptMessagesFromQueue(String inputQueueName, String urlJMSserver) {
		this.inputQueueName = inputQueueName;
		this.urlJMSserver = urlJMSserver;
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIAcceptMesssages Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Set the wait interval for accepting messages
	  *
	  * @param waitInterval how many milliseconds to wait for a message before returning nothing (0 = block indefinitely)
	  */
	public void setWaitInterval(int waitInterval) {
		this.waitInterval = waitInterval;
	}

	/**
	  * Set the wait interval setting to block forever
	  *
	  */
	public void setWaitIntervalBlockIndefinitely() {
		this.waitInterval = 0;
	}

	/**
	  * Set up conditions for accepting messages
	  *
	  * @return true for success, false for failure
	  */
	public boolean performSetup() throws MessagingException {
		if (inputQueueName == null) {
			logger.severe("Could not perform setup. Missing Queue information");
			return false;
		}

		try {
			inQueue = new InputQueue();

			// Open the queue
			inQueue.open(inputQueueName, urlJMSserver);
		}
		catch (GenericQueueException e) {
			logger.severe("Stopped processing: Caught GenericQueueException: " + e);
			return false;
		}

		return true;
	}

	/**
	  * Process a message
	  *
	  * @return the next message retrieved
	  */
	public String acceptNextMessage() throws MessagingException {
		if (inQueue == null) {
			logger.severe("Could not accept messages. inQueue is null.");
			throw new MessagingException("Could not accept messages. inQueue is null.");
		}

		try {
			String receivedMessage = inQueue.getNextMessage(waitInterval);
			replytoQueueURI = inQueue.getReplytoQueueURI();
			logger.finer("returned from inQueue.getNextMessage(" + (new Integer(waitInterval).toString()) + ")");
			return receivedMessage;
		}
		catch (NoMessageFoundException e) {
			// Is OK. Doesn't cause a problem.
		}
		catch (GenericQueueException e) {
			throw new MessagingException("GenericQueueException encountered while accepting next message from Queue: " + e);
		}

		return null;
	}

	/**
	  * Do not consume the message
	  *
	  */
	public void stayMessage(String auditKey)
							 throws MessagingException {
		if (inQueue == null) {
			return;
		}

		try {
			inQueue.sessionRollback();
			logger.warning("Message with AuditKey Value " + auditKey + " left on queue (rolledback).");
		}
		catch (GenericQueueException e) {
			throw new MessagingException("GenericQueueException encountered while leaving message (key=" + auditKey + ") on Queue: " + e);
		}
	}

	/**
	  * Consume the message now
	  *
	  */
	public void consumeMessage(String auditKey)
							 throws MessagingException{
		if (inQueue == null) {
			return;
		}

		try {
			inQueue.sessionCommit();
		}
		catch (GenericQueueException e) {
			throw new MessagingException("GenericQueueException encountered while commiting message (key=" + auditKey + ") on Queue: " + e);
		}
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		if (inQueue == null) {
			return;
		}

		inQueue.close(shutdownLogger);
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
        * Get the ReplyToQ name of the last-returned message
        *
        * @return String ReplyToQ name in the form of a URI that can be used in the creation methods to reconstruct an MQQueue object. Null if none available.
        */
	public String getReplytoQueueURI() {
		return replytoQueueURI;
	}

	private String inputQueueName = null;
	private String urlJMSserver = null;
	private InputQueue inQueue = null;
	private int waitInterval = 0;			// how many milliseconds to wait for a message before returning nothing (0 = block indefinitely)
	private String replytoQueueURI = null;	// the ReplyToQ info in a received message in the form of a URI that
											// can be used in the creation methods to reconstruct an MQQueue object.
											// e.g "queue://ERROR_QUEUE_MANAGER/ERROR_QUEUE?targetClient=1"
}
