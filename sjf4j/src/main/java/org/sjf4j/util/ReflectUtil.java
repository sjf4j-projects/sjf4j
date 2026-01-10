package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeField;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeType;
import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.Copy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Decode;

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
    public static boolean  isPojoCandidate(Class<?> clazz) {
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
        if (NodeRegistry.isNodeValue(clazz)) {
            return false;
        }

        String pkg = clazz.getPackage() == null ? "" : clazz.getPackage().getName();
        return !inJdkPackage(pkg);
    }


    public static NodeRegistry.PojoInfo analyzePojo(Class<?> clazz, boolean orElseThrow) {
        if (!isPojoCandidate(clazz)) {
            if (orElseThrow) throw new JsonException("Class " + clazz.getName() + " cannot be a POJO candidate");
            else return null;
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
            if (orElseThrow) throw new JsonException("Missing no-args constructor of " + clazz.getName(), e);
        } catch (IllegalAccessException e) {
            if (orElseThrow) throw new JsonException("Cannot access no-args constructor of " + clazz.getName(), e);
        }

        Supplier<?> lambdaConstructor = null;
        if (constructor != null) {
            lambdaConstructor = createLambdaConstructor(lookup, clazz, constructor);
        }

        // Fields
        Map<String, NodeRegistry.FieldInfo> fields = Sjf4jConfig.global().mapSupplier.create();
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

                if (getter == null && lambdaGetter == null) {
//                    log.warn("No accessible getter or setter found for field '{}' of {}",
//                            field.getName(), curClazz);
                } else {
                    Type fieldType = TypeUtil.getFieldType(curType, field);
                    fields.put(getFieldName(field), new NodeRegistry.FieldInfo(field.getName(),
                            fieldType, getter, lambdaGetter, setter, lambdaSetter));
                }
            }

            curType = curClazz.getGenericSuperclass();
            curClazz = curClazz.getSuperclass();
        } while (isPojoCandidate(curClazz));

        return new NodeRegistry.PojoInfo(clazz, constructor, lambdaConstructor, fields);
    }

    public static String getFieldName(Field field) {
        NodeField nf = field.getAnnotation(NodeField.class);
        if (nf != null && !nf.value().isEmpty()) {
            return nf.value();
        } else {
            return field.getName();
        }
    }

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


    /// NodeValue

    public static NodeRegistry.ValueCodecInfo analyzeNodeValue(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(NodeValue.class)) return null;

        MethodHandle encodeHandle = null, decodeHandle = null, copyHandle = null;
        MethodHandles.Lookup lookup = ROOT_LOOKUP;
        if (!IS_JDK8) {
            try {
                lookup = (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, clazz, ROOT_LOOKUP);
            } catch (Exception ignored) {}
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class &&
                (encodeHandle == null || decodeHandle == null || copyHandle == null)) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.isBridge()) continue;
                // Encode
                if (encodeHandle == null && m.isAnnotationPresent(Encode.class)) {
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Cannot use @Encode on static methods");
                    if (current != clazz) {
                        Method override = findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        encodeHandle = lookup.unreflect(m);
                        continue;
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
                // Decode
                if (decodeHandle == null && m.isAnnotationPresent(Decode.class)) {
                    if (!Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Must use @Decode on static methods");
                    if (current != clazz) {
                        Method override = findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        decodeHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Copy
                if (copyHandle == null && m.isAnnotationPresent(Copy.class)) {
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("Cannot use @Copy on static methods");
                    if (current != clazz) {
                        Method override = findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        copyHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            current = current.getSuperclass();
        }

        if (encodeHandle == null)
            throw new JsonException("Missing @Encode method in class " + clazz.getName());
        if (encodeHandle.type().parameterCount() != 1) {
            throw new JsonException("@Encode method must have no parameters, but found " +
                    (encodeHandle.type().parameterCount() - 1));
        }
        Class<?> rawClazz = encodeHandle.type().returnType();
        if (!NodeType.of(rawClazz).isRaw())
            throw new JsonException("@Encode method return invalid type " + rawClazz.getName() +
                    " in class " + clazz.getName() +
                    ". The return type must be a supported raw type (String, Number, Boolean, null, Map, or List).");

        if (decodeHandle == null)
            throw new JsonException("Missing @Decode method in class " + clazz.getName());
        if (decodeHandle.type().parameterCount() != 1)
            throw new JsonException("@Decode method must have exactly one parameter, but found " +
                    decodeHandle.type().parameterCount());
        Class<?> rawClazz2 = decodeHandle.type().parameterType(0);
        Class<?> valueClazz2 = decodeHandle.type().returnType();
        if (rawClazz2 != rawClazz)
            throw new JsonException("@Decode method parameter type must match @Encode return type. " +
                    "Expected: " + rawClazz.getName() + ", Found: " + rawClazz2.getName());
        if (valueClazz2 != clazz)
            throw new JsonException("@Decode method return type must be " + clazz.getName() +
                    ", but found " + valueClazz2.getName());

        if (copyHandle != null) {
            if (copyHandle.type().parameterCount() != 1)
                throw new JsonException("@Copy method must have no parameters, but found " +
                        (copyHandle.type().parameterCount() + 1));
            Class<?> nodeClazz3 = copyHandle.type().returnType();
            if (nodeClazz3 != clazz)
                throw new JsonException("@Copy method return type must be " + clazz.getName() +
                        ", but found " + nodeClazz3.getName());
        }

        return new NodeRegistry.ValueCodecInfo(clazz, rawClazz, null,
                encodeHandle, decodeHandle, copyHandle);
    }

    private static Method findOverride(Method baseMethod, Class<?> clazz) {
        try {
            Method m = clazz.getDeclaredMethod(
                    baseMethod.getName(),
                    baseMethod.getParameterTypes()
            );
            if (m.isBridge()) return null;
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }


}
