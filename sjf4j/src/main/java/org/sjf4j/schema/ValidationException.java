package org.sjf4j.schema;

import org.sjf4j.exception.SchemaException;

import java.util.Objects;


/**
 * Exception wrapper carrying a {@link ValidationResult}.
 * <p>
 * Thrown by convenience APIs such as {@link SchemaPlan#requireValid(Object)}
 * and {@link SchemaValidator#requireValid(Object)}.
 * The exception message is a compact summary of the last validation message,
 * which now includes both instance and keyword JSON Pointer paths when present.
 */
public class ValidationException extends SchemaException {
    private final ValidationResult result;

    /**
     * Creates an exception from a validation result.
     */
    public ValidationException(ValidationResult result) {
        super(buildMessage(Objects.requireNonNull(result, "result")));
        this.result = result;
    }

    /**
     * Builds concise exception message from result state.
     */
    private static String buildMessage(ValidationResult result) {
        ValidationMessage first = result.getMessages().isEmpty() ? null : result.getMessages().get(0);
        if (first == null) return "Validation failed with " + result.count() + " error(s)";
        return "Validation failed with " + result.count() + " error(s); first: " + first;
    }

    /**
     * Returns the underlying validation result.
     */
    public ValidationResult getResult() {
        return result;
    }

}
