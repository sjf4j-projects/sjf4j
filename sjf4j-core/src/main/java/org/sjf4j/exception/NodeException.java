package org.sjf4j.exception;


/**
 * Base runtime exception for JSON-related errors.
 */
public class NodeException extends RuntimeException {


    /**
     * Creates a node exception with message.
     */
    public NodeException(String message) {
        super(message);
    }

    /**
     * Creates a node exception with message and cause.
     */
    public NodeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a node exception with full RuntimeException options.
     */
    public NodeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Creates a node exception from cause.
     */
    public NodeException(Throwable cause) {
        super(cause);
    }


}
