package org.sjf4j.exception;

/**
 * Exception for OBNT processing errors.
 */
public class NodeException extends JsonException {

    public NodeException(String message) {
        super(message);
    }

    public NodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NodeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public NodeException(Throwable cause) {
        super(cause);
    }

}
