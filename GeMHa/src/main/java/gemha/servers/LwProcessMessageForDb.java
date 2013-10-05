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
import java.util.Vector;

import lw.XML.*;
import lw.db.*;
import lw.utils.*;
import gemha.support.LwProcessMessageForDbSettings;
import gemha.support.LwMessagingException;
import gemha.support.LwProcessResponse;
import gemha.support.LwProcessResponse.ProcessResponseCode;
import gemha.interfaces.LwIProcessMesssage;

/**
  * This class transforms an XML message into a SQL statement and submits it to the database for processing.
  *
  * @author Liam Wade
  * @version 1.0 20/11/2008
  */
public class LwProcessMessageForDb implements LwIProcessMesssage {

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
    private final BlockingQueue<Future<LwProcessResponse>> responseQueue = new LinkedBlockingQueue<Future<LwProcessResponse>>(1000);

	private LwDbConnection dbConn = null;
	private LwProcessMessageForDbSettings settings = null;
	
	public LwProcessMessageForDb() {
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

		if (settingsFileName == null) {
			throw new LwSettingsException("Parameter settingsFileName was null.");
		}

		settings = new LwProcessMessageForDbSettings(settingsFileName, LwXMLDocument.SCHEMA_VALIDATION_ON);

		try {
			// Note: if autoCommitting() true, all SQL statements will be executed and committed as individual transactions
			// with no need to call commit(), otherwise transactions are grouped until commited
			 dbConn = new LwDbConnection(settings.getJdbcClass(), settings.getDbURL(), settings.getUserName(), settings.getUserPass(), settings.autoCommitting());
		}
		catch(LwDbException e) {
			logger.severe("Couldn't create new LwDbConnection: " + e.getMessage());
			throw new LwSettingsException("Couldn't create new LwDbConnection: " + e.getMessage());
		}

		//////////////////////////////////////////////////////////////////
		// Set the db date format...
		//////////////////////////////////////////////////////////////////
		try {
			String dateFormat = settings.getDateFormat();

			if (dateFormat != null) {
				dbConn.setDateFormat(dateFormat);
			}
		}
		catch(LwDbException e) {
			logger.severe("Couldn't set Date Format: " + e.getMessage());
			throw new LwSettingsException("Couldn't set Date Format: " + e.getMessage());
		}

		try {
			 // Prepare any supplied Prepared Statements now...
			 // Note:	Looks like oracle does not support precompilation. In this case, the statement may not be sent to
			 // 		the database until the PreparedStatement object is executed.
			 //			This means we cannot tell if the SQL is valid until the first message comes along to execute it!!!!!
			 for (LwPreparedStatementTemplate pst : settings.getPreparedStatementTemplates()) {
				 dbConn.prepareStatement(pst);
			 }
		}
		catch(LwDbException e) {
			logger.severe("Couldn't prepare a supplied Prepared Statement: " + e.getMessage());
			throw new LwSettingsException("Couldn't prepare a supplied Prepared Statement: " + e.getMessage());
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
	  * @throws LwMessagingException if a problem was encountered processing the message
	  */
	@Override
	public LwProcessResponse processMessageSynch(final String messageText, final LwXMLDocument inputDoc, String auditKeyValues)
											throws LwMessagingException {
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
	  * @throws LwMessagingException if a problem was encountered processing the message
	  */
	@Override
	public void processMessageAsynch(final String messageText, final LwXMLDocument inputDoc, String auditKeyValues)
											throws LwMessagingException {
		processMessage(messageText, inputDoc, auditKeyValues, ProcessingMode.ASYNCHRONOUS);
	}

	/**
	/**
	  * Process a message to perform an action against the database
	  * A LwMessagingException will be thrown when a technical problem arises.
	  * If the error is related to the database action being requested, a negative error code will be returned and the reason will appear in the response message
	  *
	  * @param messageText the message to be processed
	  * @param inputDoc the original input message as an XML document, null if message was not XML. To be returned with result - DO NOT MODIFY, is NOT threadsafe!!
	  * @param auditKeyValues audit Key Values for the message (can be null). To be returned with result
	  * @param synchronous 	if true, the message is to be processed on the same thread as the caller, blocking for result
	  * 					if false, the message is to be processed on another thread, non-blocking. Result to be collected by calling getResponse().
	  *
	  * @return the next response from process, null if no more results will ever arrive
	  * 
	  * @throws LwMessagingException if a problem was encountered processing the message
	  */
	private LwProcessResponse processMessage(final String messageText, final LwXMLDocument inputDoc, final String auditKeyValues, ProcessingMode processingMode)
											throws LwMessagingException {

		Callable<LwProcessResponse> processMessageTask = new Callable<LwProcessResponse>() {
			@Override
			public LwProcessResponse call() throws LwMessagingException {
				logger.info("Control now in messageProcessor.");
				logger.finer("Processing message: " + messageText);
		
				LwXMLDocument response = createResponseDoc(); // create doc shell

				//////////////////////////////////////////////////////////////////
				// Set up a new XML doc
				//////////////////////////////////////////////////////////////////
				LwXMLDocument newDoc = null;
				try {
					newDoc = LwXMLDocument.createDoc(messageText, LwXMLDocument.SCHEMA_VALIDATION_OFF);
				}
				catch(LwXMLException e) {
					logger.severe("LwXMLException: " + e.getMessage());
					logger.warning("InputMessage was :" + messageText);
					throw new LwMessagingException("Could not create new XML document: " + e.getMessage());
				}
		

				//////////////////////////////////////////////////////////////////
				// Open a Db connection, if one not already open.
				//////////////////////////////////////////////////////////////////
				if ( ! dbConn.connectionOpen()) { // then just open it - assume was closed because things were quiet.
					try {
						dbConn.reOpen();
					}
					catch(LwDbException e) {
						logger.severe("LwDbException: " + e.getMessage());
						throw new LwMessagingException("Caught LwDbException trying to re-open database connection: " + e.getMessage());
					}
				}
		
				//////////////////////////////////////////////////////////////////
				// Do the work, committing if successful, rolling back if not...
				// Beware that a transaction may commit immediately, if requested in message
				//////////////////////////////////////////////////////////////////
		
				// Store all actions in this Vector
				Vector<LwProcessMessageForDbAction> allActions = new Vector<LwProcessMessageForDbAction>();
		
				int numActionsApplied;
				try {
					numActionsApplied = performActions(newDoc, allActions);
		
					try {
						//////////////////////////////////////////////////////////////////////////
						// Commit transactions (not already committed immediately on instrs)
						//////////////////////////////////////////////////////////////////////////
						dbConn.sessionCommit();
						logger.info("Committed outstanding Db transaction(s).");
	
						//////////////////////////////////////////////////////////////////////////
						// Add all Action results to response, marking appropriate ones as committed...
						//////////////////////////////////////////////////////////////////////////
						for (LwProcessMessageForDbAction dbAction : allActions) {
							dbAction.markExecutedAsCommitted();
							dbAction.addResultToResponse(response);
						}
					}
					catch(LwDbException e) {
						throw new LwMessagingException("Caught LwDbException trying to commit transaction(s): " + e.getMessage());
					}
				}
				catch(Exception e) {
					// Just temporarily catch ANY exception, so can roll back trancaction, if we got an error...
					try { dbConn.sessionRollback();} catch(LwDbException e2) {/* can't do any more anyway - already Exceptioned */}
					logger.warning("Rolled back Db transaction(s). Throwing LwMessagingException...");
					e.printStackTrace();
					throw new LwMessagingException(e.toString() + ": see console for Stack Trace."); // gives name and getMessage()
				}

				LwProcessResponse.Builder responseBuilder = new LwProcessResponse.Builder(ProcessResponseCode.SUCCESS, numActionsApplied)
					.setAuditKeyValues(auditKeyValues)
					.setResponse(response.toString())
					.setInputDoc(inputDoc);
		
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
				throw new LwMessagingException("[" + Thread.currentThread().getName() + "]: Caught Exception calling processMessageTask: " + e);
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
	  * @throws LwMessagingException if a problem was encountered processing the message
	  * @throws InterruptedException if CALLING thread is noticed as interrupted while getting response
	  */
	@Override
	public LwProcessResponse getResponse() throws LwMessagingException, InterruptedException {
		LwProcessResponse response = null;
		try {
			Future<LwProcessResponse> fr = responseQueue.take(); // will block here if queue empty (will never return null - BlockingQueue doesn't allow)
			response = fr.get(); // will block here if next task in queue not yet finished
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof LwMessagingException)
				throw (LwMessagingException) cause;
			else
				throw launderThrowable(cause);
		}

		logger.info("[" + Thread.currentThread().getName() + "]: Returning response retrieved from Response Queue Future to response handler.");
		return response;
	}

	/**
	  * Do whatever you like when things are quiet
	  *
	  */
	public void goQuiet() {
		logger.info("All quiet, going to close database connection.");
		dbConn.close(null);
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		if (dbConn != null) {
			dbConn.close(shutdownLogger);
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
	  * Perform an action against the database.
	  * NOTE: this method may be called on a separate thread, in processMessage
	  *
	  * @param inputDoc the XML containing the database commands
	  * @param allActions store actions and their results in this Vector
	  *
	  * @return 0 for success with no response necessary, n for success and response(s) ready, less than zero for error that will be explained in the response.
	  */
	private int performActions(LwXMLDocument inputDoc, Vector<LwProcessMessageForDbAction> allActions)
											throws LwMessagingException {

		logger.finer("Going to perform all actions...");

		String actionOnError = "respond";

		//////////////////////////////////////////////////////////////////////////
		// Find out what to do with errors...
		//////////////////////////////////////////////////////////////////////////
		String dbLevelactionOnError = inputDoc.getValueForTag("MESSAGE/DBACTION/ACTION_ON_ERROR"); // respond, exception
		if (dbLevelactionOnError != null) {
			actionOnError = dbLevelactionOnError;
		}

		inputDoc.setCurrentNodeToFirstElement(); // need to go back to top of doc, for next search

		//////////////////////////////////////////////////////////////////////////
		// Process all actions...
		// The processing order is, all INSERTs, then all UPDATEs, then all DELETEs, and finally all  SELECTs  
		//////////////////////////////////////////////////////////////////////////
		int totalActionsApplied = 0;
		String[] actions = {"INSERT", "UPDATE", "DELETE", "SELECT"}; // aid to select actions in order
		for (String action : actions) {
			int numThisTypeOfActionApplied = 0;
			while (inputDoc.setCurrentNodeByPath("MESSAGE/DBACTION/" + action, ++numThisTypeOfActionApplied)) {
				logger.fine("Found " + action + " action to process.");
				LwProcessMessageForDbAction dbAction = new LwProcessMessageForDbAction(action, inputDoc, settings.getAuditKeyNamesSet(action), settings.getAuditKeysSeparator());
				allActions.addElement(dbAction);

				dbAction.buildAction(settings.getDefaultTablename());
				dbAction.performAction(dbConn, actionOnError);

				totalActionsApplied += dbAction.getNumActions();
				inputDoc.setCurrentNodeToFirstElement(); // need to go back to top of doc, for next search
			}
		}

		return totalActionsApplied;
	}

	/**
	  * Start a response doc
	  *
	  */
	private LwXMLDocument createResponseDoc()
								throws LwMessagingException {

		LwXMLDocument newResponse = null;

		try {
			newResponse = LwXMLDocument.createDoc("<MESSAGE><DBACTION></DBACTION></MESSAGE>", LwXMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(LwXMLException e) {
			logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
			throw new LwMessagingException("LwProcessMessageForDb.buildErrorResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
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
				responseQueue.put(execPool.submit( new Callable<LwProcessResponse>() {
					public LwProcessResponse call() throws LwMessagingException {
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
