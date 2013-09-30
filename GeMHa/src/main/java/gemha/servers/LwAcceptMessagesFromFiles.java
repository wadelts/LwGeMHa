package gemha.servers;

import java.util.logging.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.*;

import lw.XML.*;
import lw.utils.LwFilenameFilter;
import lw.utils.LwLogger;
import lw.utils.LwSettingsException;
import gemha.support.LwMessagingException;
import gemha.interfaces.LwIAcceptMesssages;

/**
  * This class retrieves messages from files.
  *
  * @author Liam Wade
  * @version 1.0 30/10/2008
  */
public class LwAcceptMessagesFromFiles implements LwIAcceptMesssages {

    private static final Logger logger = Logger.getLogger("gemha");

	public LwAcceptMessagesFromFiles(String inputFileDir, String inputFileNameFilter, boolean sortOnFileName, ArrayList<String> colNameList,
										String dataFormat, String fieldSeparator, int maxRecsPerMessage, String XMLFormat,
										String actionOnError, String preparedStatementName, String immediateCommit, int numRecsToSkip)
																								throws LwSettingsException {
		this.inputFileDir = inputFileDir;
		this.inputFileNameFilter = inputFileNameFilter;
		this.sortOnFileName = sortOnFileName;
		this.colNameList = colNameList;
		this.dataFormat = dataFormat;
		if (fieldSeparator != null) {this.fieldSeparator = fieldSeparator;}
		this.maxRecsPerMessage = maxRecsPerMessage;
		this.XMLFormat = (XMLFormat == null ? "SELECT" : XMLFormat.toUpperCase());
		this.actionOnError = actionOnError;
		this.preparedStatementName = preparedStatementName;
		this.immediateCommit = immediateCommit;
		this.numRecsToSkip = numRecsToSkip;

		if (dataFormat == null) {
			throw new LwSettingsException("LwAcceptMessagesFromFiles.constructor(): dataFormat parameter is null.");
		}

		if (dataFormat.equals("CSV")) {
			if (colNameList == null) {
				throw new LwSettingsException("LwAcceptMessagesFromFiles.constructor(): colNameList parameter is null.");
			}
		}
	}

	/**
	  * Set the wait interval for accepting messages
	  *
	  * @param waitInterval how many milliseconds to wait for a message before returning nothing (0 = block indefinitely)
	  */
	public void setWaitInterval(int waitInterval) {
	}

	/**
	  * Set the wait interval setting to block forever
	  *
	  */
	public void setWaitIntervalBlockIndefinitely() {
	}

	/**
	  * Set up conditions for accepting messages
	  *
	  * @return true for success, false for failure
	  */
	public boolean performSetup()
								throws LwMessagingException {

		logger.finer("Starting Files-input setup now...");

		if (inputFileDir == null || inputFileNameFilter == null) {
			logger.severe("Could not perform setup. Missing File-selection information");
			return false;
		}

		logger.finest("Instructed to get input data from files.");
		File searchdir = new File(inputFileDir);
		LwFilenameFilter filter = new LwFilenameFilter(inputFileNameFilter);

		inputFileNames = searchdir.list(filter);

		if (inputFileNames == null || inputFileNames.length < 1) {
			logger.severe("No files matched the FileNameFilter pattern. Nothing to process.");
			throw new LwMessagingException("No files matched the FileNameFilter pattern. Nothing to process.");
		}
		else {
			if (sortOnFileName) { // sort the file names first, using the compareTo() method of String
				Arrays.sort(inputFileNames);
			}

			// Add the path to each file name...
			for (int i = 0; i < inputFileNames.length; i++) {
				inputFileNames[i] = inputFileDir + inputFileNames[i];
			}
		}

		logger.finer("Files-input setup was successful.");

		return true;
	}

	/**
	  * Process a message
	  *
	  * @return the next message retrieved
	  */
	public String acceptNextMessage()
									throws LwMessagingException {

		if (inputFileNames == null || fileNum > (inputFileNames.length-1)) { // then am finished file list
			return null;
		}

		String receivedMessage = null;

		try {
			if (dataFormat.equals("CSV")) { // then we're processing n number of records from file(s) for a given message
				receivedMessage = getNextMessage();
			}
			else { // each file is one message
				inputFileName = getNextFileName();
				if (inputFileName == null) {
					return null;
				}

				receivedMessage = getFileContents(inputFileName);
				logger.info("Message loaded from file " + inputFileName);
			}
		}
		catch(IOException e) {
			throw new LwMessagingException("IOException encountered while reading next message from File " + inputFileName + ": " + e);
		}
		catch(LwXMLException e) {
			throw new LwMessagingException("LwXMLException encountered while reading CSV messages from File " + inputFileName + ": " + e);
		}

		if (receivedMessage == null) { // then no records left - report
			if (inputFileNames != null) {
				logger.info(inputFileNames.length + " file(s) processed.");
				return null;
			}
		}

		return receivedMessage;
	}

	/**
	  * Do not consume the message
	  *
	  */
	public void stayMessage(String auditKey)
							throws LwMessagingException {
	}

	/**
	  * Consume the message now
	  *
	  */
	public void consumeMessage(String auditKey)
							throws LwMessagingException {
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		try {
			shutdownLogger.appendln("I0100 LwAcceptMessagesFromFiles.performCleanup(): Total of " + recsRead + " record(s) read from file(s)");
			shutdownLogger.appendln("I0101 LwAcceptMessagesFromFiles.performCleanup(): Total of " + recsReturnedForAllMessages + " record(s) loaded from file(s)");

			if (inputFileNames != null) {
				shutdownLogger.appendln("I0102 LwAcceptMessagesFromFiles.performCleanup(): " + inputFileNames.length + " file(s) processed.");
			}
		}
		catch(IOException e) {
			System.out.println("E0100 LwAcceptMessagesFromFiles.performCleanup(): could not write to shutDownLogFile.");
		}

		if (inputSource != null) {
			try { inputSource.close();} catch(IOException e) { /* do nothing */}
			inputSource = null;
		}
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	  * Fetch the entire contents of a text file, and return it in a String.
	  *
	  * @param fileName is the name of the file to read.
	  *
	  * @return the message from the file
	  */
	private String getFileContents(String fileName)
									throws IOException {
		// This is the important terminator check for CSV file processing!!!!
		if (fileName == null) {
			return null;
		}

		StringBuilder contents = new StringBuilder();

		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader inputSource =  new BufferedReader(new FileReader(fileName));
		try {
			String line = null; //not declared within while loop
			/*
			* readLine is a bit quirky :
			* it returns the content of a line MINUS the newline.
			* it returns null only for the END of the stream.
			* it returns an empty String if two newlines appear in a row.
			*/
			while (( line = inputSource.readLine()) != null) {
				contents.append(line);
				contents.append(System.getProperty("line.separator"));
			}
		}
		finally {
			inputSource.close();
		}

		return contents.toString();
	}


	/**
	  * Fetch the message for sending and return it in a String.
	  * It will contain maxRecsPerMessage records (which may originate from different files)
	  *
	  *
	  * @return the the next message built from CSV records
	  */
	private String getNextMessage()
								throws IOException, LwXMLException {

		// Start new doc
		recsReturnedForCurrentMessage = 0;
		feederXML = createFeederDoc();

		// Add records to the doc
		String nextRec = "";
		while (recsReturnedForCurrentMessage < maxRecsPerMessage && nextRec != null) {
			nextRec = getNextRec();
			logger.finer("Got record to process from file :" + nextRec);

			if (nextRec != null) {
				if (recsRead > numRecsToSkip) { // then have skipped first n recs (or wasn't due to skip any)
					recsReturnedForCurrentMessage++;
					addRecToResponse(feederXML, nextRec);
					logger.finer("Added record " + recsRead + " to Response from file " + inputFileName);
				}
				else {
					logger.finer("Skipped record " + recsRead + " from file " + inputFileName);
				}
			}
		}

		if (recsReturnedForCurrentMessage == 0) { // then no records were found - don't return the XML "shell"
			logger.info("Total of " + recsRead + " record(s) read from file(s)");
			logger.info("Total of " + recsReturnedForAllMessages + " record(s) loaded from file(s)");
			return null;
		}
		else {
			recsReturnedForAllMessages += recsReturnedForCurrentMessage;
			logger.info("Batch of " + recsReturnedForCurrentMessage + " record(s) loaded from file(s)");
			return feederXML.toString();
		}
	}

	/**
	  * Fetch the next row from the current file, and return it in a String.
	  * If we run out of records, the next file will be opened and tried.
	  *
	  * @return the next record from the file(s)
	  */
	private String getNextRec()
							throws IOException {

		// See if we've run out of records in a file (or are just beginning)
		// Open next file if needs be
		if (inputSource == null) { // open next file
			String nextFileName = getNextFileName();
			if (nextFileName == null) {
				return null;
			}
			else {
				//use buffering, reading one line at a time
				//FileReader always assumes default encoding is OK!
				inputSource =  new BufferedReader(new FileReader(nextFileName));
				inputFileName = nextFileName; // for logging purposes only
				logger.info("Opened file " + inputFileName + " for input.");
			}
		}

		String line = null; //not declared within while loop
		/*
		* readLine is a bit quirky :
		* it returns the content of a line MINUS the newline.
		* it returns null only for the END of the stream.
		* it returns an empty String if two newlines appear in a row.
		*/
		if (( line = inputSource.readLine()) == null) { // then no more data in this file
			inputSource.close();
			inputSource = null;
			return getNextRec(); // try for another file - will return null immediately if no more files
		}
		else {
			recsRead++;
			return line;
		}
	}

	/**
	  * Start a feeder doc
	  *
	  *
	  * @return the new Feeder XML doc
	  */
	private LwXMLDocument createFeederDoc()
								throws LwXMLException {

		LwXMLDocument newFeederDoc = null;

		try {
			if (XMLFormat != null && XMLFormat.equals("INSERT")) {
				actionObject = "DBACTION";
			}

			String newDoc = "<MESSAGE><" + actionObject + "></" + actionObject + "></MESSAGE>";
			newFeederDoc = LwXMLDocument.createDoc(newDoc, LwXMLDocument.SCHEMA_VALIDATION_OFF);

			// Go to DBACTION node and add ACTION_ON_ERROR value, if exists
			newFeederDoc.setCurrentNodeByPath("/MESSAGE/DBACTION", 1);
			if (XMLFormat != null && XMLFormat.equals("INSERT") && actionOnError != null) { // add actionOnError
				newFeederDoc.addElement(null, "ACTION_ON_ERROR", actionOnError);
			}
		}
		catch(LwXMLException e) {
			logger.severe("Caught LwXMLException creating a new XML doc for feeding: " + e.getMessage());
			throw new LwXMLException("LwProcessMessageForDb.buildErrorResponse(): Caught Exception creating a new XML doc for feeding: " + e.getMessage());
		}

		return newFeederDoc;
	}

	/**
	  * Add a row to the supplied XML doc
	  *
	  * @param responseXML the XML document to which a row should be added.
	  * @param row the data to be parsed and added to the doc.
	  */
	private void addRecToResponse(LwXMLDocument responseXML, String row)
															throws LwXMLException {

		// Defaults are for format of a SELECT statement...
		String action = "ROW";

		if (XMLFormat != null && XMLFormat.equals("INSERT")) {
			action = "INSERT";
		}

		// Go to DBACTION node
		responseXML.setCurrentNodeByPath("/MESSAGE/DBACTION", 1);

		// Create a new ROW or INSERT aggregate and set current node to that new node
		responseXML.setCurrentNode(responseXML.addElement(null, action, null));
		if (XMLFormat != null && XMLFormat.equals("INSERT") && preparedStatementName != null) { // add preparedStatementName
			responseXML.addElement(null, "PREPARED_STATEMENT_NAME", preparedStatementName);
		}
		if (XMLFormat != null && XMLFormat.equals("INSERT") && immediateCommit != null) { // add immediateCommit
			responseXML.addElement(null, "IMMEDIATE_COMMIT", immediateCommit);
		}


		// Create a new COLUMN aggregate under the latest ROW and set current node to that new node
		responseXML.setCurrentNode(responseXML.addElement(null, "COLUMNS", null));

		StringTokenizer rowData = new StringTokenizer(row, fieldSeparator);
		for (String colName : colNameList) {
			if (rowData.hasMoreTokens()) {
				String colData = rowData.nextToken();
				responseXML.addElement(null, colName, colData);
			}
		}
	}


	/**
	  * Get the next file name in the list
	  *
	  * @return the next file name
	  */
	private String getNextFileName() {
		if (inputFileNames == null || fileNum >= (inputFileNames.length-1)) { // then am finished file list
			return null;
		}
		else {
			return inputFileNames[++fileNum];
		}
	}

	private String inputFileDir = null;			// the directory in which candidate files reside
	private String inputFileNameFilter = null;	// how to identify files to be processed
	private boolean sortOnFileName = false;		// should the lsit of file anmes be sorted by filename?
	private String dataFormat = "XML";			// input messages data format - XML, TEXT, CSV
	private String fieldSeparator = "	";		// spearator for parsing fields within a CSV record (\t doesn't work here!!!)
	private String XMLFormat = null;			// signify the shape of XML to be created, for example possibly matching a database action (SELECT, INSERT)
	private String actionOnError = null;		// the action to be taken when the created INSERT fails - RESPOND or EXCEPTION
	private String preparedStatementName = null;// the name of a Prepared Statement to use with the created INSERT
	private String immediateCommit = null;		// the setting for whether the created action should be immediately committed
	private int numRecsToSkip = 0;				// number of records to skip during reading of files i.e ignore first n recs (when handling CSV file(s))

	// vars used when handling CSV file(s)
	private int maxRecsPerMessage = 1;				// number of records to add to a particular message, before starting a new message (when handling CSV file(s))
	private int recsReturnedForCurrentMessage = 0;	// number of records added to the current message being processed (when handling CSV file(s))
	private int recsReturnedForAllMessages = 0;		// number of records returned from all files being processed (when handling CSV file(s))
	private int recsRead = 0;						// number of records read so far from all files being processed (when handling CSV file(s))
	private LwXMLDocument feederXML = null;		// build XML message in this doc (when handling CSV file(s))
	private ArrayList<String> colNameList = null;	// the list of column names for naming XML tags (when handling CSV file(s))
	private BufferedReader inputSource = null;		// Used when we're reading single CSV records from file(s)

	private String[] inputFileNames = null;		// the list of files to process
	private String inputFileName = null;		// Holds name of file currently being processed (for XML/TEXT file)
	private int fileNum = -1;					// the next file name inputFileNames in the  array
	private String actionObject = "TABLE";		// highest =level object in response - could be TABLE or DBACTION
}
