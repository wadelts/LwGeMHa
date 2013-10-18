package gemha.support;

import java.util.logging.*;
import java.util.*;

import lw.XML.*;
import lw.db.*;
import lw.utils.*;

/**
  * This class loads all the settings for the LwProcessMessageForDb class.
  * @author Liam Wade
  * @version 1.0 07/11/2008
  */
public class ProcessMessageForDbSettings
{

    private static final Logger logger = Logger.getLogger("gemha");
	
	/**
    * Will create a new exception with the given reason.
    *
	* @param logger the Logger for audit messages
	* @param settingsFileName the name of the settings file, path included
	* @param settingsFileSchemaValidation the set to true to have the settings file validate against its Schema definition
    */
	public ProcessMessageForDbSettings(String settingsFileName, boolean settingsFileSchemaValidation)
																			throws SettingsException {
		if (settingsFileName == null) {
			throw new SettingsException("LwProcessMessageForDbSettings.constructor(): Required parameter is null.");
		}

		this.settingsFileName = settingsFileName;
		this.settingsFileSchemaValidation = settingsFileSchemaValidation;

		try {
			getSettings();
		}
		catch(XMLException e) {
			throw new SettingsException("LwProcessMessageForDbSettings.constructor(): caught LwXMLException: " + e);
		}

		recordSettings();
	}


	/**
	  * Get helper method for jdbcClass
	  *
	  * @return the jdbcClass for the SQL driver
	  */
	public String getJdbcClass() {
			return jdbcClass;
	}

	/**
	  * Get helper method for dbURL
	  *
	  * @return the dbURL for the SQL driver
	  */
	public String getDbURL() {
			return dbURL;
	}

	/**
	  * Get helper method for userName
	  *
	  * @return the userName for the SQL driver
	  */
	public String getUserName() {
			return userName;
	}

	/**
	  * Get helper method for userPass
	  *
	  * @return the userPass for the SQL driver
	  */
	public String getUserPass() {
			return userPass;
	}

	/**
	  * Get helper method for autoCommit setting
	  *
	  * @return the autoCommit setting for the SQL driver
	  */
	public boolean autoCommitting() {
			return autoCommit;
	}

	/**
	  * Get helper method for defaultTablename
	  *
	  * @return the defaultTablename for the SQL driver
	  */
	public String getDefaultTablename() {
			return defaultTablename;
	}

	/**
	  * Get helper method for updateLockingStrategy
	  *
	  * @return the updateLockingStrategy for the SQL driver
	  */
	public String getUpdateLockingStrategy() {
			return updateLockingStrategy;
	}

	/**
	  * Get helper method for dateFormat
	  *
	  * @return the dateFormat for the database
	  */
	public String getDateFormat() {
			return dateFormat;
	}

	/**
	  * Get helper method for preparedStatementTemplates
	  *
	  * @return a Vector of the Prepared Statement Templates, never null
	  */
	public Vector<LwPreparedStatementTemplate> getPreparedStatementTemplates() {
			return preparedStatementTemplates;
	}

	/**
	  * Get helper method for auditKeyNamesSetForInserts
	  *
	  * @param action the type of database action for which we require the key names
	  *
	  * @return the a Vector of the audit Key Names For whichever action was specified
	  */
	public Vector<XMLTagValue> getAuditKeyNamesSet(String action) {
			if (action == null) {
				return null;
			}

			if (action.toUpperCase().equals("INSERT")) {
				return auditKeyNamesSetForInserts;
			}
			else if (action.toUpperCase().equals("UPDATE")) {
				return auditKeyNamesSetForUpdates;
			}
			else if (action.toUpperCase().equals("DELETE")) {
				return auditKeyNamesSetForDeletes;
			}
			else {
				return auditKeyNamesSetForSelects;
			}

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
		// Get the name SQL driver class
		jdbcClass = settingsDoc.getValueForTag("Params/JdbcClass");

		// Get the database URL
		dbURL = settingsDoc.getValueForTag("Params/DbURL");

		// Get the User name for logging on to the database
		userName = settingsDoc.getValueForTag("Params/UserName");

		// Get the User password for logging on to the database
		userPass = settingsDoc.getValueForTag("Params/UserPass");

		// Get the setting for Auto-commit - if on, all transactions will be automatically committed when the connection is closed.
		String strAutoCommit = settingsDoc.getValueForTag("Params/AutoCommit");
		if (strAutoCommit != null) {
			autoCommit = strAutoCommit.toLowerCase().equals("on");
		}

		// Get the name to use for database table, for use if none is supplied as part of a transaction.
		defaultTablename = settingsDoc.getValueForTag("Params/DefaultTablename");

		// Get the strategy to be employed for update locking.
		updateLockingStrategy = settingsDoc.getValueForTag("Params/UpdateLockingStrategy");
		if (updateLockingStrategy == null) {
			updateLockingStrategy = "optimistic";
		}

		// Get the format of dates to be used for all queries/updates to the database, for both in- and out-bound date elements.
		dateFormat = settingsDoc.getValueForTag("Params/DateFormat");

		//////////////////////////////////////////////////////////////////////////
		// Get any PreparedStatements, if exist...
		//////////////////////////////////////////////////////////////////////////
		int i = 0;
		while (settingsDoc.setCurrentNodeByPath("PreparedStatements/PreparedStatement", ++i)) {
			String preparedStatementName = settingsDoc.getValueForTag("PreparedStatementName");
			XMLTagValue preparedStatementSQL = settingsDoc.getValueForTagPlusAttributes("PreparedStatementSQL");

			// Get the SQL statement parameter column names (will actually be stored in the value part of the LwXMLTagValue - all Names will be "Column")
			// This encapsulates the order in which actual values will be applied to the prepared statement
			ArrayList<String> paramList = null;
			if (settingsDoc.setCurrentNodeByPath("ParameterOrder", 1)) {
				Vector<XMLTagValue> paramColumns = settingsDoc.getValuesForTagsChildren();

				// Transfer column names to simple list
				paramList = new ArrayList<String>(paramColumns.size());
				for(XMLTagValue p : paramColumns) {
					paramList.add(p.getTagValue());
				}

				settingsDoc.restoreCurrentNode();
			}

			LwPreparedStatementTemplate pst = new LwPreparedStatementTemplate(preparedStatementName, preparedStatementSQL.getTagValue(), paramList, preparedStatementSQL.getAttributeValue("ReturnType"));
			preparedStatementTemplates.addElement(pst);

			settingsDoc.setCurrentNodeToFirstElement(); // need to go back to top of doc, for next search
		}

		//////////////////////////////////////////////////////////////////////////
		// Get the values for ALL Audit KeyName TAGs, if exist...
		//////////////////////////////////////////////////////////////////////////
		i = 0;
		while (settingsDoc.setCurrentNodeByPath("Auditing/AuditKeys", ++i)) {
			XMLTagValue auditKeysAggregate = settingsDoc.getValueForTagPlusAttributesForCurrentNode(); // just getting attribute(s) for current node
			String dbAction = auditKeysAggregate.getAttributeValue("DbAction");
			if (dbAction != null) {
				if (dbAction.equals("insert")) {
					auditKeyNamesSetForInserts = settingsDoc.getValuesForTag("KeyName");
				}
				else if (dbAction.equals("update")) {
					auditKeyNamesSetForUpdates = settingsDoc.getValuesForTag("KeyName");
				}
				else if (dbAction.equals("delete")) {
					auditKeyNamesSetForDeletes = settingsDoc.getValuesForTag("KeyName");
				}
				else if (dbAction.equals("select")) {
					auditKeyNamesSetForSelects = settingsDoc.getValuesForTag("KeyName");
				}
			}

			settingsDoc.setCurrentNodeToFirstElement(); // need to go back to top of doc, for next search
		}


		auditKeysSeparator = settingsDoc.getValueForTag("Auditing/AuditKeysSeparator");

	}


  /**
    * Record the settings in the log. Call this AFTER assigning any filehandler(s) to the Logger.
    *
	*/
	public void recordSettings() {
		logger.config("Jdbc Class Driver name is " + jdbcClass);
		logger.config("Database URL  is " + dbURL);
		logger.config("UserName is " + userName);
		logger.config("Application AutoCommit is " + (autoCommit ? "on" : "off"));
		logger.config("DefaultTablename is " + defaultTablename);
		logger.config("UpdateLockingStrategy is " + updateLockingStrategy);
		logger.config("DateFormat for queries and results is " + (dateFormat == null ? "database default" : dateFormat));

		// Record Prepared Statements, if exist
		for (LwPreparedStatementTemplate pst : preparedStatementTemplates) {
			logger.config("Found Prepared Statement  " + pst.getPreparedStatementName() + " (returns " + pst.getReturnType() + "): " + pst.getTemplateSQL());
		}

		// Record Audit key Names, if exist
		if (auditKeyNamesSetForInserts != null) {
			for (XMLTagValue tv : auditKeyNamesSetForInserts) {
				logger.config("Found Audit Insert KeyName Tag at " + tv.getPathToName() + " Value=" + tv.getTagValue());
			}
		}

		// Record Audit key Names, if exist
		if (auditKeyNamesSetForUpdates != null) {
			for (XMLTagValue tv : auditKeyNamesSetForUpdates) {
				logger.config("Found Audit Update KeyName Tag at " + tv.getPathToName() + " Value=" + tv.getTagValue());
			}
		}

		// Record Audit key Names, if exist
		if (auditKeyNamesSetForDeletes != null) {
			for (XMLTagValue tv : auditKeyNamesSetForDeletes) {
				logger.config("Found Audit Delete KeyName Tag at " + tv.getPathToName() + " Value=" + tv.getTagValue());
			}
		}

		// Record Audit key Names, if exist
		if (auditKeyNamesSetForSelects != null) {
			for (XMLTagValue tv : auditKeyNamesSetForSelects) {
				logger.config("Found Audit Select KeyName Tag at " + tv.getPathToName() + " Value=" + tv.getTagValue());
			}
		}

		logger.config("auditKeysSeparator is " + auditKeysSeparator);
	}

	private String settingsFileName = null;
	private boolean settingsFileSchemaValidation = false;

    private String jdbcClass = "oracle.jdbc.driver.OracleDriver";
    private String dbURL = null;
	private String userName = null;
	private String userPass = null;
	private boolean autoCommit = true;
    private String defaultTablename = null;
    private String dateFormat = null;				// the format for dates to be used in queries and updates, both in and out
    														// which will be more efficient if we're just performing the same insert all the time.
    private String updateLockingStrategy = null;	// Choose optimistic locking, pessimistic locking or none for updates.
    														// Actually only pessimistic requires an additional step. Optimistic will still call
    														// update(), but will supply the old values for the updating cols in the qualList parameter.

	private Vector<LwPreparedStatementTemplate> preparedStatementTemplates = new Vector<LwPreparedStatementTemplate>();
	private Vector<XMLTagValue> auditKeyNamesSetForInserts = null;
	private Vector<XMLTagValue> auditKeyNamesSetForUpdates = null;
	private Vector<XMLTagValue> auditKeyNamesSetForDeletes = null;
	private Vector<XMLTagValue> auditKeyNamesSetForSelects = null;
	private String auditKeysSeparator = null;			// if exists, will be used to separate values for concatenated audit keys
}