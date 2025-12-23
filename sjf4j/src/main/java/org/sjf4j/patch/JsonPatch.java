package org.sjf4j.patch;

import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.util.ContainerUtil;
import org.sjf4j.util.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JsonPatch {

    private final List<PatchOp> ops;

    public JsonPatch() {
        this.ops = new ArrayList<>();
    }

    public JsonPatch(List<PatchOp> ops) {
        this.ops = ops;
    }

    public static JsonPatch fromJson(String json) {
        List<PatchOp> ops = Sjf4j.fromJson(json, new TypeReference<List<PatchOp>>() {});
        return new JsonPatch(ops);
    }

    public static JsonPatch diff(Object source, Object target) {
        List<PatchOp> ops = ContainerUtil.diff(source, target);
        return new JsonPatch(ops);
    }

    public String toJson() {
        return Sjf4j.toJson(ops);
    }

    public List<PatchOp> toList() {
        return ops;
    }

    public int size() {
        return ops.size();
    }

    public void add(PatchOp op) {
        Objects.requireNonNull(op, "op must not be null");
        ops.add(op);
    }

    public Object apply(Object target) {
        for (PatchOp op : ops) {
            target = op.apply(target);
        }
        return target;
    }


}

