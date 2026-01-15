package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InstancedNode {
    private final Object node;
    private final JsonType jsonType;
    private final NodeType nodeType;
    private final boolean encoded;

    // runtime evaluation state
    private Set<String> evaluatedProperties;
    private int evaluatedItems = 0;
    private Map<String, InstancedNode> subInstanceCache;

    private InstancedNode(Object node, JsonType jsonType, NodeType nodeType, boolean encoded) {
        this.node = node;
        this.jsonType = jsonType;
        this.nodeType = nodeType;
        this.encoded = encoded;
    }

    // NULL
    public static final InstancedNode NULL = new InstancedNode(null, JsonType.NULL, null, false);

    public static InstancedNode infer(Object node) {
        if (node == null) return NULL;
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

    public void addEvaluatedProperty(String key) {
        if (evaluatedProperties == null)
            evaluatedProperties = new HashSet<>();
        evaluatedProperties.add(key);
    }
    public Set<String> getEvaluatedProperties() {return evaluatedProperties;}

    public void setEvaluatedItems(int evaluatedItems) {
        this.evaluatedItems = evaluatedItems;
    }
    public int getEvaluatedItems() {return evaluatedItems;}

    public InstancedNode getSubByKey(String key) {
        if (jsonType != JsonType.OBJECT) return NULL;
        InstancedNode subInstance = null;
        if (subInstanceCache != null) subInstance = subInstanceCache.get(key);
        if (subInstance == null) {
            Object subNode = NodeWalker.getInObject(node, key);
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.isEncoded()) {
                    if (subInstanceCache == null) subInstanceCache = new HashMap<>();
                    subInstanceCache.put(key, subInstance);
                }
            }
        }
        return subInstance == null ? NULL : subInstance;
    }

    public InstancedNode getSubByIndex(int idx) {
        if (jsonType != JsonType.ARRAY) return NULL;
        InstancedNode subInstance = null;
        String key = String.valueOf(idx);
        if (subInstanceCache != null) subInstance = subInstanceCache.get(key);
        if (subInstance == null) {
            Object subNode = NodeWalker.getInArray(node, idx);
            if (subNode != null) {
                subInstance = InstancedNode.infer(subNode);
                if (subInstance.isEncoded()) {
                    if (subInstanceCache == null) subInstanceCache = new HashMap<>();
                    subInstanceCache.put(key, subInstance);
                }
            }
        }
        return subInstance  == null ? NULL : subInstance;
    }


}
