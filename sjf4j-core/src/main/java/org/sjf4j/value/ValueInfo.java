package org.sjf4j.value;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
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
    public final JsonType rawJsonType;
    public final ValueCodec<Object, Object> codec;
    public final ValueCodec.StreamingValueCodec.Parser<Object> streamParser;
    public final ValueCodec.StreamingValueCodec.Generator<Object> streamGenerator;
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
        this.rawJsonType = JsonType.of(NodeKind.plainOf(rawClazz));
        this.codec = (ValueCodec<Object, Object>) codec;
        if (codec instanceof ValueCodec.StreamingValueCodec) {
            ValueCodec.StreamingValueCodec<Object, Object> streaming =
                    (ValueCodec.StreamingValueCodec<Object, Object>) codec;
            this.streamParser = streaming.streamParser();
            this.streamGenerator = streaming.streamGenerator();
        } else {
            this.streamParser = null;
            this.streamGenerator = null;
        }
        this.valueToRawHandle = valueToRawHandle;
        this.rawToValueHandle = rawToValueHandle;
        this.valueCopyHandle = valueCopyHandle;
    }

    /**
     * Adapts metadata from a registered parent type to a resolved runtime class.
     */
    public ValueInfo(Class<?> runtimeClazz, ValueInfo info) {
        this.runtimeClazz = runtimeClazz;
        this.valueFormat = info.valueFormat;
        this.valueClazz = info.valueClazz;
        this.rawClazz = info.rawClazz;
        this.rawJsonType = info.rawJsonType;
        this.codec = info.codec;
        this.streamParser = info.streamParser;
        this.streamGenerator = info.streamGenerator;
        this.valueToRawHandle = info.valueToRawHandle;
        this.rawToValueHandle = info.rawToValueHandle;
        this.valueCopyHandle = info.valueCopyHandle;
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
                throw new BindingException("failed to encode value type '" + valueClazz.getName() +
                        "' with ValueCodec '" + codec.getClass().getName() + "'", e);
            }
        } else if (valueToRawHandle != null) {
            try {
                return valueToRawHandle.invoke(value);
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to encode value type '" + valueClazz.getName() +
                        "' with annotated method " + valueToRawHandle, e);
            }
        }
        throw new BindingException("no value binding found for type " + valueClazz.getName() +
                ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
    }

    /**
     * Decodes a raw node representation, including JSON null, to its domain value.
     */
    public Object rawToValue(Object raw) {
        if (runtimeClazz != null && runtimeClazz != valueClazz) {
            throw new BindingException("cannot decode value type '" + valueClazz.getName() +
                    "' as runtime subtype '" + runtimeClazz.getName() + "'");
        }
        if (raw != null && !rawClazz.isInstance(raw)) {
            throw new BindingException("cannot decode raw type '" + raw.getClass().getName() +
                    "' as value type '" + valueClazz.getName() + "': expected raw type '" + rawClazz.getName() + "'");
        }
        if (codec != null) {
            try {
                return codec.rawToValue(raw);
            } catch (BindingException e) {
                throw e;
            } catch (Exception e) {
                throw new BindingException("failed to decode value type '" + valueClazz.getName() +
                        "' with ValueCodec '" + codec.getClass().getName() + "'", e);
            }
        } else if (rawToValueHandle != null) {
            try {
                return rawToValueHandle.invoke(raw);
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to decode value type '" + valueClazz.getName() +
                        "' with annotated method " + rawToValueHandle, e);
            }
        }
        throw new BindingException("no value binding found for type " + valueClazz.getName() +
                ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
    }

    /**
     * Copies a value using codec-defined semantics when supplied; otherwise
     * returns the original value. Mutable value types need an explicit copy hook.
     */
    public Object valueCopy(Object value) {
        if (codec != null) {
            try {
                return codec.valueCopy(value);
            } catch (BindingException e) {
                throw e;
            } catch (Exception e) {
                throw new BindingException("failed to copy value type '" + valueClazz.getName() +
                        "' with ValueCodec '" + codec.getClass().getName() + "'", e);
            }
        } else if (valueCopyHandle != null) {
            try {
                return valueCopyHandle.invoke(value);
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to copy value type '" + valueClazz.getName() +
                        "' with annotated method " + valueCopyHandle, e);
            }
        }
        return value;
    }

}
