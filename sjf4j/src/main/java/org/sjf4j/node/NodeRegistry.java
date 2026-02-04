package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.NodeValue;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


public final class NodeRegistry {

    /// NodeValue

    private static final Map<Class<?>, ValueCodecInfo> VALUE_CODEC_INFO_CACHE = new ConcurrentHashMap<>();

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

    public static class ValueCodecInfo {
        private final Class<?> valueClazz;
        private final Class<?> rawClazz;
        private final ValueCodec<Object, Object> valueCodec;
        private final MethodHandle encodeHandle;
        private final MethodHandle decodeHandle;
        private final MethodHandle copyHandle;

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

        public Class<?> getValueClass() {
            return valueClazz;
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
            } else {
                throw new JsonException("No value binding found for type " + valueClazz.getName() +
                        ": missing @NodeValue annotation and no ValueCodec registered");
            }
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
            } else {
                throw new JsonException("No value binding found for type " + valueClazz.getName() +
                        ": missing @NodeValue annotation and no ValueCodec registered");
            }
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
            } else {
                throw new JsonException("No value binding found for type " + valueClazz.getName() +
                        ": missing @NodeValue annotation and no ValueCodec registered");
            }
        }

    }

    public static ValueCodecInfo registerValueCodec(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        ValueCodecInfo vci = VALUE_CODEC_INFO_CACHE.get(clazz);
        if (vci == null) {
            if (clazz.isAnnotationPresent(NodeValue.class)) {
                vci = ReflectUtil.analyzeNodeValue(clazz);
                VALUE_CODEC_INFO_CACHE.put(clazz, vci);
            }
        }
        return vci;
    }

    public static void refreshInstantValueCodec(Sjf4jConfig.InstantFormat instantFormat) {
        VALUE_CODEC_INFO_CACHE.remove(java.time.Instant.class);
        if (instantFormat == Sjf4jConfig.InstantFormat.EPOCH_MILLIS) {
            registerValueCodec(new ValueCodec.InstantEpochMillisValueCodec());
        } else {
            registerValueCodec(new ValueCodec.InstantStringValueCodec());
        }
    }

    public static <N, R> ValueCodecInfo registerValueCodec(ValueCodec<N, R> valueCodec) {
        Objects.requireNonNull(valueCodec, "valueCodec is null");
        Class<R> rawClazz = valueCodec.getRawClass();
        if (!NodeType.of(rawClazz).isRaw())
            throw new JsonException("Invalid raw type in ValueCodec " + valueCodec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of the supported node value types: " +
                    "String, Number, Boolean, null, Map, or List.");

        Class<N> valueClazz = valueCodec.getValueClass();
        Objects.requireNonNull(valueClazz, "clazz is null");
        VALUE_CODEC_INFO_CACHE.put(valueClazz, new ValueCodecInfo(valueClazz, rawClazz, valueCodec,
                null, null, null));
        return VALUE_CODEC_INFO_CACHE.get(valueClazz);
    }

    public static ValueCodecInfo getValueCodecInfo(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return VALUE_CODEC_INFO_CACHE.get(clazz);
    }

    public static boolean isNodeValue(Class<?> clazz) {
        ValueCodecInfo vci = registerValueCodec(clazz);
        return vci != null;
    }

    public static Map<Class<?>, ValueCodecInfo> getAllValueCodecInfos() {
        return VALUE_CODEC_INFO_CACHE;
    }


    /// POJO

    private static final Map<Class<?>, Optional<PojoInfo>> POJO_CACHE = new ConcurrentHashMap<>();

    public static PojoInfo registerPojo(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return POJO_CACHE.computeIfAbsent(clazz, (k) -> {
            PojoInfo pi = ReflectUtil.analyzePojo(k, false);
            return pi != null ? Optional.of(pi) : Optional.empty();
        }).orElse(null);
    }

    /**
     * Registers a class and returns its POJO information, or throws an exception if it's not a valid POJO.
     *
     * @param clazz the class to register
     * @return the PojoInfo for the class
     * @throws IllegalArgumentException if the clazz parameter is null
     * @throws JsonException if the class is not a valid POJO
     */
    public static PojoInfo registerPojoOrElseThrow(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return POJO_CACHE.computeIfAbsent(clazz, (k) -> {
            PojoInfo pi = ReflectUtil.analyzePojo(k, true);
            return pi != null ? Optional.of(pi) : Optional.empty();
        }).orElseThrow(() -> new JsonException("Failed to register class " + clazz.getName() + " as a valid POJO"));
    }


    public static PojoInfo getPojoInfo(Class<?> clazz) {
        return POJO_CACHE.get(clazz).orElse(null);
    }

    /**
     * Retrieves field information for a specific field in a registered class.
     *
     * @param clazz the class containing the field
     * @param fieldName the name of the field to get information for
     * @return the FieldInfo for the specified field, or null if not found
     */
    public static FieldInfo getFieldInfo(Class<?> clazz, String fieldName) {
        return registerPojoOrElseThrow(clazz).getFields().get(fieldName);
    }

    /**
     * Checks if a class is a valid POJO.
     *
     * @param clazz the class to check
     * @return true if the class is a valid POJO, false otherwise
     */
    public static boolean isPojo(Class<?> clazz) {
        PojoInfo pi = registerPojo(clazz);
        return pi != null;
    }


    /// Info

    /**
     * Represents metadata about a POJO class, including its constructor and fields.
     */
    public static class PojoInfo {
        private final Class<?> clazz;
        private final CreatorInfo creatorInfo;
        private final Map<String, FieldInfo> fields;
        private final Map<String, FieldInfo> aliasFields;
        private final boolean isJojo;
        private final boolean isJajo;

        public PojoInfo(Class<?> clazz, CreatorInfo creatorInfo,
                        Map<String, FieldInfo> fields, Map<String, FieldInfo> aliasFields) {
            this.clazz = clazz;
            this.creatorInfo = creatorInfo;
            this.fields = fields;
            this.aliasFields = aliasFields;
            this.isJojo = JsonObject.class.isAssignableFrom(clazz);
            this.isJajo = JsonArray.class.isAssignableFrom(clazz);
        }

        public Class<?> getType() {
            return clazz;
        }

        public CreatorInfo getCreatorInfo() {
            return creatorInfo;
        }

        public Map<String, FieldInfo> getFields() {
            return fields;
        }

        public Map<String, FieldInfo> getAliasFields() {
            return aliasFields;
        }

        public boolean isJojo() {
            return isJojo;
        }
        public boolean isJajo() {
            return isJajo;
        }
    }

    public static class CreatorInfo {
        private final Class<?> clazz;
        private final MethodHandle noArgsCtorHandle;
        private final Supplier<?> noArgsCtorLambda;
        private final Executable argsCreator;
        private final MethodHandle argsCreatorHandle;
        private final String[] argNames;
        private final Type[] argTypes;
        private final Map<String, Integer> argIndexes;
        private final Map<String, String> aliasMap;

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

        public boolean hasCreator() {
            return argsCreator != null || noArgsCtorHandle != null;
        }

        public MethodHandle getNoArgsCtorHandle() {
            return noArgsCtorHandle;
        }

        public Supplier<?> getNoArgsCtorLambda() {
            return noArgsCtorLambda;
        }

        public Executable getArgsCreator() {
            return argsCreator;
        }

        public MethodHandle getArgsCreatorHandle() {
            return argsCreatorHandle;
        }

        public String[] getArgNames() {
            return argNames;
        }

        public Type[] getArgTypes() {
            return argTypes;
        }

        public Map<String, Integer> getArgIndexes() {
            return argIndexes;
        }

        public int getArgIndex(String name) {
            if (argIndexes != null) {
                Integer idx = argIndexes.get(name);
                if (idx != null) return idx;
            }
            return -1;
        }

        public Map<String, String> getAliasMap() {
            return aliasMap;
        }

        // Create instance
        public boolean hasNoArgsCtor() {
            return noArgsCtorHandle != null;
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
                Type[] argTypes = getArgTypes();
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
            if (hasNoArgsCtor()) return newPojoNoArgs();
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

    public static class RecordInfo {
        private final Class<?> clazz;
        private final Constructor<?> compCtor;
        private final MethodHandle compCtorHandle;
        private final int compCount;
        private final String[] compNames;
        private final Class<?>[] compClasses;
        private final Type[] compTypes;

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

        public Class<?> getClazz() {
            return clazz;
        }

        public Constructor<?> getCompCtor() {
            return compCtor;
        }

        public MethodHandle getCompCtorHandle() {
            return compCtorHandle;
        }

        public int getCompCount() {
            return compCount;
        }

        public String[] getCompNames() {
            return compNames;
        }

        public Class<?>[] getCompClasses() {
            return compClasses;
        }

        public Type[] getCompTypes() {
            return compTypes;
        }

    }

    /**
     * Represents metadata about a field in a POJO class, including its type and accessors.
     */
    public static class FieldInfo {
        private final String name;
        private final Type type;
        private final MethodHandle getter;
        private final Function<Object, Object> lambdaGetter;
        private final MethodHandle setter;
        private final BiConsumer<Object, Object> lambdaSetter;

        /**
         * Constructs a FieldInfo with the specified name, type, getters, and setters.
         *
         * @param name the field name
         * @param type the field type
         * @param getter the method handle for the getter
         * @param lambdaGetter a lambda that gets the field value
         * @param setter the method handle for the setter
         * @param lambdaSetter a lambda that sets the field value
         */
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

        public String getName() {
            return name;
        }

        /**
         * Gets the field type.
         *
         * @return the field type
         */
        public Type getType() {
            return type;
        }

        public boolean hasGetter() {
            return getter != null;
        }

        public MethodHandle getGetter() {
            return getter;
        }

        public boolean hasSetter() {
            return setter != null;
        }

        public MethodHandle getSetter() {
            return setter;
        }

        /**
         * Invokes the getter for this field on the specified receiver object.
         *
         * @param receiver the object to get the field value from
         * @return the field value
         * @throws IllegalArgumentException if the receiver is null
         * @throws JsonException if no getter is available or if invocation fails
         */
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

        /**
         * Invokes the getter for this field on the specified receiver object (alternative implementation).
         * <p>
         * FIXME: This method appears to be a test version.
         *
         * @param receiver the object to get the field value from
         * @return the field value
         * @throws IllegalArgumentException if the receiver is null
         * @throws JsonException if no getter is available or if invocation fails
         */
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

        /**
         * Invokes the setter for this field on the specified receiver object with the given value (alternative implementation).
         * <p>
         * FIXME: This method appears to be a test version.
         *
         * @param receiver the object to set the field value on
         * @param value the value to set
         * @throws IllegalArgumentException if the receiver is null
         * @throws JsonException if no setter is available, if value conversion fails, or if invocation fails
         */
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


}
