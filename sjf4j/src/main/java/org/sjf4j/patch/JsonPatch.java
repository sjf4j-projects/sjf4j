package org.sjf4j.patch;

import java.util.List;

public class JsonPatch {

    private List<PatchOp> ops;

    public JsonPatch(List<PatchOp> ops) {
        this.ops = ops;
    }

    public Object apply(Object target) {
        for (PatchOp op : ops) {
            target = applyOp(target, op);
        }
        return target;
    }


    /// Private

    private Object applyOp(Object target, PatchOp op) {
        return target;
    }
}

