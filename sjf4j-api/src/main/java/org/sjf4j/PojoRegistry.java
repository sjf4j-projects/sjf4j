package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.util.RecordUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class PojoRegistry {

    private static final Map<Class<?>, PojoInfo> POJO_CACHE = new ConcurrentHashMap<>();

    public static PojoInfo register(@NonNull Class<?> type) {
        if (RecordUtil.isRecordClass(type)) {
            throw new JsonException("Not support Record now");
        }
        return POJO_CACHE.computeIfAbsent(type, PojoRegistry::analyzePojo);
//        PojoInfo pojoInfo = POJO_CACHE.get(type);
//        if (pojoInfo == null) {
//            if (RecordUtil.isRecordClass(type)) {
//                throw new JsonException("Not support Record now");
//            } else {
//                pojoInfo = POJO_CACHE.computeIfAbsent(type, PojoRegistry::analyzePojo);
//            }
//        }
//        return pojoInfo;
    }

    public static PojoInfo registerOrElseThrow(@NonNull Class<?> type) {
        PojoInfo pi = register(type);
        pi.isPojoOrElseThrow();
        return pi;
    }

    public static void remove(@NonNull Class<?> type) {
        POJO_CACHE.remove(type);
    }

    public static PojoInfo getPojoInfo(@NonNull Class<?> type) {
        return POJO_CACHE.get(type);
    }

    public static FieldInfo getFieldInfo(@NonNull Class<?> type, String fieldName) {
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

    private static PojoInfo analyzePojo(@NonNull Class<?> type) {
        String pkg = type.getPackage() == null ? "" : type.getPackage().getName();
        if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("jakarta.")) {
            log.warn("Skipping Java system class: {}", type);
            return new PojoInfo(type, null, null);
        }

        // Constructor
        MethodHandle constructor = null;
        try {
            Constructor<?> con = type.getDeclaredConstructor();
            try {
                // JDK8
                con.setAccessible(true);
                constructor = ROOT_LOOKUP.unreflectConstructor(con);
            } catch (IllegalAccessException e) {
                try {
                    // JDK9+
                    Method m = MethodHandles.class.getMethod("privateLookupIn",
                            Class.class, MethodHandles.Lookup.class);
                    MethodHandles.Lookup lookup = (MethodHandles.Lookup) m.invoke(null, type, ROOT_LOOKUP);
                    constructor = lookup.unreflectConstructor(con);
                } catch (Exception e2) {
//                    throw new JsonException("Cannot access no-argument constructor of type " + type.getName());
                    log.warn("Cannot access no-args constructor of class {}", type.getName());
                }
            }
        } catch (NoSuchMethodException e) {
//            throw new JsonException("Missing no-argument constructor.");
            log.warn("Missing no-args constructor of class {}", type.getName());
        }
        if (constructor == null) {
            return new PojoInfo(type, null, null);
        }

        // Fields
        Map<String, FieldInfo> fields = new LinkedHashMap<>();
        for (Class<?> c = type; c != null && c != Object.class && c != JsonObject.class; c = c.getSuperclass()) {
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
                        MethodHandles.Lookup lookup = (MethodHandles.Lookup) m.invoke(null, type, ROOT_LOOKUP);
                        getter = lookup.unreflectGetter(field);
                        setter = lookup.unreflectSetter(field);
                    } catch (Exception e2) {
                        log.warn("Cannot access field '{}' of {}", field.getName(), type.getName(), e2);
                    }
                }
                if (getter == null && setter == null) {
                    log.warn("No accessible getter or setter found for field '{}' in class {}",
                            field.getName(), type.getName());
                } else {
                    fields.put(field.getName(), new FieldInfo(field.getName(), field.getGenericType(),getter, setter));
                }
            }
        }

        return new PojoInfo(type, constructor, fields);
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

    }


}
