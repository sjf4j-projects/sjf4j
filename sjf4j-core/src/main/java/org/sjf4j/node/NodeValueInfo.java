package org.sjf4j.node;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.exception.BindingException;

import java.lang.invoke.MethodHandle;

/**
 * Cached binding metadata for a {@link NodeValueCodec} or {@code @NodeValue}
 * conversion methods.
 */
public class NodeValueInfo {
    public final String codecName;
    public final Class<?> valueClazz;
    public final Class<?> rawClazz;
    public final NodeValueCodec<Object, Object> valueCodec;
    public final MethodHandle valueToRawHandle;
    public final MethodHandle rawToValueHandle;
    public final MethodHandle valueCopyHandle;

    /**
     * Creates value codec metadata.
     */
    @SuppressWarnings("unchecked")
    public NodeValueInfo(String codecName, Class<?> valueClazz, Class<?> rawClazz, NodeValueCodec<?, ?> valueCodec,
                         MethodHandle valueToRawHandle, MethodHandle rawToValueHandle, MethodHandle valueCopyHandle) {
        this.codecName = codecName == null ? "" : codecName;
        this.valueClazz = valueClazz;
        this.rawClazz = rawClazz;
        this.valueCodec = (NodeValueCodec<Object, Object>) valueCodec;
        this.valueToRawHandle = valueToRawHandle;
        this.rawToValueHandle = rawToValueHandle;
        this.valueCopyHandle = valueCopyHandle;
    }

    /**
     * Returns whether this is the unnamed default codec.
     */
    public boolean isDefault() {
        return codecName.isEmpty();
    }

    /**
     * Encodes a domain value to its raw node representation.
     */
    public Object valueToRaw(Object value) {
        if (valueCodec != null) {
            try {
                return valueCodec.valueToRaw(value);
            } catch (Exception e) {
                throw new BindingException("failed to valueToRaw() for value type " + valueClazz.getName() +
                        " using ValueCodec " + valueCodec.getClass().getName(), e);
            }
        } else if (valueToRawHandle != null) {
            try {
                return valueToRawHandle.invoke(value);
            } catch (Throwable e) {
                throw new BindingException("failed to valueToRaw() for value type " + valueClazz.getName() +
                        " using annotated method " + valueToRawHandle, e);
            }
        }
        throw new BindingException("no value binding found for type " + valueClazz.getName() +
                ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
    }

    /**
     * Decodes a raw node representation to its domain value.
     */
    public Object rawToValue(Object raw) {
        if (raw != null && !rawClazz.isInstance(raw))
            throw new BindingException("cannot rawToValue() from raw type " + raw.getClass().getName() +
                    " to value type " + valueClazz.getName() + ". Expected raw type: " + rawClazz.getName());
        if (valueCodec != null) {
            try {
                return valueCodec.rawToValue(raw);
            } catch (Exception e) {
                throw new BindingException("failed to rawToValue() to value type " + valueClazz.getName() +
                        " using ValueCodec " + valueCodec.getClass().getName(), e);
            }
        } else if (rawToValueHandle != null) {
            try {
                return rawToValueHandle.invoke(raw);
            } catch (Throwable e) {
                throw new BindingException("failed to rawToValue() to value type " + valueClazz.getName() +
                        " using annotated method " + rawToValueHandle, e);
            }
        }
        throw new BindingException("no value binding found for type " + valueClazz.getName() +
                ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
    }

    /**
     * Copies value using codec-defined semantics.
     */
    public Object valueCopy(Object value) {
        if (valueCodec != null) {
            try {
                return valueCodec.valueCopy(value);
            } catch (Exception e) {
                throw new BindingException("failed to valueCopy() for value type " + valueClazz.getName() +
                        " using ValueCodec " + valueCodec.getClass().getName(), e);
            }
        } else if (valueCopyHandle != null) {
            try {
                return valueCopyHandle.invoke(value);
            } catch (Throwable e) {
                throw new BindingException("failed to valueCopy() for value type " + valueClazz.getName() +
                        " using annotated method " + valueCopyHandle, e);
            }
        }
        return value;
    }

}
