package org.sjf4j.schema;

import org.sjf4j.util.Asserts;

import java.util.Objects;


/**
 * Exception wrapper carrying a {@link ValidationResult}.
 * <p>
 * Thrown by convenience APIs such as {@link SchemaPlan#requireValid(Object)}
 * and {@link SchemaValidator#requireValid(Object)}.
 * The exception message summarizes the error count and the first error;
 * the full result remains available through {@link #getResult()}.
 */
public class ValidationException extends SchemaException {
    private final ValidationResult result;

    /**
     * Creates an exception from a validation result.
     */
    public ValidationException(ValidationResult result) {
        super(buildMessage(Asserts.notNull(result, "result")));
        this.result = result;
    }

    /**
     * Builds concise exception message from result state.
     */
    private static String buildMessage(ValidationResult result) {
        ValidationMessage first = null;
        int errors = 0;
        for (ValidationMessage message : result.getMessages()) {
            if (message.getSeverity() != ValidationMessage.Severity.ERROR) continue;
            if (first == null) first = message;
            errors++;
        }
        if (first == null) return "Validation failed with 0 error(s)";
        return "Validation failed with " + errors + " error(s); first: " + first;
    }

    /**
     * Returns the underlying validation result.
     */
    public ValidationResult getResult() {
        return result;
    }

}
