package org.sjf4j;

import com.sun.org.apache.xalan.internal.lib.NodeInfo;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.ReflectUtil;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static sun.awt.image.MultiResolutionCachedImage.map;


public final class NodeRegistry {

//    private static final Map<Class<?>, Optional<NodeInfo>> NODE_INFO_CACHE = new ConcurrentHashMap<>();

//    public static class NodeInfo {
//        ConvertibleInfo convertibleInfo;
//        PojoInfo pojoInfo;
//        public NodeInfo() {}
//        public NodeInfo(ConvertibleInfo convertibleInfo, PojoInfo pojoInfo) {
//            this.convertibleInfo = convertibleInfo;
//            this.pojoInfo = pojoInfo;
//        }
//
//        public boolean isConvertible() {
//            return convertibleInfo != null;
//        }
//
//        public boolean isPojo() {
//            return convertibleInfo == null && pojoInfo != null && pojoInfo.isPojo();
//        }
//
//        public ConvertibleInfo getConvertibleInfo() { return convertibleInfo; }
//        public PojoInfo getPojoInfo() { return pojoInfo; }
//    }

//    public static Optional<NodeInfo> getNodeInfo(Class<?> clazz) {
//        return NODE_INFO_CACHE.get(clazz);
////        Optional<NodeInfo> opt = NODE_INFO_CACHE.get(clazz);
////        if (opt != null && opt.isPresent()) {
////            return opt.get();
////        } else {
////            return null;
////        }
//    }

    /// Convertible

    private static final Map<Class<?>, ConvertibleInfo> CONVERTIBLE_CACHE = new ConcurrentHashMap<>();

    public static class ConvertibleInfo {
        private final Class<?> clazz;
        private final NodeConverter<Object> converter;
        private final MethodHandle convertHandle;
        private final MethodHandle unconvertHandle;
        private final MethodHandle copyHandle;

        @SuppressWarnings("unchecked")
        public ConvertibleInfo(Class<?> clazz, NodeConverter<?> converter,
                               MethodHandle convertHandle, MethodHandle unconvertHandle, MethodHandle copyHandle) {
            this.clazz = clazz;
            this.converter = (NodeConverter<Object>) converter;
            this.convertHandle = convertHandle;
            this.unconvertHandle = unconvertHandle;
            this.copyHandle = copyHandle;
        }

        public Object convert(Object node) {
            if (converter != null) {
                try {
                    return converter.convert(node);
                } catch (Exception e) {
                    throw new JsonException("Failed to convert using converter " + converter.getClass().getName() +
                            " for target type " + clazz.getName(), e);
                }
            } else if (convertHandle != null) {
                try {
                    return convertHandle.invoke(node);
                } catch (Throwable e) {
                    throw new JsonException("Failed to convert using @Convert method in " + clazz.getName(), e);
                }
            } else {
                throw new JsonException("No @Convert method or NodeConverter registered for " + clazz.getName());
            }
        }

        public Object unconvert(Object raw) {
            if (converter != null) {
                try {
                    return converter.unconvert(raw);
                } catch (Exception e) {
                    throw new JsonException("Failed to unconvert using converter " + converter.getClass().getName() +
                            " for target type " + clazz.getName(), e);
                }
            } else if (unconvertHandle != null) {
                try {
                    return unconvertHandle.invoke(raw);
                } catch (Throwable e) {
                    throw new JsonException("Failed to unconvert using @Unconvert method in " + clazz.getName(), e);
                }
            } else {
                throw new JsonException("No @Unconvert method or NodeConverter registered for " + clazz.getName());
            }
        }

        public Object copy(Object node) {
            if (converter != null) {
                try {
                    return converter.copy(node);
                } catch (Exception e) {
                    throw new JsonException("Failed to copy using converter " + converter.getClass().getName() +
                            " for target type " + clazz.getName(), e);
                }
            } else if (copyHandle != null) {
                try {
                    return copyHandle.invoke(node);
                } catch (Throwable e) {
                    throw new JsonException("Failed to copy using @Copy method in " + clazz.getName(), e);
                }
            } else {
                throw new JsonException("No @copy method or NodeConverter registered for " + clazz.getName());
            }
        }

    }

    public static ConvertibleInfo registerConvertible(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        ConvertibleInfo ci = ReflectUtil.analyzeConvertible(clazz);
        // FIXME: Facades
        CONVERTIBLE_CACHE.put(clazz, ci);
        return ci;
    }

    public static <T> ConvertibleInfo registerConvertible(Class<T> clazz, NodeConverter<T> converter) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        if (converter == null) throw new IllegalArgumentException("Converter must not be null");

        ConvertibleInfo ci = new ConvertibleInfo(clazz, converter, null, null, null);
        CONVERTIBLE_CACHE.put(clazz, ci);
        return ci;
    }

    public static ConvertibleInfo getConvertibleInfo(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return CONVERTIBLE_CACHE.get(clazz);
    }

    public static boolean isConvertible(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return CONVERTIBLE_CACHE.containsKey(clazz);
    }


    /// POJO

    private static final Map<Class<?>, Optional<PojoInfo>> POJO_CACHE = new ConcurrentHashMap<>();

    public static PojoInfo registerPojo(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return POJO_CACHE.computeIfAbsent(clazz, (k) -> {
            PojoInfo pi = ReflectUtil.analyzePojo(k);
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
        PojoInfo pi = registerPojo(clazz);
        if (pi == null) throw new JsonException("Not a valid POJO");
        return pi;
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
            return constructor != null || lambdaConstructor != null;
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
                    throw new JsonException("Failed to invoke constructor of class " + clazz, e);
                }
            }
            throw new JsonException("Not found no-args constructor of class " + clazz);
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
            throw new JsonException("No-args constructor not found for POJO " + clazz);
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
                    value = NumberUtil.as((Number) value, clazz);
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
                    value = NumberUtil.as((Number) value, clazz);
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