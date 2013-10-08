package gemha.interfaces;

import lw.utils.*;
import gemha.support.*;

/**
  * Encapsulates processMessage method for processing supplied messages.
  * @author Liam Wade
  * @version 1.0 21/10/2008
  */
public interface IProcessMesssage_old {

/**
  * Set up conditions for accepting messages
  *
  * @return true for success, false for failure
  *
  * @throws MessagingException when any error is encountered
  */
void performSetup(String settingsFileName) throws SettingsException;

/**
  * Process a message
  *
  * @param messageText the message to be processed
  * @return 0 for success with no response necessary, 1 for success and response is ready, less than zero for error
  */
int processMessage(String message) throws MessagingException;

/**
  * Return the response message
  *
  * @return the response, null if none exists
  */
String getResponse();

/**
  * Do whatever you like when things are quiet
  *
  */
void goQuiet();

/**
  * Perform any clean-up actions before closing down
  *
  */
void performCleanup(LwLogger shutdownLogger);
}
