package gemha.servers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.Enumeration;
import java.util.Vector;
import java.io.*;

import org.w3c.dom.Node;

import lw.utils.*;
import lw.XML.*;
import gemha.support.ProcessMessageForFileSettings;
import gemha.support.MessagingException;
import gemha.support.ProcessResponse;
import gemha.support.ProcessResponse.ProcessResponseCode;
import gemha.interfaces.IProcessMesssage;

/**
  * This class processes an XML message to send a record to a file.
  *
  * Thread-safety: This class is NOT thread safe.
  * This is because responses in Asynchronous mode are saved to a single queue, so any thread blocking
  * on this queue cannot identify responses proper to it. (I thought of implementing responseQueue as
  * ThreadLocal but messages can be submitted to the queue by one thread and the response extracted by another.)
  * 
  * The Overridden methods performSetup and performCleanup should only be called once per instance, from any thread. 
  * The Overridden methods processMessageAsynch, processMessageSynch, getResponse, goQuiet and awaitTermination can
  * be called any time and by any thread - but ordering to the output medium will only be predictable if called by
  * a single thread.
  * 
  * If calling of processMessageAsynch and processMessageSynch is mixed, ordering to the output medium will be unpredictable.
  *
  * @author Liam Wade
  * @version 1.0 16/12/2008
  * 
  */
public class ProcessMessageForFile implements IProcessMesssage {

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

	volatile private String messagesFileName = null;
	volatile private PrintWriter outFile;			// Write access to this file is limited to our execPool
	volatile private boolean outFileIsEmpty = true;	// true if nothing yet written to file
	volatile private ProcessMessageForFileSettings fileSettings = null;

	public ProcessMessageForFile() {
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Set up conditions for processing messages
	  *
	  * @param settingsFileName the file from which to get set-up information
	  *
	  * @return true for success, false for failure
	  * @throws SettingsException if a problem was encountered performing setup
	  */
	@Override
	public void performSetup(String settingsFileName) throws SettingsException {
		if (fileSettings != null)
			throw new IllegalStateException("LwProcessMessageForFile.performSetup can only be called once");

		if (settingsFileName == null) {
			throw new SettingsException("Parameter settingsFileName was null.");
		}

		fileSettings = new ProcessMessageForFileSettings(settingsFileName, XMLDocument.SCHEMA_VALIDATION_ON);

		messagesFileName = LwLogger.createFileNameFromTemplate(fileSettings.getMessagesFileNameTemplate(), null);

		try {
			openMessagesFile(messagesFileName, fileSettings.getFileOpenMode().equals("append"));
		}
		catch (MessagingException e) {
			logger.severe("LwMessagingException: " + e.getMessage());
			throw new SettingsException("Caught LwMessagingException trying to open new output file " + messagesFileName + ": " + e.getMessage());
		}
	}

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
	  * @throws MessagingException if a problem was encountered processing the message
	  */
	@Override
	public ProcessResponse processMessageSynch(final String messageText, final XMLDocument inputDoc, String auditKeyValues)
											throws MessagingException {
		return processMessage(messageText, inputDoc, auditKeyValues, ProcessingMode.SYNCHRONOUS);
	}
	
	/**
	  * Process a message on another thread, non-blocking. Result to be collected by calling getResponse().
	  * As only processing one message at a time, not bothering to check for isInterrupted() - let it finish
	  *
	  * @param messageText the message to be processed
	  * @param inputDoc the original input message as an XML document, null if message was not XML. To be returned with result - DO NOT MODIFY, is NOT threadsafe!!
	  * @param auditKeyValues audit Key Values for the message (can be null). To be returned with result
	  *
	  * @throws MessagingException if a problem was encountered processing the message
	  */
	@Override
	public void processMessageAsynch(final String messageText, final XMLDocument inputDoc, String auditKeyValues)
											throws MessagingException {
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
	private ProcessResponse processMessage(final String messageText, final XMLDocument inputDoc, String auditKeyValues, ProcessingMode processingMode)
											throws MessagingException {

		Callable<ProcessResponse> processMessageTask = new Callable<ProcessResponse>() {
			@Override
			public ProcessResponse call() throws MessagingException {
				logger.info("[" + Thread.currentThread().getName() + "]: Control now in messageProcessor.");
		
				if (outFile == null) { // then out file was closed, re-open it, appending
					openMessagesFile(messagesFileName, true);
				}
		
				logger.finer("[" + Thread.currentThread().getName() + "]: Processing message: " + messageText);
		
				//////////////////////////////////////////////////////////////////
				// Set up a new XML doc
				//////////////////////////////////////////////////////////////////
				XMLDocument newDoc = null;
				try {
					newDoc = XMLDocument.createDoc(messageText, XMLDocument.SCHEMA_VALIDATION_OFF);
				}
				catch(XMLException e) {
					logger.severe("[" + Thread.currentThread().getName() + "]: LwXMLException: " + e.getMessage());
					logger.warning("[" + Thread.currentThread().getName() + "]: InputMessage was :" + messageText);
					throw new MessagingException("Could not create new XML document: " + e.getMessage());
				}
		
				///////////////////////////////////////////////
				// Get audit information from message (or make it up)...
				///////////////////////////////////////////////
				String auditKeyValues = getConcatenatedAuditKeyValues(newDoc, fileSettings.getAuditKeyNamesSet(), fileSettings.getAuditKeysSeparator()); // works at "current node" level
		
		
				///////////////////////////////////////////////
				// Send column names to the file, if requested...
				///////////////////////////////////////////////
				if (outFileIsEmpty && fileSettings.columnNamesToBeIncluded()) {
					outFileIsEmpty = false;
					// Just take names from first-found row
					if (newDoc.setCurrentNodeByPath(fileSettings.getColumnsLocation(), 1)) {
						Vector<XMLTagValue> row = newDoc.getValuesForTagsChildren();
		
						int colNum = 0;
						for (XMLTagValue col : row) {
							outFile.print(col.getTagName());
							if (++colNum < row.size()) { // then not last column, so add separator
								outFile.print(fileSettings.getFieldSeparator());
							}
						}
						outFile.println();
						logger.info("[" + Thread.currentThread().getName() + "]: Wrote column names record to file.");
					}
				}
		
				///////////////////////////////////////////////
				// Send all rows of data to the file...
				///////////////////////////////////////////////
				
				int numRowsProcessed = 0;
				while (newDoc.setCurrentNodeByPath(fileSettings.getColumnsLocation(), ++numRowsProcessed)) {
					Vector<XMLTagValue> row = newDoc.getValuesForTagsChildren();
		
					int colNum = 0;
					for (XMLTagValue col : row) {
						outFile.print(col.getTagValue());
						if (++colNum < row.size()) { // then not last column, so add separator
							outFile.print(fileSettings.getFieldSeparator());
						}
					}
					outFile.println();
					logger.info("[" + Thread.currentThread().getName() + "]: Wrote record for audit key value " + auditKeyValues);
					newDoc.setCurrentNodeToFirstElement(); // For looping
				}
				numRowsProcessed--; // just need to roll back by one to get actual number processed
		
		
				///////////////////////////////////////////////
				// If got here, message was successfully transmitted.
				// So build Response...
				///////////////////////////////////////////////
				XMLDocument response = createResponseDoc(); // create doc shell
				response.addElement(null, "SEND_STATUS", "SUCCESS");
				response.addElement(null, "NUM_ROWS_WRITTEN", String.valueOf(numRowsProcessed));
				
				ProcessResponse.Builder responseBuilder = new ProcessResponse.Builder(ProcessResponseCode.SUCCESS, numRowsProcessed)
						.setResponse(response.toString())
						.setInputDoc(inputDoc)
						.setAuditKeyValues(auditKeyValues);
				
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
		logger.info("All quiet, going to close output file.");

		// Don't want to do this immediately, otherwise a queued task would re-open, a running task would fail
		// So, put on queue, to follow any previous requests to process a message
		if (outFile != null) {
			execPool.execute( new Runnable() {
				public void run() {
					if (outFile != null) outFile.close(); // Have to ask again, as am now executing later, in another thread
					outFile = null;
				}
			});
		}
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	@Override
	public void performCleanup(LwLogger shutdownLogger) {
		// So, put on queue, to follow any previous requests to process a message
		if (outFile != null) {
			if ( ! execPool.isShutdown()) { // this check in case we,ve already called this method
				execPool.execute( new Runnable() {
					public void run() {
					}
				});
			}
		}
		
		// Flush responseQueue (reading thread then has option to close down itself, on receiving this Poison Pill)
		// (subMitPoisonPill() could have been called prior to this.)
		subMitPoisonPill();
		
		// Wait for things to stop
		try {
			execPool.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e1) {
			// Just reset interruption and continue
			Thread.currentThread().interrupt();
		}

		// Don't want to do this immediately, otherwise a queued task would re-open, a running task would fail
		if (outFile != null) outFile.close(); // Have to ask again, as am now executing later, in another thread
			outFile = null;

		if (shutdownLogger != null) {
			try { shutdownLogger.appendln("Closed output file.");} catch (IOException e) { /* do nothing */}
		}
		else {
			logger.info("Closed output file.");
		}
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
		
	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	  * Open a new messages file of the given name
	  *
	  * @param fileName the path & name of the file to create/append to
	  * @param append true if we want to append to a new file, false to start new / overwrite
	  *
	  */
	private void openMessagesFile(String fileName, boolean append)
										throws MessagingException {
		///////////////////////////////////////////////
		// Open a new messages file.
		///////////////////////////////////////////////
		//initialize StreamResult with File object to save to file
		try {
			outFile = new PrintWriter(new BufferedWriter(new FileWriter(fileName, append)));
			logger.info("Opened output file " + fileName + " for " + (append ? "append" : "create"));
			outFileIsEmpty = ! append; // set this to true every time we create a new file
		}
		catch (IOException e) {
			logger.severe("IOException: " + e.getMessage());
			throw new MessagingException("Caught IOException trying to open a new messages output file " + fileName + ": " + e.getMessage());
		}
	}

	/**
	  *
	  * Get the concatenated values for the given audit key names
	  *
	  * @param doc the XML document
	  * @param auditKeyNamesSet the set of column names to identify the audit key values
	  * @param separator the char(s) used to separate audit key parts
	  *
	  * @return the concatenated audit key values for this message, the empty string if no values
	  */
	private String getConcatenatedAuditKeyValues(XMLDocument doc, Vector<XMLTagValue> auditKeyNamesSet, String separator) {

		String concatenatedValues = "";

		// Using Enumeration instead of for-each loop, so could use hasMoreElements() when adding separator
		Enumeration<XMLTagValue> enumAuditKeyNames = auditKeyNamesSet.elements();
		while (enumAuditKeyNames.hasMoreElements()) {
			XMLTagValue tv = enumAuditKeyNames.nextElement();

			String nextValue = doc.getValueForTag(tv.getTagValue());

			if (nextValue != null) {
				concatenatedValues += nextValue + ((separator != null && enumAuditKeyNames.hasMoreElements()) ? separator : "");
			}
		}

		return concatenatedValues;
	}

	/**
	  * Start a response doc
	  *
	  */
	private XMLDocument createResponseDoc()
								throws MessagingException {

		XMLDocument newResponse = null;

		try {
			newResponse = XMLDocument.createDoc("<FILE_REQUEST></FILE_REQUEST>", XMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(XMLException e) {
			logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
			throw new MessagingException("LwProcessMessageForDb.buildErrorResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
		}
		
		return newResponse;
	}

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