package org.sjf4j.value;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.exception.BindingException;

import java.lang.invoke.MethodHandle;

/**
 * Cached binding metadata for a {@link ValueCodec} or {@code @NodeValue}
 * conversion methods.
 */
public class ValueInfo {
    public final Class<?> runtimeClazz;
    public final String valueFormat;
    public final Class<?> valueClazz;
    public final Class<?> rawClazz;
    public final ValueCodec<Object, Object> codec;
    public final MethodHandle valueToRawHandle;
    public final MethodHandle rawToValueHandle;
    public final MethodHandle valueCopyHandle;

    /**
     * Creates value codec metadata.
     */
    @SuppressWarnings("unchecked")
    public ValueInfo(String valueFormat, Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> codec,
                     MethodHandle valueToRawHandle, MethodHandle rawToValueHandle, MethodHandle valueCopyHandle) {
        this.runtimeClazz = null;
        this.valueFormat = valueFormat == null ? "" : valueFormat;
        this.valueClazz = valueClazz;
        this.rawClazz = rawClazz;
        this.codec = (ValueCodec<Object, Object>) codec;
        this.valueToRawHandle = valueToRawHandle;
        this.rawToValueHandle = rawToValueHandle;
        this.valueCopyHandle = valueCopyHandle;
    }

    public ValueInfo(Class<?> runtimeClazz, ValueInfo info) {
        this.runtimeClazz = runtimeClazz;
        this.valueFormat = info.valueFormat;
        this.valueClazz = info.valueClazz;
        this.rawClazz = info.rawClazz;
        this.codec = info.codec;
        this.valueToRawHandle = info.valueToRawHandle;
        this.rawToValueHandle = info.rawToValueHandle;
        this.valueCopyHandle = info.rawToValueHandle;
    }


    /**
     * Encodes a domain value to its raw node representation.
     */
    public Object valueToRaw(Object value) {
        if (codec != null) {
            try {
                return codec.valueToRaw(value);
            } catch (BindingException e) {
                throw e;
            } catch (Exception e) {
                throw new BindingException("failed to valueToRaw() for value type " + valueClazz.getName() +
                        " using ValueCodec " + codec.getClass().getName(), e);
            }
        } else if (valueToRawHandle != null) {
            try {
                return valueToRawHandle.invoke(value);
            } catch (BindingException e) {
                throw e;
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
        if (runtimeClazz != null && runtimeClazz != valueClazz) {
            throw new BindingException("cannot rawToValue() to runtime subtype " + runtimeClazz.getName() +
                        " from node value type " + valueClazz.getName());
        }
        if (raw != null && !rawClazz.isInstance(raw)) {
            throw new BindingException("cannot rawToValue() from raw type " + raw.getClass().getName() +
                    " to value type " + valueClazz.getName() + ". Expected raw type: " + rawClazz.getName());
        }
        if (codec != null) {
            try {
                return codec.rawToValue(raw);
            } catch (BindingException e) {
                throw e;
            } catch (Exception e) {
                throw new BindingException("failed to rawToValue() to value type " + valueClazz.getName() +
                        " using ValueCodec " + codec.getClass().getName(), e);
            }
        } else if (rawToValueHandle != null) {
            try {
                return rawToValueHandle.invoke(raw);
            } catch (BindingException e) {
                throw e;
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
        if (codec != null) {
            try {
                return codec.valueCopy(value);
            } catch (BindingException e) {
                throw e;
            } catch (Exception e) {
                throw new BindingException("failed to valueCopy() for value type " + valueClazz.getName() +
                        " using ValueCodec " + codec.getClass().getName(), e);
            }
        } else if (valueCopyHandle != null) {
            try {
                return valueCopyHandle.invoke(value);
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to valueCopy() for value type " + valueClazz.getName() +
                        " using annotated method " + valueCopyHandle, e);
            }
        }
        return value;
    }

}
