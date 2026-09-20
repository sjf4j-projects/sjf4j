package org.sjf4j.external;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.NodeException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Setup-time registry for ServiceLoader-discovered external node classifiers.
 *
 */
public final class ExternalNodeRegistry {

    private ExternalNodeRegistry() {}

    private static final Map<Class<?>, ExternalNode<?>> EXTERNAL_NODES = new ConcurrentHashMap<>();

    static {
        ClassLoader loader = ExternalNodeProvider.class.getClassLoader();
        for (ExternalNodeProvider provider : ServiceLoader.load(ExternalNodeProvider.class, loader)) {
            ExternalNode<?> externalNode = provider.externalNode();
            if (externalNode == null) continue;

            Class<?> nodeType = externalNode.nodeType();
            if (nodeType == null) {
                throw new NodeException("external node type must not be null");
            }
            if (nodeType == Object.class || NodeKind.plainOf(nodeType) != NodeKind.UNKNOWN) {
                throw new NodeException("external node type must not be a native OBNT type: '" + nodeType.getName() + "'");
            }
            if (EXTERNAL_NODES.put(nodeType, externalNode) != null) {
                throw new NodeException("external node already registered for type '" + nodeType.getName() + "'");
            }
        }
    }

    /**
     * Returns the classifier whose discovered root type accepts {@code nodeType},
     * or {@code null} when no classifier is available.
     */
    public static ExternalNode<?> resolve(Class<?> nodeType) {
        for (Map.Entry<Class<?>, ExternalNode<?>> entry : EXTERNAL_NODES.entrySet()) {
            Class<?> candidateType = entry.getKey();
            if (!candidateType.isAssignableFrom(nodeType)) {
                continue;
            }
            return entry.getValue();
        }
        return null;
    }


}
