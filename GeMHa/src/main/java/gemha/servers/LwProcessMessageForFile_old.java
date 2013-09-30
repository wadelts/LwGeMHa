package gemha.servers;

import java.util.logging.*;
import java.util.Enumeration;
import java.util.Vector;
import java.io.*;

import lw.XML.*;
import lw.utils.*;
import gemha.support.LwProcessMessageForFileSettings;
import gemha.support.LwMessagingException;
import gemha.interfaces.LwIProcessMesssage_old;

/**
  * This class processes an XML message to send a record to a file.
  *
  * @author Liam Wade
  * @version 1.0 16/12/2008
  */
public class LwProcessMessageForFile_old implements LwIProcessMesssage_old {

    private static final Logger logger = Logger.getLogger("gemha");

	public LwProcessMessageForFile_old() {
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
	public void performSetup(String settingsFileName) throws LwSettingsException {

		if (settingsFileName == null) {
			throw new LwSettingsException("Parameter settingsFileName was null.");
		}

		settings = new LwProcessMessageForFileSettings(settingsFileName, LwXMLDocument.SCHEMA_VALIDATION_ON);

		messagesFileName = LwLogger.createFileNameFromTemplate(settings.getMessagesFileNameTemplate(), null);

		try {
			openMessagesFile(messagesFileName, settings.getFileOpenMode().equals("append"));
		}
		catch (LwMessagingException e) {
			logger.severe("LwMessagingException: " + e.getMessage());
			throw new LwSettingsException("Caught LwMessagingException trying to open new output file " + messagesFileName + ": " + e.getMessage());
		}
	}

	/**
	  * Process a message
	  *
	  * @param messageText the message to be processed
	  *
	  * @return 0 for success with no response necessary, 1 for success and response is ready, less than zero for error
	  */
	public int processMessage(String messageText)
											throws LwMessagingException {

		logger.info("Control now in messageProcessor.");

		if (messageText == null) {
			return -1;
		}

		if (outFile == null) { // then out file was closed, re-open it, appending
			openMessagesFile(messagesFileName, true);
		}

		logger.finer("Processing message: " + messageText);

		//////////////////////////////////////////////////////////////////
		// Set up a new XML doc
		//////////////////////////////////////////////////////////////////
		LwXMLDocument newDoc = new LwXMLDocument();
		try {
			newDoc.createDoc(messageText, LwXMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(LwXMLException e) {
			logger.severe("LwXMLException: " + e.getMessage());
			logger.warning("InputMessage was :" + messageText);
			throw new LwMessagingException("Could not create new XML document: " + e.getMessage());
		}

		///////////////////////////////////////////////
		// Get audit information from message (or make it up)...
		///////////////////////////////////////////////
		auditKeyValues = getConcatenatedAuditKeyValues(newDoc, settings.getAuditKeyNamesSet(), settings.getAuditKeysSeparator()); // works at "current node" level


		///////////////////////////////////////////////
		// Send column names to the file, if requested...
		///////////////////////////////////////////////
		if (outFileIsEmpty && settings.columnNamesToBeIncluded()) {
			outFileIsEmpty = false;
			// Just take names from first-found row
			if (newDoc.setCurrentNodeByPath("/MESSAGE/FILE_REQUEST/TABLE/ROW/COLUMNS", 1)) {
				Vector<LwXMLTagValue> saveColumns = newDoc.getValuesForTagsChildren();

				int colNum = 0;
				for (LwXMLTagValue tv : saveColumns) {
					outFile.print(tv.getTagName());
					if (++colNum < saveColumns.size()) { // then not last column, so add separator
						outFile.print(settings.getFieldSeparator());
					}
				}
				outFile.println();
				logger.info("Wrote column names record to file.");
			}
		}

		///////////////////////////////////////////////
		// Send all rows of data to the file...
		///////////////////////////////////////////////
		int i = 0;
		while (newDoc.setCurrentNodeByPath("/MESSAGE/FILE_REQUEST/TABLE/ROW/COLUMNS", ++i)) {
			Vector<LwXMLTagValue> saveColumns = newDoc.getValuesForTagsChildren();

			int colNum = 0;
			for (LwXMLTagValue tv : saveColumns) {
				outFile.print(tv.getTagValue());
				if (++colNum < saveColumns.size()) { // then not last column, so add separator
					outFile.print(settings.getFieldSeparator());
				}
			}
			outFile.println();
			logger.info("Wrote record for audit key value " + auditKeyValues);
		}


		///////////////////////////////////////////////
		// If got here, message was successfully transmitted.
		// So build Response...
		///////////////////////////////////////////////
		createResponseDoc(); // shell
		response.addElement(null, "SEND_STATUS", "SUCCESS");
		response.addElement(null, "NUM_ROWS_WRITTEN", String.valueOf(i-1));


		return 1;
	}

	/**
	  * Return the response message
	  *
	  * @return the response, null if none exists
	  */
	public String getResponse() {
		LwXMLDocument tempResponse = response;
		response = null;
		return tempResponse.toString();
	}

	/**
	  * Do whatever you like when things are quiet
	  *
	  */
	public void goQuiet() {
		logger.info("All quiet, going to close output file.");

		if (outFile != null) {
			outFile.close();
			outFile = null;
		}
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		if (outFile != null) {
			outFile.close();
			outFile = null;
		}

		if (shutdownLogger != null) {
			try { shutdownLogger.appendln("Closed output file.");} catch (IOException e) { /* do nothing */}
		}
		else {
			logger.info("Closed output file.");
		}
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
										throws LwMessagingException {
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
			throw new LwMessagingException("Caught IOException trying to open a new messages output file " + fileName + ": " + e.getMessage());
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
	private String getConcatenatedAuditKeyValues(LwXMLDocument doc, Vector<LwXMLTagValue> auditKeyNamesSet, String separator) {

		String concatenatedValues = "";

		// Using Enumeration instead of for-each loop, so could use hasMoreElements() when adding separator
		Enumeration<LwXMLTagValue> enumAuditKeyNames = auditKeyNamesSet.elements();
		while (enumAuditKeyNames.hasMoreElements()) {
			LwXMLTagValue tv = enumAuditKeyNames.nextElement();

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
	private void createResponseDoc()
								throws LwMessagingException {

		response = new LwXMLDocument();

		try {
			response.createDoc("<FILE_REQUEST></FILE_REQUEST>", LwXMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(LwXMLException e) {
			logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
			throw new LwMessagingException("LwProcessMessageForDb.buildErrorResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
		}
	}

	private LwXMLDocument response = null;
	private String messagesFileName = null;
	private PrintWriter outFile = null;
	private boolean outFileIsEmpty = true;	// true if nothing yet written to file
	private LwProcessMessageForFileSettings settings = null;
	private String auditKeyValues = null; // the audit key values as a concatenated string
}
