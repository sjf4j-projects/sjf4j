package org.sjf4j.patch;


import org.sjf4j.JsonException;
import org.sjf4j.util.ContainerUtil;
import org.sjf4j.util.NodeUtil;

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
            throw new JsonException("Failed to apply PatchOp '" + op.getOp() + "'", e);
        }
    }

    // Pre-register build-in PatchOps
    static {
        // test
        PatchOpRegistry.register(PatchOp.STD_TEST, (target, op) -> {
            Object node = op.getPath().findNode(target);
            if (ContainerUtil.equals(node, op.getValue())) {
                return target;
            } else {
                throw new JsonException("'test' operation failed at path " + op.getPath() + ": expected " +
                        op.getValue() + ", found " + node);
            }
        });

        // add
        PatchOpRegistry.register(PatchOp.STD_ADD, (target, op) -> {
            op.getPath().add(target, op.getValue());
            return target;
        });

        // remove
        PatchOpRegistry.register(PatchOp.STD_REMOVE, (target, op) -> {
            op.getPath().remove(target);
            return target;
        });

        // replace
        PatchOpRegistry.register(PatchOp.STD_REPLACE, (target, op) -> {
            if (!op.getPath().hasNonNull(target))
                throw new JsonException("'replace' operation failed at path " + op.getPath() +
                        ": cannot replace value at non-existent path");
            op.getPath().replace(target, op.getValue());
            return target;
        });

        // copy
        PatchOpRegistry.register(PatchOp.STD_COPY, (target, op) -> {
            Object value = op.getFrom().findNode(target);
            if (value == null) throw new JsonException("'copy' operation failed at from " + op.getFrom() +
                    ": value is not exist");
            op.getPath().add(target, value);
            return target;
        });

        // move
        PatchOpRegistry.register(PatchOp.STD_MOVE, (target, op) -> {
            Object value = op.getFrom().remove(target);
            if (value == null) throw new JsonException("'move' operation failed at from " + op.getFrom() +
                    ": value is not exist");
            op.getPath().add(target, value);
            return target;
        });

        // exist
        PatchOpRegistry.register(PatchOp.EXT_EXIST, (target, op) -> {
            if (op.getPath().contains(target)) {
                return target;
            } else  {
                throw new JsonException("'exist' operation failed at path " + op.getPath());
            }
        });

        // ensurePut
        PatchOpRegistry.register(PatchOp.EXT_ENSURE_PUT, (target, op) -> {
            op.getPath().ensurePut(target, op.getValue());
            return target;
        });

    }

}
