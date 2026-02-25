package org.sjf4j.exception;


/**
 * Base runtime exception for JSON-related errors.
 */
public class JsonException extends RuntimeException {


    /**
     * Creates a JSON exception with message.
     */
    public JsonException(String message) {
        super(message);
    }

    /**
     * Creates a JSON exception with message and cause.
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a JSON exception with full RuntimeException options.
     */
    public JsonException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Creates a JSON exception from cause.
     */
    public JsonException(Throwable cause) {
        super(cause);
    }


}
