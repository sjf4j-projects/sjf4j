package org.sjf4j.patch;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Types;

import java.util.List;
import java.util.Objects;

/**
 * JSON Patch document modeled as a JsonArray of {@link PatchOperation}.
 *
 * <p>Supports RFC 6902 operations plus a small set of SJF4J extensions
 * (e.g. {@code exist}, {@code ensurePut}).
 */
public class JsonPatch extends JsonArray {

    /**
     * Returns patch operation element type.
     */
    @Override
    public Class<?> elementType() {
        return PatchOperation.class;
    }

    /**
     * Creates an empty patch document.
     */
    public JsonPatch() {
        super();
    }

    /**
     * Creates a patch document from operation list.
     */
    public JsonPatch(List<PatchOperation> operations) {
        super(operations);
    }

    /**
     * Parses patch document from JSON string.
     */
    public static JsonPatch fromJson(String json) {
        return Sjf4j.fromJson(json, JsonPatch.class);
    }

    /**
     * Computes patch operations that transform source into target.
     */
    public static JsonPatch diff(Object source, Object target) {
        List<PatchOperation> ops = Patches.diff(source, target);
        return new JsonPatch(ops);
    }


    /**
     * Adds one patch operation.
     */
    public void add(PatchOperation operation) {
        Objects.requireNonNull(operation, "operation");
        super.add(operation);
    }


    /**
     * Applies all operations to target in document order.
     * <p>
     * Execution is stateful: each op observes mutations produced by previous ops.
     */
    public void apply(Object target) {
        Objects.requireNonNull(target, "target");
        forEach(v -> {
            if (v instanceof PatchOperation) {
                PatchOperation operation =  (PatchOperation) v;
                operation.apply(target);
            } else throw new JsonException("Unsupported patch type: " + Types.name(v));
        });
    }

}
