package gemha.support;

/**
  * Encapsulates exceptions resulting from errors returned from message-handling activity.
  * @author Liam Wade
  * @version 1.0 25/06/2003
  */
public class MessagingException extends Exception
{
  /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

/**
    * Will create a new exception.
    */
	public MessagingException() {
	}

  /**
    * Will create a new exception with the given reason.
	* @param reason the text explaining the error
    */
	public MessagingException(String reason) {
		super(reason);
	}

  /**
    * Will create a new exception with the given message and remembering the causing exception.
	* @param message, the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
	* @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
    */
	public MessagingException(String message, Throwable cause) {
		super(message, cause);
	}
}