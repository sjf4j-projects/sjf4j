package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.exception.NodeException;
import org.sjf4j.NodeKind;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.path.PathSegment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime wrapper around the representation validated by a schema, with cached
 * type metadata.
 * <p>
 * Also carries mutable per-validation state such as evaluated-location marks
 * and recursion-detection stack. Instances are validation-scoped and not
 * thread-safe. A {@code @NodeValue} domain instance is encoded through its value
 * binding before this wrapper is created, so evaluators inspect the raw OBNT
 * representation rather than the domain instance.
 */
public final class Instance {
    private Object node;
    private JsonType jsonType;
    private boolean converted;

    private Map<Object, Instance> subInstanceCache;
    private Instance pathParent;
    private String pathKey;
    private int pathIndex;
    private boolean pathByIndex;

    // runtime state
    private Deque<EvaluationBits> evaluatedStack;
    // Active references for this instance only; the common single-ref case
    // needs no stack allocation.
    private SchemaPlan firstActiveRef;
    private Deque<SchemaPlan> nestedActiveRefs;


    private Instance(Object node, JsonType jsonType, boolean converted) {
        this.node = node;
        this.jsonType = jsonType;
        this.converted = converted;
    }

    /** Returns the representation evaluated by schema keywords. */
    public Object node() {
        return node;
    }
    /** Returns the JSON-semantic shape of the evaluated representation. */
    public JsonType jsonType() {
        return jsonType;
    }
    /** Returns whether a value binding encoded the domain node into raw OBNT. */
    public boolean converted() {
        return converted;
    }

    Instance reuse(Object node, JsonType jsonType, boolean converted) {
        this.node = node;
        this.jsonType = jsonType;
        this.converted = converted;
        return reset();
    }

    /**
     * Resets mutable validation state for wrapper reuse.
     * <p>
     * Node/type metadata is preserved.
     */
    Instance reset() {
        this.evaluatedStack = null;
        this.firstActiveRef = null;
        this.nestedActiveRefs = null;
        return this;
    }

    private Instance clearPath() {
        this.pathParent = null;
        this.pathKey = null;
        this.pathIndex = 0;
        this.pathByIndex = false;
        return this;
    }

    private Instance bindPathKey(Instance pathParent, String pathKey) {
        this.pathParent = pathParent;
        this.pathKey = pathKey;
        this.pathByIndex = false;
        return this;
    }

    private Instance bindPathIndex(Instance pathParent, int pathIndex) {
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

    static Instance infer(Object node) {
        return infer(node, (ValidationContext) null);
    }

    /**
     * Infers node metadata and wraps it as an Instance.
     * <p>
     * Registered logical value node types are first encoded to raw values for
     * schema validation. The raw value is not recursively rebound or copied
     * before evaluation. Child wrappers created
     * later from converted values can be cached and reused within the same
     * validation traversal.
     */
    static Instance infer(Object node, ValidationContext context) {
        Class<?> clazz = node == null ? null : node.getClass();
        NodeKind kind = clazz == null ? NodeKind.VALUE_NULL : NodeKind.plainOf(clazz);
        if (kind == NodeKind.UNKNOWN) {
            TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
            if (ti.valueSet != null) {
                ValueInfo vi = ti.valueSet.defaultInfo;
                if (vi != null) {
                    node = vi.valueToRaw(node);
                    JsonType jsonType = node == null
                            ? JsonType.NULL
                            : JsonType.of(NodeKind.plainOf(vi.rawClazz));
                    return new Instance(node, jsonType, true);
                }
                kind = NodeKind.VALUE_BINDING;
            } else if (ti.externalNode != null) {
                kind = ti.externalNode.nodeKind(node);
            } else if (ti.pojoInfo != null) {
                kind = NodeKind.OBJECT_POJO;
            }
        }
        JsonType jsonType = JsonType.of(kind);
        if (jsonType.isValue() && context != null) {
            return context.reusedLeaf().reuse(node, jsonType, false);
        }
        return new Instance(node, jsonType, false);
    }

    /**
     * Marks one property/item index as evaluated.
     */
    void markEvaluated(int propIdx) {
        if (evaluatedStack == null) return;
        EvaluationBits evaluated = evaluatedStack.peek();
        if (evaluated == null) return;
        evaluated.set(propIdx);
    }
    
    /**
     * Marks a range of property/item indexes as evaluated.
     */
    void markEvaluated(int fromIdx, int toIdx) {
        if (evaluatedStack == null) return;
        EvaluationBits evaluated = evaluatedStack.peek();
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
        evaluatedStack.push(new EvaluationBits());
    }

    /**
     * Pushes an empty evaluated frame.
     */
    void pushEvaluated() {
        if (evaluatedStack == null) return;
        evaluatedStack.push(new EvaluationBits());
    }

    /**
     * Pushes an existing evaluated frame.
     */
    void pushEvaluated(EvaluationBits evaluated) {
        if (evaluatedStack == null) return;
        evaluatedStack.push(evaluated);
    }

    /**
     * Pops the current evaluated frame.
     */
    EvaluationBits popEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.pop();
    }

    /**
     * Returns the current evaluated frame.
     */
    EvaluationBits peekEvaluated() {
        if (evaluatedStack == null || evaluatedStack.isEmpty()) return null;
        return evaluatedStack.peek();
    }

    /**
     * Merges all evaluated frames into one EvaluationBits snapshot.
     */
    EvaluationBits mergedEvaluated() {
        EvaluationBits merged = new EvaluationBits();
        if (evaluatedStack != null) {
            for (EvaluationBits bs : evaluatedStack) merged.or(bs);
        }
        return merged;
    }

    /**
     * Returns child instance for an object key.
     * <p>
     * Encoded children are cached by key to avoid repeated value-binding encoding.
     */
    Instance inferSubByKey(String key, Object subNode, ValidationContext context) {
        if (jsonType != JsonType.OBJECT)
            throw new NodeException("Type mismatch: inferSubByKey() requires OBJECT node, but was " + jsonType);
        if (subInstanceCache != null) {
            Instance subInstance = subInstanceCache.get(key);
            if (subInstance != null) return subInstance.reset();
        }

        Instance subInstance = Instance.infer(subNode, context).bindPathKey(this, key);
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
    Instance inferSubByIndex(int idx, Object subNode, ValidationContext context) {
        if (jsonType != JsonType.ARRAY)
            throw new NodeException("Type mismatch: inferSubByIndex() requires ARRAY node, but was " + jsonType);
        if (subInstanceCache != null) {
            Instance subInstance = subInstanceCache.get(idx);
            if (subInstance != null) return subInstance.reset();
        }

        Instance subInstance = Instance.infer(subNode, context).bindPathIndex(this, idx);
        if (subInstance.converted) {
            if (subInstanceCache == null) subInstanceCache = new HashMap<>();
            subInstanceCache.put(idx, subInstance);
        }
        return subInstance;
    }


    /**
     * Checks only the active reference chain for this instance. Sequential
     * references to the same plan are valid; nested references to an active
     * plan without advancing to a child instance are cyclic.
     */
    void enterRef(SchemaPlan plan, PathSegment keywordPs) {
        if (firstActiveRef == null) {
            firstActiveRef = plan;
            return;
        }
        if (firstActiveRef == plan || (nestedActiveRefs != null && nestedActiveRefs.contains(plan))) {
            throw new NodeException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                    "cyclic schema reference detected", keywordPs, plan.schemaUri));
        }
        if (nestedActiveRefs == null) nestedActiveRefs = new ArrayDeque<>();
        nestedActiveRefs.push(plan);
    }

    /** Leaves the reference entered by the matching enterRef call. */
    void leaveRef() {
        if (nestedActiveRefs != null && !nestedActiveRefs.isEmpty()) {
            nestedActiveRefs.pop();
        } else {
            firstActiveRef = null;
        }
    }

}
