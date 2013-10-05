package gemha.support;

import java.util.logging.*;
import java.util.*;

import lw.XML.*;
import lw.utils.*;

/**
  * This class loads all the settings for the LwProcessMessageForFile class.
  * @author Liam Wade
  * @version 1.0 15/12/2008
  */
public class LwProcessMessageForFileSettings
{
	
    private static final Logger logger = Logger.getLogger("gemha");

	private String columnsLocation = "/MESSAGE/FILE_REQUEST/TABLE/ROW/COLUMNS";
	private String settingsFileName = null;
	private boolean settingsFileSchemaValidation = false;

    private String messagesFileNameTemplate = null;
    private String fieldSeparator = null;
	private boolean includeColumnNames = false;			// should column names be included in output?
    private String fileOpenMode = null;					// should we create or append (to) file when first starting up

	private Vector<LwXMLTagValue> auditKeyNamesSet = null;
	private String auditKeysSeparator = null;			// if exists, will be used to separate values for concatenated audit keys

	/**
    * Will create a new LwProcessMessageForFileSettings object.
    *
	* @param logger the Logger for audit messages
	* @param settingsFileName the name of the settings file, path included
	* @param settingsFileSchemaValidation the set to true to have the settings file validate against its Schema definition
    */
	public LwProcessMessageForFileSettings(String settingsFileName, boolean settingsFileSchemaValidation)
																			throws LwSettingsException {
		if (settingsFileName == null) {
			throw new LwSettingsException("LwProcessMessageForFileSettings.constructor(): Required parameter is null.");
		}

		this.settingsFileName = settingsFileName;
		this.settingsFileSchemaValidation = settingsFileSchemaValidation;

		try {
			getSettings();
		}
		catch(LwXMLException e) {
			throw new LwSettingsException("LwProcessMessageForFileSettings.constructor(): caught LwXMLException: " + e);
		}

		recordSettings();
	}


	/**
	  * Get helper method for Columns Location
	  *
	  * @return the location of Column elements within the received message
	  */
	public String getColumnsLocation() {
			return columnsLocation;
	}

	/**
	  * Get helper method for Messages File Name Template
	  *
	  * @return the template to be used in constructing a Messages FileName
	  */
	public String getMessagesFileNameTemplate() {
			return messagesFileNameTemplate;
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
	  * Get helper method for Column Names inclusion
	  *
	  * @return true if Column Names are to be included in output
	  */
	public boolean columnNamesToBeIncluded() {
			return includeColumnNames;
	}

	/**
	  * Get helper method for File Open Mode
	  *
	  * @return the File Open Mode at startup - append or create
	  */
	public String getFileOpenMode() {
			return fileOpenMode;
	}

	/**
	  * Get helper method for auditKeyNamesSet
	  *
	  * @return a Vector of the audit Key Names by which to extract audit keys from a messgae
	  */
	public Vector<LwXMLTagValue> getAuditKeyNamesSet() {
		return auditKeyNamesSet;

	}

	/**
	  * Get helper method for auditKeysSeparator
	  *
	  * @return the auditKeysSeparator for the SQL driver
	  */
	public String getAuditKeysSeparator() {
			return auditKeysSeparator;
	}

  /**
    * Get the settings from the settings file
    *
	*/
	private void getSettings()
							throws LwSettingsException, LwXMLException {
		LwXMLDocument settingsDoc = null;

		settingsDoc = LwXMLDocument.createDocFromFile(settingsFileName, settingsFileSchemaValidation);

		//////////////////////////////////////////////////////////////////////////
		// Get the application general Parameters...
		//////////////////////////////////////////////////////////////////////////
		// Get the Columns Location within the received message...
		String tempColumnsLocation = settingsDoc.getValueForTag("Applic/Params/ColumnsLocation");
		if (tempColumnsLocation != null) {
			columnsLocation = tempColumnsLocation;
		}
				
		// Get the Messages FileName Template
		messagesFileNameTemplate = settingsDoc.getValueForTag("Applic/Params/MessagesFileNameTemplate");
		if (messagesFileNameTemplate == null) {
			messagesFileNameTemplate = "LwProcessMessageForFile_?.txt";
		}

		// Get the Separator to be used between fields in the output record - default to tab char
		fieldSeparator = settingsDoc.getValueForTag("Applic/Params/FieldSeparator");
		if (fieldSeparator == null) {
			fieldSeparator = "\t";
		}
		// replace escaped chars with literals, which will have been processed for escaping - variables are not so processed
		if (fieldSeparator.length() > 1 && fieldSeparator.startsWith("\\")) { // then is escaped char, replace with literal...
			switch (fieldSeparator.charAt(1)) {
				case 'b' :	fieldSeparator = "\b";	// backspace
							break;
				case 'f' :	fieldSeparator = "\f";	// form feed
							break;
				case 'n' :	fieldSeparator = "\n";	// newline
							break;
				case 'r' :	fieldSeparator = "\r";	// return
							break;
				case 't' :	fieldSeparator = "\t";	// tab
							break;
				case '\'' :	fieldSeparator = "\'";	// single quote
							break;
				case '\"' :	fieldSeparator = "\"";	// double quote
							break;
				case '\\' :	fieldSeparator = "\\";	// back slash
							break;
			} // end switch (fieldSeparator.charAt(1))
		}

		// Find out if we should include column names in the output file
		String strIncludeColumnNames = settingsDoc.getValueForTag("Applic/Params/IncludeColumnNames");
		if (strIncludeColumnNames != null) {
			includeColumnNames = strIncludeColumnNames.equals("true");
		}

		// Get the File Open Mode
		fileOpenMode = settingsDoc.getValueForTag("Applic/Params/FileOpenMode");
		if (fileOpenMode == null) {
			fileOpenMode = "create";
		}

		//////////////////////////////////////////////////////////////////////////
		// Get the values for Audit KeyName TAGs, if exist...
		//////////////////////////////////////////////////////////////////////////
		auditKeyNamesSet = settingsDoc.getValuesForTag("Applic/Auditing/AuditKeys/KeyName");

		// Get the separator to use when concatenating audit keys, if exists
		auditKeysSeparator = settingsDoc.getValueForTag("Applic/Auditing/AuditKeysSeparator");

	}


  /**
    * Record the settings in the log. Call this AFTER assigning any filehandler(s) to the Logger.
    *
	*/
	public void recordSettings() {
		logger.config("Location of Columns in received message is " + columnsLocation);
		logger.config("Messages FileName Template is " + messagesFileNameTemplate);
		logger.config("Field Separator is " + fieldSeparator);
		logger.config("Column Names will " + (includeColumnNames ? "" : "NOT ") + "be included in output.");
		logger.config("FileOpenMode is " + fileOpenMode);

		// Record Audit key Names, if exist
		if (auditKeyNamesSet != null) {
			for (LwXMLTagValue tv : auditKeyNamesSet) {
				logger.config("Found Audit KeyName Tag at " + tv.getPathToName() + " Value=" + tv.getTagValue());
			}
		}

		logger.config("AuditKeysSeparator is " + auditKeysSeparator);
	}

}