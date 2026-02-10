package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4jConfig;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


public final class NodeRegistry {

    // All in TypeInfo
    private static final Map<Class<?>, TypeInfo> TYPE_INFO_CACHE = new ConcurrentHashMap<>();

    public static TypeInfo registerTypeInfo(Class<?> clazz) {
        return registerTypeInfo(clazz, false);
    }

    public static TypeInfo registerTypeInfo(Class<?> clazz, boolean mustPojo) {
        if (hitRawType(clazz)) return NONE_INFO;
        TypeInfo ti = TYPE_INFO_CACHE.get(clazz);
        if (ti != null) {
            if (mustPojo && ti.pojoInfo == null) {
                throw new JsonException("Class '" + clazz.getName() + "' is not a POJO");
            }
            return ti;
        }

        ValueCodecInfo vci = ReflectUtil.analyzeNodeValue(clazz);
        if (vci != null) {
            if (mustPojo) {
                throw new JsonException("Class '" + clazz.getName() + "' is annotated with @NodeValue, not a POJO");
            }
            ti = new TypeInfo(clazz, null, vci);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }
        PojoInfo pi = ReflectUtil.analyzePojo(clazz, mustPojo);
        if (pi != null) {
            ti = new TypeInfo(clazz, pi, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }
        TYPE_INFO_CACHE.put(clazz, NONE_INFO);
        return NONE_INFO;
    }

    private static boolean hitRawType(Class<?> clazz) {
        return clazz == null || clazz == Object.class || clazz == String.class || clazz == Boolean.class
                || clazz == Map.class || clazz == List.class || clazz.isPrimitive();
    }

    /// NodeValue

    // Bootstrap JDK Types
    static {
        registerValueCodec(new ValueCodec.UriValueCodec());
        registerValueCodec(new ValueCodec.UrlValueCodec());
        registerValueCodec(new ValueCodec.UuidValueCodec());
        registerValueCodec(new ValueCodec.LocaleValueCodec());
        registerValueCodec(new ValueCodec.CurrencyValueCodec());
        registerValueCodec(new ValueCodec.ZoneIdValueCodec());
        if (Sjf4jConfig.global().instantFormat == Sjf4jConfig.InstantFormat.EPOCH_MILLIS) {
            registerValueCodec(new ValueCodec.InstantEpochMillisValueCodec());
        } else {
            registerValueCodec(new ValueCodec.InstantStringValueCodec());
        }
        registerValueCodec(new ValueCodec.LocalDateValueCodec());
        registerValueCodec(new ValueCodec.LocalDateTimeValueCodec());
        registerValueCodec(new ValueCodec.OffsetDateTimeValueCodec());
        registerValueCodec(new ValueCodec.ZonedDateTimeValueCodec());
        registerValueCodec(new ValueCodec.DurationValueCodec());
        registerValueCodec(new ValueCodec.PeriodValueCodec());
        registerValueCodec(new ValueCodec.PathValueCodec());
        registerValueCodec(new ValueCodec.FileValueCodec());
        registerValueCodec(new ValueCodec.PatternValueCodec());
        registerValueCodec(new ValueCodec.InetAddressValueCodec());
        registerValueCodec(new ValueCodec.DateValueCodec());
        registerValueCodec(new ValueCodec.CalendarValueCodec());
    }

    public static <N, R> ValueCodecInfo registerValueCodec(ValueCodec<N, R> valueCodec) {
        Objects.requireNonNull(valueCodec, "valueCodec is null");
        Class<R> rawClazz = valueCodec.getRawClass();
        if (!NodeType.of(rawClazz).isRaw())
            throw new JsonException("Invalid raw type in ValueCodec " + valueCodec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of String, Number, Boolean, Map, List or Object.");
        Class<N> valueClazz = valueCodec.getValueClass();
        Objects.requireNonNull(valueClazz, "clazz is null");

        ValueCodecInfo vci = new ValueCodecInfo(valueClazz, rawClazz, valueCodec);
        TYPE_INFO_CACHE.put(valueClazz, new TypeInfo(valueClazz, null, vci));
        return vci;
    }

    public static ValueCodecInfo registerValueCodec(Class<?> clazz) {
        return registerTypeInfo(clazz).valueCodecInfo;
    }

    public static ValueCodecInfo getValueCodecInfo(Class<?> clazz) {
        return registerValueCodec(clazz);
    }

    public static void refreshInstantValueCodec(Sjf4jConfig.InstantFormat instantFormat) {
        if (instantFormat == Sjf4jConfig.InstantFormat.EPOCH_MILLIS) {
            registerValueCodec(new ValueCodec.InstantEpochMillisValueCodec());
        } else {
            registerValueCodec(new ValueCodec.InstantStringValueCodec());
        }
    }


    /// POJO

    public static PojoInfo registerPojo(Class<?> clazz) {
        return registerTypeInfo(clazz).pojoInfo;
    }

    public static PojoInfo registerPojoOrElseThrow(Class<?> clazz) {
        return registerTypeInfo(clazz, true).pojoInfo;
    }

    public static PojoInfo getPojoInfo(Class<?> clazz) {
        return registerPojo(clazz);
    }

    public static FieldInfo getFieldInfo(Class<?> clazz, String fieldName) {
        return registerPojoOrElseThrow(clazz).fields.get(fieldName);
    }

    public static boolean isPojo(Class<?> clazz) {
        return registerTypeInfo(clazz).pojoInfo != null;
    }

    /// Info

    private static final TypeInfo NONE_INFO = new TypeInfo(Object.class, null, null);

    // TypeInfo
    public static class TypeInfo {
        public final Class<?> clazz;
        public final PojoInfo pojoInfo;
        public final ValueCodecInfo valueCodecInfo;
        public TypeInfo(Class<?> clazz, PojoInfo pojoInfo, ValueCodecInfo valueCodecInfo) {
            this.clazz = clazz;
            this.pojoInfo = pojoInfo;
            this.valueCodecInfo = valueCodecInfo;
        }
        public boolean isPojo() {
            return pojoInfo != null;
        }
        public boolean isNodeValue() {
            return valueCodecInfo != null;
        }
    }

    // PojoInfo
    public static class PojoInfo {
        public final Class<?> clazz;
        public final CreatorInfo creatorInfo;
        public final Map<String, FieldInfo> fields;
        public final int fieldCount;
        public final Map<String, FieldInfo> aliasFields;
        public final boolean isJojo;
        public final boolean isJajo;
        public PojoInfo(Class<?> clazz, CreatorInfo creatorInfo,
                        Map<String, FieldInfo> fields, Map<String, FieldInfo> aliasFields) {
            this.clazz = clazz;
            this.creatorInfo = creatorInfo;
            this.fields = fields;
            this.fieldCount = fields.size();
            this.aliasFields = aliasFields;
            this.isJojo = JsonObject.class.isAssignableFrom(clazz);
            this.isJajo = JsonArray.class.isAssignableFrom(clazz);
        }
    }

    // CreatorInfo
    public static class CreatorInfo {
        public final Class<?> clazz;
        public final MethodHandle noArgsCtorHandle;
        public final Supplier<?> noArgsCtorLambda;
        public final Executable argsCreator;
        public final MethodHandle argsCreatorHandle;
        public final String[] argNames;
        public final Type[] argTypes;
        public final Map<String, Integer> argIndexes;
        public final Map<String, String> aliasMap;
        public CreatorInfo(Class<?> clazz, MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda,
                           Executable argsCreator, MethodHandle argsCreatorHandle,
                           String[] argNames, Type[] argTypes, Map<String, Integer> argIndexes,
                           Map<String, String> aliasMap) {
            this.clazz = clazz;
            this.noArgsCtorHandle = noArgsCtorHandle;
            this.noArgsCtorLambda = noArgsCtorLambda;
            this.argsCreator = argsCreator;
            this.argsCreatorHandle = argsCreatorHandle;
            this.argNames = argNames;
            this.argTypes = argTypes;
            this.argIndexes = argIndexes;
            this.aliasMap = aliasMap;
        }

        public int getArgIndex(String name) {
            if (argIndexes != null) {
                Integer idx = argIndexes.get(name);
                if (idx != null) return idx;
            }
            return -1;
        }

        public Object newPojoNoArgs() {
            if (noArgsCtorLambda != null) {
                return noArgsCtorLambda.get();
            } else if (noArgsCtorHandle != null) {
                try {
                    return noArgsCtorHandle.invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke constructor of " + clazz, e);
                }
            }
            throw new JsonException("Failed to create instance of " + clazz + ": Not found no-args constructor");
        }

        public Object newPojoWithArgs(Object[] args) {
            Objects.requireNonNull(args, "args is null");
            if (argsCreatorHandle == null) {
                throw new JsonException("Failed to create instance of " + clazz + ": No creator constructor");
            }
            try {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) {
                        Class<?> argClazz = Types.rawClazz(argTypes[i]);
                        args[i] = missingValueOfClass(argClazz);
                    }
                }
                return argsCreatorHandle.invokeWithArguments(args);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke creator constructor of " + clazz, e);
            }
        }

        public Object forceNewPojo() {
            if (noArgsCtorHandle != null) return newPojoNoArgs();
            Object[] args = new Object[argNames.length];
            return newPojoWithArgs(args);
        }

        private static Object missingValueOfClass(Class<?> clazz) {
            if (clazz == null) return null;
            if (!clazz.isPrimitive()) return null;
            if (clazz == boolean.class) return false;
            if (clazz == byte.class) return (byte) 0;
            if (clazz == short.class) return (short) 0;
            if (clazz == int.class) return 0;
            if (clazz == long.class) return 0L;
            if (clazz == float.class) return 0f;
            if (clazz == double.class) return 0d;
            if (clazz == char.class) return '\0';
            return null;
        }
    }

    // RecordInfo
    public static class RecordInfo {
        public final Class<?> clazz;
        public final Constructor<?> compCtor;
        public final MethodHandle compCtorHandle;
        public final int compCount;
        public final String[] compNames;
        public final Class<?>[] compClasses;
        public final Type[] compTypes;
        public RecordInfo(Class<?> clazz, Constructor<?> compCtor, MethodHandle compCtorHandle,
                          int compCount, String[] compNames, Class<?>[] compClasses, Type[] compTypes) {
            this.clazz = clazz;
            this.compCtor = compCtor;
            this.compCtorHandle = compCtorHandle;
            this.compCount = compCount;
            this.compNames = compNames;
            this.compClasses = compClasses;
            this.compTypes = compTypes;
        }
    }

    // FieldInfo
    public static class FieldInfo {
        public final String name;
        public final Type type;
        public final MethodHandle getter;
        public final Function<Object, Object> lambdaGetter;
        public final MethodHandle setter;
        public final BiConsumer<Object, Object> lambdaSetter;
        public FieldInfo(String name, Type type,
                         MethodHandle getter, Function<Object, Object> lambdaGetter,
                         MethodHandle setter, BiConsumer<Object, Object> lambdaSetter) {
            this.name = name;
            this.type = type;
            this.getter = getter;
            this.lambdaGetter = lambdaGetter;
            this.setter = setter;
            this.lambdaSetter = lambdaSetter;
        }

        public boolean hasGetter() {
            return getter != null;
        }
        public boolean hasSetter() {
            return setter != null;
        }


        public Object invokeGetter(Object receiver) {
            if (receiver == null) throw new IllegalArgumentException("Receiver must not be null");
            if (lambdaGetter != null) {
                return lambdaGetter.apply(receiver);
            }
            if (getter == null) {
                throw new JsonException("No getter available for field '" + name + "' of " + type);
            }
            try {
                return getter.invoke(receiver);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke getter for field '" + name + "' of " + type, e);
            }
        }

        public Object invokeGetter2(Object receiver) {
            if (receiver == null) throw new IllegalArgumentException("Receiver must not be null");
            if (getter == null) throw new JsonException("No getter available for field '" + name + "' of " + type);
            try {
                return getter.invoke(receiver);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke getter for field '" + name + "' of type '" +
                        type+ "' (node type: " + Types.name(receiver)+ ")", e);
            }
        }

        public boolean invokeSetterIfPresent(Object receiver, Object value) {
            if (setter == null) return false;
            invokeSetter(receiver, value);
            return true;
        }

        public void invokeSetter(Object receiver, Object value) {
            if (receiver == null) throw new IllegalArgumentException("Receiver must not be null");
            if (setter == null)
                throw new JsonException("No setter available for field '" + name + "' of " + type);
            try {
                if (lambdaSetter != null) {
                    lambdaSetter.accept(receiver, value);
                    return;
                }
                setter.invoke(receiver, value);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke setter for field '" + name + "' of type '" + type +
                        "' with value '" + Types.name(value) + "' (node type: " + Types.name(receiver)+ ")", e);
            }
        }

        public void invokeSetter2(Object receiver, Object value) {
            if (receiver == null) throw new IllegalArgumentException("Receiver must not be null");
            if (setter == null) {
                throw new JsonException("No setter available for field '" + name + "' of " + type);
            }
            if (value instanceof Number && type instanceof Class) {
                Class<?> clazz = (Class<?>) type;
                if ((clazz.isPrimitive() && clazz != boolean.class && clazz != char.class) ||
                        Number.class.isAssignableFrom(clazz)) {
                    value = Numbers.to((Number) value, clazz);
                }
            }
            try {
                setter.invoke(receiver, value);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke setter for field '" + name + "' of type '" + type +
                        "' with value " + (value == null ? "null" : value.getClass()), e);
            }
        }
    }

    // ValueCodecInfo
    public static class ValueCodecInfo {
        final Class<?> valueClazz;
        final Class<?> rawClazz;
        final ValueCodec<Object, Object> valueCodec;
        final MethodHandle encodeHandle;
        final MethodHandle decodeHandle;
        final MethodHandle copyHandle;
        @SuppressWarnings("unchecked")
        public ValueCodecInfo(Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> valueCodec,
                              MethodHandle encodeHandle, MethodHandle decodeHandle, MethodHandle copyHandle) {
            this.valueClazz = valueClazz;
            this.rawClazz = rawClazz;
            this.valueCodec = (ValueCodec<Object, Object>) valueCodec;
            this.encodeHandle = encodeHandle;
            this.decodeHandle = decodeHandle;
            this.copyHandle = copyHandle;
        }
        public ValueCodecInfo(Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> valueCodec) {
            this(valueClazz, rawClazz, valueCodec, null, null, null);
        }

        public Object encode(Object value) {
            if (valueCodec != null) {
                try {
                    return valueCodec.encode(value);
                } catch (Exception e) {
                    throw new JsonException("Failed to encode value of type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (encodeHandle != null) {
                try {
                    return encodeHandle.invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to encode value of type " + valueClazz.getName() +
                            " using @Encode method " + encodeHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @NodeValue annotation and no ValueCodec registered");
        }

        public Object decode(Object raw) {
            if (raw != null && !rawClazz.isInstance(raw))
                throw new JsonException("Cannot decode raw of type " + raw.getClass().getName() +
                        " to value type " + valueClazz.getName() + ". Expected raw type: " + rawClazz.getName());
            if (valueCodec != null) {
                try {
                    return valueCodec.decode(raw);
                } catch (Exception e) {
                    throw new JsonException("Failed to decode raw to value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (decodeHandle != null) {
                try {
                    return decodeHandle.invoke(raw);
                } catch (Throwable e) {
                    throw new JsonException("Failed to decode raw to value type " + valueClazz.getName() +
                            " using @Decode method " + decodeHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @NodeValue annotation and no ValueCodec registered");
        }

        public Object copy(Object value) {
            if (valueCodec != null) {
                try {
                    return valueCodec.copy(value);
                } catch (Exception e) {
                    throw new JsonException("Failed to copy value of type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (copyHandle != null) {
                try {
                    return copyHandle.invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to copy value of type " + valueClazz.getName() +
                            " using @Copy method " + copyHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @NodeValue annotation and no ValueCodec registered");
        }

    }

}
