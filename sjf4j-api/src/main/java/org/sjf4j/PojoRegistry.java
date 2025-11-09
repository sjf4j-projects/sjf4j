package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.util.RecordUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class PojoRegistry {

    // Use `Type` to support TypeReference<Wrapper<Person, Baby>>
    private static final Map<Type, PojoInfo> POJO_CACHE = new ConcurrentHashMap<>();


    public static PojoInfo register(@NonNull Type type) {
        while (type instanceof TypeReference) {
            type = ((TypeReference<?>) type).getType();
        }
        if (POJO_CACHE.containsKey(type)) {
            return POJO_CACHE.get(type);
        }

        Class<?> clazz = TypeUtil.getRawClass(type);
        if (clazz.isArray()) {
            log.warn("Skipping Array class: {}", clazz);
            return null;
        }
        String pkg = clazz.getPackage() == null ? "" : clazz.getPackage().getName();
        if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("jakarta.")) {
            log.warn("Skipping Java system class: {}", clazz);
            return null;
        }

        if (RecordUtil.isRecordClass(type)) {
            //TODO
            throw new JsonException("Not support Record yet");
        }

        PojoInfo pi = analyzePojo(type);
        POJO_CACHE.put(type, pi);
        return pi;
    }

    public static PojoInfo registerOrElseThrow(@NonNull Type type) {
        PojoInfo pi = register(type);
        if (pi == null) throw new JsonException("Not a valid POJO");
        pi.isPojoOrElseThrow();
        return pi;
    }

    public static void remove(@NonNull Type type) {
        POJO_CACHE.remove(type);
    }

    public static PojoInfo getPojoInfo(@NonNull Type type) {
        return POJO_CACHE.get(type);
    }

    public static FieldInfo getFieldInfo(@NonNull Type type, String fieldName) {
        PojoInfo pojoInfo = getPojoInfo(type);
        if (pojoInfo != null) {
            return pojoInfo.getFields().get(fieldName);
        }
        return null;
    }

    public static boolean isPojo(@NonNull Class<?> type) {
        return register(type).isPojo();
    }


    /// Private

    private static final MethodHandles.Lookup ROOT_LOOKUP = MethodHandles.lookup();

    private static PojoInfo analyzePojo(@NonNull Type type) {
        Class<?> clazz = TypeUtil.getRawClass(type);
        if (clazz.isArray()) {
            log.warn("Skipping Array class: {}", clazz);
            return new PojoInfo(clazz, null, null);
        }

        String pkg = clazz.getPackage() == null ? "" : clazz.getPackage().getName();
        if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("jakarta.")) {
            log.warn("Skipping Java system class: {}", clazz);
            return new PojoInfo(clazz, null, null);
        }

        // Constructor
        MethodHandle constructor = null;
        try {
            Constructor<?> con = clazz.getDeclaredConstructor();
            try {
                // JDK8
                con.setAccessible(true);
                constructor = ROOT_LOOKUP.unreflectConstructor(con);
            } catch (IllegalAccessException e) {
                try {
                    // JDK9+
                    Method m = MethodHandles.class.getMethod("privateLookupIn",
                            Class.class, MethodHandles.Lookup.class);
                    MethodHandles.Lookup lookup = (MethodHandles.Lookup) m.invoke(null, clazz, ROOT_LOOKUP);
                    constructor = lookup.unreflectConstructor(con);
                } catch (Exception e2) {
//                    throw new JsonException("Cannot access no-argument constructor of type " + type.getName());
                    log.warn("Cannot access no-args constructor of class {}", clazz.getName());
                }
            }
        } catch (NoSuchMethodException e) {
//            throw new JsonException("Missing no-argument constructor.");
            log.warn("Missing no-args constructor of class {}", clazz.getName());
        }
        if (constructor == null) {
            return new PojoInfo(clazz, null, null);
        }

        // Fields
        Map<String, FieldInfo> fields = new LinkedHashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class && c != JsonObject.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) { continue; }
                MethodHandle getter = null;
                MethodHandle setter = null;
                try {
                    // JDK8
                    field.setAccessible(true);
                    getter = ROOT_LOOKUP.unreflectGetter(field);
                    setter = ROOT_LOOKUP.unreflectSetter(field);
                } catch (IllegalAccessException e) {
                    try {
                        // JDK9+
                        Method m = MethodHandles.class.getMethod("privateLookupIn",
                                Class.class, MethodHandles.Lookup.class);
                        MethodHandles.Lookup lookup = (MethodHandles.Lookup) m.invoke(null, clazz, ROOT_LOOKUP);
                        getter = lookup.unreflectGetter(field);
                        setter = lookup.unreflectSetter(field);
                    } catch (Exception e2) {
                        log.warn("Cannot access field '{}' of {}", field.getName(), clazz.getName(), e2);
                    }
                }
                if (getter == null && setter == null) {
                    log.warn("No accessible getter or setter found for field '{}' in class {}",
                            field.getName(), clazz.getName());
                } else {
                    Type fieldType = TypeUtil.getFieldType(type, field);
                    fields.put(field.getName(), new FieldInfo(field.getName(), fieldType, getter, setter));
                }
            }
        }

        return new PojoInfo(clazz, constructor, fields);
    }


    /// Class

    @Getter @Setter
    public static class PojoInfo {
        private Class<?> type;
        private MethodHandle constructor;
        private Map<String, FieldInfo> fields;

        public PojoInfo(Class<?> type, MethodHandle constructor) {
            this(type, constructor, null);
        }

        public PojoInfo(Class<?> type, MethodHandle constructor, Map<String, FieldInfo> fields) {
            this.type = type;
            this.constructor = constructor;
            this.fields = fields;
        }

        public boolean isPojo() {
            return constructor != null &&
                    (JsonObject.class.isAssignableFrom(type) || (fields != null && !fields.isEmpty()));
        }

        public void isPojoOrElseThrow() {
            if (constructor == null) {
                throw new JsonException("No-args constructor not found for POJO " + type);
            }
            if (!JsonObject.class.isAssignableFrom(type) && (fields == null || fields.isEmpty())) {
                throw new JsonException("POJO " + type + " has no accessible fields and is not a JsonObject");
            }
        }

    }

    @Getter @Setter
    public static class FieldInfo {
        private String name;
        private Type type;
        private MethodHandle getter;
        private MethodHandle setter;

        public FieldInfo(String name, Type type, MethodHandle getter, MethodHandle setter) {
            this.name = name;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
        }

        public Object invokeGetter(@NonNull Object receiver) {
            if (getter == null) {
                throw new JsonException("No getter available for field '" + name + "' of " + type);
            }
            try {
                return getter.invoke(receiver);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke getter for field '" + name + "' of " + type, e);
            }
        }

        public void invokeSetter(@NonNull Object receiver, Object value) {
            if (setter == null) {
                throw new JsonException("No setter available for field '" + name + "' of " + type);
            }
            try {
                setter.invoke(receiver, value);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke setter for field '" + name + "' of " + type +
                        " with value " + (value == null ? "null" : value.getClass()), e);
            }
        }

    }


}
