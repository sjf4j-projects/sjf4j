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
public class OperationRegistry {

    /**
     * Patch operation handler.
     */
    @FunctionalInterface
    public interface OperationHandler {
        void apply(Object target, PatchOperation operation);
    }

    private static final Map<String, OperationHandler> OPERATION_CACHE = new ConcurrentHashMap<>();

    /**
     * Registers handler for operation name.
     */
    public static void register(String opName, OperationHandler opHandler) {
        Objects.requireNonNull(opName, "opName");
        Objects.requireNonNull(opHandler, "opHandler");
        OPERATION_CACHE.put(opName, opHandler);
    }

    /**
     * Returns true when operation handler is registered.
     */
    public static boolean exists(String op) {
        return OPERATION_CACHE.containsKey(op);
    }

    /**
     * Returns handler for operation name, or null.
     */
    public static OperationHandler get(String op) {
        return OPERATION_CACHE.get(op);
    }

    /**
     * Applies operation by looking up its registered handler.
     */
    public static void apply(Object target, PatchOperation operation) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operation, "operation");
        OperationHandler handler = OPERATION_CACHE.get(operation.getOp());
        if (handler == null) throw new JsonException("No OperationHandler for '" + operation.getOp() + "'");
        try {
            handler.apply(target, operation);
        } catch (Exception e) {
            throw new JsonException("Failed to apply PatchOperation '" + operation.getOp() + "'", e);
        }
    }

    // Pre-register build-in PatchOperations
    static {
        // test
        OperationRegistry.register(PatchOperation.STD_TEST, (target, operation) -> {
            Object node = operation.getPath().getNode(target);
            if (!Nodes.equals(node, operation.getValue())) {
                throw new JsonException("'test' operation failed at path " + operation.getPath() + ": expected " +
                        operation.getValue() + ", found " + node);
            }
        });

        // add
        OperationRegistry.register(PatchOperation.STD_ADD, (target, operation) -> {
            operation.getPath().add(target, operation.getValue());
        });

        // remove
        OperationRegistry.register(PatchOperation.STD_REMOVE, (target, operation) -> {
            JsonPointer path = operation.getPath();
            if (!path.contains(target)) {
                throw new JsonException("'remove' operation failed at path " + path + ": no value exist");
            }
            path.remove(target);
        });

        // replace
        OperationRegistry.register(PatchOperation.STD_REPLACE, (target, operation) -> {
            operation.getPath().replace(target, operation.getValue());
        });

        // copy
        OperationRegistry.register(PatchOperation.STD_COPY, (target, operation) -> {
            Object value = operation.getFrom().getNode(target);
            if (value == null) throw new JsonException("'copy' operation failed at from " + operation.getFrom() +
                    ": no value exist");
            operation.getPath().add(target, value);
        });

        // move
        OperationRegistry.register(PatchOperation.STD_MOVE, (target, operation) -> {
            Object value = operation.getFrom().remove(target);
            if (value == null) {
                throw new JsonException("'move' operation failed at from " + operation.getFrom() + ": no value exist");
            }
            operation.getPath().add(target, value);
        });

        // exist
        OperationRegistry.register(PatchOperation.EXT_EXIST, (target, operation) -> {
            if (!operation.getPath().contains(target)) {
                throw new JsonException("'exist' operation failed at path " + operation.getPath());
            }
        });

        // ensurePut
        OperationRegistry.register(PatchOperation.EXT_ENSURE_PUT, (target, operation) -> {
            operation.getPath().ensurePut(target, operation.getValue());
        });

    }

}
