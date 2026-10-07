package org.sjf4j.node;

import org.sjf4j.exception.BindingException;
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
    public final ExternalNode<Object> externalNode;

    static final TypeInfo NONE = new TypeInfo(Object.class, null,
            null, null, null, null);

    /**
     * Creates type metadata for the supplied classification, including an
     * external node classifier when applicable.
     */
    @SuppressWarnings("unchecked")
    public TypeInfo(Class<?> clazz, ValueInfo[] valueInfos,
                    OneOfInfo oneOfInfo, ContainerInfo containerInfo, PojoInfo pojoInfo,
                    ExternalNode<?> externalNode) {
        this.clazz = clazz;
        this.valueInfos = valueInfos;
        this.oneOfInfo = oneOfInfo;
        this.containerInfo = containerInfo;
        this.pojoInfo = pojoInfo;
        this.externalNode = (ExternalNode<Object>) externalNode;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Returns the default or named value codec metadata, or {@code null} when
     * no codec is registered for the requested format.
     */
    public ValueInfo getValueInfo(String valueFormat) {
        if (valueInfos == null) return null;
        if (valueFormat == null) return valueInfos[0];
        for (ValueInfo info : valueInfos) {
            if (info.valueFormat.equals(valueFormat)) {
                return info;
            }
        }
        return null;
    }

    public ValueInfo requireValueInfo(String valueFormat) {
        ValueInfo info = getValueInfo(valueFormat);
        if (info == null) {
            throw new BindingException("no ValueCodec registered for type '" +
                    clazz.getName() + "' with format '" + valueFormat + "'");
        }
        return info;
    }


}
