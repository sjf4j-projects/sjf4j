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
    public final NodeValueInfo nodeValueInfo;
    public final NodeValueInfo[] namedValueCodecs;
    public final OneOfInfo oneOfInfo;
    public final ContainerInfo containerInfo;
    public final PojoInfo pojoInfo;
    public final ExternalNode<?> externalNode;

    static final NodeValueInfo[] EMPTY_VALUE_CODECS = new NodeValueInfo[0];
    static final TypeInfo NONE = new TypeInfo(Object.class, null, EMPTY_VALUE_CODECS,
            null, null, null, null);

    /**
     * Creates type metadata for the supplied classification, including an
     * external node classifier when applicable.
     */
    public TypeInfo(Class<?> clazz, NodeValueInfo nodeValueInfo, NodeValueInfo[] namedValueCodecs,
                    OneOfInfo oneOfInfo, ContainerInfo containerInfo, PojoInfo pojoInfo,
                    ExternalNode<?> externalNode) {
        this.clazz = clazz;
        this.nodeValueInfo = nodeValueInfo;
        this.namedValueCodecs = namedValueCodecs == null ? EMPTY_VALUE_CODECS : namedValueCodecs;
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
        return nodeValueInfo != null || namedValueCodecs.length > 0;
    }

    /**
     * Returns the default or named value codec metadata, or {@code null} when
     * no codec is registered for the requested format.
     */
    public NodeValueInfo getValueCodecInfo(String valueFormat) {
        if (valueFormat == null || valueFormat.isEmpty()) return nodeValueInfo;
        for (NodeValueInfo vci : namedValueCodecs) {
            if (vci.codecName.equals(valueFormat)) {
                return vci;
            }
        }
        return null;
    }

}
