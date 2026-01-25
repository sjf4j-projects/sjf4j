package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.Nodes;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InstancedNode {
    private final Object node;
    private final JsonType jsonType;
    private final NodeType nodeType;
    private final boolean encoded;
    private Map<String, InstancedNode> subInstanceCache;

    // runtime state
    private Deque<BitSet> evaluatedStack;
    private int refSchemaTimes = 0;
    private Deque<Object> refSchemaStack;


    private InstancedNode(Object node, JsonType jsonType, NodeType nodeType, boolean encoded) {
        this.node = node;
        this.jsonType = jsonType;
        this.nodeType = nodeType;
        this.encoded = encoded;
    }

    public InstancedNode reset() {
        this.evaluatedStack = null;
        this.refSchemaTimes = 0;
        this.refSchemaStack = null;
        return this;
    }

    // NULL
    public static final InstancedNode NULL = new InstancedNode(null, JsonType.NULL, null, false);

    public static InstancedNode infer(Object node) {
        if (node == null) return NULL.reset();
        NodeType nodeType = NodeType.of(node);
        boolean encoded = false;
        if (nodeType == NodeType.VALUE_NODE_VALUE) {
            node = NodeRegistry.getValueCodecInfo(node.getClass()).encode(node);
            nodeType = NodeType.of(node);
            encoded = true;
        }
        return new InstancedNode(node, JsonType.of(nodeType), nodeType, encoded);
    }

    public Object getNode() {return node;}
    public JsonType getJsonType() {return jsonType;}
    public NodeType getNodeType() {return nodeType;}
    public boolean isEncoded() {return encoded;}

    public void markEvaluated(int propIdx) {
        if (evaluatedStack == null) return;
        BitSet evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(propIdx);
    }
    public void markEvaluated(int fromIdx, int toIdx) {
        if (evaluatedStack == null) return;
        BitSet evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(fromIdx, toIdx);
    }
    public void createEvaluated() {
        if (evaluatedStack == null) evaluatedStack = new ArrayDeque<>();
        evaluatedStack.push(new BitSet());
    }
    public void pushEvaluated() {
        if (evaluatedStack == null) return;
        evaluatedStack.push(new BitSet());
    }
    public void pushEvaluated(BitSet evaluated) {
        if (evaluatedStack == null) return;
        evaluatedStack.push(evaluated);
    }
    public BitSet popEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.pop();
    }
    public BitSet peekEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.peek();
    }
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
    public boolean isRecursiveRef(Object schema) {
        if (refSchemaTimes++ > 0) {
            if (refSchemaStack == null) refSchemaStack = new ArrayDeque<>();
            if (refSchemaStack.contains(schema)) return true;
            else refSchemaStack.push(schema);
        }
        return false;
    }

}
