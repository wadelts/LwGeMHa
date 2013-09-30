package gemha.support;

/**
  * Encapsulates exceptions resulting from errors returned from message-handling activity.
  * @author Liam Wade
  * @version 1.0 25/06/2003
  */
public class LwMessagingException extends Exception
{
  /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

/**
    * Will create a new exception.
    */
	public LwMessagingException() {
	}

  /**
    * Will create a new exception with the given reason.
	* @param reason the text explaining the error
    */
	public LwMessagingException(String reason) {
		super(reason);
	}
}