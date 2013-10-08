package gemha.interfaces;

import lw.utils.LwLogger;
import gemha.support.*;

/**
  * Encapsulates methods for accepting messages.
  * @author Liam Wade
  * @version 1.0 21/10/2008
  */
public interface IAcceptMesssages {

/**
  * Set the wait interval for accepting messages
  *
  * @param waitInterval how many milliseconds to wait for a message before returning nothing (0 = block indefinitely)
  */
void setWaitInterval(int waitInterval);

/**
  * Set the wait interval setting to block forever
  *
  */
void setWaitIntervalBlockIndefinitely();

/**
  * Set up conditions for accepting messages
  *
  * @return true for success, false for failure
  *
  * @throws MessagingException when any error is encountered
  */
boolean performSetup() throws MessagingException;

/**
  * Process a message
  *
  * @return the next message retrieved
  *
  * @throws MessagingException when any error is encountered
  */
String acceptNextMessage() throws MessagingException;

/**
  * Do not consume the message
  *
  *
  * @throws MessagingException when any error is encountered
  */
void stayMessage(String auditKey) throws MessagingException;

/**
  * Consume the message now
  *
  *
  * @throws MessagingException when any error is encountered
  */
void consumeMessage(String auditKey) throws MessagingException;

/**
  * Perform any clean-up actions before closing down
  *
  */
void performCleanup(LwLogger shutdownLogger);


}

