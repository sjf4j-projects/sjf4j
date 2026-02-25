package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around a runtime JSON node with cached type information.
 */
public final class InstancedNode {
    private final Object node;
    private final Class<?> objectType;
    private final JsonType jsonType;
    private final NodeKind nodeKind;
    private final boolean encoded;
    private Map<String, InstancedNode> subInstanceCache;

    // runtime state
    private Deque<BitSet> evaluatedStack;
    private int refSchemaTimes = 0;
    private Deque<Object> refSchemaStack;


    private InstancedNode(Object node, JsonType jsonType, NodeKind nodeKind, boolean encoded) {
        this.node = node;
        this.objectType = node == null ? null : node.getClass();
        this.jsonType = jsonType;
        this.nodeKind = nodeKind;
        this.encoded = encoded;
    }

    /**
     * Resets runtime validation state for reuse.
     */
    public InstancedNode reset() {
        this.evaluatedStack = null;
        this.refSchemaTimes = 0;
        this.refSchemaStack = null;
        return this;
    }

    // NULL
    public static final InstancedNode NULL = new InstancedNode(null, JsonType.NULL, null, false);

    /**
     * Infers node metadata and wraps it as an InstancedNode.
     */
    public static InstancedNode infer(Object node) {
        if (node == null) return NULL.reset();
        NodeKind nodeKind = NodeKind.of(node);
        boolean encoded = false;
        if (nodeKind == NodeKind.VALUE_REGISTERED) {
            node = NodeRegistry.getValueCodecInfo(node.getClass()).encode(node);
            nodeKind = NodeKind.of(node);
            encoded = true;
        }
        return new InstancedNode(node, JsonType.of(nodeKind), nodeKind, encoded);
    }

    /**
     * Returns the wrapped runtime node.
     */
    public Object getNode() {return node;}
    /**
     * Returns the runtime object type.
     */
    public Class<?> getObjectType() {return objectType;}
    /**
     * Returns the inferred JSON type.
     */
    public JsonType getJsonType() {return jsonType;}
    /**
     * Returns the inferred node kind.
     */
    public NodeKind getNodeType() {return nodeKind;}
    /**
     * Returns true when value codec encoding was applied.
     */
    public boolean isEncoded() {return encoded;}

    /**
     * Marks one property/item index as evaluated.
     */
    public void markEvaluated(int propIdx) {
        if (evaluatedStack == null) return;
        BitSet evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(propIdx);
    }
    /**
     * Marks a range of property/item indexes as evaluated.
     */
    public void markEvaluated(int fromIdx, int toIdx) {
        if (evaluatedStack == null) return;
        BitSet evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(fromIdx, toIdx);
    }
    /**
     * Initializes evaluated tracking with a fresh frame.
     */
    public void createEvaluated() {
        if (evaluatedStack == null) evaluatedStack = new ArrayDeque<>();
        evaluatedStack.push(new BitSet());
    }
    /**
     * Pushes an empty evaluated frame.
     */
    public void pushEvaluated() {
        if (evaluatedStack == null) return;
        evaluatedStack.push(new BitSet());
    }
    /**
     * Pushes an existing evaluated frame.
     */
    public void pushEvaluated(BitSet evaluated) {
        if (evaluatedStack == null) return;
        evaluatedStack.push(evaluated);
    }
    /**
     * Pops the current evaluated frame.
     */
    public BitSet popEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.pop();
    }
    /**
     * Returns the current evaluated frame.
     */
    public BitSet peekEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.peek();
    }
    /**
     * Merges all evaluated frames into one BitSet.
     */
    public BitSet mergedEvaluated() {
        BitSet merged = new BitSet();
        if (evaluatedStack != null) {
            for (BitSet bs : evaluatedStack) merged.or(bs);
        }
        return merged;
    }

//    public void addEvaluatedProperty(String key) {
//        if (evaluatedProperties == null)
//            evaluatedProperties = new HashSet<>();
//        evaluatedProperties.add(key);
//    }
//    public Set<String> getEvaluatedProperties() {return evaluatedProperties;}
//
//    public void addEvaluatedItem(int idx) {
//        if (evaluatedItems == null) {
//            evaluatedItems = new BitSet();
//        }
//        evaluatedItems.set(idx);
//    }
//    public void addEvaluatedItems(int fromIdx, int toIdx) {
//        if (evaluatedItems == null) {
//            evaluatedItems = new BitSet();
//        }
//        evaluatedItems.set(fromIdx, toIdx);
//    }
//    public BitSet getEvaluatedItems() {return evaluatedItems;}

    /**
     * Returns a cached child instance for an object key when available.
     */
    public InstancedNode getSubByKey(String key) {
        if (jsonType != JsonType.OBJECT) return NULL.reset();
        InstancedNode subInstance = null;
        if (subInstanceCache != null) subInstance = subInstanceCache.get(key);
        if (subInstance == null) {
            Object subNode = Nodes.getInObject(node, key);
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.isEncoded()) {
                    if (subInstanceCache == null) subInstanceCache = new HashMap<>();
                    subInstanceCache.put(key, subInstance);
                }
            }
        }
        return subInstance == null ? NULL.reset() : subInstance.reset();
    }

    /**
     * Returns a cached child instance for an array index when available.
     */
    public InstancedNode getSubByIndex(int idx) {
        if (jsonType != JsonType.ARRAY) return NULL.reset();
        InstancedNode subInstance = null;
        String key = String.valueOf(idx);
        if (subInstanceCache != null) subInstance = subInstanceCache.get(key);
        if (subInstance == null) {
            Object subNode = Nodes.getInArray(node, idx);
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.isEncoded()) {
                    if (subInstanceCache == null) subInstanceCache = new HashMap<>();
                    subInstanceCache.put(key, subInstance);
                }
            }
        }
        return subInstance  == null ? NULL.reset() : subInstance.reset();
    }

    // refSchema
    /**
     * Returns true when a recursive schema reference is detected.
     */
    public boolean isRecursiveRef(Object schema) {
        if (refSchemaTimes++ > 0) {
            if (refSchemaStack == null) refSchemaStack = new ArrayDeque<>();
            if (refSchemaStack.contains(schema)) return true;
            else refSchemaStack.push(schema);
        }
        return false;
    }

}
