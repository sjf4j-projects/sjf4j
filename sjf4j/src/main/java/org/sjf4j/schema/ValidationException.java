package org.sjf4j.schema;

import org.sjf4j.exception.SchemaException;


/**
 * Exception wrapper carrying a {@link ValidationResult}.
 * <p>
 * Thrown by convenience APIs such as {@code validateOrThrow()}.
 * The exception message is a compact summary of the last validation message,
 * which now includes both instance and keyword JSON Pointer paths when present.
 */
public class ValidationException extends SchemaException {
    private final ValidationResult result;

    /**
     * Creates an exception from a validation result.
     */
    public ValidationException(ValidationResult result) {
        super(buildMessage(result));
        this.result = result;
    }

    /**
     * Builds concise exception message from result state.
     */
    private static String buildMessage(ValidationResult result) {
        ValidationMessage lastOne = result.getLastMessage();
        if (lastOne == null) {
            return "Failed with " + result.count() + " errors";
        } else {
            return "Failed with " + result.count() + " errors, last one: " + lastOne;
        }
    }

    /**
     * Returns the underlying validation result.
     */
    public ValidationResult getResult() {
        return result;
    }

}
