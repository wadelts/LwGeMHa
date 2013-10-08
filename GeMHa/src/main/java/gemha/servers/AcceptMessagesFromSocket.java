package gemha.servers;

import java.util.logging.*;
import java.util.concurrent.*;

import gemha.support.MessagingException;
import gemha.interfaces.IAcceptMesssages;
import lw.sockets.interfaces.*;
import lw.sockets.*;
import lw.utils.LwLogger;
import lw.utils.SettingsException;

/**
  * This class retrieves messages from a socket.
  *
  * The processing algorithm is:
  * acceptNextMessage() blocks on dataQueue, awaiting a message from socket
  * LwXMLSocketServer calls messageReceived(), placing message in dataQueue and returning immediately
  * LwXMLSocketServer, on returning from messageReceived(), blocks on synchQueue
  * Either stayMessage() or consumeMessage() is called, setting the value for consumeMessage flag and performing a take() on synchQueue
  * LwXMLSocketServer is released from block on synchQueue, calls getConsumeMessage() and continues it's own processing
  * The above take() and put() on the synchQueue work as a synchroniser - both block until there is a "meeting"
  *
  * @author Liam Wade
  * @version 1.0 13/01/2009
  */
public class AcceptMessagesFromSocket implements IAcceptMesssages
													,LwIXMLSocketServerListener {

    private static final Logger logger = Logger.getLogger("gemha");
	
	public AcceptMessagesFromSocket(int portNumber)
																		throws SettingsException {
		this.portNumber = portNumber;
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
	}

	/**
	  * Set the wait interval setting to block forever
	  *
	  */
	public void setWaitIntervalBlockIndefinitely() {
	}

	/**
	  * Set up conditions for accepting messages
	  *
	  * @return true for success, false for failure
	  */
	public boolean performSetup()
								throws MessagingException {
		try {
			// TODO: NEED TO FIX synchQueue IDEA
			sockServer = new XMLSocketServer(Executors.newFixedThreadPool(2), this, portNumber, synchQueue);
			new Thread(sockServer).start();
			socketClosed = false;
			logger.info("sockServer started.");
		}
		catch(SocketException e) {
			logger.severe("Couldn't create new sockServer: " + e.getMessage());
			return false;
		}

		return true;
	}

	/**
	  * Process a message
	  *
	  * @return the next message retrieved
	  */
	public String acceptNextMessage()
									throws MessagingException {
		if (socketClosed) {
			return null;
		}
		else {
			String receivedMessage = null;
			// Get next message, blocking indefinitely if necessary
			try { receivedMessage = dataQueue.take();} catch(InterruptedException e) { /* Do nothing if interrupted */}
			if (receivedMessage.equals("exception")) {
				// Exception will be stored for us
				throw messagingException;
			}
			else if (receivedMessage.equals("closedSocket")) {
				// Socket was closed by a client, so return null, which should tell caller to close down.
				return null;
			}
			else {
				consumeMessage = false;
				logger.finer("Returned with message from dataQueue.take()");
				return receivedMessage;
			}
		}
	}

	/**
	  * Do not consume the message
	  *
	  */
	public void stayMessage(String auditKey)
							throws MessagingException {

		consumeMessage = false;

		// release LwXMLSocketServer thread, now that we've set consumeMessage
		try {synchQueue.take();} catch(InterruptedException e) { /* Do nothing if interrupted */}
	}

	/**
	  * Consume the message now
	  *
	  */
	public void consumeMessage(String auditKey)
							throws MessagingException {


		consumeMessage = true;

		// release LwXMLSocketServer thread, now that we've set consumeMessage
		try {synchQueue.take();} catch(InterruptedException e) { /* Do nothing if interrupted */}
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		socketClosed = true;
		try {sockServer.close(shutdownLogger);} catch(SocketException e) { /* Do nothing - too late */}
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	//////////////////////////////////////////////////////////////////////////
	//				Start: Interface LwIXMLSocketServer Methods
	//////////////////////////////////////////////////////////////////////////
	/**
	  * Will be called by the supporting object when a complete message is available for delivery
	  *
	  * @param event holds information on the event
	  *
	  * @return true if the supplied message is to be consumed, otherwise false
	  */
	public boolean messageReceived(SocketEvent event) {
		logger.info("Got message from port " + event.getPortNumber() + " for TID " + event.getTID());
		logger.fine("Got message from port " + event.getPortNumber() + " for TID " + event.getTID() + ": " + event.getReceivedMessage());
		dataQueue.offer(event.getReceivedMessage()); // no blocking here - caller will block on synchQueue instead

		return true; // but actually defer decision until later
	}

	/**
	  * Will be called by the supporting object when a complete message is available for delivery
	  * and a response is expected.
	  * By definition, messages will always be consumed.
	  *
	  * @param event holds information on the event
	  *
	  * @return the response to be sent back over the socket
	  */
	public String messageReceivedAndWantResponse(SocketEvent event) {
		return "<MESSAGE></MESSAGE>"; // but it is not expected that this is called in this implementation
	}

	/**
	  * Will be called by the supporting object when an error is encountered
	  *
	  * @param event holds information on the event
	  * @param exception LwSocketException explaining the problem
	  *
	  */
	public void handleError(SocketEvent event, SocketException exception) {
		logger.info("Handled error from Socket Server on port " + event.getPortNumber() + " for TID " + event.getTID() + " LwSocketException: " + exception.getMessage());
		messagingException = new MessagingException("LwSocketException reported by socket server: " + exception.getMessage());
		dataQueue.offer("exception"); // in case have to wake me up
	}

	/**
	  * Will be called by the supporting object (implementing LwIXMLSocketServer) when a request has been received over the connection to close down.
	  * If true is returned, the socket server will be closed for good, otherwise the request will be ignored,
	  * (only the current socket will be closed).
	  *
	  * @param event holds information on the event
	  *
	  * @return true if the Socket Server shold close down, false if it should remain open
	  */
	public boolean canCloseServerSocket(SocketEvent event) {
		logger.info("Agreed to request to close Server Socket on port " + event.getPortNumber() + " for event TID " + event.getTID());
		socketClosed = true;
		dataQueue.offer("closedSocket"); // in case have to wake me up
		return true;
	}

	/**
	  * Will be called by the supporting object (implementing LwIXMLSocketServer) to find out if a message should be consumed.
	  * And is reset here to false.
	  *
	  * @return true if a message should be consumed, otherwise false
	  */
	public boolean getConsumeMessage() {
		boolean response = consumeMessage;
		consumeMessage = false;
		return response;
	}
	//////////////////////////////////////////////////////////////////////////
	//				End: Interface LwIXMLSocketServer Methods
	//////////////////////////////////////////////////////////////////////////

    private XMLSocketServer sockServer = null;
	private int portNumber = 0;			// the port number for the socket server
	private SynchronousQueue<String> synchQueue = new SynchronousQueue<String>();	// sychronise messages between this thread and the LwXMLSocketServer thread (SynchronousQueue has no space, so blocks on put and take)
//	private SynchronousQueue<String> dataQueue = new SynchronousQueue<String>();	// sychronise messages between receiving and getting processed (SynchronousQueue has no space, so blocks on put)
	private ArrayBlockingQueue<String> dataQueue = new ArrayBlockingQueue<String>(2);	// Allow consumer block put provider can continue (but then blocks on synchQueue)
	private boolean socketClosed = true;
	private boolean consumeMessage = true;											// indicate to message supplier if message can be consumed
	private MessagingException messagingException = null;						// will be passed if we encounter a socket exception
}
