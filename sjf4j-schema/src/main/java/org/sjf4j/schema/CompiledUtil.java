package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime support required by generated compiled schema validators.
 * Keep helper methods limited to semantics which cannot be emitted directly.
 */
public final class CompiledUtil {
    private CompiledUtil() {}

    /** Creates a fail-fast error from compiled diagnostics; no SchemaPlan execution. */
    public static ValidationException validationFailure(String keyword, String schemaUri,
                                                        String keywordPointer, PathSegment instancePath) {
        PathSegment schemaPath = keywordPointer == null
                ? PathSegment.Root.INSTANCE : JsonPointer.parse(keywordPointer).tail();
        ValidationResult.Message message = new ValidationResult.Message(
                keyword, "compiled assertion failed", null, instancePath, schemaPath,
                schemaUri == null ? null : URI.create(schemaUri));
        return new ValidationException(new ValidationResult(false, Collections.singletonList(message)));
    }

    /**
     * Rebase a genuine subplan failure to its position within the outer value.
     * This is an exception-only operation and never revalidates any schema.
     */
    public static ValidationException rebaseFailure(ValidationException failure, PathSegment parentPath) {
        if (parentPath == null || parentPath == PathSegment.Root.INSTANCE) return failure;
        ValidationResult original = failure.getResult();
        List<ValidationResult.Message> errors = rebaseMessages(original.getErrors(), parentPath);
        List<ValidationResult.Message> annotations = rebaseMessages(original.getAnnotations(), parentPath);
        return new ValidationException(new ValidationResult(false, errors, annotations));
    }

    private static List<ValidationResult.Message> rebaseMessages(
            List<ValidationResult.Message> messages, PathSegment prefix) {
        if (messages.isEmpty()) return Collections.emptyList();
        List<ValidationResult.Message> rebased = new ArrayList<>(messages.size());
        String base = prefix.rootedPointerExpr();
        for (ValidationResult.Message message : messages) {
            PathSegment child = message.getInstancePs();
            String combined = base + (child == null ? "" : child.rootedPointerExpr());
            PathSegment full = JsonPointer.parse(combined).tail();
            rebased.add(new ValidationResult.Message(message.getKeyword(), message.getMessage(),
                    message.getValue(), full, message.getKeywordPs(), message.getSchemaUri()));
        }
        return rebased;
    }

}
