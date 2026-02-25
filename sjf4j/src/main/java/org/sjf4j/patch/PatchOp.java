package org.sjf4j.patch;

import org.sjf4j.path.JsonPointer;


/**
 * Single JSON Patch operation.
 *
 * <p>Fields follow RFC 6902 ({@code op}, {@code path}, optional {@code from}/{@code value})
 * with a few SJF4J extensions.
 */
public final class PatchOp {

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


    private String op;
    private JsonPointer path;
    private Object value;             // Optional
    private JsonPointer from;         // Optional

    /**
     * Creates an empty patch operation.
     */
    public PatchOp() {}

    /**
     * Creates a patch operation with all fields.
     */
    public PatchOp(String op, JsonPointer path, Object value, JsonPointer from) {
        this.op = op;
        this.path = path;
        this.value = value;
        this.from = from;
    }

    /**
     * Returns operation name.
     */
    public String getOp() {
        return op;
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
     * Applies this operation to target node.
     */
    public void apply(Object target) {
        PatchOpRegistry.apply(target, this);
    }

}
