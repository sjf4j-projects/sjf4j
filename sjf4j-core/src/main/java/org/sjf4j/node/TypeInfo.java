package org.sjf4j.node;

import org.sjf4j.external.ExternalNode;

/**
 * Cached classification and metadata for a Java type.
 *
 * <p>Value codecs may have a default and named variants. At most one of the
 * polymorphic, container, and object-binding metadata fields is populated.</p>
 */
public class TypeInfo {
    public final Class<?> clazz;
    public final NodeValueInfo[] nodeValueInfos;
    public final OneOfInfo oneOfInfo;
    public final ContainerInfo containerInfo;
    public final PojoInfo pojoInfo;
    public final ExternalNode<?> externalNode;

    static final TypeInfo NONE = new TypeInfo(Object.class, null,
            null, null, null, null);

    /**
     * Creates type metadata for the supplied classification, including an
     * external node classifier when applicable.
     */
    public TypeInfo(Class<?> clazz, NodeValueInfo[] nodeValueInfos,
                    OneOfInfo oneOfInfo, ContainerInfo containerInfo, PojoInfo pojoInfo,
                    ExternalNode<?> externalNode) {
        this.clazz = clazz;
        this.nodeValueInfos = nodeValueInfos;
        this.oneOfInfo = oneOfInfo;
        this.containerInfo = containerInfo;
        this.pojoInfo = pojoInfo;
        this.externalNode = externalNode;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Returns true when object reads must stay on the framework-owned path.
     * Native backend modules may only bypass SJF4J when this is false.
     */
    public boolean requiresPojoReader() {
        return pojoInfo != null && pojoInfo.requiresPojoReader;
    }

    /**
     * Returns true when object writes must stay on the framework-owned path.
     * Native backend modules may only bypass SJF4J when this is false.
     */
    public boolean requiresPojoWriter() {
        return pojoInfo != null && pojoInfo.requiresPojoWriter;
    }

    public boolean hasValueCodecs() {
        return nodeValueInfos != null;
    }

    /**
     * Returns the default or named value codec metadata, or {@code null} when
     * no codec is registered for the requested format.
     */
    public NodeValueInfo getNodeValueInfo(String valueFormat) {
        if (nodeValueInfos == null) return null;
        if (valueFormat == null) return nodeValueInfos[0];
        for (NodeValueInfo info : nodeValueInfos) {
            if (info.valueFormat.equals(valueFormat)) {
                return info;
            }
        }
        return null;
    }

}
