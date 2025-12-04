package org.sjf4j;

import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.PojoUtil;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registry for POJO (Plain Old Java Object) information. This class maintains a cache of POJO metadata,
 * including constructors and field information, to optimize the conversion between POJOs and JSON structures.
 * <p>
 * It provides methods for registering, retrieving, and managing POJO information, including constructor
 * handles and field accessors (getters/setters).
 */
public final class PojoRegistry {

    /**
     * Thread-safe cache that stores POJO information by class type.
     */
    private static final Map<Class<?>, Optional<PojoInfo>> POJO_CACHE = new ConcurrentHashMap<>();

    /**
     * Registers a class and returns its POJO information if it's a valid POJO.
     *
     * @param clazz the class to register
     * @return an Optional containing the PojoInfo if the class is a valid POJO, otherwise empty
     * @throws IllegalArgumentException if the clazz parameter is null
     */
    public static Optional<PojoInfo> register(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return POJO_CACHE.computeIfAbsent(clazz, PojoUtil::analyzePojo);
    }

    /**
     * Registers a class and returns its POJO information, or throws an exception if it's not a valid POJO.
     *
     * @param clazz the class to register
     * @return the PojoInfo for the class
     * @throws IllegalArgumentException if the clazz parameter is null
     * @throws JsonException if the class is not a valid POJO
     */
    public static PojoInfo registerOrElseThrow(Class<?> clazz) {
        return register(clazz).orElseThrow(() -> new JsonException("Not a valid POJO"));
    }

    /**
     * Removes a class from the POJO registry cache.
     *
     * @param clazz the class to remove
     * @throws IllegalArgumentException if the clazz parameter is null
     */
    public static void remove(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        POJO_CACHE.remove(clazz);
    }

    /**
     * Retrieves the POJO information for a registered class.
     *
     * @param clazz the class to get information for
     * @return the PojoInfo for the class, or null if the class is not registered or is not a valid POJO
     */
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
        Optional<PojoInfo> opt = register(clazz);
        return opt.map(pi -> pi.getFields().get(fieldName)).orElse(null);
    }

    /**
     * Checks if a class is a valid POJO.
     *
     * @param clazz the class to check
     * @return true if the class is a valid POJO, false otherwise
     */
    public static boolean isPojo(Class<?> clazz) {
        return register(clazz).map(PojoInfo::isPojo).orElse(false);
    }


    /// Info

    /**
     * Represents metadata about a POJO class, including its constructor and fields.
     */
    public static class PojoInfo {
        // Type may be better
        private final Class<?> type;
        private final MethodHandle constructor;
        private final Supplier<?> lambdaConstructor;
        private final Map<String, FieldInfo> fields;

        /**
         * Constructs a PojoInfo with the specified type, constructor, and supplier.
         *
         * @param type the POJO class type
         * @param constructor the method handle for the no-args constructor
         * @param supplier a lambda that creates new instances of the POJO
         */
        public PojoInfo(Class<?> type, MethodHandle constructor, Supplier<?> supplier) {
            this(type, constructor, supplier, null);
        }

        /**
         * Constructs a PojoInfo with the specified type, constructor, supplier, and fields.
         *
         * @param type the POJO class type
         * @param constructor the method handle for the no-args constructor
         * @param lambdaConstructor a lambda that creates new instances of the POJO
         * @param fields a map of field names to FieldInfo objects
         */
        public PojoInfo(Class<?> type, MethodHandle constructor, Supplier<?> lambdaConstructor,
                        Map<String, FieldInfo> fields) {
            this.type = type;
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
         * Checks if this object represents a valid POJO and throws an exception if not.
         *
         * @throws JsonException if no constructor is found for the POJO class
         */
        public void isPojoOrElseThrow() {
            if (constructor == null && lambdaConstructor == null) {
                throw new JsonException("Not found no-args constructor of class " + type);
            }
//            if (!JsonObject.class.isAssignableFrom(type) && (fields == null || fields.isEmpty())) {
//                throw new JsonException("POJO " + type + " has no accessible fields and is not a JsonObject");
//            }
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
                    throw new JsonException("Failed to invoke constructor of class " + type, e);
                }
            }
            throw new JsonException("Not found no-args constructor of class " + type);
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
                    throw new JsonException("Failed to invoke constructor for '" + type + "'", e);
                }
            }
            throw new JsonException("No-args constructor not found for POJO " + type);
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