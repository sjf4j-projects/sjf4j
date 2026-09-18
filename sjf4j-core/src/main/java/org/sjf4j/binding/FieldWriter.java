package org.sjf4j.binding;


import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoAccess;
import org.sjf4j.node.Types;
import org.sjf4j.node.ValueCodecInfo;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Function;


@FunctionalInterface
public interface FieldWriter {

    /**
     * Writes one POJO property.
     *
     * @param count number of properties already written
     * @return updated property count
     */
    int write(StreamingWriter writer, Object owner, StreamingContext context, int count) throws IOException;


    @FunctionalInterface
    interface ToBooleanFunction<T> {
        boolean apply(T obj);
    }

    @FunctionalInterface
    interface ToByteFunction<T> {
        byte apply(T obj);
    }

    @FunctionalInterface
    interface ToShortFunction<T> {
        short apply(T obj);
    }

    @FunctionalInterface
    interface ToIntFunction<T> {
        int apply(T obj);
    }

    @FunctionalInterface
    interface ToLongFunction<T> {
        long apply(T obj);
    }

    @FunctionalInterface
    interface ToFloatFunction<T> {
        float apply(T obj);
    }

    @FunctionalInterface
    interface ToDoubleFunction<T> {
        double apply(T obj);
    }

    @FunctionalInterface
    interface ToCharFunction<T> {
        char apply(T obj);
    }


    /*
     * --------------------------------------------------------------
     * Create
     * --------------------------------------------------------------
     */

    static FieldWriter create(String fieldName, Type fieldType, Class<?> fieldBoxed,
                              MethodHandle getterHandle, Function<Object, Object> getterLambda,
                              ValueCodecInfo resolvedValueCodec,
                              MethodHandles.Lookup lookup) {

        if (getterHandle == null) {
            return null;
        }

        if (resolvedValueCodec != null) {
            return (writer, owner, context, count) -> {
                Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }

                Object raw = resolvedValueCodec.valueToRaw(value);
                count = _writeName(writer, fieldName, count);
                StreamingIO.writeNode(writer, raw, context);
                return count;
            };
        }

        /*
         * --------------------------------------------------------------
         * Primitive fields
         * --------------------------------------------------------------
         */

        if (fieldType == int.class) {
            return _createForPrimitiveInt(fieldName, getterHandle, lookup);
        }
        if (fieldType == long.class) {
            return _createForPrimitiveLong(fieldName, getterHandle, lookup);
        }
        if (fieldType == double.class) {
            return _createForPrimitiveDouble(fieldName, getterHandle, lookup);
        }
        if (fieldType == float.class) {
            return _createForPrimitiveFloat(fieldName, getterHandle, lookup);
        }
        if (fieldType == short.class) {
            return _createForPrimitiveShort(fieldName, getterHandle, lookup);
        }
        if (fieldType == byte.class) {
            return _createForPrimitiveByte(fieldName, getterHandle, lookup);
        }
        if (fieldType == boolean.class) {
            return _createForPrimitiveBoolean(fieldName, getterHandle, lookup);
        }
        if (fieldType == char.class) {
            return _createForPrimitiveChar(fieldName, getterHandle, lookup);
        }

        /*
         * --------------------------------------------------------------
         * Built-in scalar fast paths
         * --------------------------------------------------------------
         */

        if (fieldBoxed == String.class) {
            return (writer, owner, context, count) -> {
                String value = (String) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeStringValue(value);
                return count;
            };
        }
        if (fieldBoxed == Integer.class) {
            return (writer, owner, context, count) -> {
                Integer value = (Integer) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeIntValue(value);
                return count;
            };
        }
        if (fieldBoxed == Long.class) {
            return (writer, owner, context, count) -> {
                Long value = (Long) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeLongValue(value);
                return count;
            };
        }
        if (fieldBoxed == Double.class) {
            return (writer, owner, context, count) -> {
                Double value = (Double) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeDoubleValue(value);
                return count;
            };
        }
        if (fieldBoxed == Float.class) {
            return (writer, owner, context, count) -> {
                Float value = (Float) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeFloatValue(value);
                return count;
            };
        }
        if (fieldBoxed == Short.class) {
            return (writer, owner, context, count) -> {
                Short value = (Short) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeShortValue(value);
                return count;
            };
        }
        if (fieldBoxed == Byte.class) {
            return (writer, owner, context, count) -> {
                Byte value = (Byte) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeByteValue(value);
                return count;
            };
        }
        if (fieldBoxed == Boolean.class) {
            return (writer, owner, context, count) -> {
                Boolean value = (Boolean) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeBooleanValue(value);
                return count;
            };
        }
        if (fieldBoxed == Character.class) {
            return (writer, owner, context, count) -> {
                Character value = (Character) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeStringValue(value.toString());
                return count;
            };
        }
        if (fieldBoxed == BigInteger.class) {
            return (writer, owner, context, count) -> {
                BigInteger value = (BigInteger) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeBigIntegerValue(value);
                return count;
            };
        }
        if (fieldBoxed == BigDecimal.class) {
            return (writer, owner, context, count) -> {
                BigDecimal value = (BigDecimal) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeBigDecimalValue(value);
                return count;
            };
        }
        if (fieldBoxed == Number.class) {
            return (writer, owner, context, count) -> {
                Number value = (Number) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeNumberValue(value);
                return count;
            };
        }
        if (fieldBoxed.isEnum()) {
            return (writer, owner, context, count) -> {
                Enum<?> value = (Enum<?>) PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
                if (value == null) {
                    return _writeNullValueField(writer, fieldName, context, count);
                }
                count = _writeName(writer, fieldName, count);
                writer.writeStringValue(value.name());
                return count;
            };
        }

        /*
         * --------------------------------------------------------------
         * Generic field
         * --------------------------------------------------------------
         */

        return (writer, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, fieldName, context, count);
            }
            count = _writeName(writer, fieldName, count);
            StreamingIO.writeNode(writer, value, context);
            return count;
        };
    }


    static FieldWriter _createForPrimitiveInt(String fieldName, MethodHandle getterHandle,
                                              MethodHandles.Lookup lookup) {
        ToIntFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                        _castClass(ToIntFunction.class), int.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                int value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeIntValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(int.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                int value = (int) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeIntValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveLong(String fieldName, MethodHandle getterHandle,
                                               MethodHandles.Lookup lookup) {
        ToLongFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToLongFunction.class), long.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                long value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeLongValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(long.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                long value = (long) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeLongValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveDouble(String fieldName, MethodHandle getterHandle,
                                                 MethodHandles.Lookup lookup) {
        ToDoubleFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToDoubleFunction.class), double.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                double value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeDoubleValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(double.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                double value = (double) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeDoubleValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveFloat(String fieldName, MethodHandle getterHandle,
                                              MethodHandles.Lookup lookup) {
        ToFloatFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToFloatFunction.class), float.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                float value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeFloatValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(float.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                float value = (float) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeFloatValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveShort(String fieldName, MethodHandle getterHandle,
                                                MethodHandles.Lookup lookup) {
        ToShortFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToShortFunction.class), short.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                short value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeShortValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(short.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                short value = (short) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeShortValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveByte(String fieldName, MethodHandle getterHandle,
                                               MethodHandles.Lookup lookup) {
        ToByteFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToByteFunction.class), byte.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                byte value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeByteValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(byte.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                byte value = (byte) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeByteValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveBoolean(String fieldName, MethodHandle getterHandle,
                                              MethodHandles.Lookup lookup) {
        ToBooleanFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToBooleanFunction.class), boolean.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                boolean value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeBooleanValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(boolean.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                boolean value = (boolean) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeBooleanValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }

    static FieldWriter _createForPrimitiveChar(String fieldName, MethodHandle getterHandle,
                                                  MethodHandles.Lookup lookup) {
        ToCharFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                _castClass(ToCharFunction.class), char.class);
        if (getterLambda != null) {
            return (writer, owner, context, count) -> {
                char value = getterLambda.apply(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeCharValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(char.class, Object.class));
        return (writer, owner, context, count) -> {
            try {
                char value = (char) getter.invokeExact(owner);
                count = _writeName(writer, fieldName, count);
                writer.writeCharValue(value);
                return count;
            } catch (Throwable e) {
                throw new BindingException("failed to write field '" + fieldName +
                        "' of node type '" + Types.name(owner) + "'", e);
            }
        };
    }


    /*
     * --------------------------------------------------------------
     * Private helper
     * --------------------------------------------------------------
     */

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <T> Class<T> _castClass(Class clazz) {
        return (Class<T>) clazz;
    }

    static int _writeName(StreamingWriter writer, String fieldName, int count) throws IOException {
        if (count > 0) {
            writer.separateProperty();
        }

        writer.writeName(fieldName);
        return count + 1;
    }

    static int _writeNullValueField(StreamingWriter writer, String fieldName,
                                    StreamingContext context, int count) throws IOException {
        if (!context.includeNulls) {
            return count;
        }

        count = _writeName(writer, fieldName, count);
        writer.writeNull();
        return count;
    }

}
