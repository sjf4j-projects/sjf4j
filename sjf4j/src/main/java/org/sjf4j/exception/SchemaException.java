package org.sjf4j.exception;

/**
 * Exception for schema compilation/validation errors.
 */
public class SchemaException extends JsonException {

    /**
     * Creates a schema exception with message.
     */
    public SchemaException(String message) {
        super(message);
    }

    /**
     * Creates a schema exception with message and cause.
     */
    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a schema exception from cause.
     */
    public SchemaException(Throwable cause) {
        super(cause);
    }
}
