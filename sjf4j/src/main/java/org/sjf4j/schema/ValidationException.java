package org.sjf4j.schema;

import org.sjf4j.JsonException;

import java.util.List;


public class ValidationException extends JsonException {
    private final ValidationResult result;

    public ValidationException(ValidationResult result) {
        super(buildMessage(result));
        this.result = result;
    }

    private static String buildMessage(ValidationResult result) {
        List<ValidationMessage> errors = result.getErrors();
        if (errors.isEmpty()) return "Schema validation failed with no error details";
        ValidationMessage first = errors.get(0);
        return "Failed with " + errors.size() + " errors, first one: '" + first.getKeyword() +
                "' at '" + first.getPath() + "': " + first.getMessage();
    }

    public ValidationResult getResult() {
        return result;
    }
}
