package org.sjf4j.patch;


import org.sjf4j.JsonException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class PatchOpRegistry {

    @FunctionalInterface
    public interface PatchOpHandler {
        Object apply(Object target, PatchOp op);
    }

    private static final Map<String, PatchOpHandler> PATCH_OP_CACHE = new ConcurrentHashMap<>();

    public static void register(String opName, PatchOpHandler opHandler) {
        Objects.requireNonNull(opName, "opName");
        Objects.requireNonNull(opHandler, "opHandler");
        PATCH_OP_CACHE.put(opName, opHandler);
    }

    public static boolean exists(String opName) {
        return PATCH_OP_CACHE.containsKey(opName);
    }

    public static PatchOpHandler get(String opName) {
        return PATCH_OP_CACHE.get(opName);
    }

    public static PatchOpHandler getOrElseThrow(String opName) {
        PatchOpHandler opHandler = PATCH_OP_CACHE.get(opName);
        if (opHandler == null) throw new JsonException("No PatchOpHandler for '" + opName + "'");
        return opHandler;
    }

    public static Object apply(Object target, PatchOp op) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(op, "op");
        PatchOpHandler opHandler = PATCH_OP_CACHE.get(op.getOp());
        if (opHandler == null) throw new JsonException("No PatchOpHandler for '" + op.getOp() + "'");
        try {
            return opHandler.apply(target, op);
        } catch (Exception e) {
            throw new JsonException("PatchOp '" + op.getOp() + "' apply failed", e);
        }
    }

    // Pre-register build-in PatchOps
    static {
        // test
        PatchOpRegistry.register("test", (target, op) -> {
            Object node = op.getPath().findNode(target);
            if (Objects.equals(node, op)) {
                return target;
            } else {
                throw new JsonException("'test' operation failed at path " + op.getPath() + ": expected " +
                        op.getValue() + ", found " + node);
            }
        });

        // exist
        PatchOpRegistry.register("exist", (target, op) -> {
            if (op.getPath().contains(target)) {
                return target;
            } else  {
                throw new JsonException("'exist' operation failed at path " + op.getPath());
            }
        });

        // add
        PatchOpRegistry.register("add", (target, op) -> {
            op.getPath().add(target, op.getValue());
            return target;
        });

        // remove
        PatchOpRegistry.register("remove", (target, op) -> {
            op.getPath().remove(target);
            return target;
        });

        // replace
        PatchOpRegistry.register("replace", (target, op) -> {
            if (!op.getPath().hasNonNull(target))
                throw new JsonException("'replace' operation failed at path " + op.getPath() +
                        ": cannot replace value at non-existent path");
            op.getPath().replace(target, op.getValue());
            return target;
        });

        // copy
        PatchOpRegistry.register("move", (target, op) -> {
            Object value = op.getPath().findNode(target);
            if (value == null) throw new JsonException("'copy' operation failed at path " + op.getPath() +
                    ": value is not exist");
            op.getPath().add(target, value);
            return target;
        });

        // move
        PatchOpRegistry.register("move", (target, op) -> {
            Object value = op.getPath().remove(target);
            if (value == null) throw new JsonException("'move' operation failed at path " + op.getPath() +
                    ": value is not exist");
            op.getPath().add(target, value);
            return target;
        });

        // ensurePut
        PatchOpRegistry.register("ensurePut", (target, op) -> {
            op.getPath().ensurePut(target, op.getValue());
            return target;
        });

    }

}
