package org.sjf4j.patch;

import org.sjf4j.JsonArray;

import java.util.List;
import java.util.Objects;

public class JsonPatch extends JsonArray {

    @Override
    public Class<?> elementType() {
        return PatchOp.class;
    }

    public JsonPatch() {
        super();
    }

    public JsonPatch(List<PatchOp> ops) {
        super(ops);
    }

    public static JsonPatch fromJson(String json) {
        return JsonPatch.fromJson(json, JsonPatch.class);
    }

    public static JsonPatch diff(Object source, Object target) {
        List<PatchOp> ops = PatchUtil.diff(source, target);
        return new JsonPatch(ops);
    }


    public void add(PatchOp op) {
        Objects.requireNonNull(op, "op must not be null");
        super.add(op);
    }


    public void apply(Object target) {
        Objects.requireNonNull(target, "target must not be null");
        forEach(v -> {
            PatchOp op =  (PatchOp) v;
            op.apply(target);
        });
    }

}

