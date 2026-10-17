package org.sjf4j.value;

import org.sjf4j.NodeKind;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.util.Invokers;
import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry of value codecs.
 *
 * <p>Register custom codecs during application initialization, before metadata
 * for the codec type or dependent bindings is analyzed. The registry is shared
 * by all {@code Sjf4j} instances and later registrations do not update metadata
 * already cached by {@link TypeRegistry}.
 */
public final class ValueRegistry {
    private ValueRegistry() {}

    /* Index zero is always the default codec. */
    private static final ConcurrentHashMap<Class<?>, ValueInfo[]> VALUE_INFOS = new ConcurrentHashMap<>();

    /**
     * Resolves codecs registered directly for a class or declared through {@code @NodeValue}.
     * Index zero is the default codec. Returns {@code null} when no codec set matches.
     * The returned array is registry-owned and must not be modified.
     */
    public static ValueInfo[] resolve(Class<?> runtimeClazz) {
        Asserts.notNull(runtimeClazz, "runtimeClazz");

        ValueInfo[] infos = VALUE_INFOS.get(runtimeClazz);
        if (infos != null) return infos;

        ValueInfo info = analyzeByAnnotation(runtimeClazz);
        if (info != null) return new ValueInfo[]{info};

        ArrayDeque<Class<?>> types = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        types.add(runtimeClazz);
        visited.add(runtimeClazz);
        for (int distance = 0; !types.isEmpty(); distance++) {
            ValueInfo[] matched = null;
            Class<?> matchType = null;
            for (int count = types.size(); count > 0; count--) {
                Class<?> type = types.remove();
                infos = VALUE_INFOS.get(type);
                if (infos != null) {
                    if (matched != null) {
                        throw new BindingException("ambiguous NodeValue for runtime type '" +
                                runtimeClazz.getName() + "': registered for '" + matchType.getName() +
                                "' and '" + type.getName() + "' at type distance " + distance + "'");
                    }
                    matched = infos;
                    matchType = type;
                }

                Class<?> superclass = type.getSuperclass();
                if (superclass != null && visited.add(superclass)) types.add(superclass);
                for (Class<?> iface : type.getInterfaces()) {
                    if (visited.add(iface)) types.add(iface);
                }
            }
            if (matched != null) {
                ValueInfo[] resolved = new ValueInfo[matched.length];
                for (int i = 0; i < matched.length; i++) {
                    resolved[i] = new ValueInfo(runtimeClazz, matched[i]);
                }
                return resolved;
            }
        }
        return null;
    }


    /**
     * Registers value codec metadata. Published arrays are immutable snapshots.
     * Custom registrations must be completed during application initialization
     * before relevant metadata is analyzed.
     * The raw class must be a fixed supported type: String, Number, Boolean,
     * Map, or List. A JSON null is a runtime value and is not represented by
     * a raw class.
     *
     * @param forceDefault whether to replace the current default when a codec is already registered
     */
    public static void register(ValueInfo valueInfo, boolean forceDefault) {
        Asserts.notNull(valueInfo, "nodeValueInfo");
        validateRegistration(valueInfo);

        VALUE_INFOS.compute(valueInfo.valueClazz, (valueClazz, oldInfos) -> {
            if (oldInfos == null) {
                return new ValueInfo[]{valueInfo};
            }

            for (ValueInfo oldInfo : oldInfos) {
                if (oldInfo != null && valueInfo.valueFormat.equals(oldInfo.valueFormat)) {
                    throw new BindingException("valueCodec already registered for type '" + valueClazz.getName() +
                            "' and valueFormat '" + valueInfo.valueFormat + "'");
                }
            }

            ValueInfo[] infos = new ValueInfo[oldInfos.length + 1];
            if (forceDefault) {
                infos[0] = valueInfo;
                System.arraycopy(oldInfos, 0, infos, 1, oldInfos.length);
            } else {
                System.arraycopy(oldInfos, 0, infos, 0, oldInfos.length);
                infos[oldInfos.length] = valueInfo;
            }
            return infos;
        });
    }

    /**
     * Registers a codec using its declared value and raw classes. The same
     * application-initialization requirement as {@link #register(ValueInfo, boolean)} applies.
     * The raw class must be a fixed supported type: String, Number, Boolean,
     * Map, or List. A JSON null is a runtime value and is not represented by
     * a raw class.
     *
     * @param valueFormat the named format, or {@code null} for the default format
     * @param forceDefault whether to replace the current default when a codec is already registered
     */
    public static <N, R> void registerByCodec(ValueCodec<N, R> codec, String valueFormat, boolean forceDefault) {
        Asserts.notNull(codec, "codec");

        Class<R> rawClazz = codec.rawClazz();
        Class<N> valueClazz = codec.valueClazz();

        ValueInfo info = new ValueInfo(valueFormat, valueClazz, rawClazz, codec, null, null, null);
        register(info, forceDefault);
    }

    /**
     * Registers the public static final ValueCodec fields annotated with
     * {@link NodeValue} on the specified declaring class. Registration must
     * precede metadata analysis; this does not rescan inherited fields.
     *
     * <p>All declarations are checked before publishing any registrations.
     * Default formats are registered first; named-only fields are ordered by
     * field name to avoid dependence on reflection's field enumeration order.
     */
    public static void registerAnnotated(Class<?> registryClass) {
        Asserts.notNull(registryClass, "registryClass");

        Field[] fields = registryClass.getDeclaredFields();
        // Default-bearing fields come first, including their named aliases.
        // This preserves Instant's historical default/iso/epochMillis order.
        Arrays.sort(fields, Comparator
                .comparing((Field f) -> !hasDefaultFormat(f))
                .thenComparing(Field::getName));

        List<ValueInfo> defaults = new ArrayList<>();
        List<ValueInfo> named = new ArrayList<>();
        Map<Class<?>, Set<String>> declaredFormats = new HashMap<>();

        for (Field field : fields) {
            NodeValue annotation = field.getAnnotation(NodeValue.class);
            if (annotation == null) continue;

            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
                    || !Modifier.isFinal(modifiers)
                    || !ValueCodec.class.isAssignableFrom(field.getType())) {
                throw new BindingException("@NodeValue codec field must be public static final ValueCodec: "
                        + field);
            }

            Type declaredType = field.getGenericType();
            if (!(declaredType instanceof ParameterizedType)) {
                throw new BindingException("@NodeValue codec field must declare concrete V/R types: " + field);
            }
            Type[] arguments = ((ParameterizedType) declaredType).getActualTypeArguments();
            if (arguments.length != 2 || !(arguments[0] instanceof Class)
                    || !(arguments[1] instanceof Class)) {
                throw new BindingException("@NodeValue codec field must declare concrete V/R classes: " + field);
            }

            ValueCodec<?, ?> codec;
            try {
                codec = (ValueCodec<?, ?>) field.get(null);
            } catch (IllegalAccessException e) {
                throw new BindingException("cannot access @NodeValue codec field: " + field, e);
            }
            if (codec == null) {
                throw new BindingException("@NodeValue codec field is null: " + field);
            }
            if (codec.valueClazz() != arguments[0] || codec.rawClazz() != arguments[1]) {
                throw new BindingException("@NodeValue codec field V/R types do not match codec: " + field);
            }

            String[] formats = annotation.formats();
            if (formats.length == 0) {
                throw new BindingException("@NodeValue codec field has no formats: " + field);
            }
            Set<String> seen = declaredFormats.computeIfAbsent(codec.valueClazz(), k -> new HashSet<>());
            for (String format : formats) {
                if (!seen.add(format)) {
                    throw new BindingException("duplicate @NodeValue codec format '" + format
                            + "' for type '" + codec.valueClazz().getName() + "'");
                }
                ValueInfo info = new ValueInfo(format, codec.valueClazz(), codec.rawClazz(),
                        codec, null, null, null);
                validateRegistration(info);
                (format.isEmpty() ? defaults : named).add(info);
            }
        }

        for (ValueInfo info : defaults) register(info, false);
        for (ValueInfo info : named) register(info, false);
    }

    private static boolean hasDefaultFormat(Field field) {
        NodeValue annotation = field.getAnnotation(NodeValue.class);
        if (annotation == null) return false;
        for (String format : annotation.formats()) {
            if (format.isEmpty()) return true;
        }
        return false;
    }

    private static void validateRegistration(ValueInfo valueInfo) {
        Class<?> rawClazz = Asserts.notNull(valueInfo.rawClazz, "rawClazz");
        if (rawClazz == Object.class || !NodeKind.plainOf(rawClazz).isRaw()) {
            throw new BindingException("invalid ValueCodec raw type '" + rawClazz.getName() +
                    "': expected a supported String, Number, Boolean, Map, or List type");
        }

        Class<?> valueClazz = Asserts.notNull(valueInfo.valueClazz, "valueClazz");
        if (NodeKind.plainOf(valueClazz) != NodeKind.UNKNOWN) {
            throw new BindingException("cannot register ValueCodec for native OBNT type '" +
                    valueClazz.getName() + "'");
        }

    }


    // Bootstrap all built-in codecs from their annotated declarations.
    static {
        registerAnnotated(ValueCodec.class);
    }


    /**
     * Analyzes {@code @NodeValue} conversion methods on a class, returning
     * {@code null} if it is not annotated. Invalid declarations fail with
     * {@link BindingException}.
     */
    public static ValueInfo analyzeByAnnotation(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(NodeValue.class)) return null;

        MethodHandle valueToRawHandle = null, rawToValueHandle = null, valueCopyHandle = null;
        MethodHandles.Lookup lookup = Invokers.resolveLookup(clazz);

        Class<?> current = clazz;
        while (current != null && current != Object.class &&
                (valueToRawHandle == null || rawToValueHandle == null || valueCopyHandle == null)) {
            for (Constructor<?> ctor : current.getDeclaredConstructors()) {
                // Decode
                if (ctor.isAnnotationPresent(RawToValue.class)) {
                    if (rawToValueHandle != null)
                        throw new BindingException("multiple @" + RawToValue.class.getName() +
                                " definitions found in " + clazz.getName());
                    try {
                        rawToValueHandle = lookup.unreflectConstructor(ctor);
                    } catch (IllegalAccessException e) {
                        throw new BindingException(e);
                    }
                }
            }

            for (Method m : current.getDeclaredMethods()) {
                if (m.isBridge()) continue;
                // Encode
                if (m.isAnnotationPresent(ValueToRaw.class)) {
                    if (valueToRawHandle != null)
                        throw new BindingException("multiple @" + ValueToRaw.class.getName() +
                                " definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new BindingException("cannot use @" + ValueToRaw.class.getName() +
                                " on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        valueToRawHandle = lookup.unreflect(m);
                        continue;
                    } catch (IllegalAccessException e) {
                        throw new BindingException(e);
                    }
                }
                // Decode
                if (m.isAnnotationPresent(RawToValue.class)) {
                    if (rawToValueHandle != null)
                        throw new BindingException("multiple @" + RawToValue.class.getName() +
                                " definitions found in " + clazz.getName());
                    if (!Modifier.isStatic(m.getModifiers()))
                        throw new BindingException("must use @" + RawToValue.class.getName() +
                                " on constructor or static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        rawToValueHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new BindingException(e);
                    }
                }
                // Copy
                if (m.isAnnotationPresent(ValueCopy.class)) {
                    if (valueCopyHandle != null)
                        throw new BindingException("multiple @" + ValueCopy.class.getName() +
                                " definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new BindingException("cannot use @" + ValueCopy.class.getName() +
                                " on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        valueCopyHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new BindingException(e);
                    }
                }
            }// for
            current = current.getSuperclass();
        }

        if (valueToRawHandle == null)
            throw new BindingException("missing @" + ValueToRaw.class.getName() + " method in " + clazz.getName());
        if (valueToRawHandle.type().parameterCount() != 1) {
            throw new BindingException("@" + ValueToRaw.class.getName() + " method must have no parameters, but found " +
                    (valueToRawHandle.type().parameterCount() - 1) + ", in " + clazz.getName());
        }
        Class<?> valueToRawReturnBoxed = Types.box(valueToRawHandle.type().returnType());
        if (!NodeKind.plainOf(valueToRawReturnBoxed).isRaw())
            throw new BindingException("@" + ValueToRaw.class.getName() + " method return invalid type " +
                    valueToRawReturnBoxed.getName() + " in " + clazz.getName() +
                    ". The return type must be a supported String, Number, Boolean, Map, or List type; " +
                    "JSON null is a value, not a declared raw type.");

        if (rawToValueHandle == null)
            throw new BindingException("missing @" + RawToValue.class.getName() + " method in " + clazz.getName());
        if (rawToValueHandle.type().parameterCount() != 1)
            throw new BindingException("@" + RawToValue.class.getName() +
                    " method must have exactly one parameter, but found " + rawToValueHandle.type().parameterCount());
        Class<?> rawToValueParamBoxed = Types.box(rawToValueHandle.type().parameterType(0));
        Class<?> rawToValueReturnClazz = rawToValueHandle.type().returnType();
        if (rawToValueParamBoxed != valueToRawReturnBoxed)
            throw new BindingException("@" + RawToValue.class.getName() + " method parameter type must match @" +
                    ValueToRaw.class.getName() + " return type. " + "Expected: " + valueToRawReturnBoxed.getName() +
                    ", Found: " + rawToValueParamBoxed.getName());
        if (rawToValueReturnClazz != clazz)
            throw new BindingException("@" + RawToValue.class.getName() + " method return type must be " +
                    clazz.getName() + ", but found " + rawToValueReturnClazz.getName());

        if (valueCopyHandle != null) {
            if (valueCopyHandle.type().parameterCount() != 1)
                throw new BindingException("@" + ValueCopy.class.getName() + " method must have no parameters, but found " +
                        (valueCopyHandle.type().parameterCount() + 1));
            Class<?> copyReturnClazz = valueCopyHandle.type().returnType();
            if (copyReturnClazz != clazz)
                throw new BindingException("@" + ValueCopy.class.getName() + " method return type must be " + clazz.getName() +
                        ", but found " + copyReturnClazz.getName());
        }

        return new ValueInfo("", clazz, valueToRawReturnBoxed, null,
                valueToRawHandle, rawToValueHandle, valueCopyHandle);
    }


    private static Method _findOverride(Method baseMethod, Class<?> clazz) {
        try {
            Method m = clazz.getDeclaredMethod(baseMethod.getName(), baseMethod.getParameterTypes());
            if (m.isBridge()) return null;
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }


}
