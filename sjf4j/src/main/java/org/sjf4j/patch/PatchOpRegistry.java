package org.sjf4j.patch;


import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPointer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Registry for patch operation handlers.
 *
 * <p>Handlers are registered at startup for RFC 6902 ops and
 * SJF4J-specific extensions.
 */
public class PatchOpRegistry {

    /**
     * Patch operation handler.
     */
    @FunctionalInterface
    public interface PatchOpHandler {
        void apply(Object target, PatchOp op);
    }

    private static final Map<String, PatchOpHandler> PATCH_OP_CACHE = new ConcurrentHashMap<>();

    /**
     * Registers handler for operation name.
     */
    public static void register(String opName, PatchOpHandler opHandler) {
        Objects.requireNonNull(opName, "opName");
        Objects.requireNonNull(opHandler, "opHandler");
        PATCH_OP_CACHE.put(opName, opHandler);
    }

    /**
     * Returns true when operation handler is registered.
     */
    public static boolean exists(String opName) {
        return PATCH_OP_CACHE.containsKey(opName);
    }

    /**
     * Returns handler for operation name, or null.
     */
    public static PatchOpHandler get(String opName) {
        return PATCH_OP_CACHE.get(opName);
    }

    /**
     * Applies operation by looking up its registered handler.
     */
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
            if (!Nodes.equals(node, op.getValue())) {
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
            JsonPointer path = op.getPath();
            if (!path.contains(target)) {
                throw new JsonException("'remove' operation failed at path " + path + ": no value exist");
            }
            path.remove(target);
        });

        // replace
        PatchOpRegistry.register(PatchOp.STD_REPLACE, (target, op) -> {
            op.getPath().replace(target, op.getValue());
        });

        // copy
        PatchOpRegistry.register(PatchOp.STD_COPY, (target, op) -> {
            Object value = op.getFrom().getNode(target);
            if (value == null) throw new JsonException("'copy' operation failed at from " + op.getFrom() +
                    ": no value exist");
            op.getPath().add(target, value);
        });

        // move
        PatchOpRegistry.register(PatchOp.STD_MOVE, (target, op) -> {
            Object value = op.getFrom().remove(target);
            if (value == null) {
                throw new JsonException("'move' operation failed at from " + op.getFrom() + ": no value exist");
            }
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
