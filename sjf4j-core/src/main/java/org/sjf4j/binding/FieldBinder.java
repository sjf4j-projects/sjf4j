package org.sjf4j.binding;

import org.sjf4j.exception.BindingException;
import org.sjf4j.value.NodeValueInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.PojoAccess;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;

@FunctionalInterface
public interface FieldBinder {

    void bind(StreamingReader reader, Object owner, Type ownerType, Class<?> ownerBoxed,
              StreamingContext context) throws IOException;


    @FunctionalInterface
    interface ObjBooleanConsumer<T> {
        void accept(T obj, boolean value);
    }

    @FunctionalInterface
    interface ObjByteConsumer<T> {
        void accept(T obj, byte value);
    }

    @FunctionalInterface
    interface ObjShortConsumer<T> {
        void accept(T obj, short value);
    }

    @FunctionalInterface
    interface ObjFloatConsumer<T> {
        void accept(T obj, float value);
    }

    @FunctionalInterface
    interface ObjCharConsumer<T> {
        void accept(T obj, char value);
    }


    /*
     * --------------------------------------------------------------
     * Create
     * --------------------------------------------------------------
     */

    static FieldBinder create(String fieldName, Type fieldType, Class<?> fieldBoxed,
                              boolean genericDependent, OneOfInfo oneOfInfo,
                              MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                              NodeValueInfo resolvedValueCodec,
                              MethodHandles.Lookup lookup) {

        if (setterHandle == null) {
            return (reader, owner, ownerType, ownerBoxed, context) ->
                    reader.skipNext();
        }

        if (genericDependent) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                Type runtimeFieldType = Types.resolveMemberType(ownerType, ownerBoxed, fieldType);
                Object value = StreamingIO.readNode(reader, runtimeFieldType, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (oneOfInfo != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                Object value = OneOfIO.readOneOf(reader, oneOfInfo, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (resolvedValueCodec != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                Object value = StreamingIO.readValueWithCodec(reader, fieldType, fieldBoxed, resolvedValueCodec, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        /*
         * --------------------------------------------------------------
         * Primitive fields
         * --------------------------------------------------------------
         */

        if (fieldType == int.class) {
            return _createForPrimitiveInt(fieldName, setterHandle, lookup);
        }
        if (fieldType == long.class) {
            return _createForPrimitiveLong(fieldName, setterHandle, lookup);
        }
        if (fieldType == double.class) {
            return _createForPrimitiveDouble(fieldName, setterHandle, lookup);
        }
        if (fieldType == float.class) {
            return _createForPrimitiveFloat(fieldName, setterHandle, lookup);
        }
        if (fieldType == byte.class) {
            return _createForPrimitiveByte(fieldName, setterHandle, lookup);
        }
        if (fieldType == short.class) {
            return _createForPrimitiveShort(fieldName, setterHandle, lookup);
        }
        if (fieldType == boolean.class) {
            return _createForPrimitiveBoolean(fieldName, setterHandle, lookup);
        }
        if (fieldType == char.class) {
            return _createForPrimitiveChar(fieldName, setterHandle, lookup);
        }

        /*
         * --------------------------------------------------------------
         * Containers
         * --------------------------------------------------------------
         */

        if (List.class.isAssignableFrom(fieldBoxed)) {
            Type elementType = Types.resolveTypeArgument(fieldType, List.class, 0);
            Class<?> elementBoxed = Types.rawBox(elementType);
            TypeInfo[] elementTiRef = new TypeInfo[1];
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo elementTi = elementTiRef[0];
                if (elementTi == null) {
                    elementTi = TypeRegistry.registerTypeInfo(elementBoxed);
                    elementTiRef[0] = elementTi;
                }
                Object value = StreamingIO.readListOrNull(reader, fieldBoxed, elementType, elementBoxed, elementTi, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (Set.class.isAssignableFrom(fieldBoxed)) {
            Type elementType = Types.resolveTypeArgument(fieldType, Set.class, 0);
            Class<?> elementBoxed = Types.rawBox(elementType);
            TypeInfo[] elementTiRef = new TypeInfo[1];
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo elementTi = elementTiRef[0];
                if (elementTi == null) {
                    elementTi = TypeRegistry.registerTypeInfo(elementBoxed);
                    elementTiRef[0] = elementTi;
                }
                Object value = StreamingIO.readSetOrNull(reader, fieldBoxed, elementType, elementBoxed, elementTi, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (Map.class.isAssignableFrom(fieldBoxed)) {
            Type valueType = Types.resolveTypeArgument(fieldType, Map.class, 1);
            Class<?> valueBoxed = Types.rawBox(valueType);
            TypeInfo[] valueTiRef = new TypeInfo[1];
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo valueTi = valueTiRef[0];
                if (valueTi == null) {
                    valueTi = TypeRegistry.registerTypeInfo(valueBoxed);
                    valueTiRef[0] = valueTi;
                }
                Object value = StreamingIO.readMapOrNull(reader, fieldBoxed, valueType, valueBoxed, valueTi, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (fieldBoxed.isArray()) {
            Class<?> componentClazz = fieldBoxed.getComponentType();
            Class<?> componentBoxed = Types.box(componentClazz);
            TypeInfo[] componentTiRef = new TypeInfo[1];
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo componentTi = componentTiRef[0];
                if (componentTi == null) {
                    componentTi = TypeRegistry.registerTypeInfo(componentClazz);
                    componentTiRef[0] = componentTi;
                }
                Object value = StreamingIO.readJavaArrayOrNull(reader, fieldBoxed, componentClazz, componentBoxed,
                        componentTi, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        /*
         * --------------------------------------------------------------
         * Built-in scalar fast paths
         * --------------------------------------------------------------
         */

        if (fieldBoxed == String.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextStringOrNull());
            };
        }
        if (fieldBoxed == Integer.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextInt());
            };
        }
        if (fieldBoxed == Long.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextLong());
            };
        }
        if (fieldBoxed == Double.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextDouble());
            };
        }
        if (fieldBoxed == Float.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextFloat());
            };
        }
        if (fieldBoxed == Short.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextShort());
            };
        }
        if (fieldBoxed == Byte.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextByte());
            };
        }
        if (fieldBoxed == Boolean.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextBoolean());
            };
        }
        if (fieldBoxed == Character.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                String str = reader.nextStringOrNull();
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner,
                        str == null || str.isEmpty() ? null : str.charAt(0));
            };
        }
        if (fieldBoxed == Number.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextNumber());
            };
        }
        if (fieldBoxed == BigInteger.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextBigInteger());
            };
        }
        if (fieldBoxed == BigDecimal.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.nextBigDecimal());
            };
        }
        if (fieldBoxed.isEnum()) {
            @SuppressWarnings("rawtypes")
            Class<? extends Enum> enumType = fieldBoxed.asSubclass(Enum.class);
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                String str = reader.nextStringOrNull();
                @SuppressWarnings("unchecked")
                Object enumValue = str == null ? null : Enum.valueOf(enumType, str);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, enumValue);
            };
        }

        /*
         * --------------------------------------------------------------
         * Generic POJO / JsonObject / Optional / class-level codec / ...
         *
         * Cache TypeInfo once.
         * --------------------------------------------------------------
         */

        TypeInfo[] fieldTiRef = new TypeInfo[1];
        return (reader, owner, ownerType, ownerBoxed, context) -> {
            TypeInfo fieldTi = fieldTiRef[0];
            if (fieldTi == null) {
                fieldTi = TypeRegistry.registerTypeInfo(fieldBoxed);
                fieldTiRef[0] = fieldTi;
            }
            Object value = StreamingIO.readNode(reader, fieldType, fieldBoxed, fieldTi, context);
            PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
        };

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <T> Class<T> _castClass(Class clazz) {
        return (Class<T>) clazz;
    }


    static FieldBinder _createForPrimitiveInt(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjIntConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjIntConsumer.class), int.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextIntValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, int.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextIntValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveLong(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjLongConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjLongConsumer.class), long.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextLongValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, long.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextLongValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveDouble(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjDoubleConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjDoubleConsumer.class), double.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextDoubleValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, double.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextDoubleValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveFloat(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjFloatConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjFloatConsumer.class), float.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextFloatValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, float.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextFloatValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveShort(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjShortConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjShortConsumer.class), short.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextShortValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, short.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextShortValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveByte(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjByteConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjByteConsumer.class), byte.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextByteValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, byte.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextByteValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveBoolean(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjBooleanConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjBooleanConsumer.class), boolean.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextBooleanValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, boolean.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextBooleanValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static FieldBinder _createForPrimitiveChar(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjCharConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjCharConsumer.class), char.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.nextCharValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, char.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.nextCharValue());
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }


}
