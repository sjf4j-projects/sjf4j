package org.sjf4j.node;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.value.ValueInfo;

/**
 * Cached classification and metadata for a Java type.
 *
 * <p>Value codecs may have a default and named variants. At most one of the
 * polymorphic, container, and object-binding metadata fields is populated.</p>
 */
public class TypeInfo {
    public final Class<?> clazz;
    public final ValueInfo[] valueInfos;
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
    public TypeInfo(Class<?> clazz, ValueInfo[] valueInfos,
                    OneOfInfo oneOfInfo, ContainerInfo containerInfo, PojoInfo pojoInfo,
                    ExternalNode<?> externalNode) {
        this.clazz = clazz;
        this.valueInfos = valueInfos;
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

    public boolean isNodeValue() {
        return valueInfos != null;
    }

    /**
     * Returns the default or named value codec metadata, or {@code null} when
     * no codec is registered for the requested format.
     */
    public ValueInfo getNodeValueInfo(String valueFormat) {
        if (valueInfos == null) return null;
        if (valueFormat == null) return valueInfos[0];
        for (ValueInfo info : valueInfos) {
            if (info.valueFormat.equals(valueFormat)) {
                return info;
            }
        }
        return null;
    }

}
