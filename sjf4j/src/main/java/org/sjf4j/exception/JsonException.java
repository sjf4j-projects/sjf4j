package org.sjf4j.exception;


/**
 * Exception class for JSON operations in the SJF4J library.
 * <p>
 * This runtime exception is thrown when errors occur during JSON parsing, serialization,
 * deserialization, or other JSON-related operations. It provides constructors for
 * different error scenarios and wraps underlying exceptions when necessary.
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