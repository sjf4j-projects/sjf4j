package org.sjf4j.patch;

import org.sjf4j.JsonArray;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Types;

import java.util.List;
import java.util.Objects;

/**
 * JSON Patch document modeled as a JsonArray of {@link PatchOp}.
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
        return PatchOp.class;
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
    public JsonPatch(List<PatchOp> ops) {
        super(ops);
    }

    /**
     * Parses patch document from JSON string.
     */
    public static JsonPatch fromJson(String json) {
        return JsonPatch.fromJson(json, JsonPatch.class);
    }

    /**
     * Computes patch operations that transform source into target.
     */
    public static JsonPatch diff(Object source, Object target) {
        List<PatchOp> ops = Patches.diff(source, target);
        return new JsonPatch(ops);
    }


    /**
     * Adds one patch operation.
     */
    public void add(PatchOp op) {
        Objects.requireNonNull(op, "op is null");
        super.add(op);
    }


    /**
     * Applies all operations to target in document order.
     * <p>
     * Execution is stateful: each op observes mutations produced by previous ops.
     */
    public void apply(Object target) {
        Objects.requireNonNull(target, "target is null");
        forEach(v -> {
            if (v instanceof PatchOp) {
                PatchOp op =  (PatchOp) v;
                op.apply(target);
            } else throw new JsonException("Unsupported patch type: " + Types.name(v));
        });
    }

}
