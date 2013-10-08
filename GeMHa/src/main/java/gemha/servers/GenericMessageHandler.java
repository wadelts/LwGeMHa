package gemha.servers;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.*;

import lw.utils.*;
import lw.XML.*;
import gemha.interfaces.*;
import gemha.support.*;

/**
  * This server class processes a message from a chosen source (e.g. MQ, Database, Socket or File(s)) and places the response(s) on an MQ queue, to a Socket or in file(s).
  * The sequence of events is as follows:
  * Messages flow in the direction: messageListener --> messageProcessor --> messageResponder
  * We accept messages from the messageListener, which retrieves them from a File/Queue/Socket.
  * The messageProcessor is passed the message, processes it, sends it to the Database/Socket/File and returns
  * a response (if required).
  * If a response IS required the ResponseProcessorTask will accept the response from the messageProcessor and
  * send it to the appropriate medium (File/Socket/HTTP Connection), via the messageResponder.
  * 
  * One example would be a (simple) SELECT request retrieved from a Queue, processed against the database and
  * the resulting dataset returned to another Queue.
  *  
  * This class employs the services of IApp interface to allow graceful shutdown.
  * 
  * NOTE: This implementation will only work/be useful for a source input that can be read continuously by one thread
  * 	  and processed by other threads, without having to commit the retrieval of the input message.
  * 	  For example, retrieving from a queue usually means only committing the "get" after the message has been
  * 	  successfully processed, effectively killing any possibility of processing in parallel.
  * 
  * @author Liam Wade
  * @version 1.0 15/10/2008
  * @version 1.1 30/09/2013 Implemented ResponseProcessorTask as runnable, accepting responses from Processor on separate thread. 
  */
public class GenericMessageHandler implements IApp
{
    private static final Logger logger = Logger.getLogger("gemha");

	private final String settingsFileName;
	private volatile GenericMessageHandlerSettings settings = null;
	private IAcceptMesssages messageListener = null;		// the interface for accepting messages
	private IProcessMesssage messageProcessor = null;	// the interface for processing messages
	private IStoreMesssage messageResponder = null;		// the interface for storing responses
	
	private volatile boolean mainProcessToCloseDown = false;	// controls main while-loop (can be set by ResponseProcessorTask)
    // Executor for processing messages
    private final ExecutorService execPool = Executors.newSingleThreadExecutor();
	private ResponseProcessorTask responseProcessorTask;		// Handles responses from messageProcessor. Will be null if messageResponder supplied in constructor


	/**
	  * Constructor used when LwGenericMessageHandler itself will decide from which
	  * object it is to receive messages. 
	  *
	  * @param settingsFileName (and optionally path) of file from which settings are to be read
	  */
	public GenericMessageHandler(String settingsFileName) {
		this.settingsFileName = settingsFileName;
	}

	/**
	  * Constructor used when LwGenericMessageHandler is given the object
	  * from which it is to receive messages. 
	  *
	  * @param settingsFileName Name (and optionally path) of file from which settings are to be read
	  * @param messageListener object that will supply messages.
	  */
	public GenericMessageHandler(String settingsFileName, IAcceptMesssages messageListener) {
		this.settingsFileName = settingsFileName;
		this.messageListener = messageListener;
	}


	/**
	  * Constructor used when LwGenericMessageHandler is given the object
	  * from which it is to receive messages and to which it should send any Responses.
	  * That is, the incoming messages AND responses are handled programatically (perhaps by the same process.)
	  *
	  * @param settingsFileName Name (and optionally path) of file from which settings are to be read
	  * @param messageListener object that will supply messages - implements interface LwIAcceptMesssages.
	  * @param messageResponder object that will forward response messages - implements interface LwIStoreMesssage.
	  */
	public GenericMessageHandler(String settingsFileName, IAcceptMesssages messageListener, IStoreMesssage messageResponder) {
		this.settingsFileName = settingsFileName;
		this.messageListener = messageListener;
		this.messageResponder = messageResponder;
	}

	/**
	  *
	  * Start the application
	  */
	public void start() {
		final long start;
		final long end;

		start = System.nanoTime();
		
		logger.info("<*<*<*<*< Starting Up >*>*>*>*>");

		try {
			settings = new GenericMessageHandlerSettings(settingsFileName, XMLDocument.SCHEMA_VALIDATION_ON);
		}
		catch(SettingsException e) {
			logger.severe("Exception encountered loading application configuration settings from file " + settingsFileName + ": " + e);
			System.out.println("Exception encountered loading application configuration settings from file " + settingsFileName + ": " + e);
			System.exit(-10);
		}

		//////////////////////////////////////////////////////////////////
		// Load the procesing interface...
		// This dynamically-loaded class will perform whatever procesing
		// is necessary for this particular instance of LwGenericMessageHandler
		//////////////////////////////////////////////////////////////////
		messageProcessor = loadMessageProcessor();

		logger.info("<*<*<*<*< Startup completed successfully >*>*>*>*>");

		//////////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////
		//                M a i n  P r o c e s s i n g...
		//////////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////

		//////////////////////////////////////////////////////////////////
		// Determine and prepare input medium, if not already supplied
		// in a constructor...
		//////////////////////////////////////////////////////////////////
		setupMessageListener();


		//////////////////////////////////////////////////////////////////
		// Determine and prepare output medium...
		// No problem if no output medium, if don't want to output anything.
		//////////////////////////////////////////////////////////////////
		setupMessageResponder();


		//////////////////////////////////////////////////////////////////
		// Start handling messages...
		//////////////////////////////////////////////////////////////////
		try {

			handleMessages(settings);
			
			// Finished now, but give executors time to flush results
			if (responseProcessorTask != null)
				try {
					messageProcessor.awaitTermination(1, TimeUnit.MINUTES);
					// Flush execPool buffer and shut it down (no waiting)...
					execPool.shutdown();
					execPool.awaitTermination(1, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					// Reset interrupted, finishing anyway
					Thread.currentThread().interrupt();
				}
		}
		catch (MessagingException e) {
			logger.severe("Stopped processing: Caught LwMessagingException exception: " + e);
			System.exit(-16);
		}
//		catch (Exception e) {
//			logger.severe("Stopped processing: Caught unknown exception: " + e);
//		}
		finally { // but probably won't even get here, as normally terminated by interrupt
			logger.info("Shutting down (further messages may only be sent to file " + settings.getShutDownLogFileName() + ")");

			end = System.nanoTime();
			logger.info("Finished processing: LwGenericMessageHandler ran for " + (end - start)/1.0e9 + " seconds");

			// Stop the response-processing thread
			// BEST NOT DO THIS. Allow the Processor tell the response-processing thread that all finished
//			if (responseProcessorTask != null)
//				responseProcessorTask.interrupt();
			// Close log file now. Is no use during shutdown anyway, as the Logger has its own shutdown hook
			// and can therefore be shut down by the virtual machine before our shutdown hook.
			settings.releaseResourses();
		}
		
		logger.exiting("LwGenericMessageHandler", "start");
	}

	/**
	 *  Load the class chosen to process messages.
	 *  Application will exit if any problem encountered either loading class
	 *  or performing its setup procedure.
	 *  
	 *  @return the loaded message processor
	 */
	private IProcessMesssage loadMessageProcessor() {
		IProcessMesssage messageProcessor = null;
		try {
			messageProcessor = (IProcessMesssage)(Class.forName(settings.getMessageProcessingClassName()).newInstance());
			messageProcessor.performSetup(settings.getMessageProcessingSettingsFileName());
		}
		catch(ClassNotFoundException e1) {
			logger.severe("Could not load message-processing class " + settings.getMessageProcessingClassName() + ", specified in TagName MessageProcessingClassName.");
			System.exit(-6);
		}
		catch(InstantiationException e2) {
			logger.severe("Could not instantiate message-processing class " + settings.getMessageProcessingClassName() + ", specified in TagName MessageProcessingClassName.");
			System.exit(-7);
		}
		catch(IllegalAccessException e3) {
			logger.severe("IllegalAccessException instantiating message-processing class " + settings.getMessageProcessingClassName() + ", specified in TagName MessageProcessingClassName. " + e3.getMessage());
			System.exit(-8);
		}
		catch(SettingsException e4) {
			logger.severe("LwSettingsException instantiating message-processing class " + settings.getMessageProcessingClassName() + ", specified in TagName MessageProcessingClassName. " + e4.getMessage());
			System.exit(-9);
		}

		if (messageProcessor == null) {
			logger.severe("Could not load message-processing class " + settings.getMessageProcessingClassName() + ", specified in TagName MessageProcessingClassName. Unknown reason.");
			System.exit(-11);
		}
		
		return messageProcessor;
	}

	/**
	 * Instantiate the correct Message Listener (input) and tell it to perform any setup, if appropriate.
	 */
	private void setupMessageListener() {
		if (messageListener == null) {
			try {
				if (settings.getInputQueueName() != null) { // then am to read messages from MQ
					messageListener = new AcceptMessagesFromQueue(settings.getInputQueueName(), settings.getInputUrlJMSserver());
				}
				else if (settings.getPortNumber() > 0) {
					messageListener = new AcceptMessagesFromSocket(settings.getPortNumber());
				}
				else if (settings.getInputFileNameFilter() != null) {
						messageListener = new AcceptMessagesFromFiles(settings.getInputFileDir(), settings.getInputFileNameFilter(), settings.sortFilteredFileNames(), settings.getColNameList(),
																		settings.getInputDataFormat(), settings.getFieldSeparator(), settings.getMaxRecsPerMessage(),
																		settings.getXMLFormat(), settings.getActionOnError(), settings.getPreparedStatementName(),
																		settings.getImmediateCommit(), settings.getNumRecordsToSkip());
				}
				else { // something very wrong indeed!
					logger.severe("Missing essential information for input source - for example inputQueueName, input portNumber or inputFileNameFilter (or was LwGenericMessageHandler called with incorrect Constructor?)");
					System.exit(-12);
				}
			} catch (SettingsException e) {
				logger.severe("Stopped processing at setupMessageListener: Caught LwSettingsException exception: " + e);
				System.exit(-13);
			}
		}

		try {
			messageListener.performSetup();
		} catch (MessagingException e) {
			logger.severe("Stopped processing calling messageListener.performSetup(): Caught LwMessagingException exception: " + e);
			System.exit(-14);
		}
	}

	/**
	 * Instantiate the correct Message Responder and tell it to open the storage medium, if appropriate.
	 * 
	 * @throws MessagingException if openStorage() encounters a problem
	 */
	private void setupMessageResponder()  {
		if (messageResponder == null) { // but don't check if has already been supplied in a Constructor
			if (settings.getOutputQueueName() != null) { // then am to send messages to MQ
				if (settings.getReplyToQueueName() == null) { // no instruction on where to return a response
					messageResponder = new StoreMesssageToQueue(settings.getOutputQueueName(), settings.getOutputUrlJMSserver());
				}
				else {
					messageResponder = new StoreMesssageToQueue(settings.getOutputQueueName(), settings.getReplyToQueueName());
				}
			}
			else if (settings.getOutputFileNameTemplate() != null) {
				messageResponder = new StoreMesssageToFile(settings.getOutputFileNameTemplate(), settings.getConvertedInputDataFormat());
			}
			else if (settings.getHTTPServerUrl() != null) {
				messageResponder = new StoreMesssageToHTTP(settings.getHTTPServerUrl(), settings.getHTTPEndPointName(), settings.getHTTPWithBackoff(), settings.getConvertedInputDataFormat());
			}
			
			// Only set this up if we know messageResponder is one of the "inbuilt" types - i.e. messageResponder was NOT
			// supplied in a constructor (if it were, we wouldn't know how to handle responses)
			// messageListener should always be populated by now
			// inLoopMode of ResponseProcessorTask set to true here to tell it to loop for responses,
			// as will run in its own thread.
			responseProcessorTask = new ResponseProcessorTask(true, messageResponder, messageListener);
			execPool.execute(responseProcessorTask);
		}

		if (messageResponder != null) {
			try {
				messageResponder.openStorage();
			} catch (MessagingException e) {
				logger.severe("Stopped processing calling messageResponder.openStorage(): Caught LwMessagingException exception: " + e);
				System.exit(-15);
			}
		}
	}


	/**
	  *
	  * This is the main control loop
	  *
	  * @param settings the loaded application config settings
	  *
	  */
	private void handleMessages(GenericMessageHandlerSettings settings)
																throws MessagingException {
		int numMessagesProcessed = 0;

		while ( ! mainProcessToCloseDown) {
			String receivedMessage = null;
			boolean skipMessage = false;	// set to true if there is a problem with a message and it is to be skipped, that is removed from the input queue


			//////////////////////////////////////////////////////////////////
			// Get a message, waiting if necessary
			//////////////////////////////////////////////////////////////////
			messageListener.setWaitInterval(settings.getMilliSecondsBeforeQuiet()); // block for n seconds (if blocking relevant)
			receivedMessage = messageListener.acceptNextMessage();

			if (receivedMessage == null) { // then no message available, might want to try again, depending on input medium
				receivedMessage = handleIncomingEmptyMessage(settings);
			}

			//////////////////////////////////////////////////////////////////
			// Give client chance to close us down
			//////////////////////////////////////////////////////////////////
			if (Thread.currentThread().isInterrupted()) {
				// Will process next message, if relevant, then exit
				mainProcessToCloseDown = true;
			}
				
			//////////////////////////////////////////////////////////////////
			// Handle the incoming Message...
			//////////////////////////////////////////////////////////////////
			if (receivedMessage != null) { // then have something to work on

				numMessagesProcessed = handleIncomingMessage(settings, numMessagesProcessed, skipMessage, receivedMessage);
			}


			if (settings.getInputLimit() > 0) { // then a limit was set on the number of messages to process
				if (numMessagesProcessed >= settings.getInputLimit()) {
					mainProcessToCloseDown = true;
					logger.info("Specfied limit of " + settings.getInputLimit() + " message(s) reached.");
				}
			}

			if ( mainProcessToCloseDown) { // There's a slight chance this msg may not output, if another thread sets mainProcessToCloseDown just after checking.
				logger.info("Stopping processing and going to close down. (mainProcessToCloseDown is true)");
			}
		} // end while(mainProcessToCloseDown)
	
		if (messageProcessor != null) { // Tell it no more messages to process
			messageProcessor.processMessageAsynch(null, null, null); // Send Poison Pill to processor
		}
	}

	/**
	 * Handle an incoming message, performing some validation, before forwarding to the Message Processor.
	 * 
	 * @param settings the application settings
	 * @param numMessagesProcessed minds the total number of messages processed by the Processor
	 * @param skipMessage whether message should be skipped or not
	 * 
	 * @return the new value for numMessagesProcessed
	 * @throws MessagingException
	 */
	private int handleIncomingMessage(GenericMessageHandlerSettings settings, int numMessagesProcessed,
									  boolean skipMessage, String receivedMessage) throws MessagingException {
		numMessagesProcessed++;

		String auditKeyValues = "unknown";
		String messageForProcessor = null;
		XMLDocument inputDoc = null;			// the XML version of the received input message

		//////////////////////////////////////////////////////////////////
		// Perform XML-based checks and actions or simply pass on message "as is"...
		//////////////////////////////////////////////////////////////////
		if ("XML".equals(settings.getConvertedInputDataFormat())) { // CSV input would be converted to XML
			inputDoc = createXMLDocFromInput(receivedMessage, settings);

			if (inputDoc != null) {

				// Determine auditKeyValues
				auditKeyValues = getAuditKeyValuesForXMLMessage(inputDoc, settings);
				if ( ! auditKeysFound(inputDoc, settings)) { // will be valid only if ALL auditkeys found in message
					skipMessage = determineActionOnMissingAuditKeys(settings, skipMessage, auditKeyValues, inputDoc);
				}

				// Determine Data Contract Name
				if ( ! skipMessage && ! dataContractNameValid(inputDoc, settings)) { // will be valid if no name to check, or is matched
					skipMessage = determineActionOnInvalidDataContractName(settings, skipMessage, auditKeyValues, inputDoc);
				}

				// Build Target
				if ( ! skipMessage) {
					try {
						messageForProcessor = buildMessageForTarget(inputDoc, settings);
						logger.info("Message for target with AuditKey Value " + auditKeyValues + " built. See next line for content (if logging @ level FINE)...");
						logger.fine(messageForProcessor);
					}
					catch (XMLException e) {
						logger.severe("Message with AuditKey Value " + auditKeyValues + " caused an LwXMLException: "  + e);
						messageListener.stayMessage(auditKeyValues);
						throw new MessagingException("Caught LwXMLException from buildMessageForTarget() (see root cause)", e);
					}
				}
			}
		}
		else { // pass on whole message to processor
			messageForProcessor = receivedMessage;
		}

		if ( ! skipMessage) {
			if (messageForProcessor == null) { // then nothing to process - BIG PROBLEM!
				logger.severe("No Target message was built from Message with AuditKey Value " + auditKeyValues);
				messageListener.stayMessage(auditKeyValues);
				mainProcessToCloseDown = true;
				throw new MessagingException("No Target message was built from Message with AuditKey Value " + auditKeyValues);
			}
			else {
				//////////////////////////////////////////////////////////////////
				// All OK, get the Processor to deal with the message
				//////////////////////////////////////////////////////////////////
				messageProcessor.processMessageAsynch(messageForProcessor, inputDoc, auditKeyValues);

			} // end if (messageForProcessor == null)
		} // end if ( ! skipMessage)
		
		messageListener.consumeMessage(auditKeyValues);
		return numMessagesProcessed;
	}

	/**
	 * Wait for a message to arrive or quit handling messages, depending on the input medium.
	 * A null message has different meaning based on input medium:
	 * 		Queue 		- we'll wait for another message to arrive, blocking indefinitely
	 * 		Socket 		- we'll stop handling messages
	 * 		File 		- we'll stop handling messages, sending a Poison Pill to Processor
	 * 		Java Object - we'll stop handling messages
	 * 
	 * @param settings the application settings
	 * @return the next message, if is Queue-based input, otherwise null
	 * @throws MessagingException
	 */
	private String handleIncomingEmptyMessage(GenericMessageHandlerSettings settings) throws MessagingException {
		
//		messageProcessor.goQuiet(); // tell processor we're not busy now, but only when running in blocking mode (i.e. not batch).

		if (messageListener instanceof AcceptMessagesFromQueue) { // then want to block now, await next message and, possibly, close output queue
			// Close targetConnection here, if is a Queue, because not busy
			if (settings.getOutputQueueName() != null) {
				if (messageResponder != null) {
					messageResponder.closeStorage(); // will be opened automatically by messageResponder, if needed (and trying to close when open causes no problem
				}
			}

			messageListener.setWaitIntervalBlockIndefinitely();
			return messageListener.acceptNextMessage();
		}
		else if (messageListener instanceof AcceptMessagesFromSocket) { // then am finished, so close down
			mainProcessToCloseDown = true;
			logger.info("Socket Server returned null, so closing down.");
		}
		else if (messageListener instanceof AcceptMessagesFromFiles) { // then am finished, so close down
			mainProcessToCloseDown = true;
			logger.info("No more files to process, so closing down.");
		}
		else { // then message listener is java object supplied in constructor, am finished, so close down
			mainProcessToCloseDown = true;
			logger.info("No more messages to process from object " + messageListener.getClass().getName() + ", so closing down.");
		}
		
		return null;
	}

	/**
	 * If Data Contract Name is expected AND was found to not match that in the input message,
	 * then we need to decide whether to continue or not.
	 *  
	 * @param settings the application settings
	 * @param skipMessage whether message should be skipped or not
	 * @param auditKeyValues transaction ID for message being processed
	 * @param inputDoc the original input message
	 * @return true if should skip this message, false if should continue processing the message
	 * @throws MessagingException if problem encountered staying or consuming message
	 */
	private boolean determineActionOnInvalidDataContractName(GenericMessageHandlerSettings settings, boolean skipMessage,
															 String auditKeyValues, XMLDocument inputDoc) throws MessagingException {
		XMLTagValue dataContractName = settings.getDataContractName();
		String actionOnError = null;
		if (dataContractName != null) { actionOnError = dataContractName.getAttributeValue("ActionOnError");}

		if (actionOnError == null || actionOnError.equals("shutdown")) {
			logger.severe("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. Will shut down.");
			messageListener.stayMessage(auditKeyValues);
			skipMessage = true;
			mainProcessToCloseDown = true;
			throw new MessagingException("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. Will shut down.");
		}
		else if (actionOnError.equals("discard")) {
			logger.warning("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. Will discard message.");
			String errorMessageFileName = LwLogger.createFileNameFromTemplate(settings.getErrorFileNameTemplate(), auditKeyValues);

			if (errorMessageFileName != null) {
				inputDoc.toFile(errorMessageFileName, false);
				logger.warning("Message with AuditKey Value " + auditKeyValues + " saved to file " + errorMessageFileName);
				logger.info("Processing of messages will continue.");
			}

			skipMessage = true;
		}
		else { // can assume actionOnError == "none", so just ignore problem
			logger.warning("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. But will still process message.");
		}
		return skipMessage;
	}

	/**
	 * @param settings the application settings
	 * @param skipMessage whether message should be skipped or not
	 * @param auditKeyValues transaction ID for message being processed
	 * @param inputDoc the original input message
	 * @return true if should skip this message, false if should continue processing the message
	 * @throws MessagingException if problem encountered staying or consuming message
	 */
	private boolean determineActionOnMissingAuditKeys(GenericMessageHandlerSettings settings, boolean skipMessage,
														String auditKeyValues, XMLDocument inputDoc) throws MessagingException {
		String actionOnError = settings.getAuditKeysAggregate().getAttributeValue("ActionOnError");
		if (actionOnError == null || actionOnError.equals("shutdown")) {
			logger.severe("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " Will shut down.");
			messageListener.stayMessage(auditKeyValues);
			skipMessage = true;
			mainProcessToCloseDown = true;
			throw new MessagingException("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " Will shut down.");
		}
		else if (actionOnError.equals("discard")) {
			logger.warning("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " Will discard message.");
			String errorMessageFileName = LwLogger.createFileNameFromTemplate(settings.getErrorFileNameTemplate(), auditKeyValues);

			if (errorMessageFileName != null) {
				inputDoc.toFile(errorMessageFileName, false);
				logger.warning("Message with AuditKey Value " + auditKeyValues + " saved to file " + errorMessageFileName);
				logger.info("Processing of messages will continue.");
			}

			skipMessage = true;
		}
		else { // can assume actionOnError == "none", so just ignore problem
			logger.warning("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " But will still process message.");
		}
		return skipMessage;
	}

	/**
	  *
	  * Create a new LwXMLDocument, loading the XML from a String.
	  *
	  * Notes:
	  *		If validation is "off", the XML messge will not be validated against a Schema
	  *		If validation is "on", the XML messge will be validated against a Schema, with two options:
	  *			1) If SchemaDefinitionFileName is supplied in the settings file, that .xsd file will be used to validate,
	  *			   employing "http://www.w3.org/2001/XMLSchema" rules.
	  *			2) If no SchemaDefinitionFileName setting is supplied, the document MUST supply the Schema definition, otherwise
	  *			   a parsing error will occur: for example [cvc-elt.1: Cannot find the declaration of element 'root'],
	  *			   where root is the name of the root element in the message.
	  *
	  * @param message the XML text from which to make a document
	  * @param settings
	  *
	  * @return a new LwXMLDocument
	  */
	private XMLDocument createXMLDocFromInput(String message, GenericMessageHandlerSettings settings) {

		XMLDocument newDoc = null;

		try {
			if (settings.getInputValidationSettings() == null) { // then no validation requested
				newDoc = XMLDocument.createDoc(message, XMLDocument.SCHEMA_VALIDATION_OFF);
			}
			else {
				boolean validateAgainstSchema = (settings.getInputValidationSettings().getAttributeValue("SchemaValidation") == null ? false : (settings.getInputValidationSettings().getAttributeValue("SchemaValidation").equals("on")));
				if (validateAgainstSchema) {
					// note that SchemaDefinitionFileName may be null, in which case the message should have the Schema definition
					// also that SchemaLanguage may be null, in which case the default Schema Language is assumed
					// neither depends on the other
					newDoc = XMLDocument.createDoc(message, XMLDocument.SCHEMA_VALIDATION_ON, settings.getInputValidationSettings().getAttributeValue("SchemaDefinitionFileName"), settings.getInputValidationSettings().getAttributeValue("SchemaLanguage"));
					logger.info("Input message was validated successfully.");
				}
				else { // then no validation turned off
					newDoc = XMLDocument.createDoc(message, XMLDocument.SCHEMA_VALIDATION_OFF);
				}
			}

			return newDoc;
		}
		catch(XMLException e) {
			logger.warning("LwXMLException: " + e.getMessage());
			logger.warning("InputMessage was :" + message);
			return null;
		}
	}

	/**
	  *
	  * Extract the aggregates to be sent to the target for processing
	  *
	  * @param inputDoc the XML message received from MQ
	  * @param settings
	  *
	  * @return a the XML string to be sent to the target for processing
	  */
	private String buildMessageForTarget(XMLDocument inputDoc, GenericMessageHandlerSettings settings)
																	throws XMLException {
		if (inputDoc == null || settings.getSendElementSet() == null) {
			return null;
		}

		//////////////////////////////////////////////////////////////////////////
		// Create a new doc...
		// We'll either send the whole message to the target, or choose tags/aggregates.
		//
		// Notes:
		// We must allow for the fact that the top-level element of the Target Message
		// might not have the same Name as that of the input message.
		//
		// Both aggregates and TAGs may be sent.
		// The path to the aggregate or tag will be created in the Target Message, if it does not already exist.
		// Individual TAGs specified with the same path will appear in the same aggregate in the sent message (i.e the parent aggregate will not be duplicated).
		// If a TAG appears in the list after an aggregate existing along the same path, the TAG will be duplicated in the sent message.
		// If a TAG appears in the list before an aggregate existing along the same path, the aggregate will be duplicated in the sent message.
		//
		// Here's the sequence...
		// Loop through the "send" list
		//		If special name "*" encountered
		//			Import all from inputDoc, changing top-level element's name, if settings.getTargetMainDocElementName() specified
		//		Otherwise...
		//			Create a new, empty, Target message (with top-level element's name=settings.getTargetMainDocElementName(), or same as that of inputDoc, if not specified)
		//			Find the "minded" element in inputDoc
		//			Find the parent of "minded" element in targetDoc (will fail if names of top-level elements differ in docs, or if sub-aggregate not yet there)
		//			If didn't find parent, create it in targetDoc (ignoring top-level element of Location attribute), and then find it
		//			Import the node into targetDoc at the found parent
		//
		//////////////////////////////////////////////////////////////////////////
		XMLDocument targetDoc = null;


		if (sendAllElementsToTarget(settings)) { // then import ALL from the inputDoc - just send whole input message
			String targetMainDocElementName = settings.getTargetMainDocElementName();
			if (targetMainDocElementName == null) { // then just want input doc, with no additional wrapper
				targetMainDocElementName = inputDoc.getCurrentNodeName();
				targetDoc = inputDoc;
			}
			else { // wrap input doc in new tag...
				try {
					targetDoc = XMLDocument.createDoc("<" + targetMainDocElementName + "></" + targetMainDocElementName + ">", XMLDocument.SCHEMA_VALIDATION_OFF);
					inputDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
					targetDoc.importNodesChildren(inputDoc.getCurrentNode(), true);
				}
				catch(XMLException e2) {
					throw new XMLException("LwGenericMessageHandler.buildMessageForTarget(): Fatal Exception creating a new doc: " + e2.getMessage());
				}
			}
		}
		else { // want to include certain child aggregates from the input doc, so must be wrapped
			String targetMainDocElementName = settings.getTargetMainDocElementName();
			if (targetMainDocElementName == null) { // then just use that of input doc
				inputDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
				targetMainDocElementName = inputDoc.getCurrentNodeName();
			}

			try {
				targetDoc = XMLDocument.createDoc("<" + targetMainDocElementName + "></" + targetMainDocElementName + ">", XMLDocument.SCHEMA_VALIDATION_OFF);

				// Add the elements from inputDoc...
				Enumeration<XMLTagValue> sendElements = settings.getSendElementSet();
				while (sendElements.hasMoreElements()) {
					XMLTagValue nextSendElement = sendElements.nextElement();

					inputDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc

					if (inputDoc.setCurrentNodeByPath(nextSendElement.getTagValue(), 1)) { // no prob if we don't find the element

						targetDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc

						// Position "pointer" in targetDoc to correct parent aggregate, creating it if we have to...
						// (Note that the first ELEMENT in the path for the input may have a different name than the new Target message, so we may ignore it)
						XMLTagValue tempTagNoValue = new XMLTagValue(nextSendElement.getTagValue(), null); // just to help split out path 
						String pathToParent = tempTagNoValue.getPathToParent();
						if (pathToParent == null) { // then SendElement is the top-level element - so no parent
							pathToParent = nextSendElement.getTagValue();
						}

						//search under both inputDoc top-level Name and that of Target top-level Name
						if ( ! (targetDoc.setCurrentNodeByPath(pathToParent, 1) || targetDoc.setCurrentNodeByPath(("/" + targetMainDocElementName + "/" + tempTagNoValue.getPathToParentLessFirstElement()), 1))
						   ) { // try to go to parent recipient (if specified), create path if can't
							String pathToParentLessFirstElement = tempTagNoValue.getPathToParentLessFirstElement();

							if (pathToParentLessFirstElement != null) { // would be null if TAG is child of first ELEMENT in doc
								targetDoc.addElement(null, pathToParentLessFirstElement, null);
								targetDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
								targetDoc.setCurrentNodeByPath(tempTagNoValue.getPathToParent(), 1);
							}
						}

						// Finally, copy the found node from inputDoc to the target message
						targetDoc.importNode(inputDoc.getCurrentNode(), true);
					}
				}

			}
			catch(XMLException e2) {
				throw new XMLException("LwGenericMessageHandler.buildMessageForTarget(): Fatal Exception creating a new doc: " + e2.getMessage());
			}
		}

		return targetDoc.toString();
	}

	/**
	  *
	  * This is a helper method to determine if the * option has been used in the settings.getSendElementSet()
	  *
	  * @param settings
	  *
	  * @return true if ALL ("*") included in set, otherwise false
	  */
	private boolean sendAllElementsToTarget(GenericMessageHandlerSettings settings) {
		Enumeration<XMLTagValue> sendElements = settings.getSendElementSet();
		while (sendElements.hasMoreElements()) {
			XMLTagValue tv = sendElements.nextElement();

			if (tv.getTagValue().equals("*")) { // then "import ALL from the inputDoc" is requested
				return true;
			}
		}

		return false;
	}

	/**
	  *
	  * Check that all Audit keys have a value in the given message
	  *
	  * @param doc the XML document
	  * @param settings
	  *
	  * @return true if a value for all keys found
	  */
	private boolean auditKeysFound(XMLDocument doc, GenericMessageHandlerSettings settings) {

		Enumeration<XMLTagValue> enumAuditKeyNames = settings.getAuditKeyNamesSet();
		while (enumAuditKeyNames.hasMoreElements()) {
			XMLTagValue tv = enumAuditKeyNames.nextElement();

			if (doc.getValueForTag(tv.getTagValue()) == null) {
				return false;
			}
		}

		return true;
	}

	/**
	  *
	  * Shut down the application gracefully - will be called by the Java VM when closing down
	  *
	  * @param doc the XML document
	  * @param settings
	  *
	  * @return the concatenated audit key values for this message, the empty string if no values
	  */
	private String getAuditKeyValuesForXMLMessage(XMLDocument doc, GenericMessageHandlerSettings settings) {

		String concatenatedValues = "";

		Enumeration<XMLTagValue> enumAuditKeyNames = settings.getAuditKeyNamesSet();
		while (enumAuditKeyNames.hasMoreElements()) {
			XMLTagValue tv = enumAuditKeyNames.nextElement();

			String nextValue = doc.getValueForTag(tv.getTagValue());

			if (nextValue != null) {
				concatenatedValues += nextValue + ((settings.getAuditKeysSeparator() != null && enumAuditKeyNames.hasMoreElements()) ? settings.getAuditKeysSeparator() : "");
			}
		}

		return concatenatedValues;
	}

	/**
	  *
	  * Match the name of the message's Contract to the one expected
	  *
	  * @param doc the XML document
	  * @param settings.getDataContractName() contains the name of the tag at which to find the data contract name and the value to match
	  *
	  * @return the true if the name of the contract for this message matches the expected one (or the expected one doesn't exist), otherwise false
	  */
	private boolean dataContractNameValid(XMLDocument doc, GenericMessageHandlerSettings settings) {

		if (settings.getDataContractName() == null) { // then will always return true
			return true;
		}

		doc.setCurrentNodeToFirstElement(); // go back to beginning of doc
		String dataContractNameForThisMessage = doc.getValueForTag(settings.getDataContractName().getAttributeValue("Location"));

		if (dataContractNameForThisMessage == null) {
			logger.warning("No Data contract name was found in current message, at location " + settings.getDataContractName().getAttributeValue("Location"));
		}

		String expectedDataContractName = settings.getDataContractName().getTagValue();

		return (expectedDataContractName != null && dataContractNameForThisMessage != null && (dataContractNameForThisMessage.equals(expectedDataContractName)) );
	}

	/**
	  *
	  * Shut down the application gracefully - will be called by the Java VM when closing down
	  *
	  * NOTE : NEVER call System.exit() from this method, or you'll go into a continuous loop!
	  *
	  */
	public void shutDown() {
		// Do a graceful shutdown here
		// NOTE: Do not use the java.util.Logger here, use LwLogger.
		// The Logger has its own shutdown hook so it can be shut down by the
		// virtual machine before our shutdown hook calls this method.

		LwLogger shutdownLogger = null;

		try {
			if (settings == null || settings.getShutDownLogFileName() == null) {
				return;
			}
			else {
				shutdownLogger = new LwLogger(settings.getShutDownLogFileName(), true);
			}
		}
		catch(IOException e) {
			System.out.println("LwGenericMessageHandler.shutDown(): could not open file " + (settings.getShutDownLogFileName() == null ? "unknown" : settings.getShutDownLogFileName()) + " to record shutdown.");
			return;
		}

		try {
			shutdownLogger.appendln("LwGenericMessageHandler.shutDown(): Shutting down");
		}
		catch(IOException e) {
			try { shutdownLogger.close(); } catch (IOException e1) { /* do nothing */}
			System.out.println("LwGenericMessageHandler.shutDown(): could not write to file " + settings.getShutDownLogFileName() + " to record shutdown.");
			return;
		}


		//////////////////////////////////////////////////////////////////
		// Shut-down commands here...
		//////////////////////////////////////////////////////////////////
		if (messageListener != null) {
			messageListener.performCleanup(shutdownLogger);
		}

		if (messageProcessor != null) {
			messageProcessor.performCleanup(shutdownLogger);
		}

		if (messageResponder != null) {
			messageResponder.performStoreMessageCleanup(shutdownLogger);
		}

		// Flush exec buffer and shut it down, if not already done (no waiting)...
		execPool.shutdown();

		try {
			shutdownLogger.appendln("LwGenericMessageHandler.shutDown(): Shutdown completed successfully");
			shutdownLogger.close();
		}
		catch(IOException e) {
			System.out.println("LwGenericMessageHandler.shutDown(): could not write to file " + settings.getShutDownLogFileName() + " to record shutdown completed successfully.");
		}
	}

	/**
	  *
	  * Processor of response messages
	  *
	  * NOTE : inputDoc is the only var we use from the main object (populated by the main thread)
	  *
	  */
	private class ResponseProcessorTask implements Runnable {
		private final IStoreMesssage messageResponder;
		private final IAcceptMesssages messageListener;		// the interface for accepting messages
		private final boolean inLoopMode;						// true if we want to keep looping (i.e. this is run in its own thread)
		private boolean errorEncountered = false;				// true if we find any error

		public ResponseProcessorTask(boolean inLoopMode, IStoreMesssage messageResponder, IAcceptMesssages messageListener) {
			if (messageResponder == null) throw new IllegalArgumentException("ResponseProcessorTask: messageResponder cannot be null.");
			if (messageListener == null) throw new IllegalArgumentException("ResponseProcessorTask: messageListener cannot be null.");

			this.inLoopMode = inLoopMode;
			this.messageResponder = messageResponder;
			this.messageListener = messageListener;
		}
		
		public void run() {
			logger.entering("ResponseProcessorTask", "run");
			do {
				// Get the response object from the messageProcessor (may block)...
				ProcessResponse processedResponse = getProcessedResponse();
				if ( processedResponse == null) { // null is Poison Pill
					logger.info("Received null value (Poison Pill) from Processor - will stop processing responses now.");
					break;
				}

				// Now check for any problems with response...
				if (foundProblemInResponse(processedResponse)) {
					break;
				}
				
				//////////////////////////////////////////////////////////////////
				// If got here, have valid response, with no errors reported from processor
				//////////////////////////////////////////////////////////////////
				String processorResponseMessage = processedResponse.getResponse();
				if (processorResponseMessage == null) { // No response expected, so consume input message now
					logger.info("Message with AuditKey Value " + processedResponse.getAuditKeyValues() + " succcessfully processed by processing class, and no response returned or expected.");
					try {
						messageListener.consumeMessage(processedResponse.getAuditKeyValues());
					} catch (MessagingException e) {
						logger.severe("Error: Received LwMessagingException trying to consume message with AuditKey Value " + processedResponse.getAuditKeyValues() + ": " + e);
						logger.severe("Going to tell main thread to stop processing.");
						mainProcessToCloseDown = true;
						break;
					}
				}
				else { // then response is ready after success, see if we should do something with it...
					logger.info("Response message with AuditKey Value " + processedResponse.getAuditKeyValues() + " returned from processing class. See next line for content (if logging @ level FINE)...");
					logger.fine(processorResponseMessage);

					//////////////////////////////////////////////////////////////////
					// Target message processed OK, build response...
					//////////////////////////////////////////////////////////////////
					String applicationResponseMessage = null;
					if (settings.getInputDataFormat() != null && settings.getConvertedInputDataFormat().equals("XML")) {
						try {
							applicationResponseMessage = buildResponseMessage(processorResponseMessage.toString(), processedResponse.getInputDoc(), settings);
						}
						catch (XMLException e) {
							logger.severe("Message with AuditKey Value " + processedResponse.getAuditKeyValues() + " caused an LwXMLException building Response message: " + e);
							logger.severe("Going to tell main thread to stop processing.");
							mainProcessToCloseDown = true;
							break;
						}
					}
					else { // don't make any changes, just return response
						applicationResponseMessage = processorResponseMessage;
					}

					if (applicationResponseMessage == null) {
						logger.severe("Error: No Response message was built. Message with AuditKey Value " + processedResponse.getAuditKeyValues());
						logger.severe("Going to tell main thread to stop processing.");
						mainProcessToCloseDown = true;
						break;
					}
					else {
						//////////////////////////////////////////////////////////////////
						// Response message built OK, send it to MQ or File...
						//////////////////////////////////////////////////////////////////
						forwardApplicationResponse(processedResponse, applicationResponseMessage);
					}
				} // end if (response == null)
			
				// Check to see if an error occurred during processing of response.
				// If a problem, "stay" the input message and stop processing.
				// If no problem, "consume" the input message and continue processing.
				try {
					if (errorEncountered) { 
						messageListener.stayMessage(processedResponse.getAuditKeyValues());
					} else {
						messageListener.consumeMessage(processedResponse.getAuditKeyValues());
						logger.info("Message with AuditKey Value " + processedResponse.getAuditKeyValues() + " consumed after processing to output medium.");
					}
				} catch (MessagingException e) {
					logger.severe("ResponseProcessorTask: Caught LwMessagingException exception Staying or consuming message: " + e);
					logger.severe("Going to tell main thread to stop processing.");
					errorEncountered = true;
					break;
				}

			} while (inLoopMode && !errorEncountered);
			
			if (errorEncountered) {
				mainProcessToCloseDown = true;				
			}
			logger.exiting("ResponseProcessorTask", "run");
		}

		/**
		 * @param processedResponse the response from the processor
		 * @param applicationResponseMessage the response message to be forwarded (for interpretation by the originating app 
		 */
		private void forwardApplicationResponse(ProcessResponse processedResponse, String applicationResponseMessage) {
			if (messageResponder != null) {
				String messageResponderInstructions = null;		// Optional, implementation-specific instructions to be passed to the message responder
				if (messageListener instanceof AcceptMessagesFromQueue) { // might have ReplytoQ URI from accepted message
					String replytoQueueURI = ((AcceptMessagesFromQueue)messageListener).getReplytoQueueURI();
					if (replytoQueueURI != null) {
						messageResponderInstructions = replytoQueueURI;
					}
				}

				try {
					messageResponder.putMessage(applicationResponseMessage, processedResponse.getAuditKeyValues(), messageResponderInstructions);
				} catch (MessagingException e) {
					logger.severe("ResponseProcessorTask: Caught LwMessagingException exception from messageResponder.putMessage(): ");
					errorEncountered = true;
				}
			}

		}

		/**
		 * Get the response object from the messageProcessor (may block)
		 * 
		 * @return the processed response
		 */
		private ProcessResponse getProcessedResponse() {
			try {
				return messageProcessor.getResponse();
			} catch (MessagingException e) {
				logger.severe("ResponseProcessorTask: Caught LwMessagingException exception from messageProcessor.getResponse(): " + e);
				logger.severe("ResponseProcessorTask: Going to tell main thread to stop processing.");
				errorEncountered = true;
			} catch (InterruptedException e) {
				// Close down thread
				logger.info("ResponseProcessorTask interrupted while getting response from messageProcessor - shutting down");
				errorEncountered = true;
			}
			
			return null;
		}

		/**
		 * Now check for any problems with response.
		 * 
		 * @param processedResponse the response object to be checked/validated
		 * 
		 * @return true if we encountered a problem
		 */
		private boolean foundProblemInResponse(ProcessResponse processedResponse) {
			if (processedResponse == null) {
				logger.info("ResponseProcessorTask received null response - shutting down");
				logger.info("Received PoisonPill from Processor: Going to tell main thread to stop processing.");
				errorEncountered = true;
				return true;
			}
			
			if (processedResponse.getResponseCode() != ProcessResponse.ProcessResponseCode.SUCCESS) {
				logger.severe("Message with AuditKey Value " + processedResponse.getAuditKeyValues() + " was not processed properly by messageProcessor. Error returned was " + processedResponse.getResponse());
				logger.severe("ResponseProcessorTask: Going to tell main thread to stop processing.");
				errorEncountered = true;
				return true;
			}
			
			if (settings.getMinResponsesExpected() > 0 && processedResponse.getRowsProcessed() < settings.getMinResponsesExpected()) { // then serious problem
				logger.severe("Message with AuditKey Value " + processedResponse.getAuditKeyValues() + " was not processed properly by messageProcessor. Insufficient Number of responses returned. Expected at least " + settings.getMinResponsesExpected() + ", received " + processedResponse.getRowsProcessed());
				logger.severe("ResponseProcessorTask: Going to tell main thread to stop processing.");
				errorEncountered = true;
				return true;
			}
			
			return false;
		}

		/**
		  *
		  * Build the final response message, for returning to MQ
		  *
		  * @param processedResponse the response from the processing Class
		  * @param inputDoc the original XML message received from MQ
		  * @param settings the application settings
		  *
		  * @return a the XML string to be sent to the target for processing
		  */
		private String buildResponseMessage(String processedResponse, XMLDocument inputDoc, GenericMessageHandlerSettings settings)
																		throws XMLException {
			if (processedResponse == null || inputDoc == null || settings.getMindElementSet() == null) {
				return null;
			}

			//////////////////////////////////////////////////////////////////////////
			// Create a new doc from the processed response...
			//////////////////////////////////////////////////////////////////////////
			XMLDocument processedResponseDoc = null;
			try {
				processedResponseDoc = XMLDocument.createDoc(processedResponse, XMLDocument.SCHEMA_VALIDATION_OFF);
			}
			catch(XMLException e2) {
				throw new XMLException("LwGenericMessageHandler.buildMessageForTarget(): Fatal Exception creating a new XML doc from processed response: " + e2.getMessage());
			}

			String responseMainDocElementName = settings.getResponseMainDocElementName();

			//////////////////////////////////////////////////////////////////////////
			// Create a new response doc (or just use processedResponse)...
			//////////////////////////////////////////////////////////////////////////
			XMLDocument responseDoc = null;
			try {
				if (responseMainDocElementName == null) { // then don't want to wrap response in any tag
					responseMainDocElementName = processedResponseDoc.getCurrentNodeName();
					responseDoc = processedResponseDoc;
				}
				else {
					responseDoc = XMLDocument.createDoc("<" + responseMainDocElementName + "></" + responseMainDocElementName + ">", XMLDocument.SCHEMA_VALIDATION_OFF);
					responseDoc.importNode(processedResponseDoc.getCurrentNode(), true);
				}
			}
			catch(XMLException e2) {
				throw new XMLException("LwGenericMessageHandler.buildMessageForTarget(): Fatal Exception creating a new doc: " + e2.getMessage());
			}

			addMindedElementsToMessage(inputDoc, settings,	responseMainDocElementName, responseDoc);

			setValuesForResponseLiterals(settings, responseDoc);

			return responseDoc.toString();
		}

		/**
		 * Set the values for any response literals...
		 *
		 * Notes:
		 * If the TAG does not exist, it will be created (along with any missing aggregates along it's path)
		 *
		 * Here's the sequence...
		 * Loop through the "literal" list
		 *		Find the element in responseDoc (as specified in the Location attribute)
		 *		If found, set the text value to that from the settings file
		 *		If NOT found
		 *			Add the element (creating parents if necessary along the way)
		 *			Find the newly created element in responseDoc
		 *			Set the text value to that from the settings file
		 *
		 * 
		 * 
		 * @param settings application settings
		 * @param responseDoc the XML doc to be amended
		 */
		private void setValuesForResponseLiterals( GenericMessageHandlerSettings settings, XMLDocument responseDoc) {
			Enumeration<XMLTagValue> enumLiteralNames = settings.getResponseLiteralsSet();
			while (enumLiteralNames.hasMoreElements()) {
				XMLTagValue tv = enumLiteralNames.nextElement();

				responseDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
				if ( responseDoc.setCurrentNodeByPath(tv.getAttributeValue("Location"), 1)) {
					responseDoc.setTextContentForCurrentNode(tv.getTagValue());
				}
				else { // no prob if we don't find the aggregate, we'll add it
					XMLTagValue tempTagNoValue = new XMLTagValue(tv.getAttributeValue("Location"), null); // just to help split out path e.g. MESSAGE.DELV.ORD or MESSAGE.DELV.ORD.ORDER_NUMBER

					if (tempTagNoValue.getPathToNameLessFirstElement() != null) {
						if (responseDoc.addElement(null, tempTagNoValue.getPathToNameLessFirstElement(), null) != null) {

							responseDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
							if (responseDoc.setCurrentNodeByPath(tempTagNoValue.getPathToName(), 1)) {
								responseDoc.setTextContentForCurrentNode(tv.getTagValue());
							}
						}
					}
				}
			}
		}

		/**
		 * Add the "mind"ed aggregates or tags...
		 *
		 * Notes:
		 * We must allow for the fact that the top-level element of the response
		 * might not have the same Name as that of the input message.
		 *
		 * Both aggregates and TAGs may be minded.
		 * The path to the aggregate or tag will be created in the response, if it does not already exist.
		 * Individual TAGs specified with the same path will appear in the same aggregate in the response message (i.e the parent aggregate will not be duplicated).
		 * If a TAG appears in the list after an aggregate existing along the same path, the TAG will be duplicated in the response message.
		 * If a TAG appears in the list before an aggregate existing along the same path, the aggregate will be duplicated in the response message.
		 *
		 * Here's the sequence...
		 * Loop through the "minded" list
		 *		If special name "*" encountered, import all from inputDoc
		 *		Otherwise...
		 *			Find the "minded" element in inputDoc
		 *			Find the parent of "minded" element in responseDoc (will fail if names of top-level elements differ in docs, or if sub-aggregate not yet there)
		 *			If didn't find parent, create it in responseDoc (ignoring top-level element of Location attribute), and then find it
		 *			Import the node into responseDoc at the found parent
		 * 
		 * 
		 * @param inputDoc the original input doc (source of the whole transaction)
		 * @param settings application settings
		 * @param responseMainDocElementName the name of the root element for the responseDoc
		 * @param responseDoc the XML doc to be amended
		 */
		private void addMindedElementsToMessage(XMLDocument inputDoc, GenericMessageHandlerSettings settings,
												String responseMainDocElementName, XMLDocument responseDoc) {
			Enumeration<XMLTagValue> mindElements = settings.getMindElementSet();
			while (mindElements.hasMoreElements()) {
				XMLTagValue tv = mindElements.nextElement();

				inputDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
				responseDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc

				if (tv.getTagValue().equals("*")) { // then import ALL from the inputDoc, but excluding the "wrapper" TAG (e.g. "MESSAGE")
					responseDoc.importNodesChildren(inputDoc.getCurrentNode(), true);
				}
				else { // find the node and import it
					if (inputDoc.setCurrentNodeByPath(tv.getTagValue(), 1)) { // no prob if we don't find the aggregate in input, we'll ignore then
						responseDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc

						// Position "pointer" in responseDoc to correct parent aggregate, creating it if we have to...
						// (Note that the first ELEMENT in the path for the input may have a different name than the response, so we may ignore it)
						XMLTagValue tempTagNoValue = new XMLTagValue(tv.getTagValue(), null); // just to help split out path e.g. MESSAGE.DELV.ORD or MESSAGE.DELV.ORD.ORDER_NUMBER

						//search under both inputDoc top-level Name and that of Target top-level Name
						if ( ! (responseDoc.setCurrentNodeByPath(tempTagNoValue.getPathToParent(), 1) || responseDoc.setCurrentNodeByPath(("/" + responseMainDocElementName + "/" + tempTagNoValue.getPathToParentLessFirstElement()), 1))
						   ) { // try to go to parent recipient (if specified), create path if can't
							String pathToParentLessFirstElement = tempTagNoValue.getPathToParentLessFirstElement();

							if (pathToParentLessFirstElement != null) { // would be null if TAG is child of first ELEMENT in doc
								responseDoc.addElement(null, pathToParentLessFirstElement, null);
								responseDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc
								responseDoc.setCurrentNodeByPath(tempTagNoValue.getPathToParent(), 1);
							}
						}

						// Now can import the minded node
						responseDoc.importNode(inputDoc.getCurrentNode(), true);
					}
				}
			}
		}
	} // end class ResponseProcessorTask
}