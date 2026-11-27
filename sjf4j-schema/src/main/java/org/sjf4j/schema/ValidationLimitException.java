package org.sjf4j.schema;

/** Validation aborted by an execution limit, rather than an assertion failure. */
public class ValidationLimitException extends SchemaException {
    public ValidationLimitException(String message) {
        super(message);
    }
}
