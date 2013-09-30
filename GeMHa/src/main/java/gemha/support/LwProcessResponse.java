package gemha.support;

import lw.XML.LwXMLDocument;
import lw.XML.LwXMLException;

/**
  * Encapsulates a response after Processing messages for a target meduim.
  * 
  * @author Liam Wade
  * @version 1.0 21/05/2013
  * 
  * @ThreadSafe
  */
public class LwProcessResponse {
	public static enum ProcessResponseCode {
		SUCCESS,
		INTERRUPTED,
		FAILURE_INVALID_MESSAGE,
		FAILURE_INVALID_XML
	};
	
	private final ProcessResponseCode responseCode;
	private final int rowsProcessed;
	private final String response;
	private final Throwable exception;
	private final String inputDoc;			// a string representation (therefore immutable) of the received input message, if exists
	private final String auditKeyValues;
	
  /**
    * Constructor
    * 
	* @param response the detailed response (can be null)
	* @param exception the exception thrown during processing, if any (can be null)
    */
	private LwProcessResponse(Builder builder) {
		this.responseCode = builder.responseCode;
		this.rowsProcessed = builder.rowsProcessed;
		this.response = builder.response;
		this.exception = builder.exception;
		this.inputDoc = builder.inputDoc;
		this.auditKeyValues = builder.auditKeyValues;
	}

	public static class Builder {
		// Required Parameters
		private final ProcessResponseCode responseCode;
		private final int rowsProcessed;
		
		// Optional Parameters - initialised to default values
		private String response = null;
		private Throwable exception = null;
		private String inputDoc;			// a string representation (therefore immutable) of the received input message, if exists
		private String auditKeyValues = null;
		
		/**
	    * Constructor
	    * 
		* @param responseCode the code describing the success or failure of processing this message
		* @param rowsProcessed the number of rows processed for this message
	    */
		public Builder(ProcessResponseCode responseCode, int rowsProcessed) {
			this.responseCode = responseCode;
			this.rowsProcessed = rowsProcessed;
		}
		
		public Builder setResponse(String val)
			{	this.response = val;	return this;		}
		public Builder setException(Throwable val)
			{	this.exception = val;	return this;		}
		public Builder setInputDoc(LwXMLDocument val)
			{	this.inputDoc = val.toString();	return this;		}
		public Builder setAuditKeyValues(String val)
			{	this.auditKeyValues = val;	return this;	}
		
		public LwProcessResponse build() {
			return new LwProcessResponse(this);
		}
	}

	public ProcessResponseCode getResponseCode() {
		return responseCode;
	}
	
	public int getRowsProcessed() {
		return rowsProcessed;
	}
	
	/**
	  * Get the detailed response (can be null)
	  *
	  * @return the detailed response after processing the message (can be null)
	  */
	public String getResponse() {
		return response;
	}

	/**
	  * Get the exception thrown during processing, if any (can be null)
	  *
	  * @return the exception thrown during processing, if any (can be null)
	  */
	public Throwable getException() {
		return exception;
	}

	/**
	  * Get the exception thrown during processing, if any (can be null)
	  *
	  * @return the original input message as an XML document, null if no XML message stored
	  */
	public LwXMLDocument getInputDoc() {
		if (inputDoc == null) return null;
		
		LwXMLDocument newDoc = null;
		try {
			newDoc = LwXMLDocument.createDoc(inputDoc, LwXMLDocument.SCHEMA_VALIDATION_OFF);
		}
		catch(LwXMLException e) {
			// Going to return null here as this should never really be thrown - we've created inputDoc
			// from an LwXMLDocument.
			return null;
		}
		return newDoc;
	}

	/**
	  * Get the audit Key Values for the message
	  *
	  * @return the audit Key Values for the message (can be null)
	  */
	public String getAuditKeyValues() {
		return auditKeyValues;
	}
}