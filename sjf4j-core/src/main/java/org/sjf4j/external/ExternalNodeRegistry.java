package org.sjf4j.external;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.NodeException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Setup-time registry for ServiceLoader-discovered external node classifiers.
 *
 * <p>Discovery occurs once, on the first {@link #init()} or {@link #resolve(Class)}
 * call, using the class loader that loaded {@link ExternalNodeProvider}; providers
 * must be visible to that loader. An {@link ExternalNodeProvider} may return
 * {@code null} only to report that its optional native model is absent; provider
 * and service configuration errors fail fast.</p>
 */
public final class ExternalNodeRegistry {
    private static volatile Map<Class<?>, ExternalNode<?>> externalNodes;
    private static boolean loading;

    private ExternalNodeRegistry() {}

    /**
     * Returns the classifier whose discovered root type accepts {@code nodeType},
     * or {@code null} when no classifier is available.
     */
    public static ExternalNode<?> resolve(Class<?> nodeType) {
        if (externalNodes == null) {
            init();
        }
        ExternalNode<?> result = null;
        for (Map.Entry<Class<?>, ExternalNode<?>> entry : externalNodes.entrySet()) {
            Class<?> candidateType = entry.getKey();
            if (!candidateType.isAssignableFrom(nodeType)) {
                continue;
            }
            ExternalNode<?> externalNode = entry.getValue();
            if (result == null || result.rootType().isAssignableFrom(candidateType)) {
                result = externalNode;
            } else if (!candidateType.isAssignableFrom(result.rootType())) {
                throw new NodeException("ambiguous external node type '" + nodeType.getName() + "'");
            }
        }
        return result;
    }

    /**
     * Discovers external node providers. This method is idempotent and may be
     * called during application startup to load providers eagerly.
     */
    public static synchronized void init() {
        if (externalNodes != null) return;
        if (loading) throw new NodeException("reentrant external node provider discovery");
        loading = true;
        try {
            Map<Class<?>, ExternalNode<?>> discovered = new LinkedHashMap<>();
            ClassLoader loader = ExternalNodeProvider.class.getClassLoader();
            for (ExternalNodeProvider provider : ServiceLoader.load(ExternalNodeProvider.class, loader)) {
                ExternalNode<?> externalNode = provider.externalNode();
                if (externalNode == null) continue;
                Class<?> nodeType = externalNode.rootType();
                if (nodeType == null) throw new NodeException("external node type must not be null");
                if (nodeType == Object.class || NodeKind.plainOf(nodeType) != NodeKind.UNKNOWN) {
                    throw new NodeException("external node type must not be a native OBNT type: '" + nodeType.getName() + "'");
                }
                if (discovered.put(nodeType, externalNode) != null) {
                    throw new NodeException("external node already registered for type '" + nodeType.getName() + "'");
                }
            }
            externalNodes = Collections.unmodifiableMap(discovered);
        } finally {
            loading = false;
        }
    }


}
