package org.sjf4j.schema;

import org.sjf4j.exception.SchemaException;


/**
 * Exception wrapper for validation results.
 */
public class ValidationException extends SchemaException {
    private final ValidationResult result;

    public ValidationException(ValidationResult result) {
        super(buildMessage(result));
        this.result = result;
    }

    private static String buildMessage(ValidationResult result) {
        ValidationMessage lastOne = result.getLastMessage();
        if (lastOne == null) {
            return "Failed with " + result.count() + " errors";
        } else {
            return "Failed with " + result.count() + " errors, last one: " + lastOne;
        }
    }

    public ValidationResult getResult() {
        return result;
    }

}
