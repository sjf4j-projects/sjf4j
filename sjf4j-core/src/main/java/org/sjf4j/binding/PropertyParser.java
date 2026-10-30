package org.sjf4j.binding;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.meta.OneOfInfo;
import org.sjf4j.util.Invokers;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.LazyTypeInfo;
import org.sjf4j.util.Types;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;

/**
 * Precomputed parser for a settable POJO property. The incoming member name
 * has already been consumed; {@code parse()} consumes its value and assigns it
 * to the owning instance.
 */
@FunctionalInterface
public interface PropertyParser {

    void parse(StreamingParser parser, Object owner, Type ownerType, Class<?> ownerBoxed,
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

    /**
     * Selects a property parser once from its setter and resolved type metadata.
     * Returns {@code null} when no setter is available.
     */
    static PropertyParser create(String fieldName, Type fieldType, Class<?> fieldBoxed,
                                 boolean genericDependent, OneOfInfo oneOfInfo,
                                 MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                                 ValueInfo resolvedValueCodec,
                                 MethodHandles.Lookup lookup) {

        if (setterHandle == null) {
            return null;
        }

        if (genericDependent) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Type runtimeFieldType = Types.resolveMemberType(ownerType, ownerBoxed, fieldType);
                Object value = StreamingIO.readNode(parser, runtimeFieldType, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (oneOfInfo != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Object value = OneOfIO.readOneOf(parser, oneOfInfo, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (resolvedValueCodec != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Object value = StreamingIO.readValueCodec(parser, resolvedValueCodec, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
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

        // Binding uses directly populated Map/List implementations only.
        // ExternalNode builders are reserved for NodeMapper conversions.

        if (Map.class.isAssignableFrom(fieldBoxed)) {
            Type valueType = Types.resolveTypeArgument(fieldType, Map.class, 1);
            Class<?> valueBoxed = Types.rawBox(valueType);
            if ((fieldBoxed == Map.class || fieldBoxed == LinkedHashMap.class)
                    && valueType == Object.class) {
                return (parser, owner, ownerType, ownerBoxed, context) -> {
                    Object value = parser.readRawObject();
                    Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
                };
            }
            LazyTypeInfo valueTiRef = new LazyTypeInfo(valueBoxed);
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo valueTi = valueTiRef.get();
                Object value = StreamingIO.readMap(parser, fieldType, fieldBoxed, valueType, valueBoxed, valueTi, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (fieldType == JsonObject.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Object value = StreamingIO.readJsonObject(parser);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (List.class.isAssignableFrom(fieldBoxed)) {
            Type elementType = Types.resolveTypeArgument(fieldType, List.class, 0);
            Class<?> elementBoxed = Types.rawBox(elementType);
            if ((fieldBoxed == List.class || fieldBoxed == ArrayList.class)
                    && elementType == Object.class) {
                return (parser, owner, ownerType, ownerBoxed, context) -> {
                    Object value = parser.readRawArray();
                    Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
                };
            }
            LazyTypeInfo elementTiRef = new LazyTypeInfo(elementBoxed);
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo elementTi = elementTiRef.get();
                Object value = StreamingIO.readList(parser, fieldType, fieldBoxed, elementType, elementBoxed, elementTi, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (fieldType == JsonArray.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Object value = StreamingIO.readJsonArray(parser);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (Set.class.isAssignableFrom(fieldBoxed)) {
            Type elementType = Types.resolveTypeArgument(fieldType, Set.class, 0);
            Class<?> elementBoxed = Types.rawBox(elementType);
            LazyTypeInfo elementTiRef = new LazyTypeInfo(elementBoxed);
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo elementTi = elementTiRef.get();
                Object value = StreamingIO.readSet(parser, fieldType, fieldBoxed, elementType, elementBoxed, elementTi, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        if (fieldBoxed.isArray()) {
            Class<?> componentClazz = fieldBoxed.getComponentType();
            Class<?> componentBoxed = Types.box(componentClazz);
            LazyTypeInfo componentTiRef = new LazyTypeInfo(componentClazz);
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                TypeInfo componentTi = componentTiRef.get();
                Object value = StreamingIO.readArray(parser, fieldType, fieldBoxed, componentClazz, componentBoxed,
                        componentTi, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
            };
        }

        /*
         * --------------------------------------------------------------
         * Built-in scalar fast paths
         * --------------------------------------------------------------
         */

        if (fieldBoxed == String.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readString());
            };
        }
        if (fieldBoxed == Integer.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readInt());
            };
        }
        if (fieldBoxed == Long.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readLong());
            };
        }
        if (fieldBoxed == Double.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readDouble());
            };
        }
        if (fieldBoxed == Float.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readFloat());
            };
        }
        if (fieldBoxed == Short.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readShort());
            };
        }
        if (fieldBoxed == Byte.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readByte());
            };
        }
        if (fieldBoxed == Boolean.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readBoolean());
            };
        }
        if (fieldBoxed == Character.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readChar());
            };
        }
        if (fieldBoxed == Number.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readNumber());
            };
        }
        if (fieldBoxed == BigInteger.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readBigInteger());
            };
        }
        if (fieldBoxed == BigDecimal.class) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, parser.readBigDecimal());
            };
        }
        if (fieldBoxed.isEnum()) {
            @SuppressWarnings("rawtypes")
            Class<? extends Enum> enumType = fieldBoxed.asSubclass(Enum.class);
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                Object enumValue = StreamingIO.readEnum(parser, fieldBoxed, context);
                Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, enumValue);
            };
        }

        // Resolve metadata lazily and reuse it for subsequent reads through StreamingIO.

        LazyTypeInfo fieldTiRef = new LazyTypeInfo(fieldBoxed);
        return (parser, owner, ownerType, ownerBoxed, context) -> {
            TypeInfo fieldTi = fieldTiRef.get();
            Object value = StreamingIO.readNode(parser, fieldType, fieldBoxed, fieldTi, context);
            Invokers.invokeSetter(fieldName, setterHandle, setterLambda, owner, value);
        };

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static <T> Class<T> _castClass(Class clazz) {
        return (Class<T>) clazz;
    }


    static PropertyParser _createForPrimitiveInt(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjIntConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjIntConsumer.class), int.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readIntValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, int.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readIntValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveLong(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjLongConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjLongConsumer.class), long.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readLongValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, long.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readLongValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveDouble(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjDoubleConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjDoubleConsumer.class), double.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readDoubleValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, double.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readDoubleValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveFloat(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjFloatConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjFloatConsumer.class), float.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readFloatValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, float.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readFloatValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveShort(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjShortConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjShortConsumer.class), short.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readShortValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, short.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readShortValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveByte(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjByteConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjByteConsumer.class), byte.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readByteValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, byte.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readByteValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveBoolean(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjBooleanConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjBooleanConsumer.class), boolean.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readBooleanValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, boolean.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readBooleanValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }

    static PropertyParser _createForPrimitiveChar(String fieldName, MethodHandle setterHandle, MethodHandles.Lookup lookup) {
        ObjCharConsumer<Object> setterLambda = Invokers.createSetterLambda(lookup, setterHandle,
                _castClass(ObjCharConsumer.class), char.class);
        if (setterLambda != null) {
            return (parser, owner, ownerType, ownerBoxed, context) -> {
                setterLambda.accept(owner, parser.readCharValue());
            };
        }
        MethodHandle setter = setterHandle.asType(
                MethodType.methodType(void.class, Object.class, char.class));
        return (parser, receiver, ownerType, ownerRawClazz, context) -> {
            try {
                setter.invokeExact(receiver, parser.readCharValue());
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to bind value to field '" + fieldName +
                        "' of node type '" + Types.name(receiver) + "'", e);
            }
        };
    }


}
