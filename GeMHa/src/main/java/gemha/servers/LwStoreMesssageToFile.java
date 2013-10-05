package gemha.servers;

import java.util.logging.*;
import java.io.*;

import lw.XML.*;
import lw.utils.LwLogger;
import gemha.interfaces.LwIStoreMesssage;
import gemha.support.*;

/**
  * This class saves messages to a file.
  *
  * @author Liam Wade
  * @version 1.0 30/10/2008
  */
public class LwStoreMesssageToFile implements LwIStoreMesssage {

    private static final Logger logger = Logger.getLogger("gemha");

	private String outputFileNameTemplate = null;	// the template from which to build output filenames
	private String dataFormat = null;				// the format of outgoing messages e.g "XML"

	private int safetySequenceNo = 0;

	public LwStoreMesssageToFile(String outputFileNameTemplate, String dataFormat) {
		this.outputFileNameTemplate = outputFileNameTemplate;
		this.dataFormat = dataFormat;
	}

	//////////////////////////////////////////////////////////////////
	// Start: Implementation methods for LwIStoreMesssage Interface...
	//////////////////////////////////////////////////////////////////
	/**
	  * Open any connections
	  *
	  * @throws LwMessagingException when any error is encountered
	  */
	public void openStorage()
	 			throws LwMessagingException {
	}

	/**
	  * Close any connections
	  *
	  * @throws LwMessagingException when any error is encountered
	  */
	public void closeStorage()
	 			throws LwMessagingException {
	}

	/**
	  * Process a message
	  *
	  * @param message the message to be processed
	  * @param instructions instructions on how the message is to be processed (will be implementation-specific). Can be null
	  *
	  * @throws LwMessagingException when any error is encountered
	  */
	public void putMessage(String message, String auditKey, String instructions)
								throws LwMessagingException {

		if (outputFileNameTemplate == null) { // am dealing with files for output
			throw new LwMessagingException("Response message with AuditKey Value " + auditKey + " not output - no outputFileNameTemplate to work with.");
		}

		String outputFileName = createFileNameFromTemplate(outputFileNameTemplate, auditKey);

		if (dataFormat != null && dataFormat.equals("XML")) {
			LwXMLDocument outputDoc = createXMLDocFromInput(message, false);
			if (outputDoc == null) {
				logger.severe("XML Response message with AuditKey Value " + auditKey + " could not be parsed.");
				throw new LwMessagingException("XML Response message with AuditKey Value " + auditKey + " could not be parsed.");
			}
			else if (outputDoc.toFile(outputFileName, false)) {
				logger.info("XML Response message with AuditKey Value " + auditKey + " output to file " + outputFileName);
			}
			else {
				logger.severe("XML Response message with AuditKey Value " + auditKey + " could not be output to file " + outputFileName);
				throw new LwMessagingException("XML Response message with AuditKey Value " + auditKey + " could not be output to file " + outputFileName);
			}
		}
		else { // No format, just send straight to file
			if ( setFileContents(outputFileName, message)) {
				logger.info("Response message with AuditKey Value " + auditKey + " output to file " + outputFileName);
			}
			else {
				// problem putting to file!
				logger.severe("Response message with AuditKey Value " + auditKey + " could not be output to file " + outputFileName);
				throw new LwMessagingException("Response message with AuditKey Value " + auditKey + " could not be output to file " + outputFileName);
			}
		} // end if (dataFormat != null && dataFormat.equals("XML"))
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performStoreMessageCleanup(LwLogger shutdownLogger) {

	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIStoreMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	  * Change the contents of text file in its entirety, overwriting any
	  * existing text.
	  *
	  * This style of implementation throws all exceptions to the caller.
	  *
	  * @param fileName the name of the file to which the content should be written.
	  *
	  * @return true if contents successfully  written, otherwise false
	*/
	private boolean setFileContents(String fileName, String contents) {
		if (fileName == null) {
			return false;
		}

		boolean result = false;

		try {
			//use buffering
			Writer output = new BufferedWriter(new FileWriter(fileName));

			try {
				//FileWriter always assumes default encoding is OK!
				output.write(contents);
				result = true;
			}
			finally {
				output.close();
			}
		}
		catch(FileNotFoundException e) {
			logger.warning("Could not find file " + fileName + " when saving contents");
		}
		catch(IOException e) {
			logger.warning("Exception encountered while saving contents to file " + fileName + ": " + e);
		}

		return result;
	}
	/**
	  * Using the template, construct a real file name.
	  * An asterisk in the template will be replaced by the audit key, a ? by "current date & time & sequence number"
	  *
	  * @param fileNameTemplate the template to use.
	  * @param fileNameTemplate the template to use.
	  *
	  * @return the constructed file name, null if fileNameTemplate was null
	  */
	private String createFileNameFromTemplate(String fileNameTemplate, String key) {

		if (fileNameTemplate == null) {
			return null;
		}

		StringBuilder constructedFileName = new StringBuilder(fileNameTemplate);

		if (key != null) {
			int startAsterisk = constructedFileName.indexOf("*");

			if (startAsterisk >= 0) { // then "*" found, replace with key
				constructedFileName.replace(startAsterisk, (startAsterisk+1), key);
			}
		}

		int startQuestionMark = constructedFileName.indexOf("?");

		if (startQuestionMark >= 0) { // then "?" found, replace with key
			constructedFileName.replace(startQuestionMark, (startQuestionMark+1), (LwLogger.getDateTime("") + (new Integer(safetySequenceNo++)).toString()) );
		}

		return constructedFileName.toString();
	}

	/**
	  *
	  * Shut down the application gracefully - will be called by the Java VM when closing down
	  *
	  * @param message the XML text from which to make a document
	  * @param validateAgainstSchema true if the XML should be validated against it's Schema
	  *
	  * @return a new LwXMLDocument
	  */
	private LwXMLDocument createXMLDocFromInput(String message, boolean validateAgainstSchema) {

		LwXMLDocument newDoc = null;

		try {
			newDoc = LwXMLDocument.createDoc(message, validateAgainstSchema);
			return newDoc;
		}
		catch(LwXMLException e) {
			logger.warning("LwXMLException: " + e.getMessage());
			return null;
		}
	}
}
