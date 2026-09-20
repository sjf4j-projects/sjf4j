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
    int write(StreamingWriter writer, PreparedName preparedName, Object owner, StreamingContext context, int count) throws IOException;


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
            return _createForValueCodec(fieldName, getterHandle, getterLambda, resolvedValueCodec);
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
            return _createForString(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Integer.class) {
            return _createForInteger(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Long.class) {
            return _createForLong(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Double.class) {
            return _createForDouble(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Float.class) {
            return _createForFloat(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Short.class) {
            return _createForShort(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Byte.class) {
            return _createForByte(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Boolean.class) {
            return _createForBoolean(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Character.class) {
            return _createForCharacter(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == BigInteger.class) {
            return _createForBigInteger(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == BigDecimal.class) {
            return _createForBigDecimal(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed == Number.class) {
            return _createForNumber(fieldName, getterHandle, getterLambda);
        }
        if (fieldBoxed.isEnum()) {
            return _createForEnum(fieldName, getterHandle, getterLambda);
        }

        return _createForObject(fieldName, getterHandle, getterLambda);
    }


    /*
     * --------------------------------------------------------------
     * Primitive fields
     * --------------------------------------------------------------
     */

    static FieldWriter _createForPrimitiveInt(String fieldName, MethodHandle getterHandle,
                                              MethodHandles.Lookup lookup) {
        ToIntFunction<Object> getterLambda = PojoAccess.createGetterLambda(lookup, getterHandle,
                        _castClass(ToIntFunction.class), int.class);
        if (getterLambda != null) {
            return (writer, preparedName, owner, context, count) -> {
                int value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeIntValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(int.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                int value = (int) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                long value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeLongValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(long.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                long value = (long) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                double value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeDoubleValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(double.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                double value = (double) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                float value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeFloatValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(float.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                float value = (float) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                short value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeShortValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(short.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                short value = (short) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                byte value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeByteValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(byte.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                byte value = (byte) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                boolean value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeBooleanValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(boolean.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                boolean value = (boolean) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
            return (writer, preparedName, owner, context, count) -> {
                char value = getterLambda.apply(owner);
                count = _writeName(writer, preparedName, count);
                writer.writeCharValue(value);
                return count;
            };
        }
        MethodHandle getter = getterHandle.asType(MethodType.methodType(char.class, Object.class));
        return (writer, preparedName, owner, context, count) -> {
            try {
                char value = (char) getter.invokeExact(owner);
                count = _writeName(writer, preparedName, count);
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
     * Reference fields
     * --------------------------------------------------------------
     */

    static FieldWriter _createForString(String fieldName, MethodHandle getterHandle,
                                        Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeStringValue((String) value);
            return count;
        };
    }

    static FieldWriter _createForInteger(String fieldName, MethodHandle getterHandle,
                                         Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeIntValue((Integer) value);
            return count;
        };
    }

    static FieldWriter _createForLong(String fieldName, MethodHandle getterHandle,
                                      Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeLongValue((Long) value);
            return count;
        };
    }

    static FieldWriter _createForDouble(String fieldName, MethodHandle getterHandle,
                                        Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeDoubleValue((Double) value);
            return count;
        };
    }

    static FieldWriter _createForFloat(String fieldName, MethodHandle getterHandle,
                                       Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeFloatValue((Float) value);
            return count;
        };
    }

    static FieldWriter _createForShort(String fieldName, MethodHandle getterHandle,
                                       Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeShortValue((Short) value);
            return count;
        };
    }

    static FieldWriter _createForByte(String fieldName, MethodHandle getterHandle,
                                      Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeByteValue((Byte) value);
            return count;
        };
    }

    static FieldWriter _createForBoolean(String fieldName, MethodHandle getterHandle,
                                         Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeBooleanValue((Boolean) value);
            return count;
        };
    }

    static FieldWriter _createForCharacter(String fieldName, MethodHandle getterHandle,
                                           Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeCharValue((Character) value);
            return count;
        };
    }

    static FieldWriter _createForBigInteger(String fieldName, MethodHandle getterHandle,
                                            Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeBigIntegerValue((BigInteger) value);
            return count;
        };
    }

    static FieldWriter _createForBigDecimal(String fieldName, MethodHandle getterHandle,
                                            Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeBigDecimalValue((BigDecimal) value);
            return count;
        };
    }

    static FieldWriter _createForNumber(String fieldName, MethodHandle getterHandle,
                                        Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeNumberValue((Number) value);
            return count;
        };
    }

    static FieldWriter _createForEnum(String fieldName, MethodHandle getterHandle,
                                      Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            writer.writeStringValue(((Enum<?>) value).name());
            return count;
        };
    }

    static FieldWriter _createForValueCodec(String fieldName, MethodHandle getterHandle,
                                            Function<Object, Object> getterLambda,
                                            ValueCodecInfo valueCodec) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            StreamingIO.writeNode(writer, valueCodec.valueToRaw(value), context);
            return count;
        };
    }

    static FieldWriter _createForObject(String fieldName, MethodHandle getterHandle,
                                        Function<Object, Object> getterLambda) {
        return (writer, preparedName, owner, context, count) -> {
            Object value = PojoAccess.invokeGetter(fieldName, getterHandle, getterLambda, owner);
            if (value == null) {
                return _writeNullValueField(writer, preparedName, context, count);
            }

            count = _writeName(writer, preparedName, count);
            StreamingIO.writeNode(writer, value, context);
            return count;
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

    static int _writeName(StreamingWriter writer, PreparedName preparedName, int count) throws IOException {
        if (count > 0) {
            writer.separateProperty();
        }

        writer.writeName(preparedName);
        return count + 1;
    }

    static int _writeNullValueField(StreamingWriter writer, PreparedName preparedName,
                                    StreamingContext context, int count) throws IOException {
        if (!context.includeNulls) {
            return count;
        }

        count = _writeName(writer, preparedName, count);
        writer.writeNull();
        return count;
    }

}
