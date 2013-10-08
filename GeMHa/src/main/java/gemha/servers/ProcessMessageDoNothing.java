package gemha.servers;

import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import lw.XML.XMLDocument;
import lw.XML.XMLException;
import lw.XML.XMLTagValue;
import lw.utils.*;
import gemha.support.MessagingException;
import gemha.support.ProcessResponse;
import gemha.support.ProcessResponse.ProcessResponseCode;
import gemha.interfaces.IProcessMesssage;

/**
  * This class does nothing with a message. It is only to plug "a hole".
  *
  * @author Liam Wade
  * @version 1.0 21/10/2008
  */
public class ProcessMessageDoNothing implements IProcessMesssage {

    private static final Logger logger = Logger.getLogger("gemha");

	// Determines whether a message is to be processed asynchronously or not.
	static private enum ProcessingMode {
		SYNCHRONOUS,
		ASYNCHRONOUS;
	}
	
    // Executor for processing messages
    private final ExecutorService execPool = Executors.newSingleThreadExecutor();
    
    // Queue for handing off responses, with fixed capacity of 1000.
    // End-of-data will be signaled by a null record
    private final BlockingQueue<Future<ProcessResponse>> responseQueue = new LinkedBlockingQueue<Future<ProcessResponse>>(1000);


    public ProcessMessageDoNothing() {
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Set up conditions for processing messages
	  *
	  * @return true for success, false for failure
	  */
	@Override
	public void performSetup(String settingsFileName) throws SettingsException {
	}

	@Override
	public ProcessResponse processMessageSynch(String messageText, XMLDocument inputDoc, String auditKeyValues)
																		throws MessagingException {
		logger.fine("My job is just to return original message.");
		return processMessage(messageText, inputDoc, auditKeyValues, ProcessingMode.SYNCHRONOUS);
	}

	@Override
	public void processMessageAsynch(String messageText, XMLDocument inputDoc, String auditKeyValues)
																		throws MessagingException {
		logger.fine("My job is just to return original message.");
		processMessage(messageText, inputDoc, auditKeyValues, ProcessingMode.ASYNCHRONOUS);
	}

	/**
	  * Process a message.
	  * As only processing one message at a time, not bothering to check for isInterrupted() - let it finish
	  *
	  * @param messageText the message to be processed
	  * @param inputDoc the original input message as an XML document, null if message was not XML. To be returned with result - DO NOT MODIFY, is NOT threadsafe!!
	  * @param auditKeyValues audit Key Values for the message (can be null). To be returned with result
	  * @param synchronous 	if true, the message is to be processed on the same thread as the caller, blocking for result
	  * 					if false, the message is to be processed on another thread, non-blocking. Result to be collected by calling getResponse().
	  *
	  * @return the next response from process, null if no more results will ever arrive
	  * 
	  * @throws MessagingException if a problem was encountered processing the message
	  */
	private ProcessResponse processMessage(final String messageText, final XMLDocument inputDoc, final String auditKeyValues, ProcessingMode processingMode)
											throws MessagingException {

		Callable<ProcessResponse> processMessageTask = new Callable<ProcessResponse>() {
			@Override
			public ProcessResponse call() throws MessagingException {
				///////////////////////////////////////////////
				// If got here, message was successfully transmitted.
				// So build Response...
				///////////////////////////////////////////////
				ProcessResponse.Builder responseBuilder = new ProcessResponse.Builder(ProcessResponseCode.SUCCESS, 1)
						.setAuditKeyValues(auditKeyValues)
						.setResponse(messageText);
				
				logger.info("[" + Thread.currentThread().getName() + "]: Returning response from Processor Task...");
				return responseBuilder.build();
			} // end Callable.call()
		};
		
		if (processingMode == ProcessingMode.ASYNCHRONOUS) { // submit for processing on a pool thread
			// If null message supplied, no more messages to process, so return PoisonPill to response handler...
			if (messageText == null) {
				subMitPoisonPill();
				return null;
			}
			
			// Submit this message for processing...
			// (See subMitPoisonPill() re flushing the executor.)
			// Note: exceptions thrown within the Callable will not return to the caller, but will re-appear in the Future :-)
			try {
				responseQueue.put(execPool.submit(processMessageTask));
				
				return null;
			} catch(InterruptedException e) { // thrown by responseQueue.put
				// Flush responseQueue (reading thread then has option to close down itself, on receiving this Poison Pill)
				subMitPoisonPill();
				
				// Quit processing now, but as we don't own the Thread, set back to interrupted - the caller will decide what to do with Thread
				// (in case queue will have called interrupted(), which clears the interrupted flag for the thread)
				Thread.currentThread().interrupt();
				return null; // Interrupted
			}
		} else { // process now, on caller's thread
			try {
				return processMessageTask.call();
			} catch (Exception e) {
				throw new MessagingException("[" + Thread.currentThread().getName() + "]: Caught Exception calling processMessageTask: " + e);
			}
		}
	}

	/**
	  * Return the next response message.
	  * When all responses have been received, null will be returned.
	  *
	  * LwProcessResponse is immutable
	  * 
	  * @return the next response from process, null if no more results will ever arrive
	  * @throws MessagingException if a problem was encountered processing the message
	  * @throws InterruptedException if CALLING thread is noticed as interrupted while getting response
	  */
	@Override
	public ProcessResponse getResponse() throws MessagingException, InterruptedException {
		ProcessResponse response = null;
		try {
			Future<ProcessResponse> fr = responseQueue.take(); // will block here if queue empty (will never return null - BlockingQueue doesn't allow)
			response = fr.get(); // will block here if next task in queue not yet finished
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof MessagingException)
				throw (MessagingException) cause;
			else
				throw launderThrowable(cause);
		}
		return response;
	}

	/**
	  * Do whatever you like when things are quiet
	  *
	  */
	@Override
	public void goQuiet() {
	}

	/**
	  * Allow caller block until all tasks have completed execution after a shutdown request, or the timeout occurs,
	  * or the current thread is interrupted, whichever happens first.
	  *
	  * @param timeout the number of units to wait before giving up wait.
	  * @param unit the unit of time of the timeout value
	 * @throws InterruptedException if current thread interrupted while waiting
	  * 
	  */
	@Override
	public void  awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			execPool.awaitTermination(timeout, unit) ;
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	@Override
	public void performCleanup(LwLogger shutdownLogger) {
		// Flush responseQueue (reading thread then has option to close down itself, on receiving this Poison Pill)
		subMitPoisonPill();
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	  * Extract and validate a Throwable that would have been contained within another Exception (eg ExcutionException)
	  *
	  * @param t the Throwable to be interpreted
	  * 
	  * @return a RuntimeException
	  * @throws IllegalStateException if the Exception was not expected (known exceptions should have been dealt with prior to calling launderThrowable
	  */
	private static RuntimeException launderThrowable(Throwable t) {
		if (t instanceof RuntimeException) 
			return (RuntimeException) t;
		else if (t instanceof Error)
			throw (Error) t;
		else
			throw new IllegalStateException("launderThrowable: Exception not checked! : ", t);
	}

	/**
	  * This method creates a Poison Pill and places it in the queue for the Executor, which will be returned to the
	  * response handler.
	  * It then tells the executor to shut down.
	  *
	  * 
	  */
	private void subMitPoisonPill() {
		logger.info("Submitting Poison Pill to Executor queue, so will cause responseProcessor to close down.");
		try {
			if ( ! execPool.isShutdown()) { // this check in case we,ve already called this method
				responseQueue.put(execPool.submit( new Callable<ProcessResponse>() {
					public ProcessResponse call() throws MessagingException {
						return null;
					} // end Callable.call()
				}));
				// Flush execPool buffer and shut it down (no waiting)...
				execPool.shutdown();
			}
			
		} catch(InterruptedException e) { // thrown by responseQueue.put
			// Set back to interrupted - the caller will decide what to do with Thread
			// (in case queue will have called interrupted(), which clears the interrupted flag for the thread)
			Thread.currentThread().interrupt();
		}
	}
}
