package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.path.PathSegment;
import org.sjf4j.value.ValueInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Reusable view of one active instance location. Schema execution state belongs
 * to ValidationContext. Frames are pooled by instance depth, not node count.
 */
final class NodeFrame {
    private Object node;
    private JsonType jsonType;
    private boolean converted;
    private NodeFrame pathParent;
    private String pathKey;
    private int pathIndex;
    private boolean pathByIndex;

    // Cached raw results survive repeated schema branches on the same instance.
    // Never cache a pooled NodeFrame: its contents are rebound for siblings.
    private Map<Object, CachedValue> convertedChildren;
    private CachedValue cachedValue;

    private static final class CachedValue {
        final Object raw;
        final JsonType type;
        final boolean converted;
        Map<Object, CachedValue> children;

        CachedValue(Object raw, JsonType type, boolean converted) {
            this.raw = raw;
            this.type = type;
            this.converted = converted;
        }
    }

    Object node() { return node; }
    JsonType jsonType() { return jsonType; }
    boolean converted() { return converted; }

    static NodeFrame infer(Object value) {
        return new NodeFrame().bind(value);
    }

    NodeFrame bind(Object value) {
        pathParent = null;
        pathKey = null;
        pathIndex = 0;
        pathByIndex = false;
        convertedChildren = null;
        cachedValue = null;
        converted = false;
        node = value;

        if (value == null) {
            jsonType = JsonType.NULL;
        } else if (value instanceof String || value instanceof Character
                || value instanceof Enum<?>) {
            jsonType = JsonType.STRING;
        } else if (value instanceof Number) {
            jsonType = JsonType.NUMBER;
        } else if (value instanceof Boolean) {
            jsonType = JsonType.BOOLEAN;
        } else if (value instanceof Map<?, ?>
                || value instanceof org.sjf4j.JsonObject) {
            jsonType = JsonType.OBJECT;
        } else if (value instanceof java.util.List<?>
                || value instanceof org.sjf4j.JsonArray
                || value.getClass().isArray() || value instanceof java.util.Set<?>) {
            jsonType = JsonType.ARRAY;
        } else {
            TypeInfo ti = TypeRegistry.registerTypeInfo(value.getClass());
            if (ti.valueSet != null) {
                ValueInfo vi = ti.valueSet.defaultInfo;
                if (vi != null) {
                    node = vi.valueToRaw(value);
                    jsonType = node == null ? JsonType.NULL
                            : JsonType.of(NodeKind.plainOf(vi.rawClazz));
                    converted = true;
                    return this;
                }
            }
            jsonType = ti.externalNode != null
                    ? JsonType.of(ti.externalNode.nodeKind(value))
                    : ti.pojoInfo != null ? JsonType.OBJECT : JsonType.UNKNOWN;
        }
        return this;
    }

    NodeFrame bindKey(NodeFrame parent, String key, Object value) {
        bindChild(parent, key, value);
        pathParent = parent;
        pathKey = key;
        pathByIndex = false;
        return this;
    }

    NodeFrame bindIndex(NodeFrame parent, int index, Object value) {
        bindChild(parent, index, value);
        pathParent = parent;
        pathIndex = index;
        pathByIndex = true;
        return this;
    }

    private void bindChild(NodeFrame parent, Object key, Object value) {
        CachedValue cached = parent.convertedChildren == null
                ? null : parent.convertedChildren.get(key);
        if (cached != null && (cached.converted || cached.raw == value)) {
            node = cached.raw;
            jsonType = cached.type;
            converted = cached.converted;
            convertedChildren = cached.children;
            cachedValue = cached;
        } else {
            bind(value);
            if (converted) {
                cached = new CachedValue(node, jsonType, true);
                if (parent.convertedChildren == null) {
                    parent.convertedChildren = new HashMap<>();
                    if (parent.cachedValue != null) {
                        parent.cachedValue.children = parent.convertedChildren;
                    }
                }
                parent.convertedChildren.put(key, cached);
                cachedValue = cached;
            }
        }
    }

    /**
     * Preserve nested conversion caches when an ordinary container frame is
     * reused for a sibling. No cache record is allocated for unconverted nodes
     * unless one of their descendants actually needed value encoding.
     */
    void finishChild() {
        if (pathParent == null || convertedChildren == null || cachedValue != null) return;

        CachedValue saved = new CachedValue(node, jsonType, false);
        saved.children = convertedChildren;
        Object key = pathByIndex ? Integer.valueOf(pathIndex) : pathKey;
        if (pathParent.convertedChildren == null) {
            pathParent.convertedChildren = new HashMap<>();
            if (pathParent.cachedValue != null) {
                pathParent.cachedValue.children = pathParent.convertedChildren;
            }
        }
        pathParent.convertedChildren.put(key, saved);
    }

    PathSegment materializePath() {
        if (pathParent == null) return PathSegment.Root.INSTANCE;
        PathSegment parent = pathParent.materializePath();
        return pathByIndex
                ? new PathSegment.Index(parent, pathIndex)
                : new PathSegment.Name(parent, pathKey);
    }
}
