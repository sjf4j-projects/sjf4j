package org.sjf4j.exception;


/**
 * Base runtime exception for JSON-related errors.
 */
public class JsonException extends RuntimeException {


    /**
     * Constructs a new JsonException with the specified detail message.
     *
     * @param message the detail message explaining the error
     */
    public JsonException(String message) {
        super(message);
    }

    /**
     * Constructs a new JsonException with the specified detail message and cause.
     *
     * @param message the detail message explaining the error
     * @param cause the underlying cause of the exception
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public JsonException(Throwable cause) {
        super(cause);
    }


}
