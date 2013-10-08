package gemha.support;

import java.util.logging.*;
import java.util.*;

import lw.XML.*;
import lw.utils.*;


/**
  * This class loads all the settings for the LwProcessMessageForSocket class.
  * @author Liam Wade
  * @version 1.0 07/11/2008
  */
public class ProcessMessageForSocketSettings
{

    private static final Logger logger = Logger.getLogger("gemha");
	
	private String settingsFileName = null;
	private boolean settingsFileSchemaValidation = false;

    private int portNumber = 0;
    private String hostName = null;
    private String applicationLevelResponse = null;

	private Vector<XMLTagValue> auditKeyNamesSet = null; // Note: the concatenated values of these keys should not generally exceed 255 chars (to include any separators)
															// If they do exceed this value, only the first 255 chars will be used as the Transaction ID for socket sends
	private String auditKeysSeparator = null;			// if exists, will be used to separate values for concatenated audit keys

	/**
    * Will create a new LwProcessMessageForSocketSettings object.
    *
	* @param logger the Logger for audit messages
	* @param settingsFileName the name of the settings file, path included
	* @param settingsFileSchemaValidation the set to true to have the settings file validate against its Schema definition
    */
	public ProcessMessageForSocketSettings(String settingsFileName, boolean settingsFileSchemaValidation)
																			throws SettingsException {
		if (settingsFileName == null) {
			throw new SettingsException("LwProcessMessageForSocketSettings.constructor(): Required parameter is null.");
		}

		this.settingsFileName = settingsFileName;
		this.settingsFileSchemaValidation = settingsFileSchemaValidation;

		try {
			getSettings();
		}
		catch(XMLException e) {
			throw new SettingsException("LwProcessMessageForSocketSettings.constructor(): caught LwXMLException: " + e);
		}

		recordSettings();
	}


	/**
	  * Get helper method for Port Number
	  *
	  * @return the Port Number for the socket
	  */
	public int getPortNumber() {
			return portNumber;
	}

	/**
	  * Get helper method for Host Name
	  *
	  * @return the Name of the Host on which the socket resides
	  */
	public String getHostName() {
			return hostName;
	}

	/**
	  * Get helper method for ApplicationLevelResponse
	  *
	  * @return the ApplicationLevelResponse
	  */
	public String getApplicationLevelResponse() {
			return applicationLevelResponse;
	}

	/**
	  * Get helper method for auditKeyNamesSet
	  *
	  * @return a Vector of the audit Key Names by which to extract audit keys from a messgae
	  */
	public Vector<XMLTagValue> getAuditKeyNamesSet() {
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
							throws SettingsException, XMLException {
		XMLDocument settingsDoc = null;

		settingsDoc = XMLDocument.createDocFromFile(settingsFileName, settingsFileSchemaValidation);

		//////////////////////////////////////////////////////////////////////////
		// Get the application general Parameters...
		//////////////////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////////////
		// Get the Port Number to use for the socket...
		//////////////////////////////////////////////////////////////////////////
		String strPortNumber = settingsDoc.getValueForTag("Params/PortNumber");
		if (strPortNumber != null) {
			try {
				portNumber = Integer.parseInt(strPortNumber);
			}
			catch(NumberFormatException e) {
				throw new SettingsException("LwProcessMessageForSocketSettings.getSettings(): Invalid PortNumber.");
			}
		}

		if (portNumber < 1) {
			throw new SettingsException("LwProcessMessageForSocketSettings.getSettings(): Invalid PortNumber. 0 not allowed");
		}

		// Get the Host Name
		hostName = settingsDoc.getValueForTag("Params/HostName");
		if (hostName == null) {
			hostName = "localhost";
		}

		// Get the Response type at application level
		applicationLevelResponse = settingsDoc.getValueForTag("Params/ApplicationLevelResponse");

		//////////////////////////////////////////////////////////////////////////
		// Get the values for Audit KeyName TAGs, if exist...
		//////////////////////////////////////////////////////////////////////////
		auditKeyNamesSet = settingsDoc.getValuesForTag("Auditing/AuditKeys/KeyName");

		// Get the separator to use when concatenating audit keys, if exists
		auditKeysSeparator = settingsDoc.getValueForTag("Auditing/AuditKeysSeparator");

	}


  /**
    * Record the settings in the log. Call this AFTER assigning any filehandler(s) to the Logger.
    *
	*/
	public void recordSettings() {
		logger.config("Port Number is " + portNumber);
		logger.config("Host name " + hostName);
		logger.config("ApplicationLevelResponse is " + applicationLevelResponse);

		// Record Audit key Names, if exist
		if (auditKeyNamesSet != null) {
			for (XMLTagValue tv : auditKeyNamesSet) {
				logger.config("Found Audit KeyName Tag at " + tv.getPathToName() + " Value=" + tv.getTagValue());
			}
		}

		logger.config("AuditKeysSeparator is " + auditKeysSeparator);
	}

}