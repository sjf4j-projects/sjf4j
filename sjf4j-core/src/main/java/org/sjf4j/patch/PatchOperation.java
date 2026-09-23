package org.sjf4j.patch;

import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.path.JsonPointer;


/**
 * Single JSON Patch operation over an OBNT document.
 *
 * <p>Fields follow RFC 6902 ({@code op}, {@code path}, optional {@code from}/{@code value})
 * with a few SJF4J extensions. The constructor retains the {@code value} reference.
 * {@code add}, {@code replace}, and {@code ensurePut} write that reference without copying it,
 * so a mutable target can alias the operation payload. Operations produced by
 * {@link Patches#diff(Object, Object)} likewise retain values from the target graph, which can
 * also be reachable from the source graph when the inputs alias. {@code copy} calls
 * {@code Sjf4j.global().deepNode}; its copy boundary for custom representations is defined by
 * the global node facade.
 */
public final class PatchOperation {

    // Standard op names defined in RFC 6902
    public static final String STD_ADD = "add";
    public static final String STD_REMOVE = "remove";
    public static final String STD_REPLACE = "replace";
    public static final String STD_MOVE = "move";
    public static final String STD_COPY = "copy";
    public static final String STD_TEST = "test";

    // Extension ops built into SJF4J
    public static final String EXT_EXIST = "exist";
    public static final String EXT_ENSURE_PUT = "ensurePut";

    private final String name;
    private final JsonPointer path;
    private final Object value;             // Optional
    private final JsonPointer from;         // Optional


    /**
     * Creates a patch operation with all fields.
     */
    @NodeCreator
    public PatchOperation(@NodeProperty("op") String op,
                          @NodeProperty("path") JsonPointer path,
                          @NodeProperty("value") Object value,
                          @NodeProperty("from") JsonPointer from) {
        this.name = op;
        this.path = path;
        this.value = value;
        this.from = from;
    }

    /**
     * Returns operation name.
     */
    public String getOp() {
        return name;
    }

    /**
     * Returns target path.
     */
    public JsonPointer getPath() {
        return path;
    }

    /**
     * Returns operation value payload.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Returns source path for move/copy operations.
     */
    public JsonPointer getFrom() {
        return from;
    }

    /**
     * Applies this operation via {@link OperationRegistry}.
     * <p>
     * Mutating non-root operations write the addressed target container; {@code test}
     * and {@code exist} are read-only. Root add and replace return the operation
     * value; root remove returns {@code null}. The operation itself is not directly
     * modified.
     */
    public Object apply(Object target) {
        return OperationRegistry.apply(target, this);
    }

}
