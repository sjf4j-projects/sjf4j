package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeKind;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime wrapper around a validating node with cached type metadata.
 * <p>
 * Also carries mutable per-validation state such as evaluated-location marks
 * and recursion-detection stack.
 */
public final class InstancedNode {
    final Object node;
    final Class<?> objectType;
    final JsonType jsonType;
    final NodeKind nodeKind;
    final boolean converted;

    private Map<Object, InstancedNode> subInstanceCache;

    // runtime state
    private Deque<BitSet> evaluatedStack;
    private int refSchemaTimes = 0;
    private Deque<Object> refSchemaStack;


    private InstancedNode(Object node, JsonType jsonType, NodeKind nodeKind, boolean converted) {
        this.node = node;
        this.objectType = node == null ? null : node.getClass();
        this.jsonType = jsonType;
        this.nodeKind = nodeKind;
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

    // NULL
    static final InstancedNode NULL = new InstancedNode(null, JsonType.NULL, null, false);

    /**
     * Infers node metadata and wraps it as an InstancedNode.
     * <p>
     * Registered value-node types are first encoded to raw values for schema
     * validation against JSON-compatible representation.
     */
    static InstancedNode infer(Object node) {
        if (node == null) return NULL.reset();
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
        return new InstancedNode(node, JsonType.of(nodeKind), nodeKind, encoded);
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

//    void addEvaluatedProperty(String key) {
//        if (evaluatedProperties == null)
//            evaluatedProperties = new HashSet<>();
//        evaluatedProperties.add(key);
//    }
//    Set<String> getEvaluatedProperties() {return evaluatedProperties;}
//
//    void addEvaluatedItem(int idx) {
//        if (evaluatedItems == null) {
//            evaluatedItems = new BitSet();
//        }
//        evaluatedItems.set(idx);
//    }
//    void addEvaluatedItems(int fromIdx, int toIdx) {
//        if (evaluatedItems == null) {
//            evaluatedItems = new BitSet();
//        }
//        evaluatedItems.set(fromIdx, toIdx);
//    }
//    BitSet getEvaluatedItems() {return evaluatedItems;}

    /**
     * Returns child instance for an object key.
     * <p>
     * Encoded children are cached by key to avoid repeated value-codec encoding.
     */
    InstancedNode inferSubByKey(String key, Object subNode) {
        if (jsonType != JsonType.OBJECT)
            throw new JsonException("Type mismatch: inferSubByKey() requires OBJECT node, but was " + jsonType);
        InstancedNode subInstance = null;
        if (subInstanceCache != null) subInstance = subInstanceCache.get(key);
        if (subInstance == null) {
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.converted) {
                    if (subInstanceCache == null) subInstanceCache = new HashMap<>();
                    subInstanceCache.put(key, subInstance);
                }
            }
        }
        return subInstance == null ? NULL.reset() : subInstance.reset();
    }

    /**
     * Returns child instance for an array index.
     * <p>
     * Encoded children are cached by index key to avoid repeated encoding.
     */
    InstancedNode inferSubByIndex(int idx, Object subNode) {
        if (jsonType != JsonType.ARRAY)
            throw new JsonException("Type mismatch: inferSubByIndex() requires ARRAY node, but was " + jsonType);
        InstancedNode subInstance = null;
        if (subInstanceCache != null) subInstance = subInstanceCache.get(idx);
        if (subInstance == null) {
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.converted) {
                    if (subInstanceCache == null) subInstanceCache = new HashMap<>();
                    subInstanceCache.put(idx, subInstance);
                }
            }
        }
        return subInstance  == null ? NULL.reset() : subInstance.reset();
    }

    // refSchema
    /**
     * Returns true when recursive schema-reference cycle is detected.
     * <p>
     * First invocation initializes tracking; subsequent invocations check whether
     * the same schema object appears again in current reference chain.
     */
    boolean isRecursiveRef(Object schema) {
        if (refSchemaTimes++ > 0) {
            if (refSchemaStack == null) refSchemaStack = new ArrayDeque<>();
            if (refSchemaStack.contains(schema)) return true;
            else refSchemaStack.push(schema);
        }
        return false;
    }

}
