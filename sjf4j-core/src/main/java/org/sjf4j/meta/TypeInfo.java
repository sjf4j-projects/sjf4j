package org.sjf4j.meta;

import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.value.ValueSet;

/**
 * Cached classification and metadata for a Java type.
 *
 * <p>Value codecs may have a default and named variants. At most one of the
 * polymorphic, container, and object-binding metadata fields is populated.</p>
 */
public final class TypeInfo {
    public final Class<?> clazz;
    public final ValueSet valueSet;
    public final OneOfInfo oneOfInfo;
    public final ContainerInfo containerInfo;
    public final PojoInfo pojoInfo;
    public final ExternalNode<Object> externalNode;

    public static final TypeInfo NONE = new TypeInfo(Object.class,
            null, null, null, null, null);

    /**
     * Creates type metadata for the supplied classification, including an
     * external node classifier when applicable.
     */
    @SuppressWarnings("unchecked")
    public TypeInfo(Class<?> clazz, ValueSet valueSet,
                    OneOfInfo oneOfInfo, ContainerInfo containerInfo, PojoInfo pojoInfo,
                    ExternalNode<?> externalNode) {
        this.clazz = clazz;
        this.valueSet = valueSet;
        this.oneOfInfo = oneOfInfo;
        this.containerInfo = containerInfo;
        this.pojoInfo = pojoInfo;
        this.externalNode = (ExternalNode<Object>) externalNode;
    }

    /** Returns whether this is the shared marker for a type without metadata. */
    public boolean isNone() {
        return this == NONE;
    }

    /** Returns the default or named codec for this value type. */
    public ValueInfo getValueInfo(String valueFormat) {
        return valueSet == null ? null : valueSet.findFormat(valueFormat);
    }

    /** Returns the requested codec or fails with the type and format. */
    public ValueInfo requireValueInfo(String valueFormat) {
        if (valueSet == null) {
            throw new BindingException("no ValueCodec registered for type '" + clazz.getName() + "'");
        }
        return valueSet.requireFormat(valueFormat);
    }

}
