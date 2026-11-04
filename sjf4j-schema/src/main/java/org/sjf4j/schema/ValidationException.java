package org.sjf4j.schema;

import org.sjf4j.util.Asserts;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Collections;

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
        ValidationResult.Message first = result.getErrors().isEmpty() ? null : result.getErrors().get(0);
        if (first == null) return "Validation failed with 0 error(s)";
        return "Validation failed with " + result.count() + " error(s); first: "
                + SchemaUtil.formatValidationError(
                        first.getMessage(), first.getInstancePs(), first.getKeywordPs(), first.getSchemaUri(), first.getKeyword());
    }

    /**
     * Creates the fail-fast exception emitted by a compiled validator.
     * Resource URI and keyword pointer remain separate in the message.
     */
    public static ValidationException failure(String keyword, String resourceUri,
                                              String keywordPointer, PathSegment instancePs) {
        PathSegment keywordPs = keywordPointer == null
                ? PathSegment.Root.INSTANCE : JsonPointer.parse(keywordPointer).tail();
        ValidationResult.Message message = new ValidationResult.Message(
                keyword, "compiled assertion failed", null, instancePs, keywordPs,
                resourceUri == null ? null : URI.create(resourceUri));
        return new ValidationException(new ValidationResult(false, Collections.singletonList(message)));
    }

    /**
     * Returns the underlying validation result.
     */
    public ValidationResult getResult() {
        return result;
    }

}
