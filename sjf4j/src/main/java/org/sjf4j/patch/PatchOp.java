package org.sjf4j.patch;

import org.sjf4j.path.JsonPointer;


public final class PatchOp {

    private final String op;
    private final JsonPointer path;
    private final Object value;             // Optional
    private final JsonPointer from;         // Optional

    public PatchOp(String op, JsonPointer path, Object value, JsonPointer from) {
        this.op = op;
        this.path = path;
        this.value = value;
        this.from = from;
    }

    public String getOp() {
        return op;
    }

    public JsonPointer getPath() {
        return path;
    }

    public Object getValue() {
        return value;
    }

    public JsonPointer getFrom() {
        return from;
    }


}
