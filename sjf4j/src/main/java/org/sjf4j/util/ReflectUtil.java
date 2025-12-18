package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeRegistry;
import org.sjf4j.annotation.convertible.Convert;
import org.sjf4j.annotation.convertible.Copy;
import org.sjf4j.annotation.convertible.NodeConvertible;
import org.sjf4j.annotation.convertible.Unconvert;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ReflectUtil {

    /**
     * Flag indicating if the current JVM is running JDK 8.
     */
    public static final boolean IS_JDK8 = System.getProperty("java.version").startsWith("1.");


    /// POJO

    /**
     * Determines if a given class is a valid POJO candidate for JSON conversion.
     *
     * @param clazz the class to check
     * @return true if the class is a valid POJO candidate, false otherwise
     */
    public static boolean isPojoCandidate(Class<?> clazz) {
        if (clazz == null || clazz == Object.class || clazz.isPrimitive() || clazz == String.class ||
                Number.class.isAssignableFrom(clazz) || clazz == Boolean.class) {
            return false;
        }
        if (clazz == JsonObject.class || clazz == JsonArray.class) {
            return false;
        }
        if (Map.class.isAssignableFrom(clazz) || List.class.isAssignableFrom(clazz) ||
                clazz.isEnum() || clazz.isInterface() || clazz.isArray()) {
            return false;
        }
        if (clazz.isAnnotationPresent(NodeConvertible.class)) {
            return false;
        }
        String pkg = clazz.getPackage() == null ? "" : clazz.getPackage().getName();
        return !inJdkPackage(pkg);
    }


    public static NodeRegistry.PojoInfo analyzePojo(Class<?> clazz) {
        if (!isPojoCandidate(clazz)) {
            return null;
        }

        // Constructor
        MethodHandles.Lookup lookup = ROOT_LOOKUP;
        if (!IS_JDK8) {
            try {
                lookup = (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, clazz, ROOT_LOOKUP);
            } catch (Exception e) {
//                log.debug("Failed to get 'privateLookupIn'", e);
            }
        }

        MethodHandle constructor = null;
        try {
            Constructor<?> con = clazz.getDeclaredConstructor();
            con.setAccessible(true);
            constructor = lookup.unreflectConstructor(con);
        } catch (NoSuchMethodException e) {
//            log.debug("Missing no-args constructor of {}", clazz);
        } catch (IllegalAccessException e) {
//            log.debug("Cannot access no-args constructor of {}", clazz);
        }

        if (constructor == null) {
            return null;
        }
        Supplier<?> lambdaConstructor = createLambdaConstructor(lookup, clazz, constructor);

        // Fields
        Map<String, NodeRegistry.FieldInfo> fields = JsonConfig.global().mapSupplier.create();
        Class<?> curClazz = clazz;
        Type curType = clazz;
        do {
            Field[] fds = curClazz.getDeclaredFields();
            AccessibleObject.setAccessible(fds, true);
            for (Field field : fds) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) { continue; }

                MethodHandle getter = null;
                try {
                    getter = lookup.unreflectGetter(field);
                } catch (Exception e) {
//                    log.warn("Failed to get getter for '{}' of {}", field.getName(), curClazz, e);
                }
                Function<Object, Object> lambdaGetter = createLambdaGetter(lookup, curClazz, field);

                MethodHandle setter = null;
                try {
                    setter = lookup.unreflectSetter(field);
                } catch (Exception e) {
//                    log.warn("Failed to get setter for '{}' of {}", field.getName(), curClazz, e);
                }
                BiConsumer<Object, Object> lambdaSetter = createLambdaSetter(lookup, curClazz, field);

                if (getter == null && setter == null) {
//                    log.warn("No accessible getter or setter found for field '{}' of {}",
//                            field.getName(), curClazz);
                } else {
                    Type fieldType = TypeUtil.getFieldType(curType, field);
                    fields.put(field.getName(),
                            new NodeRegistry.FieldInfo(field.getName(), fieldType,
                                    getter, lambdaGetter, setter, lambdaSetter));
                }
            }

            curType = curClazz.getGenericSuperclass();
            curClazz = curClazz.getSuperclass();
        } while (isPojoCandidate(curClazz));

        return new NodeRegistry.PojoInfo(clazz, constructor, lambdaConstructor, fields);
    }


    /// Private

    private static final String[] JDK_PREFIX = {
            "java.", "javax.", "jakarta.", "jdk."
    };

    private static boolean inJdkPackage(String pkg) {
        for (String prefix : JDK_PREFIX) if (pkg.startsWith(prefix)) return true;
        return false;
    }

    private static final MethodHandles.Lookup ROOT_LOOKUP = MethodHandles.lookup();

    private static final Method PRIVATE_LOOKUP_IN;

    static {
        try {
            PRIVATE_LOOKUP_IN = MethodHandles.class.getMethod("privateLookupIn", Class.class,
                    MethodHandles.Lookup.class);
        } catch (Exception e) {
            //FIXME: delete
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> createLambdaConstructor(
            MethodHandles.Lookup lookup,
            Class<T> clazz,
            MethodHandle constructor
    ) {
        if (constructor == null) return null;
        try {
            return (Supplier<T>) LambdaMetafactory.metafactory(
                    lookup,
                    "get",
                    MethodType.methodType(Supplier.class),
                    MethodType.methodType(Object.class), // erased SAM (Object)get()
                    constructor,
                    constructor.type().changeReturnType(clazz)
            ).getTarget().invoke();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String capitalize(String name) {
        return name.length() > 1
                ? Character.toUpperCase(name.charAt(0)) + name.substring(1)
                : name.toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, Object> createLambdaGetter(
            MethodHandles.Lookup lookup,
            Class<?> clazz,
            Field field
    ) {
        MethodHandle getter = null;
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            try {
                getter = lookup.findVirtual(clazz, "is" + capitalize(field.getName()),
                        MethodType.methodType(field.getType()));
            } catch (Exception ignored) {}
            if (getter == null) {
                try {
                    getter = lookup.findVirtual(clazz, "get" + capitalize(field.getName()),
                            MethodType.methodType(field.getType()));
                } catch (Exception ignored) {}
            }
        } else {
            try {
                getter = lookup.findVirtual(clazz, "get" + capitalize(field.getName()),
                        MethodType.methodType(field.getType()));
            } catch (Exception ignored) {}
        }
        if (getter == null) {
//            log.warn("Failed to find lambda getter for '{}' of {}", field.getName(), clazz);
            return null;
        }

        try {
            MethodType invokedType = MethodType.methodType(Function.class);
            MethodType samMethodType = MethodType.methodType(Object.class, Object.class);

            return (Function<Object, Object>) LambdaMetafactory.metafactory(
                    lookup,
                    "apply",
                    invokedType,
                    samMethodType,
                    getter,
                    getter.type()
            ).getTarget().invoke();
        } catch (Throwable e) {
//            log.warn("Failed to create lambda getter for '{}' of {}", field.getName(), clazz, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<Object, Object> createLambdaSetter(
            MethodHandles.Lookup lookup,
            Class<?> clazz,
            Field field
    ) {
        // Lambda-based setter does NOT support primitive types.
        if (field.getType().isPrimitive()) return null;

        MethodHandle setter = null;
        try {
            setter = lookup.findVirtual(clazz, "set" + capitalize(field.getName()),
                    MethodType.methodType(void.class, field.getType()));
        } catch (Exception e) {
//            log.warn("Failed to find lambda setter for '{}' of {}", field.getName(), clazz);
            return null;
        }

        try {
            MethodType invokedType = MethodType.methodType(BiConsumer.class);
            MethodType samMethodType = MethodType.methodType(void.class, Object.class, Object.class);

            return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
                    lookup,
                    "accept",   // BiConsumer.accept(T, V)
                    invokedType,
                    samMethodType,          // erased signature:  (Object, Object)void
                    setter,                 // (T, V)void
                    setter.type()           // implement signature
            ).getTarget().invoke();
        } catch (Throwable e) {
//            log.warn("Failed to create lambda setter for '{}' of {}", field.getName(), clazz, e);
            return null;
        }
    }


    /// Convertible

    public static NodeRegistry.ConvertibleInfo analyzeConvertible(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(NodeConvertible.class))
            throw new JsonException("Class " + clazz.getName() + " is not annotated with @NodeConvertible");

        MethodHandle convertHandle = null, unconvertHandle = null, copyHandle = null;
        MethodHandles.Lookup lookup = ROOT_LOOKUP;
        if (!IS_JDK8) {
            try {
                lookup = (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, clazz, ROOT_LOOKUP);
            } catch (Exception ignored) {}
        }

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Convert.class)) {
                try {
                    convertHandle = lookup.unreflect(m);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } else if (m.isAnnotationPresent(Unconvert.class)) {
                try {
                    unconvertHandle = lookup.unreflect(m);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } else if (m.isAnnotationPresent(Copy.class)) {
                try {
                    copyHandle = lookup.unreflect(m);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        // TODO: Check valid of convert/unconvert/copy

        if (convertHandle == null)
            throw new JsonException("Missing @Convert method in @NodeConvertible class " + clazz.getName());

        if (unconvertHandle == null)
            throw new JsonException("Missing @Unconvert method in @NodeConvertible class " + clazz.getName());

        return new NodeRegistry.ConvertibleInfo(clazz, null,
                convertHandle, unconvertHandle, copyHandle);
    }

}
