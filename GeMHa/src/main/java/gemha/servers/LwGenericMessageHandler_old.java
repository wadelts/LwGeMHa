package gemha.servers;

import java.io.*;
import java.util.logging.*;
import java.util.*;

import lw.XML.*;
import lw.utils.*;
import gemha.interfaces.*;
import gemha.support.*;

/**
  * This server class processes a message from MQ or File(s) and places the response(s) on an MQ queue or in file(s).
  * This class employs the services of IApp interface to allow graceful shutdown.
  * @author Liam Wade
  * @version 1.0 15/10/2008
  */
public class LwGenericMessageHandler_old implements IApp
{
    private static final Logger logger = Logger.getLogger("gemha");

	/**
	  * Constructor used when LwGenericMessageHandler itself will decide from which
	  * object it is to receive messages. 
	  *
	  * @param settingsFileName (and optionally path) of file from which settings are to be read
	  */
	public LwGenericMessageHandler_old(String settingsFileName) {
		this.settingsFileName = settingsFileName;
	}

	/**
	  * Constructor used when LwGenericMessageHandler is given the object
	  * from which it is to receive messages. 
	  *
	  * @param settingsFileName Name (and optionally path) of file from which settings are to be read
	  * @param messageListener object that will supply messages.
	  */
	public LwGenericMessageHandler_old(String settingsFileName, IAcceptMesssages messageListener) {
		this.settingsFileName = settingsFileName;
		this.messageListener = messageListener;
	}


	/**
	  * Constructor used when LwGenericMessageHandler is given the object
	  * from which it is to receive messages and to which it should send any Responses. 
	  *
	  * @param settingsFileName Name (and optionally path) of file from which settings are to be read
	  * @param messageListener object that will supply messages - implements interface LwIAcceptMesssages.
	  * @param messageResponder object that will supply messages - implements interface LwIStoreMesssage.
	  */
	public LwGenericMessageHandler_old(String settingsFileName, IAcceptMesssages messageListener, IStoreMesssage messageResponder) {
		this.settingsFileName = settingsFileName;
		this.messageListener = messageListener;
		this.messageResponder = messageResponder;
	}

	/**
	  *
	  * Start the application
	  */
	public void start() {

		logger.info("<*<*<*<*< Starting Up >*>*>*>*>");
		final long start;
		final long end;

		start = System.nanoTime();
		try {
			settings = new LwGenericMessageHandlerSettings_old(settingsFileName, XMLDocument.SCHEMA_VALIDATION_ON);
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
		try {
			messageProcessor = (IProcessMesssage_old)(Class.forName(settings.getMessageProcessingClassName()).newInstance());
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

		logger.info("<*<*<*<*< Startup completed successfully >*>*>*>*>");

		//////////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////
		//                M a i n  P r o c e s s i n g...
		//////////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////
		try {

			//////////////////////////////////////////////////////////////////
			// Determine and prepare input medium, if not already supplied
			// in a constructor...
			//////////////////////////////////////////////////////////////////
			if (messageListener == null) {
				if (settings.getInputQueueName() != null) { // then am to read messages from MQ
					messageListener = new AcceptMessagesFromQueue(settings.getInputQueueName(), settings.getInputUrlJMSserver());
				}
				else if (settings.getPortNumber() > 0) {
					messageListener = new AcceptMessagesFromSocket(settings.getPortNumber());
				}
				else if (settings.getInputFileNameFilter() != null) {
					messageListener = new AcceptMessagesFromFiles(settings.getInputFileDir(), settings.getInputFileNameFilter(), true, settings.getColNameList(), settings.getInputDataFormat(), settings.getFieldSeparator(), settings.getMaxRecsPerMessage(), settings.getXMLFormat(), settings.getActionOnError(), settings.getPreparedStatementName(), settings.getImmediateCommit(), settings.getNumRecordsToSkip());
				}
				else { // something very wrong indeed!
					throw new MessagingException("Missing essential information for input source - for example inputQueueName, input portNumber or inputFileNameFilter (or was LwGenericMessageHandler called with incorrect Constructor?)");
				}
			}

			messageListener.performSetup();


			//////////////////////////////////////////////////////////////////
			// Determine and prepare output medium...
			// No problem if no output medium, if don't want to output anything.
			//////////////////////////////////////////////////////////////////
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
			}

			if (messageResponder != null) {
				messageResponder.openStorage();
			}


			//////////////////////////////////////////////////////////////////
			// Start handling messages...
			//////////////////////////////////////////////////////////////////
			handleMessages(settings);

		}
		catch(SettingsException e) {
			logger.severe("Stopped processing: Caught LwSettingsException exception: " + e);
			System.exit(-12);
		}
		catch (MessagingException e) {
			logger.severe("Stopped processing: Caught LwMessagingException exception: " + e);
			System.exit(-13);
		}
//		catch (Exception e) {
//			logger.severe("Stopped processing: Caught unknown exception: " + e);
//		}
		finally { // but probably won't even get here, as normally terminated by interrupt
			logger.info("Shutting down (further messages may only be sent to file " + settings.getShutDownLogFileName() + ")");
			end = System.nanoTime();
			logger.info("Finished processing: LwGenericMessageHandler ran for " + (end - start)/1.0e9 + " seconds");
			
			// Close log file now. Is no use during shutdown anyway, as the Logger has its own shutdown hook
			// and can therefore be shut down by the virtual machine before our shutdown hook.
			settings.releaseResources();
		}

		logger.exiting("LwGenericMessageHandler", "start");
	}

	/**
	  *
	  * This is the main control loop
	  *
	  * @param settings the loaded application config settings
	  *
	  */
	private void handleMessages(LwGenericMessageHandlerSettings_old settings)
																throws MessagingException {

		boolean wantToCloseDown = false;	// controls main while-loop
		int numMessagesProcessed = 0;

		while ( ! wantToCloseDown) {
			String receivedMessage = null;
			boolean skipMessage = false;	// set to true if there is a problem with a message and it is to be skipped, that is removed from the input queue


			//////////////////////////////////////////////////////////////////
			// Get a message, waiting if necessary
			//////////////////////////////////////////////////////////////////

			messageListener.setWaitInterval(settings.getMilliSecondsBeforeQuiet()); // block for n seconds (if blocking relevant)

			receivedMessage = messageListener.acceptNextMessage();

			if (receivedMessage == null) { // then no message available, might want to try again, depending on input medium
				messageProcessor.goQuiet(); // tell processor we're not busy now.

				if (messageListener instanceof AcceptMessagesFromQueue) { // then want to block now, await next message and, possibly, close output queue
					// Close targetConnection here, if is a Queue, because not busy
					if (settings.getOutputQueueName() != null) {
						if (messageResponder != null) {
							messageResponder.closeStorage(); // will be opened automatically by messageResponder, if needed (and trying to close when open causes no problem
						}
					}

					messageListener.setWaitIntervalBlockIndefinitely();
					receivedMessage = messageListener.acceptNextMessage();
				}
				else if (messageListener instanceof AcceptMessagesFromSocket) { // then am finished, so close down
					wantToCloseDown = true;
					logger.info("Socket Server returned null, so closing down.");
				}
				else if (messageListener instanceof AcceptMessagesFromFiles) { // then am finished, so close down
					wantToCloseDown = true;
					logger.info("No more files to process, so closing down.");
				}
				else { // then message listener is java object supplied in constructor, am finished, so close down
					wantToCloseDown = true;
					logger.info("No more messages to process from object " + messageListener.getClass().getName() + ", so closing down.");
				}
			}


			if (receivedMessage != null) { // then have something to work on

				numMessagesProcessed++;

				String auditKeyValues = "unknown";
				XMLDocument inputDoc = null;
				String messageForTarget = null;

				//////////////////////////////////////////////////////////////////
				// Perform XML-based checks and actions...
				//////////////////////////////////////////////////////////////////
				if (settings.getConvertedInputDataFormat() != null && settings.getConvertedInputDataFormat().equals("XML")) {
					inputDoc = createXMLDocFromInput(receivedMessage, settings);

					if (inputDoc != null) {

						auditKeyValues = getAuditKeyValuesForXMLMessage(inputDoc, settings);
						if ( ! auditKeysFound(inputDoc, settings)) { // will be valid only if ALL auditkeys found in message
							String actionOnError = settings.getAuditKeysAggregate().getAttributeValue("ActionOnError");
							if (actionOnError == null || actionOnError.equals("shutdown")) {
								logger.severe("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " Will shut down.");
								messageListener.stayMessage(auditKeyValues);
								skipMessage = true;
								wantToCloseDown = true;
							}
							else if (actionOnError.equals("discard")) {
								logger.warning("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " Will discard message.");
								String errorMessageFileName = LwLogger.createFileNameFromTemplate(settings.getErrorFileNameTemplate(), auditKeyValues);

								if (errorMessageFileName != null) {
									inputDoc.toFile(errorMessageFileName, false);
									logger.warning("Message with AuditKey Value " + auditKeyValues + " saved to file " + errorMessageFileName);
									logger.info("Processing of messages will continue.");
								}

								messageListener.consumeMessage(auditKeyValues);
								skipMessage = true;
							}
							else { // can assume actionOnError == "none", so just ignore problem
								logger.warning("Audit key (or keys) not found in Message with AuditKey Value " + auditKeyValues + " But will still process message.");
							}
						}

						if ( ! skipMessage && ! dataContractNameValid(inputDoc, settings)) { // will be valid if no name to check, or is matched
							XMLTagValue dataContractName = settings.getDataContractName();
							String actionOnError = null;
							if (dataContractName != null) { actionOnError = dataContractName.getAttributeValue("ActionOnError");}

							if (actionOnError == null || actionOnError.equals("shutdown")) {
								logger.severe("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. Will shut down.");
								messageListener.stayMessage(auditKeyValues);
								skipMessage = true;
								wantToCloseDown = true;
							}
							else if (actionOnError.equals("discard")) {
								logger.warning("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. Will discard message.");
								String errorMessageFileName = LwLogger.createFileNameFromTemplate(settings.getErrorFileNameTemplate(), auditKeyValues);

								if (errorMessageFileName != null) {
									inputDoc.toFile(errorMessageFileName, false);
									logger.warning("Message with AuditKey Value " + auditKeyValues + " saved to file " + errorMessageFileName);
									logger.info("Processing of messages will continue.");
								}

								messageListener.consumeMessage(auditKeyValues);
								skipMessage = true;
							}
							else { // can assume actionOnError == "none", so just ignore problem
								logger.warning("Data Contract Name did not match" + (settings.getDataContractName() == null ? "" : " " + settings.getDataContractName().getTagValue()) + ". Message with AuditKey Value " + auditKeyValues + " rejected. But will still process message.");
							}
						}

						if ( ! skipMessage) {
							try {
								messageForTarget = buildMessageForTarget(inputDoc, settings);
								logger.fine("Message for target with AuditKey Value " + auditKeyValues + " built. See next line for content...");
								logger.fine(messageForTarget);
							}
							catch (XMLException e) {
								logger.severe("Message with AuditKey Value " + auditKeyValues + " caused an LwXMLException: "  + e);
							}
						}
					}
				}
				else { // pass on whole message to processor
					messageForTarget = receivedMessage;
				}

				if ( ! skipMessage) {
					if (messageForTarget == null) { // then nothing to process - BIG PROBLEM!
						logger.severe("No Target message was built from Message with AuditKey Value " + auditKeyValues);
						messageListener.stayMessage(auditKeyValues);
						wantToCloseDown = true;
					}
					else {

						//////////////////////////////////////////////////////////////////
						// All OK, process the target message
						//////////////////////////////////////////////////////////////////
						int processingResult = messageProcessor.processMessage(messageForTarget);

						logger.finer("Returned " + processingResult + " from messageProcessor.processMessage()");

						if (processingResult >= 0 && processingResult >= settings.getMinResponsesExpected()) { // then success
							if (processingResult == 0) { // No response expected, so consume input message now
								logger.info("Message with AuditKey Value " + auditKeyValues + " succcessfully processed by processing class, and no response returned or expected.");
								messageListener.consumeMessage(auditKeyValues);
							}
							else { // then response is ready after success, see if we should do something with it...
								if (messageResponder != null) { // try to build and sent output
									String processedResponse = messageProcessor.getResponse();
									logger.fine("Response message with AuditKey Value " + auditKeyValues + " returned from processing class. See next line for content...");
									logger.fine(processedResponse);

									//////////////////////////////////////////////////////////////////
									// Target message processed OK, build response...
									//////////////////////////////////////////////////////////////////
									String responseMessage = null;
									if (settings.getInputDataFormat() != null && settings.getConvertedInputDataFormat().equals("XML")) {
										try {
											responseMessage = buildResponseMessage(processedResponse, inputDoc, settings);
										}
										catch (XMLException e) {
											logger.severe("Message with AuditKey Value " + auditKeyValues + " caused an LwXMLException building Response message: " + e);
										}
									}
									else { // don't make any changes, just return response
										responseMessage = processedResponse;
									}

									if (responseMessage == null) {
										logger.warning("Error: No Response message was built. Message with AuditKey Value " + auditKeyValues);
										messageListener.stayMessage(auditKeyValues);
										wantToCloseDown = true;
									}
									else {
										//////////////////////////////////////////////////////////////////
										// Response message built OK, send it to MQ or File...
										//////////////////////////////////////////////////////////////////
										if (messageResponder != null) {
											if (messageListener instanceof AcceptMessagesFromQueue) { // might have ReplytoQ URI from accepted message
												String replytoQueueURI = ((AcceptMessagesFromQueue)messageListener).getReplytoQueueURI();
												if (replytoQueueURI != null) {
													messageResponderInstructions = replytoQueueURI;
												}
											}

											messageResponder.putMessage(responseMessage, auditKeyValues, messageResponderInstructions);
										}

										messageListener.consumeMessage(auditKeyValues);
										logger.info("Message with AuditKey Value " + auditKeyValues + " consumed after processing to output medium.");
									}
								}
								else { // no output required, so just cunsume input message
									messageListener.consumeMessage(auditKeyValues);
									logger.info("Message with AuditKey Value " + auditKeyValues + " consumed. No output was required.");
								}
							} // end if (processingResult == 0)
						}
						else { // error processing message, leave on queue and close down
							if (processingResult < 0) { // then problem identified and negative error code returned
								logger.severe("Message with AuditKey Value " + auditKeyValues + " was not processed properly by messageProcessor. Error returned was " + processingResult);
							}
							else { // insufficient number of responses returned
								logger.severe("Message with AuditKey Value " + auditKeyValues + " was not processed properly by messageProcessor. Insufficient Number of responses returned. Expected at least " + settings.getMinResponsesExpected() + ", received " + processingResult);
							}
							messageListener.stayMessage(auditKeyValues);
							wantToCloseDown = true;
						}
					} // end if (messageForTarget == null)
				} // end if ( ! skipMessage)
			} // end if (receivedMessage != null)


			if (settings.getInputLimit() > 0) { // then a limit was set on the number of messages to process
				if (numMessagesProcessed >= settings.getInputLimit()) {
					wantToCloseDown = true;
					logger.info("Specfied limit of " + settings.getInputLimit() + " message(s) reached.");
				}
			}

			if ( wantToCloseDown) {
				logger.info("Stopping processing and going to close down. (wantToCloseDown is true)");
			}
		} // end while(wantToCloseDown)
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
	private XMLDocument createXMLDocFromInput(String message, LwGenericMessageHandlerSettings_old settings) {

		XMLDocument newDoc = new XMLDocument();

		try {
			if (settings.getInputValidationSettings() == null) { // then no validation requested
				newDoc.createDoc(message, XMLDocument.SCHEMA_VALIDATION_OFF);
			}
			else {
				boolean validateAgainstSchema = (settings.getInputValidationSettings().getAttributeValue("SchemaValidation") == null ? false : (settings.getInputValidationSettings().getAttributeValue("SchemaValidation").equals("on")));
				if (validateAgainstSchema) {
					// note that SchemaDefinitionFileName may be null, in which case the message should have the Schema definition
					// also that SchemaLanguage may be null, in which case the default Schema Language is assumed
					// neither depends on the other
					newDoc.createDoc(message, XMLDocument.SCHEMA_VALIDATION_ON, settings.getInputValidationSettings().getAttributeValue("SchemaDefinitionFileName"), settings.getInputValidationSettings().getAttributeValue("SchemaLanguage"));
					logger.info("Input message was validated successfully.");
				}
				else { // then no validation turned off
					newDoc.createDoc(message, XMLDocument.SCHEMA_VALIDATION_OFF);
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
	private String buildMessageForTarget(XMLDocument inputDoc, LwGenericMessageHandlerSettings_old settings)
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
					targetDoc = new XMLDocument();
					targetDoc.createDoc("<" + targetMainDocElementName + "></" + targetMainDocElementName + ">", XMLDocument.SCHEMA_VALIDATION_OFF);
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
				targetDoc = new XMLDocument();
				targetDoc.createDoc("<" + targetMainDocElementName + "></" + targetMainDocElementName + ">", XMLDocument.SCHEMA_VALIDATION_OFF);

				// Add the elements from inputDoc...
				Enumeration<XMLTagValue> enumAggNames = settings.getSendElementSet();
				while (enumAggNames.hasMoreElements()) {
					XMLTagValue tv = enumAggNames.nextElement();

					inputDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc

					if (inputDoc.setCurrentNodeByPath(tv.getTagValue(), 1)) { // no prob if we don't find the element

						targetDoc.setCurrentNodeToFirstElement(); // go back to beginning of doc

						// Position "pointer" in targetDoc to correct parent aggregate, creating it if we have to...
						// (Note that the first ELEMENT in the path for the input may have a different name than the new Target message, so we may ignore it)
						XMLTagValue tempTagNoValue = new XMLTagValue(tv.getTagValue(), null); // just to help split out path e.g. MESSAGE.DELV.ORD or MESSAGE.DELV.ORD.ORDER_NUMBER

						//search under both inputDoc top-level Name and that of Target top-level Name
						if ( ! (targetDoc.setCurrentNodeByPath(tempTagNoValue.getPathToParent(), 1) || targetDoc.setCurrentNodeByPath((targetMainDocElementName + "/" + tempTagNoValue.getPathToParentLessFirstElement()), 1))
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
	private boolean sendAllElementsToTarget(LwGenericMessageHandlerSettings_old settings) {
		Enumeration<XMLTagValue> enumAggNames = settings.getSendElementSet();
		while (enumAggNames.hasMoreElements()) {
			XMLTagValue tv = enumAggNames.nextElement();

			if (tv.getTagValue().equals("*")) { // then "import ALL from the inputDoc" is requested
				return true;
			}
		}

		return false;
	}

	/**
	  *
	  * Build the final response message, for returning to MQ
	  *
	  * @param processedResponse the response from the processing Class
	  * @param inputDoc the original XML message received from MQ
	  * @param settings
	  *
	  * @return a the XML string to be sent to the target for processing
	  */
	private String buildResponseMessage(String processedResponse, XMLDocument inputDoc, LwGenericMessageHandlerSettings_old settings)
																	throws XMLException {
		if (processedResponse == null || inputDoc == null || settings.getMindElementSet() == null) {
			return null;
		}

		//////////////////////////////////////////////////////////////////////////
		// Create a new doc from the processed response...
		//////////////////////////////////////////////////////////////////////////
		XMLDocument processedResponseDoc = new XMLDocument();
		try {
			processedResponseDoc.createDoc(processedResponse, XMLDocument.SCHEMA_VALIDATION_OFF);
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
				responseDoc = new XMLDocument();
				responseDoc.createDoc("<" + responseMainDocElementName + "></" + responseMainDocElementName + ">", XMLDocument.SCHEMA_VALIDATION_OFF);
				responseDoc.importNode(processedResponseDoc.getCurrentNode(), true);
			}
		}
		catch(XMLException e2) {
			throw new XMLException("LwGenericMessageHandler.buildMessageForTarget(): Fatal Exception creating a new doc: " + e2.getMessage());
		}

		//////////////////////////////////////////////////////////////////////////
		// Add the "mind"ed aggregates or tags...
		//
		// Notes:
		// We must allow for the fact that the top-level element of the response
		// might not have the same Name as that of the input message.
		//
		// Both aggregates and TAGs may be minded.
		// The path to the aggregate or tag will be created in the response, if it does not already exist.
		// Individual TAGs specified with the same path will appear in the same aggregate in the response message (i.e the parent aggregate will not be duplicated).
		// If a TAG appears in the list after an aggregate existing along the same path, the TAG will be duplicated in the response message.
		// If a TAG appears in the list before an aggregate existing along the same path, the aggregate will be duplicated in the response message.
		//
		// Here's the sequence...
		// Loop through the "minded" list
		//		If special name "*" encountered, import all from inputDoc
		//		Otherwise...
		//			Find the "minded" element in inputDoc
		//			Find the parent of "minded" element in responseDoc (will fail if names of top-level elements differ in docs, or if sub-aggregate not yet there)
		//			If didn't find parent, create it in responseDoc (ignoring top-level element of Location attribute), and then find it
		//			Import the node into responseDoc at the found parent
		//////////////////////////////////////////////////////////////////////////
		Enumeration<XMLTagValue> enumAggNames = settings.getMindElementSet();
		while (enumAggNames.hasMoreElements()) {
			XMLTagValue tv = enumAggNames.nextElement();

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
					if ( ! (responseDoc.setCurrentNodeByPath(tempTagNoValue.getPathToParent(), 1) || responseDoc.setCurrentNodeByPath((responseMainDocElementName + "/" + tempTagNoValue.getPathToParentLessFirstElement()), 1))
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

		//////////////////////////////////////////////////////////////////////////
		// Set the values for any response literals...
		//
		// Notes:
		// If the TAG does not exist, it will be created (along with any missing aggregates along it's path)
		//
		// Here's the sequence...
		// Loop through the "literal" list
		//		Find the element in responseDoc (as specified in the Location attribute)
		//		If found, set the text value to that from the settings file
		//		If NOT found
		//			Add the element (creating parents if necessary along the way)
		//			Find the newly created element in responseDoc
		//			Set the text value to that from the settings file
		//
		//////////////////////////////////////////////////////////////////////////
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

		return responseDoc.toString();
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
	private boolean auditKeysFound(XMLDocument doc, LwGenericMessageHandlerSettings_old settings) {

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
	private String getAuditKeyValuesForXMLMessage(XMLDocument doc, LwGenericMessageHandlerSettings_old settings) {

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
	private boolean dataContractNameValid(XMLDocument doc, LwGenericMessageHandlerSettings_old settings) {
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

		try {
			shutdownLogger.appendln("LwGenericMessageHandler.shutDown(): Shutdown completed successfully");
			shutdownLogger.close();
		}
		catch(IOException e) {
			System.out.println("LwGenericMessageHandler.shutDown(): could not write to file " + settings.getShutDownLogFileName() + " to record shutdown completed successfully.");
		}
	}

	private String settingsFileName = null;
	private LwGenericMessageHandlerSettings_old settings = null;
	private IAcceptMesssages messageListener = null;		// the interface for accepting messages
	private IProcessMesssage_old messageProcessor = null;	// the interface for processing messages
	private IStoreMesssage messageResponder = null;		// the interface for storing responses
	private String messageResponderInstructions = null;		// Optional, implementation-specific instructions to be passed to the message responder
}