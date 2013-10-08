package gemha.servers;

import java.io.*;
import java.net.*;
import java.util.logging.*;
import java.util.*;

import lw.XML.*;
import lw.utils.*;
import gemha.support.ProcessMessageForSocketSettings;
import gemha.support.MessagingException;
import gemha.interfaces.IProcessMesssage_old;

/**
  * This class sends messages over a socket and returns responses, if required.
  *
  * @author Liam Wade
  * @version 1.0 10/12/2008
  */
public class LwProcessMessageForSocket_Old implements IProcessMesssage_old {

    private static final Logger logger = Logger.getLogger("gemha");

	public LwProcessMessageForSocket_Old() {
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
	public void performSetup(String settingsFileName) throws SettingsException {

		if (settingsFileName == null) {
			throw new SettingsException("Parameter settingsFileName was null.");
		}

		settings = new ProcessMessageForSocketSettings(settingsFileName, XMLDocument.SCHEMA_VALIDATION_ON);

		try {
			openSocket(settings.getHostName(), settings.getPortNumber());
		}
		catch (MessagingException e) {
			logger.severe("LwMessagingException: " + e.getMessage());
			throw new SettingsException("Caught LwMessagingException trying to open a new socket : " + e.getMessage());
		}
	}

	/**
	  * Process a message
	  *
	  * @param messageText the message to be processed
	  * @return 0 for success with no response necessary, 1 for success and response is ready, less than zero for error
	  */
	public int processMessage(String messageText)
											throws MessagingException {

		if (messageText == null) {
			return -1;
		}

		if (s == null) { // then socket was closed, re-open it
			openSocket(settings.getHostName(), settings.getPortNumber());
		}

		//////////////////////////////////////////////////////////////////
		// Set up a new XML doc
		//////////////////////////////////////////////////////////////////
		XMLDocument newDoc = new XMLDocument();
		try {
			newDoc.createDoc(messageText, XMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(XMLException e) {
			logger.severe("LwXMLException: " + e.getMessage());
			logger.warning("InputMessage was :" + messageText);
			throw new MessagingException("Could not create new XML document: " + e.getMessage());
		}

		///////////////////////////////////////////////
		// Get audit information from message (or make it up)...
		///////////////////////////////////////////////
		auditKeyValues = getConcatenatedAuditKeyValues(newDoc, settings.getAuditKeyNamesSet(), settings.getAuditKeysSeparator()); // works at "current node" level

		///////////////////////////////////////////////
		// Send the data (in chunks, if necessary)...
		///////////////////////////////////////////////
		StringBuffer wholeMessage = new StringBuffer(messageText);
		int serverRespCode = 0;
		while (serverRespCode == 0 && wholeMessage.length() > 0) {
			// synchronous comms means an XML response message is expected immediately, so will await it below
			// asynchronous comms means an XML response message is NOT expected
			if (wholeMessage.length() <= MAX_DATA_SIZE) {
				serverRespCode = sendMessagePart((settings.getApplicationLevelResponse().equals("synchronous") ? 2 : 0), auditKeyValues, wholeMessage.toString());
				wholeMessage.delete(0, MAX_DATA_SIZE);
			}
			else {
				serverRespCode = sendMessagePart(1, auditKeyValues, wholeMessage.substring(0, MAX_DATA_SIZE));
				wholeMessage.delete(0, MAX_DATA_SIZE);
			}
		}


		// Throw exception if a technical error was encountered
		if (serverRespCode != 0) {
			logger.severe("Socket Server returned error " + serverRespCode + " when trying to send data " + auditKeyValues + ".");
			throw new MessagingException("Socket Server returned error " + serverRespCode + " when trying to send data for message " + auditKeyValues + ".");
		}


		///////////////////////////////////////////////
		// If got here, message was successfully transmitted.
		// So build Response...
		///////////////////////////////////////////////
		createResponseDoc(); // shell
		response.addElement(null, "SEND_STATUS", "SUCCESS");

		// Check if we should await an application-level response
		if (settings.getApplicationLevelResponse().equals("synchronous")) {
			XMLDocument applicResponse = getApplicationResponse();
			response.importNode(applicResponse.getCurrentNode(), true);
		}

		return 1;
	}

	/**
	  * Return the response message
	  *
	  * @return the response, null if none exists
	  */
	public String getResponse() {
		XMLDocument tempResponse = response;
		response = null;
		return tempResponse.toString();
	}

	/**
	  * Do whatever you like when things are quiet
	  *
	  */
	public void goQuiet() {
		logger.info("All quiet, going to close socket connection.");
		///////////////////////////////////////////////
		// Close connetion to Server.
		///////////////////////////////////////////////
		Integer errNo   = new Integer(0);
		Integer service = new Integer(SERV_CLOSE);
		Integer object  = new Integer(0);
		Integer datalen  = new Integer(13);
		sendMsg(os, errNo, service, object, datalen, "Close Request" );

		try {
			s.close();
		}
		catch (IOException e) {
			logger.severe("Caught IOException trying to close socket connection (no action taken): " + e.getMessage());
		}

		s = null;
	}

	/**
	  * Perform any clean-up actions before closing down
	  *
	  */
	public void performCleanup(LwLogger shutdownLogger) {
		if (s != null) {
			///////////////////////////////////////////////
			// Close connetion to Server.
			///////////////////////////////////////////////
			Integer errNo   = new Integer(0);
//			Integer service = new Integer(SERV_CLOSE);
			Integer service = new Integer(SERV_SHUTDOWN);
			Integer object  = new Integer(0);
			Integer datalen  = new Integer(13);
			sendMsg(os, errNo, service, object, datalen, "Shutdown Request" );

			try {
				s.close();
			}
			catch (IOException e) {
				if (shutdownLogger != null) {
					try { shutdownLogger.appendln("Caught IOException trying to close socket connection (no action taken): " + e.getMessage());} catch (IOException e2) { /* do nothing */}
				}
				else {
					logger.severe("Caught IOException trying to close socket connection (no action taken): " + e.getMessage());
				}
			}

			s = null;

			if (shutdownLogger != null) {
				try { shutdownLogger.appendln("Closed socket connection.");} catch (IOException e) { /* do nothing */}
			}
			else {
				logger.info("Closed socket connection.");
			}
		}
	}

	//////////////////////////////////////////////////////////////////
	// End: Implementation methods for LwIProcessMesssage Interface...
	//////////////////////////////////////////////////////////////////

	/**
	  * Open a socket for communications
	  *
	  */
	private void openSocket(String hostName, int portNo)
						throws MessagingException {
		///////////////////////////////////////////////
		// Connect to the socket on "this" machine.
		///////////////////////////////////////////////
		try {
			s = new Socket(hostName, portNo);
		}
		catch (UnknownHostException e) {
			logger.severe("UnknownHostException: " + e.getMessage());
			throw new MessagingException("Caught UnknownHostException trying to open a new socket : " + e.getMessage());
		}
		catch (IOException e) {
			logger.severe("IOException: " + e.getMessage());
			throw new MessagingException("Caught IOException trying to open a new socket : " + e.getMessage());
		}

		try {
			// Open an input stream on the new socket.
			is = s.getInputStream();
		}
		catch (IOException e) {
			logger.severe("IOException: " + e.getMessage());
			throw new MessagingException("Caught IOException trying to open an input stream on new socket : " + e.getMessage());
		}

		try {
			// Open an output stream on the new socket.
			os = s.getOutputStream();
		}
		catch (IOException e) {
			logger.severe("IOException: " + e.getMessage());
			throw new MessagingException("Caught IOException trying to open an output stream on new socket : " + e.getMessage());
		}

		///////////////////////////////////////////////
		// Read Server Ready message.
		///////////////////////////////////////////////
		String str = null;
		// Read the Server-ready message.
		str = readMsg();

		int serv = atoi(removeZeroes(str.substring(4, 7)));

		if (serv != SERV_READY) { // then technical problem
			logger.severe("Socket Server returned error " + serv + " when trying to start conversation.");
			throw new MessagingException("Socket Server returned error " + serv + " when trying to start conversation.");
		}

		logger.info("Socket opened on port " + portNo + " on host " + hostName);
	}


	/**
	  * Send some data over the socket.
	  * @param instrCode instruction for the consumer : 0: No more data for this Message; 1: more data to come for this Message; 2: No more data for this Message AND I expect a message response; -1: discard all data for this Message
	  * @param TID the Unique Transaction ID for this message
	  * @param dataPart the data to be sent
	  *
	  * @return response code - 0 => server carried out command successfully, otherwise an error was encountered
	  */
	private int sendMessagePart(int instrCode, String TID, String dataPart) {
		// Make sure TID only 255 chars...
		if (TID.length() > 255) {
			TID = TID.substring(0, 255);
		}

		///////////////////////////////////////////////
		// Send XML message.
		///////////////////////////////////////////////
		Integer errNo   = new Integer(0);
		Integer service   = null;

		logger.fine("Socket Client instruction is " + instrCode);

		switch (instrCode) {
			case 0  : service = new Integer(SERV_CONSUME);
					  break;
			case 1  : service = new Integer(SERV_MORE);
					  break;
			case 2  : service = new Integer(SERV_CONSUME_RESPOND);
					  break;
			case -1 : service = new Integer(SERV_DISCARD);
					  break;
		}
		Integer object  = new Integer(OBJ_XML);

		Integer datalen  = new Integer(255 + dataPart.length());
		sendMsg(os, errNo, service, object, datalen, padSpace(TID, 255) + dataPart );
		logger.info("Socket Client sent data for message " + TID + (instrCode == 0 || instrCode == 2 ? ". Final chunk." : (instrCode == 1 ? ". More to follow." : ". To discard chunks already sent.")));

		///////////////////////////////////////////////
		// Read Server Response
		///////////////////////////////////////////////
		String str = readMsg();
		return atoi(removeZeroes(str.substring(0, 3)));

	}

	/**
	  * Convert an Integer object to a String and pad with leading zeroes, up to a size of 3 digits.
	  * @param i the Integer to be converted/padded
	  * @return a String containing the padded number.
	  */
	private String padZero(Integer i)
	// This method pads a 3-digit string number with leading zeros.
	{
		String s = i.toString();

		while (s.length() < 3)
			s = "0" + s;

		return s;
	}

	/**
	  * Convert an Integer object to a String and pad with leading zeroes, up to a given size.
	  * @param i the Integer to be converted/padded
	  * @param size the size of the new String, filled with this padding.
	  * @return a String containing the padded number.
	  */
	private String padZero(Integer i, int size)
	// This method pads an Integer with leading zeros, up to the specified size.
	{
		String s = i.toString();

		while (s.length() < size)
			s = "0" + s;

		return s;
	}

	/**
	  * Remove leading zeroes from the given string.
	  * @param s the String to be amended
	  * @return a String with leading zeroes removed
	  */
	private String removeZeroes(String s)
	// This method removes leading zeros. Needed because bug in Format.atoi converts 018 to 1!!!
	{

		while ( s.length() > 0 && s.charAt(0) == '0')
			s = s.substring(1);

		return s;
	}

	/**
	  * Pad a String with trailing blanks, up to a given size.
	  * @param s the String to be padded
	  * @param size the size to which the String should be increased, with this padding.
	  * @return a String containing the padding.
	  */
	private String padSpace(String s, int size)
	// This method pads a string number with leading zeros, up to the specified size.
	{
		while (s.length() < size)
			s = s + " ";

		return s;
	}

	/**
	  * nullChar-fill the string to MESSAGE_SIZE characters.
	  * @param s the String to be filled
	  * @return a String containing the filled nulls.
	  */
	private String fillString(String str)
	// This method nullChar-fills the string to MESSAGE_SIZE characters.
	{
		int len = MESSAGE_SIZE-str.length();
		for (int i = len; i > 0 ; i--)
			str = str + '\0';

		return str;
	}

	/**
	  * Read a message from the socket server.
	  * @return a String containing the response from the server.
	  */
	private String readMsg()
	// Get a response from the Scheduler server socket.
	{
		byte[] response = new byte[MESSAGE_SIZE];
		int numBytesTransferred = 0;     // Bytes received so far.
		int numTries = 0;

		///////////////////////////////////////////////
		// Need while because read can be interrupted before getting any/all data.
		// Combination of numTries<6 and socket timeout of 10 seconds will mean try for one minute
		///////////////////////////////////////////////
		while (numBytesTransferred < response.length && numTries < 6) {
			try {
				numBytesTransferred += is.read(response, numBytesTransferred, (response.length - numBytesTransferred));
				numTries++; // Need this for when server severs connection - would keep reading no bytes.
			}
			catch(InterruptedIOException e) {
				numTries++;
				numBytesTransferred += e.bytesTransferred;
				if (numBytesTransferred >= response.length)
					return new String(response);
			}
			catch(IOException e) {
				return "";
			}
		}

		///////////////////////////////////////////////
		// For when connection is severed by server.
		///////////////////////////////////////////////
		if (numBytesTransferred <= 0)
			return new String("999_999_999_00027_Connection no longer valid.");

		return new String(response);
	}

	/**
	  * Send a message to an output stream.
	  * @param os the output stream to which to send the message
	  * @param errNo the error code to be transmitted to the server
	  * @param service the the service required of the server
	  * @param object the the server object on which the server should perform this service
	  * @param datalen the number of characters (bytes) being sent in the data parameter
	  * @param data any data needing to be sent to the server in support of this service request
	  */
	private void sendMsg(OutputStream os, Integer errNo, Integer service, Integer object, Integer datalen, String data)
	{
   	  try{
         ///////////////////////////////////////////////
         // Send a request to the Scheduler server socket.
         ///////////////////////////////////////////////
         data = padZero(errNo) + "_" + padZero(service) + "_" +
                padZero(object) + "_" + padZero(datalen, 5) + "_" + data;

         data = fillString(data);

		 byte[] rawData = data.getBytes();

         os.write(rawData);
         os.flush();
	  }
      catch(IOException e)
      { // System.out.println("Scheduler: Send Error: " + e);
      }
	}

   private int atoi(String s)
   {  return (int)atol(s);
   }

  /**
  * Converts a string of digits (decimal, octal or hex) to a long integer
  * @param s a string
  * @return the numeric value of the prefix of s representing a base 10 integer
  */
   private long atol(String s)
   {  int i = 0;

      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
      if (i < s.length() && s.charAt(i) == '0')
      {  if (i + 1 < s.length() && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X'))
            return parseLong(s.substring(i + 2), 16);
         else return parseLong(s, 8);
      }
      else return parseLong(s, 10);
   }

   private long parseLong(String s, int base)
   {  int i = 0;
      int sign = 1;
      long r = 0;

      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
      if (i < s.length() && s.charAt(i) == '-') { sign = -1; i++; }
      else if (i < s.length() && s.charAt(i) == '+') { i++; }
      while (i < s.length())
      {  char ch = s.charAt(i);
         if ('0' <= ch && ch < '0' + base)
            r = r * base + ch - '0';
         else if ('A' <= ch && ch < 'A' + base - 10)
            r = r * base + ch - 'A' + 10 ;
         else if ('a' <= ch && ch < 'a' + base - 10)
            r = r * base + ch - 'a' + 10 ;
         else
            return r * sign;
         i++;
      }
      return r * sign;
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
		if (auditKeyNamesSet == null) { // than make up new key...
			return String.valueOf(++fallBackTransactionID);
		}
		else {
			Enumeration<XMLTagValue> enumAuditKeyNames = auditKeyNamesSet.elements();
			while (enumAuditKeyNames.hasMoreElements()) {
				XMLTagValue tv = enumAuditKeyNames.nextElement();

				String nextValue = doc.getValueForTag(tv.getTagValue());

				if (nextValue != null) {
					concatenatedValues += nextValue + ((separator != null && enumAuditKeyNames.hasMoreElements()) ? separator : "");
				}
			}
		}

		return concatenatedValues;
	}

	/**
	  * Start a response doc
	  *
	  */
	private void createResponseDoc()
								throws MessagingException {

		response = new XMLDocument();

		try {
			response.createDoc("<SOCKET_REQUEST></SOCKET_REQUEST>", XMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(XMLException e) {
			logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
			throw new MessagingException("LwProcessMessageForDb.buildErrorResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
		}
	}

	/**
	  * Start a response doc
	  *
	  * @return an XML document containing the response from the server application
	  */
	private XMLDocument getApplicationResponse()
								throws MessagingException {

		// Read the Server-ready message.
		StringBuilder wholeMessage = new StringBuilder(MESSAGE_SIZE);

		String str = readMsg();

		int serv = atoi(removeZeroes(str.substring(4, 7)));
		logger.fine("Received message: " + str);

		while (serv == SERV_MORE) { // keep getting more...
			wholeMessage.append(str.substring(17 + 255 - 1).trim());

			str = readMsg();
			serv = atoi(removeZeroes(str.substring(4, 7)));
		}

		if (serv == SERV_CONSUME) { // ...then finished
			wholeMessage.append(str.substring(17 + 255 - 1).trim());
		}

		if (wholeMessage.length() <= 0) {
			logger.severe("Application-level response not received from server");
			throw new MessagingException("LwProcessMessageForSocket.getApplicationResponse(): Application-level response not received from server");
		}
		else { // got something, so create an XM doc and return it...
			XMLDocument applicResponse = new XMLDocument();

			try {
				logger.finer("Going to create XML doc from returned String:" + wholeMessage.toString());
				applicResponse.createDoc(wholeMessage.toString(), XMLDocument.SCHEMA_VALIDATION_OFF);
				return applicResponse;
			}
			catch(XMLException e) {
				logger.severe("Caught LwXMLException creating a new XML doc for response: " + e.getMessage());
				throw new MessagingException("LwProcessMessageForSocket.getApplicationResponse(): Caught Exception creating a new XML doc for error response: " + e.getMessage());
			}
		}
	}

	private XMLDocument response = null;

	private Socket s;
	private InputStream is;
	private OutputStream os;

	// Constants...
	static final int SERV_READY           =  1;
	static final int SERV_CLOSE           =  2;
	static final int SERV_SHUTDOWN        =  3;
	static final int SERV_MORE            =  4;
	static final int SERV_DISCARD         =  5;
	static final int SERV_CONSUME         =  6;
	static final int SERV_REFUSE          =  7;
	static final int SERV_CONSUME_RESPOND =  8;

	static final int OBJ_XML          =  1;

	///////////////////////////////////////////////
	// Total size of transmission message
	// Note: when put up to 4196, it pushed each send to a whole second duration!!!!
	//		 Leaving at 1024 means 4 or 5 transactions per second, if they are sub-1000.
	///////////////////////////////////////////////
	private final int MESSAGE_SIZE =  1024;

	///////////////////////////////////////////////
	// Num bytes left for the message to be transferred, after the codes, control information are subtracted.
	///////////////////////////////////////////////
	private final int MAX_DATA_SIZE        =  MESSAGE_SIZE - 18 - 255;

	private ProcessMessageForSocketSettings settings = null;
	private String auditKeyValues = null; // the audit key values as a concatenated string
	private int fallBackTransactionID = 0; // to be used to create unique trans ids, if no audit keys supplied
}
