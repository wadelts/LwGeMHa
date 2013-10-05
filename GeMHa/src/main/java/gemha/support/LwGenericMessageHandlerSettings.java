package gemha.support;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.logging.*;
import java.util.*;

import lw.utils.*;
import lw.XML.*;

/**
 * This class loads all the settings for the LwGenericMessageHandler
 * application.
 * 
 * @author Liam Wade
 * @version 1.0 07/11/2008
 * 
 * @ThreadSafe
 * 
 */
public class LwGenericMessageHandlerSettings {

	private static final Logger logger = Logger.getLogger("gemha");

	
	private final String externalShell;
	private final String pid;						// The Proces ID of the Java VM running this
													// instance
	private final String ppid;						// The Proces ID of the Parent process of the
													// Java VM running this instance

	private final FileHandler fh ;
	private final String shutDownLogFileName ;

	private final String logFileName ;
	private final String loggingLevel ;

	private final int inputLimit;

	// Queue names
	private final String inputUrlJMSserver ;
	private final String inputQueueName ;
	private final int milliSecondsBeforeQuiet; // number of milliseconds
													// we'll wait for a message
													// before going quiet
													// (closing down resources)
	private final String inputDataFormat ;
	private final LwXMLTagValue dataContractName ;
	private final int portNumber; 					// port on which a socket server would listen
	private final String outputUrlJMSserver ;
	private final String outputQueueName ;
	private final String replyToQueueName ;

	// Server info for when outputting to HTTP
	private final String HTTPServerUrl;
	private final String HTTPEndPointName;
	private final boolean HTTPWithBackoff;

	// File names, if queues not being used
	private final String inputFileNameFilter ;
	private final String inputFileDir ;
	private boolean sortFilteredFileNames = true;			// should input from a fileset be sorted on file name
	private final String fieldSeparator ;
	private final int maxRecsPerMessage; // number of records to add to a
										// particular message, before starting a
										// new message (when handling CSV
										// file(s))
	private final int numRecordsToSkip; // number of records to skip, before
										// adding records to responses (when
										// handling CSV file(s))
	private final ArrayList<String> colNameList ; // the list of column names
													// for naming XML tags (when
													// handling CSV file(s))
	private final String XMLFormat ; // signify the shape of XML to be created,
										// for example possibly matching a
										// database action (SELECT, INSERT)
	private final String actionOnError ; // the action to be taken when the
											// created INSERT fails - RESPOND or
											// EXCEPTION
	private final String preparedStatementName ; // the name of a Prepared
													// Statement to use with the
													// created INSERT
	private final String immediateCommit ; // the setting for whether the
											// created action should be
											// immediately committed
	private final String outputFileNameTemplate ; // an asterisk in the file
													// name will be replaced by
													// the audit key, a ? by
													// "current date & time & sequence number"

	// Input message Validation requirements
	private final LwXMLTagValue inputValidationSettings ; // optional

	// Message-Procesing class variables
	private final String messageProcessingClassName ;
	private final String messageProcessingSettingsFileName ;

	private final int minResponsesExpected; // Minimum number of responses
											// expected from the Processing
											// Class 0..n, default = 1

	// Names of TAGs in which audit keys will be found in messages
	private final LwXMLTagValue auditKeysAggregate ; // if exists, will have an
														// attribute to say what
														// happens if a value is
														// not found for an
														// Audit key
	private final Vector<LwXMLTagValue> auditKeyNamesSet ;
	private final String auditKeysSeparator ; // if exists, will be used to
												// separate values for
												// concatenated audit keys
	private final String errorFilesDir ; // if exists, will be the directory to
											// which errored messages will be
											// saved, when must be taken off
											// queue
	private final String errorFileNameTemplate ; // if exists, will be the
													// template for building the
													// filename for errored
													// messages, when they must
													// be taken off queue

	// Name(s) of element(s) to be 'minded' and returned in response
	private final Vector<LwXMLTagValue> mindElementSet ;
	// Name(s) of element(s) to be sent to the processor Class
	private final Vector<LwXMLTagValue> sendElementSet ;
	// Name(s) of response literals - tags for which values will be set in the
	// response
	private final Vector<LwXMLTagValue> responseLiteralsSet ;

	// wrapper aggregates, for target and response messages
	private final String targetMainDocElementName ;
	private final String responseMainDocElementName ;

	/**
	 * Will create a new exception with the given reason.
	 * 
	 * @param settingsFileName
	 *            the name of the settings file, path included
	 * @param settingsFileSchemaValidation
	 *            the set to true to have the settings file validate against its
	 *            Schema definition
	 */
	public LwGenericMessageHandlerSettings(String settingsFileName, boolean settingsFileSchemaValidation)
			throws LwSettingsException {

		
		if (settingsFileName == null) {
			throw new LwSettingsException(
					"LwGenericMessageHandlerSettings.constructor(): Required parameter is null.");
		}

		// Note: Have to assign values to all final fields within the constructor
		try {
			String fileSeparator = System.getProperty("file.separator");
			
			LwXMLDocument settingsDoc = null;

			settingsDoc = LwXMLDocument.createDocFromFile(settingsFileName,
										settingsFileSchemaValidation);

			// Get the name of the shell to call to execute external cammands
			String tempExternalShell = settingsDoc.getValueForTag("Applic/Params/ExternalShell");
			if (tempExternalShell == null) {
				tempExternalShell = "sh";
			}
			
			externalShell = tempExternalShell;

			// Get the name of the class which will process messages, and a settings
			// filename, if required...
			messageProcessingClassName = settingsDoc
					.getValueForTag("Applic/Processing/MessageProcessingClassName");
			messageProcessingSettingsFileName = settingsDoc
					.getValueForTag("Applic/Processing/MessageProcessingSettingsFileName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the Proces ID of the Parent process of the Java VM running this
			// instance.
			// For UNIX:
			// This will only be available if supplied as a parameter in the java
			// run command as "-Dppid=$$" and run from a shell
			// e.g. /opt/java1.5/bin/java -Dppid=$$ -classpath .:../
			// LwGenericMessageHandler
			// ////////////////////////////////////////////////////////////////////////
			ppid = System.getProperty("ppid"); // returns null if doesn't exist

			// ////////////////////////////////////////////////////////////////////////
			// Get the Proces ID of the Java VM running this instance
			// ////////////////////////////////////////////////////////////////////////
			String tempPid = null;
			try {
				tempPid = getProcessID(externalShell);
			} catch (IOException e) {
				System.out.println("Couldn't get Process ID (log file not yet opened): caught IOException: " + e);
			}
			pid = tempPid;

			// ////////////////////////////////////////////////////////////////////////
			// Get the number of messages to process before closing down...
			// ////////////////////////////////////////////////////////////////////////
			int tempInputLimit = 0;
			String strInputLimit = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputLimit");
			if (strInputLimit != null) {
				try {
					tempInputLimit = Integer.parseInt(strInputLimit);
				} catch (NumberFormatException e) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid InputLimit.");
				}

				if (tempInputLimit < 1) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid InputLimit. 0 not allowed");
				}
			}
			inputLimit = tempInputLimit;

			inputDataFormat = settingsDoc
					.getValueForTag("Applic/Input/InputSource/DataFormat");
			dataContractName = settingsDoc
					.getValueForTagPlusAttributes("Applic/Input/InputSource/DataContractName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the Queue names (these are mandatory in the XML schema, unless
			// filenames are supplied in the stead)...
			// ////////////////////////////////////////////////////////////////////////
			inputUrlJMSserver = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputQueue/UrlJMSserver");
			inputQueueName = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputQueue/QueueName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the number of milliseconds we'll wait for a message before going
			// quiet (closing down resources)...
			// ////////////////////////////////////////////////////////////////////////
			int tempMilliSecondsBeforeQuiet = 3;
			String strMilliSecondsBeforeQuiet = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputQueue/MilliSecondsBeforeQuiet");
			if (strMilliSecondsBeforeQuiet != null) {
				try {
					tempMilliSecondsBeforeQuiet = Integer
							.parseInt(strMilliSecondsBeforeQuiet);
				} catch (NumberFormatException e) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid MilliSecondsBeforeQuiet.");
				}

				if (tempMilliSecondsBeforeQuiet < 1) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid MilliSecondsBeforeQuiet. 0 not allowed");
				}
			}
			milliSecondsBeforeQuiet = tempMilliSecondsBeforeQuiet;

			// ////////////////////////////////////////////////////////////////////////
			// Get the port number for a socket server input scenario...
			// ////////////////////////////////////////////////////////////////////////
			int tempPortNumber = 0;
			String strPortNumber = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputSocket/PortNumber");
			if (strPortNumber != null) {
				try {
					tempPortNumber = Integer.parseInt(strPortNumber);
				} catch (NumberFormatException e) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid PortNumber.");
				}

				if (tempPortNumber < 1) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid PortNumber. 0 not allowed");
				}
			}
			portNumber = tempPortNumber;


			// Now get the output medium - file or queue
			outputUrlJMSserver = settingsDoc
					.getValueForTag("Applic/Output/OutputQueue/UrlJMSserver");
			outputQueueName = settingsDoc
					.getValueForTag("Applic/Output/OutputQueue/QueueName");
			replyToQueueName = settingsDoc
					.getValueForTag("Applic/Output/OutputQueue/ReplyToQueueName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the Input File info, if not inputting from MQ or Server Socket
			// ////////////////////////////////////////////////////////////////////////
			String tempInputFileNameFilter = null;
			String tempInputFileDir = null;
			if (inputQueueName == null && portNumber <= 0) {
				tempInputFileNameFilter = settingsDoc
						.getValueForTag("Applic/Input/InputSource/InputFile/FileNameFilter");
				
				tempInputFileDir = settingsDoc
						.getValueForTag("Applic/Input/InputSource/InputFile/FileDir");

				// Standardise format of inputFileDir
				if (tempInputFileNameFilter != null) {
					if (tempInputFileDir == null) { // then just set to current
						tempInputFileDir = ".";
					}
		
					if (tempInputFileDir.length() > 0 && !tempInputFileDir.endsWith(fileSeparator)) {
						tempInputFileDir = tempInputFileDir + fileSeparator;
					}

					// Find out if we should include column names in the output file
					String strSortFilteredFileNames = settingsDoc.getValueForTag("Applic/Input/InputSource/InputFile/SortFilteredFileNames");
					if (strSortFilteredFileNames != null) {
						sortFilteredFileNames = strSortFilteredFileNames.equals("true");
					}

				}
				
			}
			inputFileNameFilter = tempInputFileNameFilter;
			inputFileDir = tempInputFileDir;

			// ////////////////////////////////////////////////////////////////////////
			// Get the Separator to be used between fields in the input record -
			// default to tab char
			// ////////////////////////////////////////////////////////////////////////
			String tempFieldSeparator = null;
			tempFieldSeparator = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputFile/CSVParams/FieldSeparator");
			if (tempFieldSeparator == null) {
				tempFieldSeparator = "	"; // tab (\t doesn't work here!!!)
			}
			// replace escaped chars with literals, which will have been processed
			// for escaping - variables are not so processed
			if (tempFieldSeparator.length() > 1 && tempFieldSeparator.equals("\\t")) { // then
																				// is
																				// escaped
																				// tab,
																				// replace
																				// with
																				// actual
																				// (had
																				// to
																				// escape
																				// the
																				// escape)...
				tempFieldSeparator = "	"; // real tab (\t doesn't work here!!!
										// Stringtokenizer (or my nextWord) don't
										// parse "\t" as tab, though outputting to a
										// file does!?!)
			}
			fieldSeparator = tempFieldSeparator;

			// ////////////////////////////////////////////////////////////////////////
			// Get the maximum number of records to be included in one message when
			// dealing with a CSV input file...
			// ////////////////////////////////////////////////////////////////////////
			int tempMaxRecsPerMessage = 1;
			String strMaxRecsPerMessage = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputFile/CSVParams/MaxRecsPerMessage");
			if (strMaxRecsPerMessage != null) {
				try {
					tempMaxRecsPerMessage = Integer.parseInt(strMaxRecsPerMessage);
				} catch (NumberFormatException e) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid MaxRecsPerMessage.");
				}

				if (tempMaxRecsPerMessage < 1) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid MaxRecsPerMessage. 0 not allowed");
				}
			}
			maxRecsPerMessage = tempMaxRecsPerMessage;

			// ////////////////////////////////////////////////////////////////////////
			// Get the number of records to skip (i.e. not process) when dealing
			// with a CSV input file...
			// ////////////////////////////////////////////////////////////////////////
			int tempNumRecordsToSkip = 0;
			String strNumRecordsToSkip = settingsDoc
					.getValueForTag("Applic/Input/InputSource/InputFile/CSVParams/NumRecordsToSkip");
			if (strNumRecordsToSkip != null) {
				try {
					tempNumRecordsToSkip = Integer.parseInt(strNumRecordsToSkip);
				} catch (NumberFormatException e) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid NumRecordsToSkip.");
				}
			}
			numRecordsToSkip = tempNumRecordsToSkip;

			// ////////////////////////////////////////////////////////////////////////
			// Get the input file column names (will actually be stored in the value
			// part of the LwXMLTagValue - all Names will be "Column")
			// This encapsulates the order in which the fields should appear in each
			// record of the input file
			// ////////////////////////////////////////////////////////////////////////
			ArrayList<String> tempColNameList = null;
			if (settingsDoc.setCurrentNodeByPath(
					"/Applic/Input/InputSource/InputFile/CSVParams/ColumnOrder", 1)) {
				Vector<LwXMLTagValue> paramColumns = settingsDoc
						.getValuesForTagsChildren();

				// Transfer column names to simple list
				tempColNameList = new ArrayList<String>(paramColumns.size());
				for (LwXMLTagValue p : paramColumns) {
					tempColNameList.add(p.getTagValue());
				}

				settingsDoc.restoreCurrentNode();
			}
			colNameList = tempColNameList;

			// ////////////////////////////////////////////////////////////////////////
			// Get any INSERT settings, if we want to produce input conforming to an
			// INSERT statement
			// ////////////////////////////////////////////////////////////////////////
			XMLFormat = settingsDoc
					.getValueForTag("/Applic/Input/InputSource/InputFile/CSVParams/XMLFormat");
			actionOnError = settingsDoc
					.getValueForTag("/Applic/Input/InputSource/InputFile/CSVParams/InsertParams/Action_On_Error");
			preparedStatementName = settingsDoc
					.getValueForTag("/Applic/Input/InputSource/InputFile/CSVParams/InsertParams/Prepared_Statement_Name");
			immediateCommit = settingsDoc
					.getValueForTag("/Applic/Input/InputSource/InputFile/CSVParams/InsertParams/Immediate_Commit");

			// ////////////////////////////////////////////////////////////////////////
			// Get the Input message Validation settings
			//
			// If SchemaDefinitionFileName doesn't exist, and SchemaValidation is
			// "on", it is assumed the input message has the Schema details
			// If SchemaDefinitionFileName does exist, it expected that the input
			// message does NOT have the Schema details - an error will occur
			// otherwise
			// ////////////////////////////////////////////////////////////////////////
			inputValidationSettings = settingsDoc
					.getValueForTagPlusAttributes("Applic/Input/InputValidation");

			// ////////////////////////////////////////////////////////////////////////
			// Get the Output File info, if not outputting to MQ
			// ////////////////////////////////////////////////////////////////////////
			String tempOutputFileNameTemplate = null;
			if (outputQueueName == null) {
				tempOutputFileNameTemplate = settingsDoc
						.getValueForTag("Applic/Output/OutputFile/FileNameTemplate");
			}
			outputFileNameTemplate = tempOutputFileNameTemplate;

			// ////////////////////////////////////////////////////////////////////////
			// Get the Output HTTP target info, if not outputting to File or MQ
			// ////////////////////////////////////////////////////////////////////////
			String tempHTTPServerUrl = null;
			String tempHTTPEndPointName = null;
			String tempHTTPWithBackoff = null;
			if (outputQueueName == null || outputFileNameTemplate == null) {
				tempHTTPServerUrl = settingsDoc
						.getValueForTag("Applic/Output/OutputHTTP/ServerUrl");
				tempHTTPEndPointName = settingsDoc
						.getValueForTag("Applic/Output/OutputHTTP/EndPointName");
				tempHTTPWithBackoff = settingsDoc
						.getValueForTag("Applic/Output/OutputHTTP/HTTPWithBackoff");
				
			}
			HTTPServerUrl = tempHTTPServerUrl;
			HTTPEndPointName = tempHTTPEndPointName;
			HTTPWithBackoff = (tempHTTPWithBackoff == null ? true : tempHTTPWithBackoff.toLowerCase().equals("true"));	// set default to true

			// ////////////////////////////////////////////////////////////////////////
			// Get the Log files settings...
			// ////////////////////////////////////////////////////////////////////////
			String tempLogFileName = null;
			String tempShutDownLogFileName = null;
			String logFileDir = settingsDoc
					.getValueForTag("Applic/Logging/LogFileDir");
			tempLogFileName = settingsDoc
					.getValueForTag("Applic/Logging/LogFileNameTemplate");
			tempShutDownLogFileName = settingsDoc
					.getValueForTag("Applic/Logging/ShutDownLogFileNameTemplate");

			if (logFileDir == null) {
				logFileDir = ".";
			}

			if (tempLogFileName == null) {
				tempLogFileName = "LwGenericMessageHandler.log";
			}

			if (tempShutDownLogFileName == null) {
				tempShutDownLogFileName = "LwGenericMessageHandler_shutdown.log";
			}

			if (logFileDir.length() > 0 && !logFileDir.endsWith(fileSeparator)) {
				logFileDir = logFileDir + fileSeparator;
			}

			// Create the actual log filename, replacing placeholders from the
			// template
			Properties replacementSet = new Properties();
			if (messageProcessingClassName != null) {
				replacementSet.put("*", messageProcessingClassName);
			}
			if (pid != null) {
				replacementSet.put("$", pid);
			}
			replacementSet.put("?", "[%datetime%]");
			replacementSet.put("#", "[%seqno%]");
			logFileName = LwLogger.createStringFromTemplate(logFileDir
					+ tempLogFileName, replacementSet);


			shutDownLogFileName = LwLogger.createStringFromTemplate(logFileDir
					+ tempShutDownLogFileName, replacementSet);

			
			String tempLoggingLevel = settingsDoc
					.getValueForTag("Applic/Logging/Level/GeneralLoggingLevel");
			// GeneralLoggingLevel may not exist, and EnvironmentLoggingLevel can be
			// used instead
			if (tempLoggingLevel == null) {
				String environmentLoggingLevel = settingsDoc
						.getValueForTag("Applic/Logging/Level/EnvironmentLoggingLevel");
				if (environmentLoggingLevel != null) {
					if (environmentLoggingLevel.equals("Development")) {
						tempLoggingLevel = "FINER";
					} else if (environmentLoggingLevel.equals("Test")) {
						tempLoggingLevel = "FINE";
					} else if (environmentLoggingLevel.equals("Production")) {
						tempLoggingLevel = "CONFIG";
					}
				}
			}
			loggingLevel = tempLoggingLevel;
			
			logger.config("Log file is " + logFileName);

			// ////////////////////////////////////////////////////////////////////////
			// Get Processing parameters...
			// ////////////////////////////////////////////////////////////////////////
			// Got the name of the class which will process messages earlier,
			// because
			// wanted to use it in log-file name.
			int tempMinResponsesExpected = 1;
			String strMinResponsesExpected = settingsDoc
					.getValueForTag("Applic/Processing/MinResponsesExpected");
			if (strMinResponsesExpected != null) {
				try {
					tempMinResponsesExpected = Integer
							.parseInt(strMinResponsesExpected);
				} catch (NumberFormatException e) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid MinResponsesExpected.");
				}

				if (tempMinResponsesExpected < 0) {
					throw new LwSettingsException(
							"LwGenericMessageHandlerSettings.getSettings(): Invalid MinResponsesExpected. Less than 0 not allowed");
				}
			}
			minResponsesExpected = tempMinResponsesExpected;

			// ////////////////////////////////////////////////////////////////////////
			// Get the values for ALL Audit KeyName TAGs, if exist...
			// ////////////////////////////////////////////////////////////////////////
			auditKeysAggregate = settingsDoc
					.getValueForTagPlusAttributes("Applic/Auditing/AuditKeys"); // just
																				// getting
																				// attribute(s)
																				// for
																				// this
																				// aggregate
			auditKeyNamesSet = settingsDoc
					.getValuesForTag("Applic/Auditing/AuditKeys/KeyName");
			auditKeysSeparator = settingsDoc
					.getValueForTag("Applic/Auditing/AuditKeysSeparator");

			// ////////////////////////////////////////////////////////////////////////
			// Get the settings for error files, if exist, setting defaults, if
			// necessary...
			// ////////////////////////////////////////////////////////////////////////
			String tempErrorFilesDir = settingsDoc
					.getValueForTag("Applic/Auditing/ErrorFiles/ErrorFilesDir");
			String tempErrorFileNameTemplate = settingsDoc
					.getValueForTag("Applic/Auditing/ErrorFiles/ErrorFileNameTemplate");

			if (tempErrorFileNameTemplate == null) {
				tempErrorFileNameTemplate = "ErrorMessage_*_?.txt";
			}
			// make sure errorFilesDir ends with slash...
			if (tempErrorFilesDir != null && tempErrorFilesDir.length() > 0
					&& !tempErrorFilesDir.endsWith(fileSeparator)) {
				tempErrorFilesDir = tempErrorFilesDir + fileSeparator;
			}
			
			if (tempErrorFilesDir != null) {
				tempErrorFileNameTemplate = tempErrorFilesDir + tempErrorFileNameTemplate;
			}
			errorFilesDir = tempErrorFilesDir;
			errorFileNameTemplate = tempErrorFileNameTemplate;

			// ////////////////////////////////////////////////////////////////////////
			// Get the values for ALL MindElements TAGs, if they exist...
			// (These are to be returned as part of the response.)
			// ////////////////////////////////////////////////////////////////////////
			mindElementSet = settingsDoc
					.getValuesForTag("Applic/Processing/MindElements/ElementName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the values for ALL SendElements TAGs, if they exist...
			// ////////////////////////////////////////////////////////////////////////
			sendElementSet = settingsDoc
					.getValuesForTag("Applic/Processing/SendElements/ElementName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the value for wrapper aggregates, for target and response
			// messages.
			// Schema ensures defaults supplied, so will never be null...
			// ////////////////////////////////////////////////////////////////////////
			targetMainDocElementName = settingsDoc
					.getValueForTag("Applic/Processing/TargetMainDocElementName");

			responseMainDocElementName = settingsDoc
					.getValueForTag("Applic/Processing/ResponseMainDocElementName");

			// ////////////////////////////////////////////////////////////////////////
			// Get the values for ALL ResponseLiterals TAGs, if they exist...
			// ////////////////////////////////////////////////////////////////////////
			responseLiteralsSet = settingsDoc
					.getValuesForTag("Applic/Processing/ResponseLiterals/ResponseLiteral");

		} catch (LwXMLException e) {
			throw new LwSettingsException(
					"LwGenericMessageHandlerSettings.constructor(): caught LwXMLException: "
							+ e);
		}

		fh = setUpLoggingToFile();
		recordSettings();
	}

	/**
	 * Get helper method for pid
	 * 
	 * @return the pid for this instance
	 */
	public String getPid() {
		return pid;
	}

	/**
	 * Get helper method for ppid
	 * 
	 * @return the parent pid for this instance
	 */
	public String getPpid() {
		return ppid;
	}

	/**
	 * Get helper method for shutDownLogFileName
	 * 
	 * @return the shutDownLogFileName
	 */
	public String getShutDownLogFileName() {
		return shutDownLogFileName;
	}

	/**
	 * Get helper method for inputLimit
	 * 
	 * @return the inputLimit
	 */
	public int getInputLimit() {
		return inputLimit;
	}

	/**
	 * Get helper method for inputUrlJMSserver
	 * 
	 * @return the inputUrlJMSserver
	 */
	public String getInputUrlJMSserver() {
		return inputUrlJMSserver;
	}

	/**
	 * Get helper method for inputQueueName
	 * 
	 * @return the inputQueueName
	 */
	public String getInputQueueName() {
		return inputQueueName;
	}

	/**
	 * Get helper method for milliSecondsBeforeQuiet
	 * 
	 * @return the number of milliseconds we'll wait for a message before going
	 *         quiet (closing down resources)
	 */
	public int getMilliSecondsBeforeQuiet() {
		return milliSecondsBeforeQuiet;
	}

	/**
	 * Get helper method for socket server portNumber
	 * 
	 * @return the socket server portNumber, if we're inputting over a socket
	 */
	public int getPortNumber() {
		return portNumber;
	}

	/**
	 * Get helper method for inputDataFormat
	 * 
	 * @return the inputDataFormat
	 */
	public String getInputDataFormat() {
		return inputDataFormat;
	}

	/**
	 * This returns the format in which the data will be presented to the
	 * Message Handler (not necessarily the same as the inputDataFormat)
	 * 
	 * @return the format in which the data will be presented to the Message
	 *         Handler (not necessarily the same as the inputDataFormat)
	 */
	public String getConvertedInputDataFormat() {
		if (inputDataFormat != null && inputDataFormat.equals("CSV")) {
			return "XML";
		} else {
			return inputDataFormat;
		}
	}

	/**
	 * Get helper method for dataContractName
	 * 
	 * @return the dataContractName
	 */
	public LwXMLTagValue getDataContractName() {
		return dataContractName;
	}

	/**
	 * Get helper method for outputUrlJMSserver
	 * 
	 * @return the outputUrlJMSserver
	 */
	public String getOutputUrlJMSserver() {
		return outputUrlJMSserver;
	}

	/**
	 * Get helper method for outputQueueName
	 * 
	 * @return the outputQueueName
	 */
	public String getOutputQueueName() {
		return outputQueueName;
	}

	/**
	 * Get helper method for replyToQueueName
	 * 
	 * @return the replyToQueueName
	 */
	public String getReplyToQueueName() {
		return replyToQueueName;
	}

	/**
	 * Get helper method for inputFileNameFilter
	 * 
	 * @return the inputFileNameFilter
	 */
	public String getInputFileNameFilter() {
		return inputFileNameFilter;
	}

	/**
	 * Get helper method for HTTPServerUrl
	 * 
	 * @return the HTTPServerUrl
	 */
	public String getHTTPServerUrl() {
		return HTTPServerUrl;
	}

	/**
	 * Get helper method for HTTPEndPointName
	 * 
	 * @return the HTTPEndPointName
	 */
	public String getHTTPEndPointName() {
		return HTTPEndPointName;
	}
	/**
	 * Get helper method for HTTPWithBackoff
	 * 
	 * @return the HTTPWithBackoff
	 */
	public boolean getHTTPWithBackoff() {
		return HTTPWithBackoff;
	}
	
	/**
	 * Get helper method for inputFileDir
	 * 
	 * @return the inputFileDir
	 */
	public String getInputFileDir() {
		return inputFileDir;
	}

	/**
	  * Get helper method for File Names sorting when input is from a fileset
	  *
	  * @return true if the list of File Names are to be sorted before processing
	  */
	public boolean sortFilteredFileNames() {
			return sortFilteredFileNames;
	}

	/**
	 * Get helper method for FieldSeparator
	 * 
	 * @return the FieldSeparator
	 */
	public String getFieldSeparator() {
		return fieldSeparator;
	}

	/**
	 * Get helper method for maxRecsPerMessage
	 * 
	 * @return the maxRecsPerMessage
	 */
	public int getMaxRecsPerMessage() {
		return maxRecsPerMessage;
	}

	/**
	 * Get helper method for numRecordsToSkip
	 * 
	 * @return the numRecordsToSkip
	 */
	public int getNumRecordsToSkip() {
		return numRecordsToSkip;
	}

	/**
	 * Get helper method for getting the list of column names for an input CSV
	 * file
	 * 
	 * @return a copy of the colNameList
	 */
	public ArrayList<String> getColNameList() {
		// Shallow copy OK here, as Strings immutable
		// Collections.copy(dest, src) would also work here - it gives a shallow copy
		// (dest must be at least as big as src, else IndexOutOfBoundsException)
		return (colNameList == null ? null : new ArrayList<String>(colNameList));
	}

	/**
	 * Get helper method for XMLFormat
	 * 
	 * @return the format the XML should take e.g SELECT, INSERT
	 */
	public String getXMLFormat() {
		return XMLFormat;
	}

	/**
	 * Get helper method for actionOnError
	 * 
	 * @return the action to be taken when the created INSERT fails - RESPOND or
	 *         EXCEPTION
	 */
	public String getActionOnError() {
		return actionOnError;
	}

	/**
	 * Get helper method for preparedStatementName
	 * 
	 * @return the name of a Prepared Statement to use with the created INSERT
	 */
	public String getPreparedStatementName() {
		return preparedStatementName;
	}

	/**
	 * Get helper method for immediateCommit
	 * 
	 * @return the setting for whether the created action should be immediately
	 *         committed
	 */
	public String getImmediateCommit() {
		return immediateCommit;
	}

	/**
	 * Get helper method for outputFileNameTemplate
	 * 
	 * @return the outputFileNameTemplate
	 */
	public String getOutputFileNameTemplate() {
		return outputFileNameTemplate;
	}

	/**
	 * Get helper method for inputValidationSettings
	 * 
	 * (LwXMLTagValue is immutable)
	 * 
	 * @return the inputValidationSettings
	 */
	public LwXMLTagValue getInputValidationSettings() {
		return inputValidationSettings;
	}

	/**
	 * Get helper method for messageProcessingClassName
	 * 
	 * @return the messageProcessingClassName
	 */
	public String getMessageProcessingClassName() {
		return messageProcessingClassName;
	}

	/**
	 * Get helper method for messageProcessingSettingsFileName
	 * 
	 * @return the messageProcessingSettingsFileName
	 */
	public String getMessageProcessingSettingsFileName() {
		return messageProcessingSettingsFileName;
	}

	/**
	 * Get helper method for minResponsesExpected
	 * 
	 * @return the minResponsesExpected
	 */
	public int getMinResponsesExpected() {
		return minResponsesExpected;
	}

	/**
	 * Get helper method for auditKeysAggregate
	 * 
	 * (LwXMLTagValue is immutable)
	 * 
	 * @return the auditKeysAggregate
	 */
	public LwXMLTagValue getAuditKeysAggregate() {
		return auditKeysAggregate;
	}

	/**
	 * Get helper method for auditKeyNamesSet
	 * 
	 * (LwXMLTagValue is immutable)
	 * 
	 * @return an enumeration of the auditKeyNamesSet
	 */
	public Enumeration<LwXMLTagValue> getAuditKeyNamesSet() {
		if (auditKeyNamesSet == null) { // return an empty enumeration
			return (new Vector<LwXMLTagValue>()).elements();
		} else {
			return auditKeyNamesSet.elements();
		}
	}

	/**
	 * Get helper method for auditKeysSeparator
	 * 
	 * @return the auditKeysSeparator
	 */
	public String getAuditKeysSeparator() {
		return auditKeysSeparator;
	}

	/**
	 * Get helper method for errorFilesDir
	 * 
	 * @return the errorFilesDir
	 */
	public String getErrorFilesDir() {
		return errorFilesDir;
	}

	/**
	 * Get helper method for errorFileNameTemplate
	 * 
	 * @return the errorFileNameTemplate
	 */
	public String getErrorFileNameTemplate() {
		return errorFileNameTemplate;
	}

	/**
	 * Get helper method for mindElementSet
	 * 
	 * (LwXMLTagValue is immutable)
	 * 
	 * @return an enumeration of the mindElementSet
	 */
	public Enumeration<LwXMLTagValue> getMindElementSet() {
		if (mindElementSet == null) { // return an empty enumeration
			return (new Vector<LwXMLTagValue>()).elements();
		} else {
			return mindElementSet.elements();
		}
	}

	/**
	 * Get helper method for sendElementSet
	 * 
	 * (LwXMLTagValue is immutable)
	 * 
	 * @return an enumeration of the sendElementSet
	 */
	public Enumeration<LwXMLTagValue> getSendElementSet() {
		if (sendElementSet == null) { // return an empty enumeration
			return (new Vector<LwXMLTagValue>()).elements();
		} else {
			return sendElementSet.elements();
		}
	}

	/**
	 * Get helper method for responseLiteralsSet
	 * 
	 * (LwXMLTagValue is immutable)
	 * 
	 * @return an enumeration of the responseLiteralsSet
	 */
	public Enumeration<LwXMLTagValue> getResponseLiteralsSet() {
		if (responseLiteralsSet == null) { // return an empty enumeration
			return (new Vector<LwXMLTagValue>()).elements();
		} else {
			return responseLiteralsSet.elements();
		}
	}

	/**
	 * Get helper method for targetMainDocElementName
	 * 
	 * @return the targetMainDocElementName
	 */
	public String getTargetMainDocElementName() {
		return targetMainDocElementName;
	}

	/**
	 * Get helper method for responseMainDocElementName
	 * 
	 * @return the responseMainDocElementName
	 */
	public String getResponseMainDocElementName() {
		return responseMainDocElementName;
	}

	/**
	 * Get the Proces ID of the Java VM running this instance. Note, this
	 * returned nothing when was a public static method in the LwLogger class!
	 * 
	 * @param externalShell
	 *            the shell to use to get the ID.
	 * 
	 * @return the pid
	 */
	private String getProcessID(String externalShell) throws IOException {

		// First try this trick (returns something like "12345@localhost")...
		String runtimeMXBean = ManagementFactory.getRuntimeMXBean().getName();
		if (runtimeMXBean != null) {
			String[] runtimeMXBeanSplit = runtimeMXBean.split("@");
			String JVMpid = runtimeMXBeanSplit[0];
			return JVMpid;
		}

		// If no luck with ManagementFactory method above, try this...
		if (externalShell == null) {
			return null;
		}

		// ////////////////////////////////////////////////////////////////////////
		// Get the Proces ID of the Java VM running this instance
		// ////////////////////////////////////////////////////////////////////////
		byte[] bo = new byte[100];
		String[] cmd = { externalShell, "-c", "echo $PPID" };
		Process p = Runtime.getRuntime().exec(cmd);
		p.getInputStream().read(bo);

		return (new String(bo)).trim();
	}

	/**
	 * Record the settings in the log. Call this AFTER assigning any
	 * filehandler(s) to the Logger.
	 * 
	 */
	private FileHandler setUpLoggingToFile() throws LwSettingsException {
		FileHandler fh = null;
		try {
			fh = new FileHandler(logFileName, true);
			// Change default format from XML records (XMLFormatter) to
			// "easy-read"
			fh.setFormatter(new SimpleFormatter());

		} catch (IOException e) {
			throw new LwSettingsException(
					"LwGenericMessageHandlerSettings.setUpLoggingToFile(): Couldn't open log file "
							+ logFileName + ": " + e);
		}

		// Send logger output to our FileHandler.
		logger.addHandler(fh);

		// Set startup level from settings file
		if (loggingLevel == null) { // set default loggingLevel (production
									// level)
			logger.setLevel(Level.CONFIG);
		} else {
			try {
				logger.setLevel(Level.parse(loggingLevel));
			} catch (Exception e) {
				throw new LwSettingsException(
						"LwGenericMessageHandlerSettings.setUpLoggingToFile(): Invalid LogLevel in settings file! "
								+ e);
			}
		}
		
		return fh;
	}

	/**
	 * Record the settings in the log. Call this AFTER assigning any
	 * filehandler(s) to the Logger.
	 * 
	 */
	private void recordSettings() {
		Level logLevel = logger.getLevel();
		logger.config("Log Level initialised to " + logLevel.getName());

		logger.config("Shut-Down Log file is " + shutDownLogFileName);

		// Display the Proces ID of the Parent process of the Java VM running
		// this instance
		logger.config("Parent Process ID is "
				+ (ppid == null ? "unknown" : ppid));

		// Get and display the Proces ID of the Java VM running this instance
		logger.config("Process ID (of Java VM) is "
				+ (pid == null ? "unknown" : pid));

		// Record the queue info, if exists
		if (inputQueueName != null) {
			logger.config("Input Queue is " + inputQueueName);
			logger.config("MilliSecondsBeforeQuiet "
					+ milliSecondsBeforeQuiet);
		} else if (portNumber > 0) {
			logger.config("Socket Server Port Number is " + portNumber);
		} else if (inputFileNameFilter != null) {
			logger.config("Input FileName Filter is " + inputFileNameFilter);
			logger.config("Input Files Directory is "
					+ (inputFileDir.equals(".") ? "the current one"
							: inputFileDir));
			logger.config("Input from a fileset will " + (sortFilteredFileNames ? "" : "NOT ") + "be sorted on filename.");
			logger.config("Field Separator (for CSV files) is "
					+ fieldSeparator);
			logger.config("Max records per input message (for CSV files) is "
					+ maxRecsPerMessage);
			logger.config("Number of records to skip (for CSV files) is "
					+ numRecordsToSkip);
			logger.config("XMLFormat is " + XMLFormat);
			if (XMLFormat != null && XMLFormat.equals("INSERT")) {
				logger.config("ActionOnError is " + actionOnError);
				logger.config("PreparedStatementName is "
						+ preparedStatementName);
				logger.config("ImmediateCommit is " + immediateCommit);
			}
		} else { // Will expect input-supplying object to be specified in the Constructor of LwGenericMessageHandler
			logger.config("Will expect input-supplying object to be specified in the Constructor of LwGenericMessageHandler");
		}

		if (inputLimit > 0) {
			logger.config("Input Limit set to " + inputLimit);
		}

		if (inputUrlJMSserver != null) {
			logger.config("Input JMS URL is " + inputUrlJMSserver);
		}

		if (outputQueueName != null) {
			logger.config("Output Queue is " + outputQueueName);
		} else if (outputFileNameTemplate != null) {
			logger.config("Output FileName Template is "
					+ outputFileNameTemplate);
		}

		if (outputUrlJMSserver != null) {
			logger.config("Output JMS URL is " + outputUrlJMSserver);
		}

		if (replyToQueueName != null) {
			logger.config("ReplyTo Queue is " + replyToQueueName);
		}

		if (HTTPServerUrl != null) {
			logger.config("HTTP ServerUrl is " + HTTPServerUrl);
		}

		if (HTTPEndPointName != null) {
			logger.config("HTTP EndPointName is " + HTTPEndPointName);
		}

		logger.config("HTTP Post will " + (HTTPWithBackoff ? "" : "NOT") + " back off incrementally when trying to connect");
		
		// Record expected Data Contract Name, if exists
		if (dataContractName != null) {
			logger.config("Will only accept messages with Data Contract Name="
					+ dataContractName.getTagValue());
			logger.config("For Data Contract Name, ActionOnError is "
					+ dataContractName.getAttributeValue("ActionOnError"));
		}

		// Record Validation settings, if any set
		if (inputValidationSettings != null) {
			String schemaValidation = inputValidationSettings
					.getAttributeValue("SchemaValidation");

			logger.config("Input message Schema Validation is "
					+ schemaValidation);

			String schemaDefinitionFileName = inputValidationSettings
					.getAttributeValue("SchemaDefinitionFileName");

			if (schemaDefinitionFileName != null) {
				logger.config("Input message Validation will "
						+ (schemaValidation == null
								|| schemaValidation.equals("off") ? "not " : "")
						+ "be performed against file "
						+ schemaDefinitionFileName
						+ "(language="
						+ inputValidationSettings
								.getAttributeValue("SchemaLanguage")
						+ ") and no Schema specification expected in message");
			}
		}

		// Record how we are to process a message
		logger.config("MessageProcessingClassName is "
				+ messageProcessingClassName);

		if (minResponsesExpected >= 0) {
			logger.config(minResponsesExpected
					+ " or more Response(s) expected/allowed from Processing class "
					+ messageProcessingClassName);
		}

		// Record what to do if Audit Keys missing from a message
		if (auditKeysAggregate != null) {
			logger.config("When an Audit key is missing from a message, perform the action: "
					+ auditKeysAggregate.getAttributeValue("ActionOnError"));
		}

		// Record Audit key Names, if exist
		Enumeration<LwXMLTagValue> enumAuditKeyNames = auditKeyNamesSet
				.elements();
		while (enumAuditKeyNames.hasMoreElements()) {
			LwXMLTagValue tv = enumAuditKeyNames.nextElement();
			logger.info("Found Audit KeyName Tag at " + tv.getPathToName()
					+ " Value=" + tv.getTagValue());
		}

		// Record Error file settings, if exist
		if (errorFilesDir != null) {
			logger.config("Error files will be placed in directory "
					+ errorFilesDir);
		}

		logger.config("Error files will be named according to the following template: "
				+ errorFileNameTemplate);

		// Record 'mind' aggregate Names, if exist
		Enumeration<LwXMLTagValue> enumMindAggNames = mindElementSet.elements();
		while (enumMindAggNames.hasMoreElements()) {
			LwXMLTagValue tv = enumMindAggNames.nextElement();
			logger.info("Found 'Mind' Element Tag at " + tv.getPathToName()
					+ " Value=" + tv.getTagValue());
		}

		// Record 'send' aggregate Names
		Enumeration<LwXMLTagValue> enumSendAggNames = sendElementSet.elements();
		while (enumSendAggNames.hasMoreElements()) {
			LwXMLTagValue tv = enumSendAggNames.nextElement();
			logger.info("Found Send Element Tag at " + tv.getPathToName()
					+ " Value=" + tv.getTagValue());
		}

		logger.config("TargetMainDocElementName is " + targetMainDocElementName);
		logger.config("ResponseMainDocElementName is "
				+ responseMainDocElementName);

		// Record response literal Names, if exist
		Enumeration<LwXMLTagValue> enumRespLiteralNames = responseLiteralsSet
				.elements();
		while (enumRespLiteralNames.hasMoreElements()) {
			LwXMLTagValue tv = enumRespLiteralNames.nextElement();
			logger.info("Found Response Literal Tag at " + tv.getPathToName()
					+ " Value=" + tv.getTagValue());
		}
	}

	/**
	 * Release any resources being retained by this object.
	 * This won't affect thread safety as is only to be called at closedown. 
	 * 
	 */
	public void releaseResourses() {
		logger.removeHandler(fh);
		fh.close();
	}

}