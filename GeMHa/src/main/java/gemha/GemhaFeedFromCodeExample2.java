package gemha;

import java.util.logging.*;
import java.io.*;

import lw.utils.IApp;
import lw.utils.LwLogger;
import lw.utils.ShutdownInterceptor;
import gemha.support.MessagingException;
import gemha.interfaces.IAcceptMesssages;
import gemha.interfaces.IStoreMesssage;
import gemha.servers.GenericMessageHandler;

/**
  * This class sends messages to a LwGenericMessageHandler for processing.
  * It also accepts responses!!!
  *
  * @author Liam Wade
  * @version 1.0 01/07/2012
  */
public class GemhaFeedFromCodeExample2 implements IAcceptMesssages
												 ,IStoreMesssage    {

	/**
	  * Constructor
	  *
	  * @param settingsFileName (and optionally path) of file from which settings are to be read
	  */
	public GemhaFeedFromCodeExample2(String settingsFileName) {
		// Don't use logger until after LwGenericMessageHandler object created
		// He will call setLogger
		// NOTE am supplying this object twice, to act as both LwIAcceptMesssages and
		// LwIStoreMesssage implementer.
		IApp app = new GenericMessageHandler(settingsFileName, this, this);

		ShutdownInterceptor shutdownInterceptor = new ShutdownInterceptor(app);

		// Register the thread to be called when the VM is shut down...
		Runtime.getRuntime().addShutdownHook(shutdownInterceptor);

		// Let's go...
		app.start();
		
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIAcceptMesssages Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Set the logger to which log messages will be sent
	  * The Generic Messahe Handler will call this immedately
	  *
	  * @param logger the logger to which log messages will be sent
	  * @return 0 for success, non-zero for error
	  */
	public void setLogger(Logger logger) {
		this.logger = logger;
	}

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
	  * @throws MessagingException if problem encountered during setup
	  */
	public boolean performSetup()
								throws MessagingException {

		logger.finer("Starting Files-input setup now...");

		logger.finer("Files-input setup was successful.");

		return true;
	}

	/**
	  * Process a message
	  *
	  * @return the next message retrieved, null if none left to send
	  * @throws MessagingException if problem encountered getting next message
	  */
	public String acceptNextMessage()
									throws MessagingException {

		String receivedMessage = null;

		receivedMessage = getNextMessage();
		
		logger.info("Blah blah");

		return receivedMessage;
	}

	/**
	  * Do not consume the message
	  *
	  */
	public void stayMessage(String auditKey)
							throws MessagingException {
	}

	/**
	  * Consume the message now
	  *
	  */
	public void consumeMessage(String auditKey)
							throws MessagingException {
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		try {
			shutdownLogger.appendln("I0100 LwAcceptMessagesFromObject.performCleanup(): Total of " + numRecsSent + " record(s) sent to handler.");
		}
		catch(IOException e) {
			System.out.println("E0100 LwAcceptMessagesFromFiles.performCleanup(): could not write to shutDownLogFile.");
		}

		// Release resources here
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIAcceptMesssages Interface...
	//////////////////////////////////////////////////////////////////

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIStoreMesssage Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Set the logger to which log messages will be sent
	  *
	  * @param logger the logger to which log messages will be sent
	  * @return 0 for success, non-zero for error
	  */
	public void setStoreMesssageLogger(Logger logger) {
		this.logger = logger;
	}

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
	public void putMessage(String message, String auditKey, String instructions)
								throws MessagingException {

		logger.info("XML Response message with AuditKey Value " + auditKey);
		logger.info("XML Response message is " + message);
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
	  * Fetch the message for sending and return it in a String.
	  *
	  * @return the the next message
	  */
	private String getNextMessage() {
		
		if (numRecsSent >= 1) {
			return null;
		}
		else {
			numRecsSent++;
			return "<MESSAGE><DBACTION><KEY>1001</KEY><ACTION_ON_ERROR>RESPOND</ACTION_ON_ERROR><INSERT><PREPARED_STATEMENT_NAME>LW_delme1_insert_001</PREPARED_STATEMENT_NAME><IMMEDIATE_COMMIT>YES</IMMEDIATE_COMMIT><COLUMNS><USERID>1</USERID><USERCODENAME>xxx1001</USERCODENAME><USERSTATUS>SUSPENDERS</USERSTATUS><ADMINCOMMENT>lemmo1</ADMINCOMMENT></COLUMNS></INSERT></DBACTION></MESSAGE>";
		}
			
	}

	/**
	 * @param args settingsFileName
	 */
	public static void main(String args[]) {
		if (args.length < 1) {
			System.out.println("LwAcceptMessagesFromObject.main(): Fatal Error at startup: Too few args.");
			System.exit(-1);
		}

		String settingsFileName = args[0];
		GemhaFeedFromCodeExample2 app = new GemhaFeedFromCodeExample2(settingsFileName);
	}

	private Logger logger = null;
	private int numRecsSent = 0;
}
