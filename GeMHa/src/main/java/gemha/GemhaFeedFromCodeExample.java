package gemha;

import java.util.logging.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.*;

import lw.XML.*;
import lw.utils.IApp;
import lw.utils.LwFilenameFilter;
import lw.utils.LwLogger;
import lw.utils.LwSettingsException;
import lw.utils.ShutdownInterceptor;
import gemha.support.LwMessagingException;
import gemha.interfaces.LwIAcceptMesssages;
import gemha.servers.LwGenericMessageHandler;

/**
  * This class sends messages to a LwGenericMessageHandler for processing.
  *
  * @author Liam Wade
  * @version 1.0 01/07/2012
  */
public class GemhaFeedFromCodeExample implements LwIAcceptMesssages {

	/**
	  * Constructor
	  *
	  * @param settingsFileName (and optionally path) of file from which settings are to be read
	  */
	public GemhaFeedFromCodeExample(String settingsFileName) {
		// Don't use logger until after LwGenericMessageHandler object created
		// He will call setLogger
		IApp app = new LwGenericMessageHandler(settingsFileName, this);

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
	  * @throws LwMessagingException if problem encountered during setup
	  */
	public boolean performSetup()
								throws LwMessagingException {

		logger.finer("Starting Files-input setup now...");

		logger.finer("Files-input setup was successful.");

		return true;
	}

	/**
	  * Process a message
	  *
	  * @return the next message retrieved, null if none left to send
	  * @throws LwMessagingException if problem encountered getting next message
	  */
	public String acceptNextMessage()
									throws LwMessagingException {

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
							throws LwMessagingException {
	}

	/**
	  * Consume the message now
	  *
	  */
	public void consumeMessage(String auditKey)
							throws LwMessagingException {
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
		GemhaFeedFromCodeExample app = new GemhaFeedFromCodeExample(settingsFileName);
	}

	private Logger logger = null;
	private int numRecsSent = 0;
}
