package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Runtime wrapper around a validating node with cached type metadata.
 * <p>
 * Also carries mutable per-validation state such as evaluated-location marks
 * and recursion-detection stack.
 */
public final class InstancedNode {
    private final Object node;
    private final Class<?> objectType;
    private final JsonType jsonType;
    private final NodeKind nodeKind;
    private final boolean converted;
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
     * <p>
     * Registered value-node types are first encoded to raw values for schema
     * validation against JSON-compatible representation.
     */
    public static InstancedNode infer(Object node) {
        if (node == null) return NULL.reset();
        NodeKind nodeKind = NodeKind.of(node);
        boolean encoded = false;
        if (nodeKind == NodeKind.VALUE_NODE_VALUE) {
            node = NodeRegistry.getValueCodecInfo(node.getClass()).valueToRaw(node);
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
     * Returns true when value-codec encoding was applied in {@link #infer(Object)}.
     */
    public boolean isConverted() {return converted;}

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
     * <p>
     * Called only when unevaluated* keywords are present.
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
     * Merges all evaluated frames into one BitSet snapshot.
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
     * Returns child instance for an object key.
     * <p>
     * Encoded children are cached by key to avoid repeated value-codec encoding.
     */
    public InstancedNode inferSubByKey(String key, Object subNode) {
        if (jsonType != JsonType.OBJECT) throw new IllegalArgumentException("Cannot inferSubByKey: jsonType != OBJECT");
        InstancedNode subInstance = null;
        if (subInstanceCache != null) subInstance = subInstanceCache.get(key);
        if (subInstance == null) {
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.isConverted()) {
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
    public InstancedNode inferSubByIndex(int idx, Object subNode) {
        if (jsonType != JsonType.ARRAY) throw new IllegalArgumentException("Cannot inferSubByIndex: jsonType != ARRAY");
        InstancedNode subInstance = null;
        if (subInstanceCache != null) subInstance = subInstanceCache.get(idx);
        if (subInstance == null) {
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.isConverted()) {
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
    public boolean isRecursiveRef(Object schema) {
        if (refSchemaTimes++ > 0) {
            if (refSchemaStack == null) refSchemaStack = new ArrayDeque<>();
            if (refSchemaStack.contains(schema)) return true;
            else refSchemaStack.push(schema);
        }
        return false;
    }

}
