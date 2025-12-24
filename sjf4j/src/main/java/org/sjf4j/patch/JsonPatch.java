package org.sjf4j.patch;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.util.ContainerUtil;
import org.sjf4j.util.TypeReference;

import java.util.ArrayList;
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
        List<PatchOp> ops = ContainerUtil.diff(source, target);
        return new JsonPatch(ops);
    }


    public void add(PatchOp op) {
        Objects.requireNonNull(op, "op must not be null");
        super.add(op);
    }

    public Object apply(Object target) {
        forEach(v -> {
            PatchOp op =  (PatchOp) v;
            op.apply(target);
        });
        return target;
    }


}

