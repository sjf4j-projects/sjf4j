package org.sjf4j.node.external;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.JsonException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Setup-time registry for external node classifiers.
 *
 * <p>Registration must complete before node classes are used by SJF4J. Adapters
 * are retained by {@link org.sjf4j.node.TypeInfo} while type metadata is built;
 * adding adapters later does not invalidate existing type metadata.</p>
 */
public final class ExternalNodeRegistry {
    private static final ConcurrentMap<Class<?>, ExternalNode<?>> REGISTERED = new ConcurrentHashMap<>();

    private ExternalNodeRegistry() {}

    /**
     * Registers an external node classifier. Only one classifier may use a
     * particular root node class.
     */
    public static void register(ExternalNode<?> externalNode) {
        Objects.requireNonNull(externalNode, "externalNode");
        Class<?> nodeType = Objects.requireNonNull(externalNode.rootType(), "externalNode.nodeType()");
        if (nodeType == Object.class || NodeKind.plainOf(nodeType) != NodeKind.UNKNOWN) {
            throw new JsonException("external node type must not be a native OBNT type: '" + nodeType.getName() + "'");
        }
        if (REGISTERED.putIfAbsent(nodeType, externalNode) != null) {
            throw new JsonException("external node already registered for type '" + nodeType.getName() + "'");
        }
    }

    /**
     * Returns the classifier whose registered root type accepts {@code nodeType},
     * or {@code null} when no classifier is registered.
     */
    public static ExternalNode<?> resolve(Class<?> nodeType) {
        ExternalNode<?> result = null;
        for (Map.Entry<Class<?>, ExternalNode<?>> entry : REGISTERED.entrySet()) {
            Class<?> candidateType = entry.getKey();
            if (!candidateType.isAssignableFrom(nodeType)) continue;
            ExternalNode<?> externalNode = entry.getValue();
            if (result == null || result.rootType().isAssignableFrom(candidateType)) {
                result = externalNode;
            } else if (!candidateType.isAssignableFrom(result.rootType())) {
                throw new JsonException("ambiguous external node type '" + nodeType.getName() + "'");
            }
        }
        return result;
    }
}
