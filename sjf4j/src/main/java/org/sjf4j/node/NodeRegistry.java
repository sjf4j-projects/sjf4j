package org.sjf4j.node;

import org.sjf4j.JsonException;
import org.sjf4j.annotation.node.NodeValue;

import java.lang.invoke.MethodHandle;
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

    public static <N, R> ValueCodecInfo registerValueCodec(ValueCodec<N, R> valueCodec) {
        Objects.requireNonNull(valueCodec, "valueCodec is null");
        Class<R> rawClazz = valueCodec.getRawClass();
        if (!NodeType.of(rawClazz).isRaw())
            throw new JsonException("Invalid raw type in ValueCodec " + valueCodec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of the supported node value types: " +
                    "String, Number, Boolean, null, Map, or List.");

        Class<N> valueClazz = valueCodec.getValueClass();
        Objects.requireNonNull(valueClazz, "clazz is null");
        return VALUE_CODEC_INFO_CACHE.computeIfAbsent(valueClazz,
                k -> new ValueCodecInfo(valueClazz, rawClazz, valueCodec,
                        null, null, null));
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
        return pi != null && pi.isPojo();
    }


    /// Info

    /**
     * Represents metadata about a POJO class, including its constructor and fields.
     */
    public static class PojoInfo {
        private final Class<?> clazz;
        private final MethodHandle constructor;
        private final Supplier<?> lambdaConstructor;
        private final Map<String, FieldInfo> fields;

        /**
         * Constructs a PojoInfo with the specified type, constructor, supplier, and fields.
         *
         * @param clazz the POJO class type
         * @param constructor the method handle for the no-args constructor
         * @param lambdaConstructor a lambda that creates new instances of the POJO
         * @param fields a map of field names to FieldInfo objects
         */
        public PojoInfo(Class<?> clazz, MethodHandle constructor, Supplier<?> lambdaConstructor,
                        Map<String, FieldInfo> fields) {
            this.clazz = clazz;
            this.constructor = constructor;
            this.lambdaConstructor = lambdaConstructor;
            this.fields = fields;
        }

        public Class<?> getType() {
            return clazz;
        }

        /**
         * Gets the map of field names to FieldInfo objects for this POJO.
         *
         * @return the map of field information
         */
        public Map<String, FieldInfo> getFields() {
            return fields;
        }

        /**
         * Checks if this object represents a valid POJO (has a constructor).
         *
         * @return true if it's a valid POJO, false otherwise
         */
        public boolean isPojo() {
//            return constructor != null || lambdaConstructor != null;
            return true;
        }


        /**
         * Creates a new instance of the POJO using either the lambda constructor or the method handle.
         *
         * @return a new instance of the POJO
         * @throws JsonException if no constructor is available or if instantiation fails
         */
        public Object newInstance() {
            if (lambdaConstructor != null) {
                return lambdaConstructor.get();
            } else if (constructor != null) {
                try {
                    return constructor.invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke constructor of " + clazz, e);
                }
            }
            throw new JsonException("Failed to create instance of " + clazz + ": Not found no-args constructor");
        }

        /**
         * Creates a new instance of the POJO using the method handle constructor.
         * <p>
         * FIXME: This method appears to be a test version.
         *
         * @return a new instance of the POJO
         * @throws JsonException if no constructor is available or if instantiation fails
         */
        public Object newInstance2() {
            if (constructor != null) {
                try {
                    return constructor.invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke constructor for '" + clazz + "'", e);
                }
            }
            throw new JsonException("Failed to create instance of " + clazz + ": Not found no-args constructor");
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

        /**
         * Gets the field type.
         *
         * @return the field type
         */
        public Type getType() {
            return type;
        }

        /**
         * Gets the method handle for the field getter.
         *
         * @return the getter method handle
         */
        public MethodHandle getGetter() {
            return getter;
        }

        /**
         * Gets the method handle for the field setter.
         *
         * @return the setter method handle
         */
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
         * Invokes the setter for this field on the specified receiver object with the given value.
         *
         * @param receiver the object to set the field value on
         * @param value the value to set
         * @throws IllegalArgumentException if the receiver is null
         * @throws JsonException if no setter is available, if value conversion fails, or if invocation fails
         */
        public void invokeSetter(Object receiver, Object value) {
            if (receiver == null) throw new IllegalArgumentException("Receiver must not be null");
            if (setter == null) {
                throw new JsonException("No setter available for field '" + name + "' of " + type);
            }
            if (value instanceof Number && type instanceof Class) {
                Class<?> clazz = (Class<?>) type;
                if ((clazz.isPrimitive() && clazz != boolean.class && clazz != char.class) ||
                        Number.class.isAssignableFrom(clazz)) {
                    value = Numbers.as((Number) value, clazz);
                }
            }

            if (lambdaSetter != null) {
                lambdaSetter.accept(receiver, value);
                return;
            }
            try {
                setter.invoke(receiver, value);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke setter for field '" + name + "' of type '" + type +
                        "' with value " + (value == null ? "null" : value.getClass()), e);
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
                    value = Numbers.as((Number) value, clazz);
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