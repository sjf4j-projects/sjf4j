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

    /**
     * Reads a structural object member for generated Schema validators.
     * Unlike a plain Nodes.getInObject call, a POJO member can select a
     * nondefault scalar codec through @NodeProperty(valueFormat/valuePattern).
     * Raw Map/List codec values stay atomic leaves, never traversable nodes.
     */
    public static Object objectPropertyValue(Object owner, String key) {
        Object value = org.sjf4j.Nodes.getInObject(owner, key);
        if (value == null || owner instanceof java.util.Map || owner instanceof org.sjf4j.JsonObject)
            return value;
        org.sjf4j.meta.TypeInfo info =
                org.sjf4j.meta.TypeRegistry.registerTypeInfo(owner.getClass());
        if (info.pojoInfo == null) return value;
        org.sjf4j.meta.PropertyInfo property = info.pojoInfo.getPropertyNoAlias(key);
        if (property == null || property.valueInfo == null) return value;
        org.sjf4j.value.ValueInfo codec = property.valueInfo;
        switch (codec.rawJsonType) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
                return codec.valueToRaw(value);
            default:
                // A codec-backed Map/List is a leaf, not a structural node.
                return value;
        }
    }

    /**
     * Implements the JSON equality semantics of uniqueItems without running
     * a SchemaPlan. The runtime and compiled validator share the same
     * Nodes.hash/Nodes.equals-based duplicate detection.
     */
    public static boolean hasDuplicateItems(Object array) {
        return Evaluator.ArrayEvaluator.hasDuplicate(array,
                org.sjf4j.Nodes.sizeInArray(array));
    }

    /** Creates a fail-fast error from compiled diagnostics; no SchemaPlan execution. */
    public static ValidationException validationFailure(String keyword, String resourceUri,
                                                        String keywordPointer, PathSegment instancePath) {
        PathSegment schemaPath = keywordPointer == null
                ? PathSegment.Root.INSTANCE : JsonPointer.parse(keywordPointer).tail();
        ValidationResult.Message message = new ValidationResult.Message(
                keyword, "compiled assertion failed", null, instancePath, schemaPath,
                resourceUri == null ? null : URI.create(resourceUri));
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
