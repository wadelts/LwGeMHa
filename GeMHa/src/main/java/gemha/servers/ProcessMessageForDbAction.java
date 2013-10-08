package gemha.servers;

import java.util.logging.*;
import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;

import lw.XML.*;
import lw.db.*;
import gemha.support.MessagingException;

/**
  * This class encapsulates the common processing of a database action - e.g. an insert, update, delete or select
  * 
  * @author Liam Wade
  * @version 1.0 26/11/2008
  */
public class ProcessMessageForDbAction {

	private String action = "insert";
	private XMLDocument inputDoc = null;
	private Vector<XMLTagValue> auditKeyNameSet = null; // holds just the audit key names
	private String auditKeysSeparator = null;			  // the char(s) to separate key values when concatenated

	private Vector<XMLTagValue> keySet = null; // holds both the audit key names and their found values
	private int numActions = 0;
	private String status = "new";		// new, built, failed/executed, committed
	private int errorCode = 0;
	private String errorText = null;
	private String auditKeyValues = null; // the audit key values as a concatenated string

	private String tableName = null;
	private String preparedStatementName = null;
	private Vector<XMLTagValue> actionColumns = null;
	private Vector<XMLTagValue> actionWhereColumns = null;
	private DbQueryResult queryResult = null;
	private boolean immediateCommit = false;


    private static final Logger logger = Logger.getLogger("gemha");
    
    /**
	  * Will create a new class for processing Db Actions
	  *
	  * @param logger the java log to which to send audit/info messages
	  * @param action the action to be performed against the database
	  * @param inputDoc the XML containing the database commands, set to the point where this action is defined
	  * @param auditKeyNameSet the set of key names to get the key values identifing this action, for auditing purposes
	  * @param auditKeysSeparator the char(s) to separate key values when concatenated
	  */
	public ProcessMessageForDbAction(String action, XMLDocument inputDoc, Vector<XMLTagValue> auditKeyNameSet, String auditKeysSeparator) {
		this.action = action.toUpperCase();
		this.inputDoc = inputDoc;
		this.auditKeyNameSet = auditKeyNameSet;
		this.auditKeysSeparator = auditKeysSeparator;
	}

	/**
	  * Gather the data needed to perform the action...
	  *
	  * @param defaultTableName the database table name to use, if not supplied in XML message
	  */
	public void buildAction(String defaultTableName)
						throws MessagingException {
		logger.fine("Building " + action + " DB action...");

		/////////////////////////////////////////////////////////////////////////////////
		// Establish commit strategy...
		/////////////////////////////////////////////////////////////////////////////////
		if ( ! action.equals("SELECT")) {
			// Check if this action should be committed immediately, so won't be rolled back later, if an error occurs with another action
			// Remember that committing commits ALL previously uncommitted transactions - not just the current one!
			String strImmediateCommit = inputDoc.getValueForTag("IMMEDIATE_COMMIT"); // YES/NO
			immediateCommit = (strImmediateCommit == null ? false : strImmediateCommit.toUpperCase().equals("YES")) ;
		}

		auditKeyValues = getConcatenatedAuditKeyValues(inputDoc, auditKeyNameSet, auditKeysSeparator); // works at "current node" level
		keySet = getAuditKeyValues(inputDoc, auditKeyNameSet); // works at "current node" level

		/////////////////////////////////////////////////////////////////////////////////
		// Get the table name, or use default
		/////////////////////////////////////////////////////////////////////////////////
		tableName = inputDoc.getValueForTag("TABLENAME");
		if (tableName == null) {
			tableName = defaultTableName;
		}

		/////////////////////////////////////////////////////////////////////////////////
		// If still no table name found, look for a prepared statement
		/////////////////////////////////////////////////////////////////////////////////
		preparedStatementName = inputDoc.getValueForTag("PREPARED_STATEMENT_NAME");

		if (tableName == null && preparedStatementName == null) { // big problem, need one of these
			logger.severe("No TABLENAME or PreparedStatementName supplied for " + action + " with AuditKey " + auditKeyValues + " (and no default tablename). See next line for input XML...");
			logger.severe("InputMessage was :" + inputDoc.toString());
			throw new MessagingException("No TABLENAME or PreparedStatementName supplied for " + action + " with AuditKey " + auditKeyValues + ".");
		}

		/////////////////////////////////////////////////////////////////////////////////
		// Get the column names and values to be inserted/updated/selected
		/////////////////////////////////////////////////////////////////////////////////
		if (inputDoc.setCurrentNodeByPath("COLUMNS", 1)) { // should fail for delete
			actionColumns = inputDoc.getValuesForTagsChildren();
			inputDoc.restoreCurrentNode();
		}

		/////////////////////////////////////////////////////////////////////////////////
		// Get the column names and values to be involved in SQL WHERE clause
		/////////////////////////////////////////////////////////////////////////////////
		if (inputDoc.setCurrentNodeByPath("WHERE", 1)) { // should fail for insert
			actionWhereColumns = inputDoc.getValuesForTagsChildren();
			inputDoc.restoreCurrentNode();
		}

		status = "built";
	}

	/**
	  * Perform the action...
	  *
	  * @param dbConn the open database connection
	  * @param actionOnError describes what to do when an error is encountered - respond or exception out
	  */
	public void performAction(DbConnection dbConn, String actionOnError)
						throws MessagingException {

		try {
			if (action.equals("INSERT")) {
				numActions = performActionInsert(dbConn, tableName, preparedStatementName, actionColumns);
			}
			else if (action.equals("UPDATE")) {
				numActions = performActionUpdate(dbConn, tableName, preparedStatementName, actionColumns, actionWhereColumns);
			}
			else if (action.equals("DELETE")) {
				numActions = performActionDelete(dbConn, tableName, preparedStatementName, actionWhereColumns);
			}
			else if (action.equals("SELECT")) {
				queryResult = null;
				queryResult = performActionSelect(dbConn, tableName, preparedStatementName, actionColumns, actionWhereColumns);
				numActions = queryResult.getResult().size();
			}

			status = "executed";

			logger.info(numActions + " " + action + "(s) performed for AuditKey " + auditKeyValues + " (not yet committed).");

			if (immediateCommit) { // try to commit this transaction NOW
				try {
					dbConn.sessionCommit();
					status = "committed";
					logger.info("Immediately Committed transaction for AuditKey " + auditKeyValues + " (immediateCommit was true).");
				}
				catch(DbException e) {
					throw new MessagingException("Caught LwDbException trying to IMMEDIATELY commit a transaction: " + e.getMessage());
				}
			}
		}
		catch (DbException e) {
			status = "failed";
			errorCode = e.getErrorCode();
			errorText = e.getMessage();

			if (actionOnError.toUpperCase().equals("RESPOND")) {
				logger.warning("Caught LwDbException exception (will respond with error) (AuditKey " + auditKeyValues + "): " + e.getMessage());
			}
			else { // assume EXCEPTION
				logger.severe("Caught LwDbException exception (will throw LwMessagingException) (AuditKey " + auditKeyValues + "): " + e.getMessage());
				throw new MessagingException("Caught LwDbException (AuditKey " + auditKeyValues + "): " + e.getMessage());
			}
		}
	}

	/**
	  * Add an aggregate to the response that will describe a successful action.
	  *
	  * @param response the complete set of responses (from all actions) to which we should add this result
	  *
	  */
	public void addResultToResponse(XMLDocument response)
											throws MessagingException {

		//////////////////////////////////////////////////////////////////////////
		// Add the success/error details to the message...
		// Not checking for problems here (things are really in bits if cannot add
		// a couple of tags).
		//////////////////////////////////////////////////////////////////////////
		response.setCurrentNodeToFirstElement();

		// First find out how many of these action aggregates we already have
		int icurrentOccurrences = response.numElements("/MESSAGE/DBACTION/" + action);

		// Now add the new one
		response.setCurrentNodeByPath("/MESSAGE/DBACTION", 1);
		response.addElement(null, action, null);

		// Now go down to the new one...
		response.setCurrentNodeByPath("/MESSAGE/DBACTION/" + action, icurrentOccurrences+1);

		//////////////////////////////////////////////////////////////////////////
		// Add the details to the message...
		//////////////////////////////////////////////////////////////////////////
		if (preparedStatementName != null) {
			response.addElement(null, "PREPARED_STATEMENT_NAME", preparedStatementName);
		}
		else if (tableName != null) {
			response.addElement(null, "TABLENAME", tableName);
		}

		//////////////////////////////////////////////////////////////////////////
		// Add key columns and their values for reference to the message...
		//////////////////////////////////////////////////////////////////////////
		String keyColsElementName = null;
		if (action.equals("INSERT")) {
			keyColsElementName = "COLUMNS";
		} else {
			keyColsElementName = "WHERE";
		}
		if ( keySet != null) {
			for (XMLTagValue tv : keySet) {
				String val = tv.getTagValue();
				if (val != null) { // add it
					response.addElement(null, keyColsElementName + "/" + tv.getTagName(), val);
				}
			}
		}

		response.addElement(null, "STATUS", status.toUpperCase());
		response.addElement(null, "NUM_SUCCESSFUL", String.valueOf(numActions));

		//////////////////////////////////////////////////////////////////////////
		// If the action failed, present details, otherwise
		// Add to response any rows found for a SELECT...
		//////////////////////////////////////////////////////////////////////////
		if (isFailed()) {
			response.addElement(null, "ERROR_CODE", String.valueOf(errorCode));
			response.addElement(null, "ERROR_TEXT", String.valueOf(errorText));
		}
		else if (action.equals("SELECT") && queryResult.getResult() != null && queryResult.getResult().size() > 0) {
			addSelectResultRowsToResponse(response);
		}

		queryResult = null; // clear any query result immediately - free up resources
	}

	/**
	 * Add to response any rows found for a SELECT
	 * 
	 * @param response the complete set of responses (from all actions) to which we should add this result
	 * 
	 * @throws MessagingException if cannot create XML document from SQL Query result
	 */
	private void addSelectResultRowsToResponse(XMLDocument response) throws MessagingException {
		// Now create a TABLE aggregate to contain the results...
		response.addElement(null, "TABLE", null);
		response.setCurrentNodeByPath("TABLE", 1); // go to TABLE aggregate

		// Add the rows and columns...
		int numRows = 0;
		for (Properties row : queryResult.getResult()) {
			response.addElement(null, "ROW", null);
			response.setCurrentNodeByPath("ROW", ++numRows); // go to last-created row
			for (Object col : row.keySet()) {
				if (queryResult.getReturnType().equals("COLUMNS")) {
					response.addElement(null, ((String)col), row.getProperty(((String)col)));
				}
				else if (queryResult.getReturnType().equals("XML")) { // create a new Doc and add it to response Doc
					try {
						XMLDocument resultDoc = null;
						resultDoc = XMLDocument.createDoc(row.getProperty(((String)col)), XMLDocument.SCHEMA_VALIDATION_OFF);
						response.importNode(resultDoc.getCurrentNode(), true);
					}
					catch(XMLException e2) {
						throw new MessagingException("Caught LwXMLException creating a new doc from SQL Query result: " + e2.getMessage());
					}
				}
			}

			response.setCurrentNodeToParentOfCurrent(); // // go back to TABLE aggregate
		}
	}

	/**
	  * Get the action type for this action
	  *
	  * @return the action type for this action - insert, update or delete
	  */
	public String getAction() {
			return action;
	}

	/**
	  * Get the audit keySet for this action (set of tag/value pairs)
	  *
	  * @return a Vector of the keySet for this action (set of tag/value pairs)
	  */
	public Vector<XMLTagValue> getKeySet() {
			return keySet;
	}

	/**
	  * Get the status type for this action
	  *
	  * @return the status type for this action - insert, update or delete
	  */
	public String getStatus() {
			return status;
	}

	/**
	  * Determine if the action failed
	  *
	  * @return true if the action to the database failed
	  */
	public boolean isFailed() {
			return status.equals("failed");
	}

	/**
	  * Determine if the action has executed successfully (but was not yet committed)
	  *
	  * @return true if the action to the database executed correctly
	  */
	public boolean isExecuted() {
			return status.equals("executed");
	}

	/**
	  * Determine if the action has been committed
	  *
	  * @return true if the action has been committed to the database
	  */
	public boolean isCommitted() {
			return status.equals("committed");
	}

	/**
	  * Get the number of database actions for this action type
	  *
	  * @return the number of database actions for this action type
	  */
	public int getNumActions() {
			return numActions;
	}

	/**
	  * Get the errorCode for a failure
	  *
	  * @return the errorCode for a failure
	  */
	public int getErrorCode() {
			return errorCode;
	}

	/**
	  * Get the errorText for a failure
	  *
	  * @return the errorText for a failure
	  */
	public String getErrorText() {
			return errorText;
	}

	/**
	  * Set status of action to committed
	  *
	  */
	public void setCommitted() {
		this.status = "committed";
	}

	/**
	  * Set status of action to committed
	  *
	  */
	public void markExecutedAsCommitted() {
		if (status.equals("executed") && numActions > 0 && ! action.equals("SELECT")) {
			status = "committed";
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
	private String getConcatenatedAuditKeyValues(XMLDocument doc, Vector<XMLTagValue> auditKeyNamesSet, String separator) {

		String concatenatedValues = "";

		// Using Enumeration instead of for-each loop, so could use hasMoreElements() when adding separator
		if (auditKeyNamesSet != null) {
			Enumeration<XMLTagValue> enumAuditKeyNames = auditKeyNamesSet.elements();
			while (enumAuditKeyNames.hasMoreElements()) {
				XMLTagValue tv = enumAuditKeyNames.nextElement();

				String nextValue = doc.getValueForTag(tv.getTagValue());

				if (nextValue != null) {
					if ("".equals(concatenatedValues)) {
						concatenatedValues = nextValue;
					} else {
						concatenatedValues += ((separator != null && enumAuditKeyNames.hasMoreElements()) ? separator : "") + nextValue;
					}
				}
			}
		}

		return concatenatedValues;
	}

	/**
	  * Get the values for the given audit key names and return all in a new set
	  *
	  * @param doc the XML document
	  * @param auditKeyNamesSet the set of column names to identify the audit key values
	  *
	  *@return a new Vector, containing both the key names and thlw values for this action
	  */
	public Vector<XMLTagValue> getAuditKeyValues(XMLDocument doc, Vector<XMLTagValue> auditKeyNamesSet) {

		Vector<XMLTagValue> keyNameValuePairs = new Vector<XMLTagValue>(auditKeyNamesSet.size());

		if (auditKeyNamesSet != null) { // add key columns and thlw values for reference

			for (XMLTagValue tv : auditKeyNamesSet) {
				String val = doc.getValueForTag(tv.getTagValue());
				if (val != null) { // add it
					keyNameValuePairs.addElement(new XMLTagValue(tv.getTagValue(), val));
				}
			}
		}

		return keyNameValuePairs;
	}

	/**
	  * Perform an insert against the database.
	  *
	  * @param dbConn the open database connection
	  * @param tableName the table into which to insert the row
	  * @param preparedStatementName the Prepared Statement to execute instead of building a separate query
	  * @param insertCols the columns of data for inserting
	  *
	  * @return 0 for success, <0 for error
	  */
	private int performActionInsert(DbConnection dbConn, String tableName, String preparedStatementName, Vector<XMLTagValue> insertCols)
																	throws DbException {

		logger.finer("Going to insert database row...");

		if (preparedStatementName == null) { // just do standalone insert
			return dbConn.insert(tableName, convertTVsToProperties(insertCols));
		}
		else  {
			// Note: Statement may have been prepared by a prior request, or from the settings file.
			// prepareInsert() will just return immediately if preparedStatementName already exists
			dbConn.prepareInsert(preparedStatementName, tableName, convertTVsToPropertiesAndMarkAllAsParams(insertCols));

			return dbConn.executePreparedStatement(preparedStatementName, convertTVsToProperties(insertCols));
		}
	}

	/**
	  * Perform an update against the database.
	  *
	  * @param dbConn the open database connection
	  * @param tableName the table into which to insert the row
	  * @param preparedStatementName the Prepared Statement to execute instead of building a separate query
	  * @param updateCols the columns of data being updated
	  * @param whereCols the columns to be ANDed for the WHERE clause
	  *
	  * @return 0 for success, <0 for error, n for number of rows updated
	  */
	private int performActionUpdate(DbConnection dbConn, String tableName, String preparedStatementName, Vector<XMLTagValue> updateCols, Vector<XMLTagValue> whereCols)
																	throws DbException {

		logger.finer("Going to perform database update...");

		if (preparedStatementName == null) { // just do standalone update
			return dbConn.update(tableName, convertTVsToProperties(updateCols), convertTVsToProperties(whereCols));
		}
		else  {
			// Note: For Update Prepared Statements, expect it to have been prepared from the settings file.
			// Haven't yet implemented dynamically created Prepared Updates

			return dbConn.executePreparedStatement(preparedStatementName, convertTVsToProperties(updateCols));
		}
	}

	/**
	  * Perform a delete against the database.
	  *
	  * @param dbConn the open database connection
	  * @param tableName the table into which to insert the row
	  * @param preparedStatementName the Prepared Statement to execute instead of building a separate query
	  * @param whereCols the columns to be ANDed for the WHERE clause
	  *
	  * @return 0 for success, <0 for error, n for number of rows removed
	  */
	private int performActionDelete(DbConnection dbConn, String tableName, String preparedStatementName, Vector<XMLTagValue> whereCols)
																	throws DbException {

		logger.finer("Going to perform database delete...");

		if (preparedStatementName == null) { // just do standalone update
			return dbConn.delete(tableName, convertTVsToProperties(whereCols));
		}
		else  {
			// Note: For Delete Prepared Statements, expect it to have been prepared from the settings file.
			// Haven't yet implemented dynamically created Prepared Deletes

			return dbConn.executePreparedStatement(preparedStatementName, convertTVsToProperties(whereCols));
		}
	}

	/**
	  * Perform a select against the database.
	  *
	  * @param dbConn the open database connection
	  * @param tableName the table from which to select rows
	  * @param preparedStatementName the Prepared Statement to execute instead of building a separate query
	  * @param updateCols the columns of data being returned
	  * @param whereCols the columns to be ANDed for the WHERE clause
	  *
	  * @return a LwDbQueryResult object containing the results
	  */
	private DbQueryResult performActionSelect(DbConnection dbConn, String tableName, String preparedStatementName, Vector<XMLTagValue> updateCols, Vector<XMLTagValue> whereCols)
																	throws DbException {

		logger.finer("Going to perform database select...");

		if (preparedStatementName == null) { // just do standalone update
			return dbConn.getQueryResults(tableName, convertTVsToProperties(updateCols), convertTVsToProperties(whereCols));
		}
		else  {
			// Note: For Update Prepared Statements, expect it to have been prepared from the settings file.
			// Haven't yet implemented dynamically created Prepared Updates
			return dbConn.executePreparedQuery(preparedStatementName, convertTVsToProperties(updateCols));
		}
	}

	/**
	  * Copy tv pairs to a Properties structure
	  *
	  * @param tvPairs a vector of tv pairs
	  *
	  * @return the tv pairs in a Properties object
	  */
	private Properties convertTVsToProperties(Vector<XMLTagValue> tvPairs) {
		Properties p = new Properties();

		if (tvPairs == null) {
			return p;
		}

		for (XMLTagValue tv : tvPairs) {
			p.put(tv.getTagName(), tv.getTagValue());
		}

		return p;
	}

	/**
	  * Copy tv pairs to a Properties structure, replacing all values with a ? (for preparing preparedStatements)
	  *
	  * @param tvPairs a vector of tv pairs
	  *
	  * @return the tv pairs in a Properties object
	  */
	private Properties convertTVsToPropertiesAndMarkAllAsParams(Vector<XMLTagValue> tvPairs) {
		Properties p = new Properties();

		if (tvPairs == null) {
			return p;
		}

		for (XMLTagValue tv : tvPairs) {
			p.put(tv.getTagName(), "?");
		}

		return p;
	}
}