package org.sjf4j.binding;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.value.ValueInfo;
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
public interface PropertyReader {

    void read(StreamingReader reader, Object owner, Type ownerType, Class<?> ownerBoxed,
              RuntimeContext context) throws IOException;


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

    static PropertyReader create(String fieldName, Type fieldType, Class<?> fieldBoxed,
                                 boolean genericDependent, OneOfInfo oneOfInfo,
                                 MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                                 ValueInfo resolvedValueCodec,
                                 MethodHandles.Lookup lookup) {

        if (setterHandle == null) {
            return null;
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
                Object value = StreamingIO.readValueCodec(reader, resolvedValueCodec, context);
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
                Object value = StreamingIO.readMap(reader, fieldType, fieldBoxed, valueType, valueBoxed, valueTi, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (fieldType == JsonObject.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                Object value = null;
                Object raw = reader.readRawNode();
                if (raw != null) value = new JsonObject(StreamingIO.castMap(raw));
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

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
                Object value = StreamingIO.readList(reader, fieldType, fieldBoxed, elementType, elementBoxed, elementTi, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (fieldType == JsonArray.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                Object value = null;
                Object raw = reader.readRawNode();
                if (raw != null) value = new JsonArray(StreamingIO.castList(raw));
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
                Object value = StreamingIO.readSet(reader, fieldType, fieldBoxed, elementType, elementBoxed, elementTi, context);
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
                Object value = StreamingIO.readArray(reader, fieldType, fieldBoxed, componentClazz, componentBoxed,
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
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readString());
            };
        }
        if (fieldBoxed == Integer.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readInt());
            };
        }
        if (fieldBoxed == Long.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readLong());
            };
        }
        if (fieldBoxed == Double.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readDouble());
            };
        }
        if (fieldBoxed == Float.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readFloat());
            };
        }
        if (fieldBoxed == Short.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readShort());
            };
        }
        if (fieldBoxed == Byte.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readByte());
            };
        }
        if (fieldBoxed == Boolean.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readBoolean());
            };
        }
        if (fieldBoxed == Character.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readChar());
            };
        }
        if (fieldBoxed == Number.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readNumber());
            };
        }
        if (fieldBoxed == BigInteger.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readBigInteger());
            };
        }
        if (fieldBoxed == BigDecimal.class) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, reader.readBigDecimal());
            };
        }
        if (fieldBoxed.isEnum()) {
            @SuppressWarnings("rawtypes")
            Class<? extends Enum> enumType = fieldBoxed.asSubclass(Enum.class);
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                Object enumValue = StreamingIO.readEnum(reader, fieldBoxed, context);
                PojoAccess.invokeSetter(fieldName, setterHandle, setterLambda, owner, enumValue);
            };
        }

        // Resolve metadata lazily and reuse it for subsequent reads through StreamingIO.

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


    static PropertyReader _createForPrimitiveInt(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjIntConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjIntConsumer.class), int.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readIntValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, int.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readIntValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveLong(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjLongConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjLongConsumer.class), long.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readLongValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, long.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readLongValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveDouble(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjDoubleConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjDoubleConsumer.class), double.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readDoubleValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, double.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readDoubleValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveFloat(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjFloatConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjFloatConsumer.class), float.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readFloatValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, float.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readFloatValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveShort(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjShortConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjShortConsumer.class), short.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readShortValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, short.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readShortValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveByte(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjByteConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjByteConsumer.class), byte.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readByteValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, byte.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readByteValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveBoolean(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjBooleanConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjBooleanConsumer.class), boolean.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readBooleanValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, boolean.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readBooleanValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyReader _createForPrimitiveChar(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjCharConsumer<Object> setterLambda = PojoAccess.createSetterLambda(lookup, setterHandle,
                _castClass(ObjCharConsumer.class), char.class);
        if (setterLambda != null) {
            return (reader, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, reader.readCharValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, char.class));
        return (reader, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, reader.readCharValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }


}
