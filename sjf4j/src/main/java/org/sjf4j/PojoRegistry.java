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


public final class PojoRegistry {

    private static final Map<Class<?>, Optional<PojoInfo>> POJO_CACHE = new ConcurrentHashMap<>();

    public static Optional<PojoInfo> register(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return POJO_CACHE.computeIfAbsent(clazz, PojoUtil::analyzePojo);
    }

    public static PojoInfo registerOrElseThrow(Class<?> clazz) {
        return register(clazz).orElseThrow(() -> new JsonException("Not a valid POJO"));
    }

    public static void remove(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        POJO_CACHE.remove(clazz);
    }

    public static PojoInfo getPojoInfo(Class<?> clazz) {
        return POJO_CACHE.get(clazz).orElse(null);
    }

    public static FieldInfo getFieldInfo(Class<?> clazz, String fieldName) {
        Optional<PojoInfo> opt = register(clazz);
        return opt.map(pi -> pi.getFields().get(fieldName)).orElse(null);
    }

    public static boolean isPojo(Class<?> clazz) {
        return register(clazz).map(PojoInfo::isPojo).orElse(false);
    }


    /// Info

    public static class PojoInfo {
        // Type may be better
        private final Class<?> type;
        private final MethodHandle constructor;
        private final Supplier<?> lambdaConstructor;
        private final Map<String, FieldInfo> fields;

        public PojoInfo(Class<?> type, MethodHandle constructor, Supplier<?> supplier) {
            this(type, constructor, supplier, null);
        }

        public PojoInfo(Class<?> type, MethodHandle constructor, Supplier<?> lambdaConstructor,
                        Map<String, FieldInfo> fields) {
            this.type = type;
            this.constructor = constructor;
            this.lambdaConstructor = lambdaConstructor;
            this.fields = fields;
        }

        public Map<String, FieldInfo> getFields() {
            return fields;
        }

        public boolean isPojo() {
            return constructor != null || lambdaConstructor != null;
        }

        public void isPojoOrElseThrow() {
            if (constructor == null && lambdaConstructor == null) {
                throw new JsonException("Not found no-args constructor of class " + type);
            }
//            if (!JsonObject.class.isAssignableFrom(type) && (fields == null || fields.isEmpty())) {
//                throw new JsonException("POJO " + type + " has no accessible fields and is not a JsonObject");
//            }
        }

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

        //FIXME: test
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

    public static class FieldInfo {
        private final String name;
        private final Type type;
        private final MethodHandle getter;
        private final Function<Object, Object> lambdaGetter;
        private final MethodHandle setter;
        private final BiConsumer<Object, Object> lambdaSetter;

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

        public Type getType() {
            return type;
        }

        public MethodHandle getGetter() {
            return getter;
        }

        public MethodHandle getSetter() {
            return setter;
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

        // FIXME: test
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

        // FIXME: test
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
