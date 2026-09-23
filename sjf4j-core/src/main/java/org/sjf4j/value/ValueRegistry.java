package org.sjf4j.value;

import org.sjf4j.NodeKind;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.PojoAccess;
import org.sjf4j.node.Types;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ValueRegistry {
    private ValueRegistry() {}

    /* Index zero is always the default codec. */
    private static final ConcurrentHashMap<Class<?>, ValueInfo[]> NODE_VALUE_INFOS = new ConcurrentHashMap<>();

    /**
     * Resolves codecs registered directly for a class or declared through {@code @NodeValue}.
     * Index zero is the default codec. Returns {@code null} when no codec set matches.
     * The returned array is registry-owned and must not be modified.
     */
    public static ValueInfo[] resolve(Class<?> runtimeClazz) {
        Objects.requireNonNull(runtimeClazz, "runtimeClazz");

        ValueInfo[] infos = NODE_VALUE_INFOS.get(runtimeClazz);
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
                infos = NODE_VALUE_INFOS.get(type);
                if (infos != null) {
                    if (matched != null) {
                        throw new JsonException("ambiguous NodeValue for runtime type '" +
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
     */
    public static void register(ValueInfo valueInfo, boolean forceDefault) {
        Objects.requireNonNull(valueInfo, "nodeValueInfo");

        NODE_VALUE_INFOS.compute(valueInfo.valueClazz, (valueClazz, oldInfos) -> {
            if (oldInfos == null) {
                return new ValueInfo[]{valueInfo};
            }

            for (ValueInfo oldInfo : oldInfos) {
                if (oldInfo != null && valueInfo.valueFormat.equals(oldInfo.valueFormat)) {
                    throw new JsonException("valueCodec already registered for type '" + valueClazz.getName() +
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

    public static <N, R> void registerByCodec(ValueCodec<N, R> codec, String valueFormat, boolean forceDefault) {
        Objects.requireNonNull(codec, "codec");

        Class<R> rawClazz = codec.rawClazz();
        if (rawClazz != Object.class && !NodeKind.plainOf(rawClazz).isRaw())
            throw new JsonException("invalid raw type in NodeValueCodec " + codec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of String, Number, Boolean, Map, List or Object.");
        Class<N> valueClazz = codec.valueClazz();
        Objects.requireNonNull(valueClazz, "valueClazz");

        ValueInfo info = new ValueInfo(valueFormat, valueClazz, rawClazz, codec, null, null, null);
        register(info, forceDefault);
    }


    // Bootstrap JDK Types
    static {
        registerByCodec(ValueCodec.URI_CODEC, null, false);
        registerByCodec(ValueCodec.URL_CODEC, null, false);
        registerByCodec(ValueCodec.UUID_CODEC, null, false);
        registerByCodec(ValueCodec.CHARSET, null, false);
        registerByCodec(ValueCodec.LOCALE, null, false);
        registerByCodec(ValueCodec.CURRENCY, null, false);
        registerByCodec(ValueCodec.ZONE_ID, null, false);
        registerByCodec(ValueCodec.INSTANT_STR, null, false);
        registerByCodec(ValueCodec.INSTANT_STR, "iso", false);
        registerByCodec(ValueCodec.INSTANT_EPOCH_MILLIS, "epochMillis", false);
        registerByCodec(PatternedValueCodec.LOCAL_DATE, null, false);
        registerByCodec(PatternedValueCodec.LOCAL_TIME, null, false);
        registerByCodec(PatternedValueCodec.LOCAL_DATE_TIME, null, false);
        registerByCodec(PatternedValueCodec.OFFSET_DATE_TIME, null, false);
        registerByCodec(PatternedValueCodec.ZONED_DATE_TIME, null, false);
        registerByCodec(ValueCodec.DURATION, null, false);
        registerByCodec(ValueCodec.PERIOD, null, false);
        registerByCodec(ValueCodec.PATH, null, false);
        registerByCodec(ValueCodec.FILE, null, false);
        registerByCodec(ValueCodec.PATTERN, null, false);
        registerByCodec(ValueCodec.INET_ADDR, null, false);
        registerByCodec(ValueCodec.DATE, null, false);
        registerByCodec(ValueCodec.CALENDAR, null, false);
        registerByCodec(ValueCodec.OPTIONAL, null, false);
    }



    public static ValueInfo analyzeByAnnotation(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(NodeValue.class)) return null;

        MethodHandle valueToRawHandle = null, rawToValueHandle = null, valueCopyHandle = null;
        MethodHandles.Lookup lookup = PojoAccess.resolveLookup(clazz);

        Class<?> current = clazz;
        while (current != null && current != Object.class &&
                (valueToRawHandle == null || rawToValueHandle == null || valueCopyHandle == null)) {
            for (Constructor<?> ctor : current.getDeclaredConstructors()) {
                // Decode
                if (ctor.isAnnotationPresent(RawToValue.class)) {
                    if (rawToValueHandle != null)
                        throw new JsonException("multiple @" + RawToValue.class.getSimpleName() +
                                " definitions found in " + clazz.getName());
                    try {
                        rawToValueHandle = lookup.unreflectConstructor(ctor);
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
            }

            for (Method m : current.getDeclaredMethods()) {
                if (m.isBridge()) continue;
                // Encode
                if (m.isAnnotationPresent(ValueToRaw.class)) {
                    if (valueToRawHandle != null)
                        throw new JsonException("multiple @" + ValueToRaw.class.getSimpleName() +
                                " definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("cannot use @" + ValueToRaw.class.getSimpleName() +
                                " on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        valueToRawHandle = lookup.unreflect(m);
                        continue;
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
                // Decode
                if (m.isAnnotationPresent(RawToValue.class)) {
                    if (rawToValueHandle != null)
                        throw new JsonException("multiple @" + RawToValue.class.getSimpleName() +
                                " definitions found in " + clazz.getName());
                    if (!Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("must use @" + RawToValue.class.getSimpleName() +
                                " on constructor or static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        rawToValueHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
                // Copy
                if (m.isAnnotationPresent(ValueCopy.class)) {
                    if (valueCopyHandle != null)
                        throw new JsonException("multiple @" + ValueCopy.class.getSimpleName() +
                                " definitions found in " + clazz.getName());
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new JsonException("cannot use @" + ValueCopy.class.getSimpleName() +
                                " on static methods in " + clazz.getName());
                    if (current != clazz) {
                        Method override = _findOverride(m, clazz);
                        if (override != null) { m = override; }
                    }
                    try {
                        valueCopyHandle = lookup.unreflect(m);
                    } catch (IllegalAccessException e) {
                        throw new JsonException(e);
                    }
                }
            }// for
            current = current.getSuperclass();
        }

        if (valueToRawHandle == null)
            throw new JsonException("missing @" + ValueToRaw.class.getSimpleName() + " method in " + clazz.getName());
        if (valueToRawHandle.type().parameterCount() != 1) {
            throw new JsonException("@" + ValueToRaw.class.getSimpleName() + " method must have no parameters, but found " +
                    (valueToRawHandle.type().parameterCount() - 1) + ", in " + clazz.getName());
        }
        Class<?> valueToRawReturnBoxed = Types.box(valueToRawHandle.type().returnType());
        if (!NodeKind.plainOf(valueToRawReturnBoxed).isRaw())
            throw new JsonException("@" + ValueToRaw.class.getSimpleName() + " method return invalid type " +
                    valueToRawReturnBoxed.getName() + " in " + clazz.getName() +
                    ". The return type must be a supported raw type (String, Number, Boolean, null, Map, or List).");

        if (rawToValueHandle == null)
            throw new JsonException("missing @" + RawToValue.class.getSimpleName() + " method in " + clazz.getName());
        if (rawToValueHandle.type().parameterCount() != 1)
            throw new JsonException("@" + RawToValue.class.getSimpleName() +
                    " method must have exactly one parameter, but found " + rawToValueHandle.type().parameterCount());
        Class<?> rawToValueParamBoxed = Types.box(rawToValueHandle.type().parameterType(0));
        Class<?> rawToValueReturnClazz = rawToValueHandle.type().returnType();
        if (rawToValueParamBoxed != valueToRawReturnBoxed)
            throw new JsonException("@" + RawToValue.class.getSimpleName() + " method parameter type must match @" +
                    ValueToRaw.class.getSimpleName() + " return type. " + "Expected: " + valueToRawReturnBoxed.getName() +
                    ", Found: " + rawToValueParamBoxed.getName());
        if (rawToValueReturnClazz != clazz)
            throw new JsonException("@" + RawToValue.class.getSimpleName() + " method return type must be " +
                    clazz.getName() + ", but found " + rawToValueReturnClazz.getName());

        if (valueCopyHandle != null) {
            if (valueCopyHandle.type().parameterCount() != 1)
                throw new JsonException("@" + ValueCopy.class.getSimpleName() + " method must have no parameters, but found " +
                        (valueCopyHandle.type().parameterCount() + 1));
            Class<?> copyReturnClazz = valueCopyHandle.type().returnType();
            if (copyReturnClazz != clazz)
                throw new JsonException("@" + ValueCopy.class.getSimpleName() + " method return type must be " + clazz.getName() +
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
