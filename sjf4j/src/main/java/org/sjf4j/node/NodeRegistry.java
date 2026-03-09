package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.path.JsonPath;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Registry for POJO metadata and {@link ValueCodec} mappings.
 */
public final class NodeRegistry {

    // All in TypeInfo
    private static final Map<Class<?>, TypeInfo> TYPE_INFO_CACHE = new ConcurrentHashMap<>();

    private static final TypeInfo NONE_INFO = new TypeInfo(Object.class, null, null, null);


    /**
     * Registers (or returns cached) metadata for a class.
     */
    public static TypeInfo registerTypeInfo(Class<?> clazz) {
        return registerTypeInfo(clazz, false);
    }

    /**
     * Registers type metadata and optionally enforces POJO availability.
     * <p>
     * Resolution order is: cache hit, {@code @NodeValue}/registered codec,
     * POJO analysis, then NONE marker.
     *
     * @param mustPojo when true, non-POJO results are rejected
     */
    public static TypeInfo registerTypeInfo(Class<?> clazz, boolean mustPojo) {
        if (fastNoType(clazz)) return NONE_INFO;
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
            ti = new TypeInfo(clazz, vci, null, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        AnyOf ann = clazz.getAnnotation(AnyOf.class);
        if (ann != null) {
            AnyOfInfo aoi = ReflectUtil.analyzeAnyOf(clazz, ann);
            ti = new TypeInfo(clazz, null, aoi, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        PojoInfo pi = ReflectUtil.analyzePojo(clazz, mustPojo);
        if (pi != null) {
            ti = new TypeInfo(clazz, null, null, pi);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        TYPE_INFO_CACHE.put(clazz, NONE_INFO);
        return NONE_INFO;
    }

    private static boolean fastNoType(Class<?> clazz) {
        return clazz == null || clazz == Object.class || clazz == String.class || clazz == Boolean.class
                || clazz == Map.class || clazz == List.class || clazz == Set.class || clazz.isPrimitive()
                || clazz == JsonObject.class || clazz == JsonArray.class;
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

    /**
     * Registers a custom {@link ValueCodec} and returns codec metadata.
     * <p>
     * The codec raw type must be a supported raw node type (String, Number,
     * Boolean, Map, List, or Object).
     */
    public static <N, R> ValueCodecInfo registerValueCodec(ValueCodec<N, R> valueCodec) {
        Objects.requireNonNull(valueCodec, "valueCodec is null");
        Class<R> rawClazz = valueCodec.rawClass();
        if (rawClazz != Object.class && !NodeKind.plainOf(rawClazz).isRaw())
            throw new JsonException("Invalid raw type in ValueCodec " + valueCodec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of String, Number, Boolean, Map, List or Object.");
        Class<N> valueClazz = valueCodec.valueClass();
        Objects.requireNonNull(valueClazz, "clazz is null");

        ValueCodecInfo vci = new ValueCodecInfo(valueClazz, rawClazz, valueCodec);
        TYPE_INFO_CACHE.put(valueClazz, new TypeInfo(valueClazz, vci, null, null));
        return vci;
    }

    /**
     * Registers value codec metadata from @NodeValue or existing codec.
     */
    public static ValueCodecInfo registerValueCodec(Class<?> clazz) {
        return registerTypeInfo(clazz).valueCodecInfo;
    }

    /**
     * Returns value codec metadata for a class.
     */
    public static ValueCodecInfo getValueCodecInfo(Class<?> clazz) {
        return registerValueCodec(clazz);
    }

    /**
     * Re-registers the Instant codec according to current instant format.
     * <p>
     * Existing Instant mappings are overwritten in the type metadata cache.
     */
    public static void refreshInstantValueCodec(Sjf4jConfig.InstantFormat instantFormat) {
        if (instantFormat == Sjf4jConfig.InstantFormat.EPOCH_MILLIS) {
            registerValueCodec(new ValueCodec.InstantEpochMillisValueCodec());
        } else {
            registerValueCodec(new ValueCodec.InstantStringValueCodec());
        }
    }


    /// POJO

    /**
     * Registers and returns POJO metadata for a class.
     */
    public static PojoInfo registerPojo(Class<?> clazz) {
        return registerTypeInfo(clazz).pojoInfo;
    }

    /**
     * Registers POJO metadata or throws if class is not a POJO.
     */
    public static PojoInfo registerPojoOrElseThrow(Class<?> clazz) {
        return registerTypeInfo(clazz, true).pojoInfo;
    }

    /**
     * Returns cached POJO metadata for a class.
     */
    public static PojoInfo getPojoInfo(Class<?> clazz) {
        return registerPojo(clazz);
    }

    /**
     * Returns metadata for a named POJO field.
     */
    public static FieldInfo getFieldInfo(Class<?> clazz, String fieldName) {
        return registerPojoOrElseThrow(clazz).fields.get(fieldName);
    }

    /**
     * Returns true when class is recognized as POJO.
     */
    public static boolean isPojo(Class<?> clazz) {
        return registerTypeInfo(clazz).pojoInfo != null;
    }

    /// Info

    // TypeInfo
    public static class TypeInfo {
        public final Class<?> clazz;
        public final ValueCodecInfo valueCodecInfo;
        public final AnyOfInfo anyOfInfo;
        public final PojoInfo pojoInfo;

        /**
         * Creates immutable type metadata holder.
         */
        public TypeInfo(Class<?> clazz, ValueCodecInfo valueCodecInfo, AnyOfInfo anyOfInfo, PojoInfo pojoInfo) {
            this.clazz = clazz;
            this.valueCodecInfo = valueCodecInfo;
            this.anyOfInfo = anyOfInfo;
            this.pojoInfo = pojoInfo;
        }
        /**
         * Returns true when this type has POJO metadata.
         */
        public boolean isPojo() {
            return pojoInfo != null;
        }
        /**
         * Returns true when this type has value codec metadata.
         */
        public boolean isNodeValue() {
            return valueCodecInfo != null;
        }
        public boolean isAnyOf() {
            return anyOfInfo != null;
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
        /**
         * Creates immutable POJO metadata holder.
         */
        public PojoInfo(Class<?> clazz, CreatorInfo creatorInfo,
                        Map<String, FieldInfo> fields,
                        Map<String, FieldInfo> aliasFields) {
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
        /**
         * Creates immutable creator metadata holder.
         */
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

        /**
         * Returns argument index by name, or -1.
         */
        public int getArgIndex(String name) {
            if (argIndexes != null) {
                Integer idx = argIndexes.get(name);
                if (idx != null) return idx;
            }
            return -1;
        }

        /**
         * Creates a POJO using no-args constructor path.
         */
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

        /**
         * Creates a POJO using argument creator path.
         */
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

        /**
         * Creates a POJO preferring no-args constructor, then args creator.
         */
        public Object forceNewPojo() {
            if (noArgsCtorHandle != null) return newPojoNoArgs();
            Object[] args = new Object[argNames.length];
            return newPojoWithArgs(args);
        }

        /**
         * Returns default missing value for primitive classes.
         */
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
        /**
         * Creates immutable record metadata holder.
         */
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
        public enum ContainerKind {
            NONE,
            LIST,
            SET,
            MAP,
            ARRAY
        }

        public final String name;
        public final Type type;
        public final Class<?> rawClazz;
        public final ContainerKind containerKind;
        public final Type argType;
        public final Class<?> argRawClazz;
        public final MethodHandle getter;
        public final Function<Object, Object> lambdaGetter;
        public final MethodHandle setter;
        public final BiConsumer<Object, Object> lambdaSetter;

        public final AnyOfInfo anyOfInfo;

        /**
         * Creates immutable field metadata holder.
         */
        public FieldInfo(String name, Type type,
                         MethodHandle getter, Function<Object, Object> lambdaGetter,
                         MethodHandle setter, BiConsumer<Object, Object> lambdaSetter,
                         AnyOfInfo anyOfInfo) {
            this.name = name;
            this.type = type;
            this.rawClazz = Types.rawBox(type);
            ContainerKind kind = ContainerKind.NONE;
            Type argType = null;
            Class<?> argRawType = null;
            if (this.rawClazz == List.class) {
                kind = ContainerKind.LIST;
                argType = Types.resolveTypeArgument(type, List.class, 0);
                argRawType = Types.rawBox(argType);
            } else if (this.rawClazz == Set.class) {
                kind = ContainerKind.SET;
                argType = Types.resolveTypeArgument(type, Set.class, 0);
                argRawType = Types.rawBox(argType);
            } else if (this.rawClazz == Map.class) {
                kind = ContainerKind.MAP;
                argType = Types.resolveTypeArgument(type, Map.class, 1);
                argRawType = Types.rawBox(argType);
            } else if (this.rawClazz.isArray()) {
                kind = ContainerKind.ARRAY;
                argType = this.rawClazz.getComponentType();
                argRawType = Types.box((Class<?>) argType);
            }
            this.containerKind = kind;
            this.argType = argType;
            this.argRawClazz = argRawType;
            this.getter = getter;
            this.lambdaGetter = lambdaGetter;
            this.setter = setter;
            this.lambdaSetter = lambdaSetter;
            this.anyOfInfo = anyOfInfo;
        }

        /**
         * Returns true when a getter is available.
         */
        public boolean hasGetter() {
            return getter != null;
        }
        /**
         * Returns true when a setter is available.
         */
        public boolean hasSetter() {
            return setter != null;
        }


        /**
         * Invokes field getter.
         */
        public Object invokeGetter(Object receiver) {
            if (receiver == null) throw new JsonException("receiver is null");
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

        /**
         * Invokes field getter using method handle only.
         */
        public Object invokeGetter2(Object receiver) {
            if (receiver == null) throw new JsonException("receiver is null");
            if (getter == null) throw new JsonException("No getter available for field '" + name + "' of " + type);
            try {
                return getter.invoke(receiver);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke getter for field '" + name + "' of type '" +
                        type+ "' (node type: " + Types.name(receiver)+ ")", e);
            }
        }

        /**
         * Invokes setter when present and reports success.
         */
        public boolean invokeSetterIfPresent(Object receiver, Object value) {
            if (setter == null) return false;
            invokeSetter(receiver, value);
            return true;
        }

        /**
         * Invokes field setter.
         */
        public void invokeSetter(Object receiver, Object value) {
            if (receiver == null) throw new JsonException("receiver is null");
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

        /**
         * Invokes setter with numeric pre-conversion fallback.
         */
        public void invokeSetter2(Object receiver, Object value) {
            if (receiver == null) throw new JsonException("receiver is null");
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
        final MethodHandle valueToRawHandle;
        final MethodHandle rawToValueHandle;
        final MethodHandle valueCopyHandle;
        /**
         * Creates immutable value-codec metadata holder.
         */
        @SuppressWarnings("unchecked")
        public ValueCodecInfo(Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> valueCodec,
                              MethodHandle valueToRawHandle, MethodHandle rawToValueHandle, MethodHandle valueCopyHandle) {
            this.valueClazz = valueClazz;
            this.rawClazz = rawClazz;
            this.valueCodec = (ValueCodec<Object, Object>) valueCodec;
            this.valueToRawHandle = valueToRawHandle;
            this.rawToValueHandle = rawToValueHandle;
            this.valueCopyHandle = valueCopyHandle;
        }
        /**
         * Creates value-codec metadata from codec instance.
         */
        public ValueCodecInfo(Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> valueCodec) {
            this(valueClazz, rawClazz, valueCodec, null, null, null);
        }

        public Class<?> getValueClazz() {
            return valueClazz;
        }
        public Class<?> getRawClazz() {
            return rawClazz;
        }

        /**
         * Encodes value to raw representation.
         */
        public Object valueToRaw(Object value) {
            if (valueCodec != null) {
                try {
                    return valueCodec.valueToRaw(value);
                } catch (Exception e) {
                    throw new JsonException("Failed to valueToRaw() for value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (valueToRawHandle != null) {
                try {
                    return valueToRawHandle.invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to valueToRaw() for value type " + valueClazz.getName() +
                            " using annotated method " + valueToRawHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
        }

        /**
         * Decodes raw value to value representation.
         */
        public Object rawToValue(Object raw) {
            if (raw != null && !rawClazz.isInstance(raw))
                throw new JsonException("Cannot rawToValue() from raw type " + raw.getClass().getName() +
                        " to value type " + valueClazz.getName() + ". Expected raw type: " + rawClazz.getName());
            if (valueCodec != null) {
                try {
                    return valueCodec.rawToValue(raw);
                } catch (Exception e) {
                    throw new JsonException("Failed to rawToValue() to value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (rawToValueHandle != null) {
                try {
                    return rawToValueHandle.invoke(raw);
                } catch (Throwable e) {
                    throw new JsonException("Failed to rawToValue() to value type " + valueClazz.getName() +
                            " using annotated method " + rawToValueHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @" + NodeValue.class.getName()+ " annotation and no ValueCodec registered");
        }

        /**
         * Copies value using codec-defined semantics.
         */
        public Object valueCopy(Object value) {
            if (valueCodec != null) {
                try {
                    return valueCodec.valueCopy(value);
                } catch (Exception e) {
                    throw new JsonException("Failed to valueCopy() for value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (valueCopyHandle != null) {
                try {
                    return valueCopyHandle.invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to valueCopy() for value type " + valueClazz.getName() +
                            " using annotated method " + valueCopyHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
        }

    }

    // AnyOfInfo

    public static class AnyOfInfo {
        final Class<?> clazz;
        final AnyOf.Mapping[] mappings;
        final String key;
        final String path;
        final AnyOf.Scope scope;
        final AnyOf.OnNoMatch onNoMatch;
        final boolean hasDiscriminator;
        final EnumMap<JsonType, Class<?>> byJsonType;
        final Map<String, Class<?>> byWhen;
        final JsonPath compiledPath;

        public AnyOfInfo(Class<?> clazz, AnyOf.Mapping[] mappings, String key,
                         String path, AnyOf.Scope scope, AnyOf.OnNoMatch onNoMatch) {
            this.clazz = clazz;
            this.mappings = mappings;
            this.key = key;
            this.path = path;
            this.scope = scope;
            this.onNoMatch = onNoMatch;
            this.compiledPath = path.isEmpty() ? null : JsonPath.compile(path);
            this.hasDiscriminator = !key.isEmpty() || !path.isEmpty();
            if (hasDiscriminator) {
                this.byJsonType = null;
                this.byWhen = new HashMap<>();
                for (AnyOf.Mapping mapping : mappings) {
                    for (String when : mapping.when()) {
                        byWhen.put(when, mapping.value());
                    }
                }
            } else {
                this.byWhen = null;
                this.byJsonType = new EnumMap<>(JsonType.class);
                for (AnyOf.Mapping mapping : mappings) {
                    byJsonType.put(JsonType.rawOf(mapping.value()), mapping.value());
                }
            }
        }

        public boolean hasDiscriminator() {return hasDiscriminator;}
        public AnyOf.Scope getScope() {return scope;}
        public AnyOf.OnNoMatch getOnNoMatch() {return onNoMatch;}
        public String getKey() {return key;}
        public String getPath() {return path;}

        public JsonPath getCompiledPath() {
            return compiledPath;
        }

        public Class<?> resolveByJsonType(JsonType jsonType) {
            if (byJsonType == null || jsonType == null) return null;
            return byJsonType.get(jsonType);
        }

        public Class<?> resolveByWhen(Object when) {
            if (byWhen == null || when == null) return null;
            return byWhen.get(String.valueOf(when));
        }


    }


}
