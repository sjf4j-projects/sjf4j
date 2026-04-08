package org.sjf4j.patch;


import org.sjf4j.Sjf4j;
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
     * Applies operation and returns the possibly replaced root document.
     */
    public static Object apply(Object target, PatchOperation operation) {
        Objects.requireNonNull(operation, "operation");
        JsonPointer path = _requirePath(operation);
        try {
            if (_isRoot(path)) {
                return _applyAtRoot(target, operation);
            }
            if (target == null) {
                throw new JsonException("Cannot apply PatchOperation '" + operation.getOp() +
                        "' to null target at non-root path " + path);
            }
            OperationHandler handler = OPERATION_CACHE.get(operation.getOp());
            if (handler == null) throw new JsonException("No OperationHandler for '" + operation.getOp() + "'");
            handler.apply(target, operation);
            return target;
        } catch (Exception e) {
            throw new JsonException("Failed to apply PatchOperation '" + operation.getOp() + "'", e);
        }
    }

    private static Object _applyAtRoot(Object target, PatchOperation operation) {
        switch (operation.getOp()) {
            case PatchOperation.STD_ADD:
            case PatchOperation.STD_REPLACE:
                return operation.getValue();
            case PatchOperation.STD_REMOVE:
                return null;
            case PatchOperation.STD_TEST:
                if (!Nodes.equals(target, operation.getValue())) {
                    throw new JsonException("'test' operation failed at path : expected " +
                            operation.getValue() + ", found " + target);
                }
                return target;
            case PatchOperation.STD_COPY: {
                JsonPointer from = _requireFrom(operation);
                if (!_contains(target, from)) {
                    throw new JsonException("'copy' operation failed at from " + from + ": no value exist");
                }
                return Sjf4j.deepNode(_valueAt(target, from));
            }
            case PatchOperation.STD_MOVE: {
                JsonPointer from = _requireFrom(operation);
                if (from.equals(operation.getPath())) {
                    return target;
                }
                if (!_contains(target, from)) {
                    throw new JsonException("'move' operation failed at from " + from + ": no value exist");
                }
                return _isRoot(from) ? target : from.remove(target);
            }
            case PatchOperation.EXT_EXIST:
                return target;
            default: {
                OperationHandler handler = OPERATION_CACHE.get(operation.getOp());
                if (handler == null) {
                    throw new JsonException("No OperationHandler for '" + operation.getOp() + "'");
                }
                if (target == null) {
                    throw new JsonException("Cannot apply PatchOperation '" + operation.getOp() +
                            "' to null root target");
                }
                handler.apply(target, operation);
                return target;
            }
        }
    }

    private static JsonPointer _requirePath(PatchOperation operation) {
        JsonPointer path = operation.getPath();
        if (path == null) {
            throw new JsonException("PatchOperation '" + operation.getOp() + "' is missing path");
        }
        return path;
    }

    private static JsonPointer _requireFrom(PatchOperation operation) {
        JsonPointer from = operation.getFrom();
        if (from == null) {
            throw new JsonException("PatchOperation '" + operation.getOp() + "' is missing from");
        }
        return from;
    }

    private static boolean _isRoot(JsonPointer pointer) {
        return pointer.depth() == 1;
    }

    private static boolean _contains(Object target, JsonPointer pointer) {
        return _isRoot(pointer) || (target != null && pointer.contains(target));
    }

    private static Object _valueAt(Object target, JsonPointer pointer) {
        return _isRoot(pointer) ? target : pointer.getNode(target);
    }

    private static boolean _isProperPrefix(JsonPointer prefix, JsonPointer pointer) {
        for (JsonPointer parent = pointer.parent(); parent != null; parent = parent.parent()) {
            if (prefix.equals(parent)) {
                return true;
            }
        }
        return false;
    }

    // Pre-register build-in PatchOperations
    static {
        // test
        OperationRegistry.register(PatchOperation.STD_TEST, (target, operation) -> {
            JsonPointer path = _requirePath(operation);
            if (!_contains(target, path)) {
                throw new JsonException("'test' operation failed at path " + path + ": no value exist");
            }
            Object node = _valueAt(target, path);
            if (!Nodes.equals(node, operation.getValue())) {
                throw new JsonException("'test' operation failed at path " + path + ": expected " +
                        operation.getValue() + ", found " + node);
            }
        });

        // add
        OperationRegistry.register(PatchOperation.STD_ADD, (target, operation) -> {
            _requirePath(operation).add(target, operation.getValue());
        });

        // remove
        OperationRegistry.register(PatchOperation.STD_REMOVE, (target, operation) -> {
            JsonPointer path = _requirePath(operation);
            if (!path.contains(target)) {
                throw new JsonException("'remove' operation failed at path " + path + ": no value exist");
            }
            path.remove(target);
        });

        // replace
        OperationRegistry.register(PatchOperation.STD_REPLACE, (target, operation) -> {
            _requirePath(operation).replace(target, operation.getValue());
        });

        // copy
        OperationRegistry.register(PatchOperation.STD_COPY, (target, operation) -> {
            JsonPointer from = _requireFrom(operation);
            if (!_contains(target, from)) {
                throw new JsonException("'copy' operation failed at from " + from + ": no value exist");
            }
            Object value = Sjf4j.deepNode(_valueAt(target, from));
            _requirePath(operation).add(target, value);
        });

        // move
        OperationRegistry.register(PatchOperation.STD_MOVE, (target, operation) -> {
            JsonPointer from = _requireFrom(operation);
            JsonPointer path = _requirePath(operation);
            if (from.equals(path)) {
                return;
            }
            if (_isProperPrefix(from, path)) {
                throw new JsonException("'move' operation failed: from " + from +
                        " is a proper prefix of path " + path);
            }
            if (!_contains(target, from)) {
                throw new JsonException("'move' operation failed at from " + from + ": no value exist");
            }

            Object working = Sjf4j.deepNode(target);
            Object workingValue = from.remove(working);
            path.add(working, workingValue);

            Object value = from.remove(target);
            path.add(target, value);
        });

        // exist
        OperationRegistry.register(PatchOperation.EXT_EXIST, (target, operation) -> {
            JsonPointer path = _requirePath(operation);
            if (!path.contains(target)) {
                throw new JsonException("'exist' operation failed at path " + path);
            }
        });

        // ensurePut
        OperationRegistry.register(PatchOperation.EXT_ENSURE_PUT, (target, operation) -> {
            _requirePath(operation).ensurePut(target, operation.getValue());
        });

    }

}
