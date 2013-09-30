package gemha.interfaces;

import lw.utils.LwLogger;
import gemha.support.*;

/**
  * Encapsulates storing of supplied messages.
  * @author Liam Wade
  * @version 1.0 30/10/2008
  */
public interface LwIStoreMesssage {

/**
  * Open any connections
  *
  * @throws LwMessagingException when any error is encountered
  */
void openStorage() throws LwMessagingException;

/**
  * Close any connections
  *
  * @throws LwMessagingException when any error is encountered
  */
void closeStorage() throws LwMessagingException;

/**
  * Process a message
  *
  * @param message the message to be processed
  * @param instructions instructions on how the message is to be processed (will be implementation-specific). Can be null
  *
  * @throws LwMessagingException when any error is encountered
  */
void putMessage(String message, String auditKey, String instructions) throws LwMessagingException;

/**
  * Perform any clean-up actions before closing down
  *
  */
void performStoreMessageCleanup(LwLogger shutdownLogger);

}

