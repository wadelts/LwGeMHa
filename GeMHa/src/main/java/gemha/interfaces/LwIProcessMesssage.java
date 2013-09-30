package gemha.interfaces;

import java.util.concurrent.TimeUnit;

import lw.utils.*;
import lw.XML.LwXMLDocument;
import gemha.support.*;

/**
  * Encapsulates processMessage method for processing supplied messages.
  * @author Liam Wade
  * @version 1.0 21/10/2008
  */
public interface LwIProcessMesssage {

/**
  * Set up conditions for accepting messages
  *
  * @return true for success, false for failure
  *
  * @throws LwSettingsException if a problem was encountered performing setup
  */
public void performSetup(String settingsFileName) throws LwSettingsException;

/**
 * Process a message on the same thread as the caller, blocking for result.
 * As only processing one message at a time, not bothering to check for isInterrupted() - let it finish
 *
 * @param messageText the message to be processed
 * @param inputDoc the original input message as an XML document, null if message was not XML. To be returned with result - DO NOT MODIFY, is NOT threadsafe!!
 * @param auditKeyValues audit Key Values for the message (can be null). To be returned with result
 *
 * @return the next response from process, null if no more results will ever arrive
 * 
 * @throws LwMessagingException if a problem was encountered processing the message
 */
public LwProcessResponse processMessageSynch(String message, final LwXMLDocument inputDoc, String auditKeyValues) throws LwMessagingException;

/**
 * Process a message on another thread, non-blocking. Result to be collected by calling getResponse().
 * As only processing one message at a time, not bothering to check for isInterrupted() - let it finish
 *
 * @param messageText the message to be processed
 * @param inputDoc the original input message as an XML document, null if message was not XML. To be returned with result - DO NOT MODIFY, is NOT threadsafe!!
 * @param auditKeyValues audit Key Values for the message (can be null). To be returned with result
 *
 * 
 * @throws LwMessagingException if a problem was encountered processing the message
 */
public void processMessageAsynch(String message, final LwXMLDocument inputDoc, String auditKeyValues) throws LwMessagingException;
/**
 * Return the next response message.
 * When all responses have been received, null will be returned.
 *
 * LwProcessResponse is immutable
 * 
 * @return the next response from process, null if no more results will ever arrive
 * @throws LwMessagingException if a problem was encountered processing the message
 * @throws InterruptedException if CALLING thread is noticed as interrupted while retrieving the message
 */
public LwProcessResponse getResponse() throws LwMessagingException, InterruptedException;

/**
  * Do whatever you like when things are quiet
  *
  */
public void goQuiet();

/**
 * Allow caller block until all tasks have completed execution after a shutdown request, or the timeout occurs,
 * or the current thread is interrupted, whichever happens first.
 *
 * @param timeout the number of units to wait before giving up wait.
 * @param unit the unit of time of the timeout value
* @throws InterruptedException if current thread interrupted while waiting
 * 
 */
public void  awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

/**
  * Perform any clean-up actions before closing down
  *
  */
public void performCleanup(LwLogger shutdownLogger);

}
