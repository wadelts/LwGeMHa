package gemha.servers;

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.*;

import lw.XML.*;
import lw.sockets.SocketComms;
import lw.sockets.SocketException;
import lw.sockets.SocketTransferMessage;
import lw.sockets.SocketComms.SocketType;
import lw.utils.*;
import gemha.support.ProcessMessageForSocketSettings;
import gemha.support.MessagingException;
import gemha.support.ProcessResponse;
import gemha.support.ProcessResponse.ProcessResponseCode;
import gemha.interfaces.IProcessMesssage;

/**
  * This class sends messages over a socket and returns responses, if required.
  *
  * @author Liam Wade
  * @version 1.0 10/12/2008
  */
public class ProcessMessageForSocket implements IProcessMesssage {

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

	volatile private Socket s;

	volatile private ProcessMessageForSocketSettings settings = null;
	volatile private int fallBackTransactionID = 0; // to be used to create unique trans ids, if no audit keys supplied
    volatile private SocketComms socketComms = null;

	public ProcessMessageForSocket() {
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
	  */
	public void performSetup(String settingsFileName) throws SettingsException {

		if (settingsFileName == null) {
			throw new SettingsException("Parameter settingsFileName was null.");
		}

		settings = new ProcessMessageForSocketSettings(settingsFileName, XMLDocument.SCHEMA_VALIDATION_ON);

		try {
			socketComms = openSocket(settings.getHostName(), settings.getPortNumber());
		}
		catch (MessagingException e) {
			logger.severe("LwMessagingException: " + e.getMessage());
			throw new SettingsException("Caught LwMessagingException trying to open a new socket : " + e.getMessage());
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
	  * Process a message
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
	private ProcessResponse processMessage(final String messageText, final XMLDocument inputDoc, final String auditKeyValues, final ProcessingMode processingMode)
											throws MessagingException {

		Callable<ProcessResponse> processMessageTask = new Callable<ProcessResponse>() {
			@Override
			public ProcessResponse call() throws MessagingException {
	
				if (s == null) { // then socket was closed, re-open it
					socketComms = openSocket(settings.getHostName(), settings.getPortNumber());
				}
		
				//////////////////////////////////////////////////////////////////
				// Set up a new XML doc
				//////////////////////////////////////////////////////////////////
				XMLDocument newDoc = null;
				try {
					newDoc = XMLDocument.createDoc(messageText, XMLDocument.SCHEMA_VALIDATION_OFF);
				}
				catch(XMLException e) {
					logger.severe("LwXMLException: " + e.getMessage());
					logger.warning("InputMessage was :" + messageText);
					throw new MessagingException("Could not create new XML document: " + e.getMessage());
				}
		
				///////////////////////////////////////////////
				// Get audit information from message (or make it up)...
				///////////////////////////////////////////////
//				String auditKeyValues = getConcatenatedAuditKeyValues(newDoc, settings.getAuditKeyNamesSet(), settings.getAuditKeysSeparator()); // works at "current node" level
		
				///////////////////////////////////////////////
				// Send the data (in chunks, if necessary)...
				///////////////////////////////////////////////
				SocketComms.SocketService service = (settings.getApplicationLevelResponse().equals("synchronous") ? SocketComms.SocketService.CONSUME_RESPOND : SocketComms.SocketService.CONSUME);
				try {
					if (socketComms == null) {
						logger.severe("socketComms IS NULL!!!!!");
						throw new MessagingException("Could not send XML document: socketComms IS NULL!!!!!");
					}
						
					socketComms.sendMessage(new SocketTransferMessage(new Integer(0), auditKeyValues, service, SocketComms.SocketFormat.XML, messageText));
				} catch (SocketException e) {
					logger.severe("LwSocketException: " + e.getMessage());
					throw new MessagingException("Could not send XML document: " + e.getMessage());
				}
				// Get confirmation/error response from server ( just to say got request)
				try {
					socketComms.next();
				} catch (SocketException e) {
					logger.severe("LwSocketException: " + e.getMessage());
					throw new MessagingException("Failed to receive response from server: " + e.getMessage());
				}
				
				// Throw exception if a technical error was encountered
				int serverRespCode = socketComms.getLastErrorNo();
				if (serverRespCode != 0) {
					logger.severe("Socket Server returned error " + serverRespCode + " when trying to send data " + auditKeyValues + ".");
					throw new MessagingException("Socket Server returned error " + serverRespCode + " when trying to send data for message " + auditKeyValues + ".");
				}
		
				///////////////////////////////////////////////
				// If got here, message was successfully transmitted.
				// So build Response...
				///////////////////////////////////////////////
				XMLDocument response = createResponseDoc(); // create doc shell
				response.addElement(null, "SEND_STATUS", "SUCCESS");
		
				// Check if we should await an application-level response
				if (settings.getApplicationLevelResponse().equals("synchronous")) {
					XMLDocument applicResponse = getApplicationResponse();
					response.importNode(applicResponse.getCurrentNode(), true);
				}
				
				ProcessResponse.Builder responseBuilder = new ProcessResponse.Builder(ProcessResponseCode.SUCCESS, 1)
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
				logger.info("[" + Thread.currentThread().getName() + "]: put to responseQueue interrupted.");
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
	public void goQuiet() {
		logger.info("All quiet, going to close socket connection.");
		try {
			socketComms.sendMessage(new SocketTransferMessage(new Integer(0), "AutoRequest", SocketComms.SocketService.CLOSE, SocketComms.SocketFormat.XML, "Close me"));
		} catch (SocketException e) {
			logger.severe("Caught LwSocketException trying to tell server to CLOSE connection (no action taken): " + e.getMessage());
		}

		try {
			s.close();
			socketComms = null;
		}
		catch (IOException e) {
			logger.severe("Caught IOException trying to close socket connection (no action taken): " + e.getMessage());
		}

		s = null;
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
		logger.info("[" + Thread.currentThread().getName() + "]: Going to block on awaitTermination...");
		execPool.awaitTermination(timeout, unit) ;
	}
		
	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		try {
			awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e1) {
			// Closing down anyway, so no prob with interrupt
		}
		
		if (s != null && socketComms != null) {
			try {
				socketComms.sendMessage(new SocketTransferMessage(new Integer(0), "AutoRequest", SocketComms.SocketService.CLOSE, SocketComms.SocketFormat.XML, "Close me"));
			} catch (SocketException e) {
				logger.severe("Caught LwSocketException trying to tell server to CLOSE connection (no action taken): " + e.getMessage());
			}
			
			try {
				s.close();
				socketComms = null;
			}
			catch (IOException e) {
				if (shutdownLogger != null) {
					try { shutdownLogger.appendln("Caught IOException trying to close socket connection (no action taken): " + e.getMessage());} catch (IOException e2) { /* do nothing */}
				}
				else {
					logger.severe("Caught IOException trying to close socket connection (no action taken): " + e.getMessage());
				}
			}

			s = null;

			if (shutdownLogger != null) {
				try { shutdownLogger.appendln("Closed socket connection.");} catch (IOException e) { /* do nothing */}
			}
			else {
				logger.info("Closed socket connection.");
			}
		}
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	  * Open a socket for communications
	  *
	  */
	private SocketComms openSocket(String hostName, int portNo)
						throws MessagingException {
		///////////////////////////////////////////////
		// Connect to the socket on "this" machine.
		///////////////////////////////////////////////
		try {
			s = new Socket(hostName, portNo);
		}
		catch (UnknownHostException e) {
			logger.severe("UnknownHostException: " + e.getMessage());
			throw new MessagingException("Caught UnknownHostException trying to open a new socket : " + e.getMessage());
		}
		catch (IOException e) {
			logger.severe("IOException: " + e.getMessage());
			throw new MessagingException("Caught IOException trying to open a new socket : " + e.getMessage());
		}

		// May throw LwSocketException
		try {
			socketComms = new SocketComms(s, SocketType.CLIENT);
			logger.info("Socket Comms object created.");
			// Read Server Ready message.
			socketComms.next();
		} catch (SocketException e) {
			throw new MessagingException("Caught LwSocketException trying to set up communications on socket : " + e.getMessage());
		}

		logger.info("Socket opened on port " + portNo + " on host " + hostName);

		return socketComms;
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
		if (auditKeyNamesSet == null) { // than make up new key...
			return String.valueOf(++fallBackTransactionID);
		}
		else {
			Enumeration<XMLTagValue> enumAuditKeyNames = auditKeyNamesSet.elements();
			while (enumAuditKeyNames.hasMoreElements()) {
				XMLTagValue tv = enumAuditKeyNames.nextElement();

				String nextValue = doc.getValueForTag(tv.getTagValue());

				if (nextValue != null) {
					concatenatedValues += nextValue + ((separator != null && enumAuditKeyNames.hasMoreElements()) ? separator : "");
				}
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
			newResponse = XMLDocument.createDoc("<MESSAGE></MESSAGE>", XMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(XMLException e) {
			logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
			throw new MessagingException("LwProcessMessageForDb.buildErrorResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
		}
		
		return newResponse;
	}

	/**
	  * Start a response doc
	  *
	  * @return an XML document containing the response from the server application
	  */
	private XMLDocument getApplicationResponse()
								throws MessagingException {

		// Now get confirmation/error response from server for action requested
		try {
			socketComms.next();
		} catch (SocketException e) {
			throw new MessagingException("Caught LwSocketException getting Application-level response from server: " + e);
		}

		if (socketComms.getLastErrorNo() != 0) {
			logger.severe("Application-level response not received from server");
			throw new MessagingException("LwProcessMessageForSocket.getApplicationResponse(): Application-level response not received from server");
		}
		else { // got something, so create an XM doc and return it...
			XMLDocument applicResponse = null;
	
			try {
				logger.finer("Going to create XML doc from returned String:" + socketComms.getLastMessageReceived());
				applicResponse = XMLDocument.createDoc(socketComms.getLastMessageReceived(), XMLDocument.SCHEMA_VALIDATION_OFF);
				return applicResponse;
			}
			catch(XMLException e) {
				logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
				throw new MessagingException("LwProcessMessageForSocket.getApplicationResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
			}
		}
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
		logger.info("[" + Thread.currentThread().getName() + "]: Submitting Poison Pill to Executor queue, so will cause responseProcessor to close down.");
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
