package org.sjf4j.patch;


import org.sjf4j.exception.JsonException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class PatchOpRegistry {

    @FunctionalInterface
    public interface PatchOpHandler {
        void apply(Object target, PatchOp op);
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

    public static void apply(Object target, PatchOp op) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(op, "op");
        PatchOpHandler opHandler = PATCH_OP_CACHE.get(op.getOp());
        if (opHandler == null) throw new JsonException("No PatchOpHandler for '" + op.getOp() + "'");
        try {
            opHandler.apply(target, op);
        } catch (Exception e) {
            throw new JsonException("Failed to apply PatchOp '" + op.getOp() + "'", e);
        }
    }

    // Pre-register build-in PatchOps
    static {
        // test
        PatchOpRegistry.register(PatchOp.STD_TEST, (target, op) -> {
            Object node = op.getPath().getNode(target);
            if (!Objects.equals(node, op.getValue())) {
                throw new JsonException("'test' operation failed at path " + op.getPath() + ": expected " +
                        op.getValue() + ", found " + node);
            }
        });

        // add
        PatchOpRegistry.register(PatchOp.STD_ADD, (target, op) -> {
            op.getPath().add(target, op.getValue());
        });

        // remove
        PatchOpRegistry.register(PatchOp.STD_REMOVE, (target, op) -> {
            op.getPath().remove(target);
        });

        // replace
        PatchOpRegistry.register(PatchOp.STD_REPLACE, (target, op) -> {
            if (!op.getPath().hasNonNull(target))
                throw new JsonException("'replace' operation failed at path " + op.getPath() +
                        ": cannot replace value at non-existent path");
            op.getPath().replace(target, op.getValue());
        });

        // copy
        PatchOpRegistry.register(PatchOp.STD_COPY, (target, op) -> {
            Object value = op.getFrom().getNode(target);
            if (value == null) throw new JsonException("'copy' operation failed at from " + op.getFrom() +
                    ": value is not exist");
            op.getPath().add(target, value);
        });

        // move
        PatchOpRegistry.register(PatchOp.STD_MOVE, (target, op) -> {
            Object value = op.getFrom().remove(target);
            if (value == null) throw new JsonException("'move' operation failed at from " + op.getFrom() +
                    ": value is not exist");
            op.getPath().add(target, value);
        });

        // exist
        PatchOpRegistry.register(PatchOp.EXT_EXIST, (target, op) -> {
            if (!op.getPath().contains(target)) {
                throw new JsonException("'exist' operation failed at path " + op.getPath());
            }
        });

        // ensurePut
        PatchOpRegistry.register(PatchOp.EXT_ENSURE_PUT, (target, op) -> {
            op.getPath().ensurePut(target, op.getValue());
        });

    }

}
