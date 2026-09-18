package org.sjf4j.external;

import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;

/**
 * Classifies nodes owned by an external node model.
 *
 * <p>Adapters must be registered before their node classes are given to SJF4J.
 * This initial integration phase only classifies nodes; traversal and mutation
 * are not provided yet.</p>
 *
 * @param <N> root node type handled by this adapter
 */
public interface ExternalNode<N> {
    /** Returns the root node class handled by this adapter. */
    Class<N> rootType();

    /** Returns the JSON-semantic type of a node handled by this adapter. */
    JsonType jsonType(N node);

    /**
     * Returns the JSON-semantic type implied by a handled runtime class.
     * Adapters whose root type has multiple runtime shapes should override this
     * method for concrete shape classes; the default is intentionally unknown.
     */
    default JsonType jsonTypeOfClass(Class<?> nodeType) {
        return JsonType.UNKNOWN;
    }

    default NodeKind nodeKind(N node) {
        switch (jsonType(node)) {
            case OBJECT: return NodeKind.OBJECT_EXTERNAL;
            case ARRAY: return NodeKind.ARRAY_EXTERNAL;
            case STRING: return NodeKind.VALUE_STRING_EXTERNAL;
            case NUMBER: return NodeKind.VALUE_NUMBER_EXTERNAL;
            case BOOLEAN: return NodeKind.VALUE_BOOLEAN_EXTERNAL;
            case NULL: return NodeKind.VALUE_NULL;
            default: return NodeKind.UNKNOWN;
        }
    }

}
