package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeKind;
import org.sjf4j.path.PathSegment;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime wrapper around a validating node with cached type metadata.
 * <p>
 * Also carries mutable per-validation state such as evaluated-location marks
 * and recursion-detection stack. Instances are validation-scoped and not
 * thread-safe.
 */
public final class InstancedNode {
    final Object node;
    final JsonType jsonType;
    final boolean converted;

    private Map<Object, InstancedNode> subInstanceCache;
    private InstancedNode pathParent;
    private String pathKey;
    private int pathIndex;
    private boolean pathByIndex;

    // runtime state
    private Deque<BitSet> evaluatedStack;
    private int refSchemaTimes = 0;
    private Deque<Object> refSchemaStack;


    private InstancedNode(Object node, JsonType jsonType, boolean converted) {
        this.node = node;
        this.jsonType = jsonType;
        this.converted = converted;
    }

    /**
     * Resets mutable validation state for wrapper reuse.
     * <p>
     * Node/type metadata is preserved.
     */
    InstancedNode reset() {
        this.evaluatedStack = null;
        this.refSchemaTimes = 0;
        this.refSchemaStack = null;
        return this;
    }

    private InstancedNode clearPath() {
        this.pathParent = null;
        this.pathKey = null;
        this.pathIndex = 0;
        this.pathByIndex = false;
        return this;
    }

    private InstancedNode bindPathKey(InstancedNode pathParent, String pathKey) {
        this.pathParent = pathParent;
        this.pathKey = pathKey;
        this.pathByIndex = false;
        return this;
    }

    private InstancedNode bindPathIndex(InstancedNode pathParent, int pathIndex) {
        this.pathParent = pathParent;
        this.pathIndex = pathIndex;
        this.pathByIndex = true;
        return this;
    }

    PathSegment materializePath() {
        if (pathParent == null) return PathSegment.Root.INSTANCE;
        PathSegment parentPs = pathParent.materializePath();
        return pathByIndex ? new PathSegment.Index(parentPs, pathIndex) : new PathSegment.Name(parentPs, pathKey);
    }

    // NULL
    static final InstancedNode NULL = new InstancedNode(null, JsonType.NULL, false);

    /**
     * Infers node metadata and wraps it as an InstancedNode.
     * <p>
     * Registered value-node types are first encoded to raw values for schema
     * validation against JSON-compatible representation. Child wrappers created
     * later from converted values can be cached and reused within the same
     * validation traversal.
     */
    static InstancedNode infer(Object node) {
        if (node == null) return NULL.reset().clearPath();
        boolean encoded = false;
        NodeKind nodeKind = NodeKind.of(node);
        if (nodeKind == NodeKind.VALUE_NODE_VALUE) {
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerTypeInfo(node.getClass()).valueCodecInfo;
            if (vci != null) {
                node = vci.valueToRaw(node);
                encoded = true;
                nodeKind = NodeKind.of(node);
            }
        }
        return new InstancedNode(node, JsonType.of(nodeKind), encoded);
    }

    /**
     * Marks one property/item index as evaluated.
     */
    void markEvaluated(int propIdx) {
        if (evaluatedStack == null) return;
        BitSet evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(propIdx);
    }
    
    /**
     * Marks a range of property/item indexes as evaluated.
     */
    void markEvaluated(int fromIdx, int toIdx) {
        if (evaluatedStack == null) return;
        BitSet evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(fromIdx, toIdx);
    }

    /**
     * Initializes evaluated tracking with a fresh frame.
     * <p>
     * Called only when unevaluated* keywords are present.
     */
    void createEvaluated() {
        if (evaluatedStack == null) evaluatedStack = new ArrayDeque<>();
        evaluatedStack.push(new BitSet());
    }

    /**
     * Pushes an empty evaluated frame.
     */
    void pushEvaluated() {
        if (evaluatedStack == null) return;
        evaluatedStack.push(new BitSet());
    }

    /**
     * Pushes an existing evaluated frame.
     */
    void pushEvaluated(BitSet evaluated) {
        if (evaluatedStack == null) return;
        evaluatedStack.push(evaluated);
    }

    /**
     * Pops the current evaluated frame.
     */
    BitSet popEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.pop();
    }

    /**
     * Returns the current evaluated frame.
     */
    BitSet peekEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.peek();
    }

    /**
     * Merges all evaluated frames into one BitSet snapshot.
     */
    BitSet mergedEvaluated() {
        BitSet merged = new BitSet();
        if (evaluatedStack != null) {
            for (BitSet bs : evaluatedStack) merged.or(bs);
        }
        return merged;
    }

    /**
     * Returns child instance for an object key.
     * <p>
     * Encoded children are cached by key to avoid repeated value-codec encoding.
     */
    InstancedNode inferSubByKey(String key, Object subNode) {
        if (jsonType != JsonType.OBJECT)
            throw new JsonException("Type mismatch: inferSubByKey() requires OBJECT node, but was " + jsonType);
        if (subInstanceCache != null) {
            InstancedNode subInstance = subInstanceCache.get(key);
            if (subInstance != null) return subInstance.reset();
        }
        if (subNode == null) return NULL.reset().bindPathKey(this, key);

        InstancedNode subInstance = InstancedNode.infer(subNode).bindPathKey(this, key);
        if (subInstance.converted) {
            if (subInstanceCache == null) subInstanceCache = new HashMap<>();
            subInstanceCache.put(key, subInstance);
        }
        return subInstance;
    }

    /**
     * Returns child instance for an array index.
     * <p>
     * Encoded children are cached by index key to avoid repeated encoding.
     */
    InstancedNode inferSubByIndex(int idx, Object subNode) {
        if (jsonType != JsonType.ARRAY)
            throw new JsonException("Type mismatch: inferSubByIndex() requires ARRAY node, but was " + jsonType);
        if (subInstanceCache != null) {
            InstancedNode subInstance = subInstanceCache.get(idx);
            if (subInstance != null) return subInstance.reset();
        }
        if (subNode == null) return NULL.reset().bindPathIndex(this, idx);

        InstancedNode subInstance = InstancedNode.infer(subNode).bindPathIndex(this, idx);
        if (subInstance.converted) {
            if (subInstanceCache == null) subInstanceCache = new HashMap<>();
            subInstanceCache.put(idx, subInstance);
        }
        return subInstance;
    }


    /**
     * Detects cyclic schema references for the current runtime instance branch.
     * <p>
     * The same compiled plan may be revisited across independent branches, but a
     * recursive revisit while validating one branch indicates an unbounded
     * schema-reference loop.
     */
    void checkCyclicRef(SchemaPlan plan, PathSegment keywordPs) {
        if (refSchemaTimes++ > 0) {
            if (refSchemaStack == null) refSchemaStack = new ArrayDeque<>();
            if (refSchemaStack.contains(plan)) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cyclic schema reference detected", keywordPs, plan.schemaUri));
            } else {
                refSchemaStack.push(plan);
            }
        }
    }

}
