package gemha.servers;

import java.util.logging.*;

import lw.utils.*;
import gemha.support.LwMessagingException;
import gemha.interfaces.LwIProcessMesssage_old;

/**
  * This class does nothing with a message. It is only to plug "a hole".
  *
  * @author Liam Wade
  * @version 1.0 21/10/2008
  */
public class LwProcessMessageDoNothing implements LwIProcessMesssage_old {

    private static final Logger logger = Logger.getLogger("gemha");

	public LwProcessMessageDoNothing() {
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Set up conditions for processing messages
	  *
	  * @return true for success, false for failure
	  */
	public void performSetup(String settingsFileName) throws LwSettingsException {
	}

	/**
	  * Process a message
	  *
	  * @param messageText the message to be processed
	  * @return 0 for success with no response necessary, 1 for success and response is ready, less than zero for error
	  */
	public int processMessage(String messageText)
											throws LwMessagingException {

		if (messageText == null) {
			return -1;
		}

		response = messageText;
		logger.fine("My job is just to return original message.");
		return 1;
	}

	/**
	  * Return the response message
	  *
	  * @return the response, null if none exists
	  */
	public String getResponse() {
		String tempResponse = response;
		response = null;
		return tempResponse;
	}

	/**
	  * Do whatever you like when things are quiet
	  *
	  */
	public void goQuiet() {
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	private String response = null;
}
