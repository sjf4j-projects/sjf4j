package org.sjf4j.exception;

/**
 * Exception for schema compilation/validation errors.
 */
public class SchemaException extends JsonException {

    public SchemaException(String message) {
        super(message);
    }

    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }

    public SchemaException(Throwable cause) {
        super(cause);
    }
}
