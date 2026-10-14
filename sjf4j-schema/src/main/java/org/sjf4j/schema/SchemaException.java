package org.sjf4j.schema;

import org.sjf4j.exception.NodeException;

/**
 * Exception for schema compilation, loading, and linking errors.
 * {@link ValidationException} is the subtype for a schema that compiled
 * successfully but rejected an instance.
 */
public class SchemaException extends NodeException {

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
