package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Central registry for SJF4J type metadata.
 * <p>
 * {@code TypeRegistry} analyzes Java classes once and caches the structural
 * {@link TypeInfo} later used by reads, writes, conversion, copying, and
 * traversal. It classifies a class as a value codec, {@code @OneOf} type,
 * supported container, or object binding.
 *
 * <p>Most application code does not need to call this class directly, but its
 * metadata model defines the runtime binding semantics used across
 * {@link Nodes}, {@link org.sjf4j.Sjf4j}, and facade integrations.
 */
public final class TypeRegistry {
    private static final Map<Class<?>, TypeInfo> TYPE_INFO_CACHE = new ConcurrentHashMap<>();


    /**
     * Returns cached metadata for a class, registering it when necessary.
     * <p>
     * This is the main entry point for runtime classification of user types.
     * The returned {@link TypeInfo} may be {@linkplain TypeInfo#isNone() none}
     * when the class has no SJF4J-managed metadata.
     */
    public static TypeInfo registerTypeInfo(Class<?> clazz) {
        return registerTypeInfo(clazz, false);
    }

    /**
     * Returns type metadata and optionally requires an object binding.
     * <p>
     * Resolution order is: cache hit, ServiceLoader-discovered external node,
     * {@code @NodeValue}/registered codec, {@code @OneOf}, container analysis,
     * object analysis, then the none marker.
     *
     * @param mustPojo when true, results without object binding are rejected
     */
    public static TypeInfo registerTypeInfo(Class<?> clazz, boolean mustPojo) {
        if (_fastNoneInfo(clazz)) return TypeInfo.NONE;
        TypeInfo ti = TYPE_INFO_CACHE.get(clazz);
        if (ti != null) {
            if (mustPojo && ti.pojoInfo == null) {
                throw new JsonException("class '" + clazz.getName() + "' is not a POJO");
            }
            return ti;
        }

        ExternalNode<?> externalNode = ExternalNodeRegistry.resolve(clazz);
        if (externalNode != null) {
            if (mustPojo) {
                throw new JsonException("class '" + clazz.getName() + "' is an external node, not a POJO");
            }
            ti = new TypeInfo(clazz, null, null, null, null, null, externalNode);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        ValueCodecInfo vci = ReflectUtil.analyzeNodeValue(clazz);
        if (vci != null) {
            if (mustPojo) {
                throw new JsonException("class '" + clazz.getName() + "' is annotated with @NodeValue, not a POJO");
            }
            ti = new TypeInfo(clazz, vci, null, null, null, null, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        OneOf ann = clazz.getAnnotation(OneOf.class);
        if (ann != null) {
            OneOfInfo aoi = ReflectUtil.analyzeOneOf(clazz, ann);
            ti = new TypeInfo(clazz, null, null, aoi, null, null, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        ContainerInfo ci = ReflectUtil.analyzeContainer(clazz);
        if (ci != null) {
            if (mustPojo) {
                throw new JsonException("class '" + clazz.getName() + "' is a container, not a POJO");
            }
            ti = new TypeInfo(clazz, null, null, null, ci, null, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        PojoInfo pi = ReflectUtil.analyzePojo(clazz, mustPojo);
        if (pi != null) {
            ti = new TypeInfo(clazz, null, null, null, null, pi, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        TYPE_INFO_CACHE.put(clazz, TypeInfo.NONE);
        return TypeInfo.NONE;
    }

    private static boolean _fastNoneInfo(Class<?> clazz) {
        return clazz == null || clazz == Object.class || clazz == String.class || clazz == Boolean.class
                || clazz == Map.class || clazz == List.class || clazz == Set.class || clazz.isPrimitive()
                || clazz == JsonObject.class || clazz == JsonArray.class;
    }

    /// NodeValue

    // Bootstrap JDK Types
    static {
        registerValueCodec(ValueCodec.URI_CODEC);
        registerValueCodec(ValueCodec.URL_CODEC);
        registerValueCodec(ValueCodec.UUID_CODEC);
        registerValueCodec(ValueCodec.LOCALE);
        registerValueCodec(ValueCodec.CURRENCY);
        registerValueCodec(ValueCodec.ZONE_ID);
        registerValueCodec(ValueCodec.INSTANT_STR);
        registerValueCodec("iso", ValueCodec.INSTANT_STR);
        registerValueCodec("epochMillis", ValueCodec.INSTANT_EPOCH_MILLIS);
        registerValueCodec(PatternedValueCodec.LOCAL_DATE);
        registerValueCodec(PatternedValueCodec.LOCAL_TIME);
        registerValueCodec(PatternedValueCodec.LOCAL_DATE_TIME);
        registerValueCodec(PatternedValueCodec.OFFSET_DATE_TIME);
        registerValueCodec(PatternedValueCodec.ZONED_DATE_TIME);
        registerValueCodec(ValueCodec.DURATION);
        registerValueCodec(ValueCodec.PERIOD);
        registerValueCodec(ValueCodec.PATH);
        registerValueCodec(ValueCodec.FILE);
        registerValueCodec(ValueCodec.PATTERN);
        registerValueCodec(ValueCodec.INET_ADDR);
        registerValueCodec(ValueCodec.DATE);
        registerValueCodec(ValueCodec.CALENDAR);
        registerValueCodec(ValueCodec.OPTIONAL);
    }

    /**
     * Registers a custom {@link ValueCodec} and returns codec metadata.
     * <p>
     * The codec raw type must be a supported raw node type (String, Number,
     * Boolean, Map, List, or Object).
     */
    public static <N, R> ValueCodecInfo registerValueCodec(ValueCodec<N, R> valueCodec) {
        return registerValueCodec("", valueCodec);
    }

    /**
     * Registers a named custom {@link ValueCodec} and returns codec metadata.
     */
    public static <N, R> ValueCodecInfo registerValueCodec(String valueFormat,
                                                             ValueCodec<N, R> valueCodec) {
        Objects.requireNonNull(valueFormat, "valueFormat");
        Objects.requireNonNull(valueCodec, "valueCodec");
        Class<R> rawClazz = valueCodec.rawClass();
        if (rawClazz != Object.class && !NodeKind.plainOf(rawClazz).isRaw())
            throw new JsonException("invalid raw type in ValueCodec " + valueCodec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of String, Number, Boolean, Map, List or Object.");
        Class<N> valueClazz = valueCodec.valueClass();
        Objects.requireNonNull(valueClazz, "valueClazz");

        ValueCodecInfo vci = new ValueCodecInfo(valueFormat, valueClazz, rawClazz, valueCodec, null, null, null);
        _putValueCodecInfo(vci);
        return vci;
    }

    private static void _putValueCodecInfo(ValueCodecInfo vci) {
        Class<?> valueClazz = vci.valueClazz;
        TypeInfo oldTi = TYPE_INFO_CACHE.get(valueClazz);
        if (oldTi == null || oldTi.isNone()) {
            TYPE_INFO_CACHE.put(valueClazz,
                    new TypeInfo(valueClazz, vci, null, null, null, null, null));
            return;
        }
        if (oldTi.pojoInfo != null || oldTi.oneOfInfo != null || oldTi.containerInfo != null || oldTi.externalNode != null) {
            throw new JsonException("type '" + valueClazz.getName() +
                    "' is already classified as a non-ValueCodec node type");
        }
        TYPE_INFO_CACHE.put(valueClazz, _newTypeInfoWithValueCodec(oldTi, vci));
    }

    private static TypeInfo _newTypeInfoWithValueCodec(TypeInfo ti, ValueCodecInfo vci) {
        if (vci.isDefault()) {
            if (ti.valueCodecInfo != null) {
                throw new JsonException("valueCodec already registered for type '" + vci.valueClazz.getName() +
                        "' and default format ''");
            }
            return new TypeInfo(ti.clazz, vci, ti.namedValueCodecs,
                    ti.oneOfInfo, ti.containerInfo, ti.pojoInfo, ti.externalNode);
        }

        for (int i = 0; i < ti.namedValueCodecs.length; i++) {
            ValueCodecInfo cur = ti.namedValueCodecs[i];
            if (cur.codecName.equals(vci.codecName)) {
                throw new JsonException("valueCodec already registered for type '" + vci.valueClazz.getName() +
                        "' and valueFormat '" + vci.codecName + "'");
            }
        }
        ValueCodecInfo[] appended = new ValueCodecInfo[ti.namedValueCodecs.length + 1];
        System.arraycopy(ti.namedValueCodecs, 0, appended, 0, ti.namedValueCodecs.length);
        appended[ti.namedValueCodecs.length] = vci;
        return new TypeInfo(ti.clazz, ti.valueCodecInfo, appended,
                ti.oneOfInfo, ti.containerInfo, ti.pojoInfo, ti.externalNode);
    }

    /**
     * Returns value codec metadata for a class and named format.
     */
    public static ValueCodecInfo resolveValueCodecOrElseThrow(Class<?> clazz, String valueFormat) {
        Objects.requireNonNull(valueFormat, "valueFormat");
        TypeInfo ti = registerTypeInfo(clazz);
        ValueCodecInfo vci = ti.getValueCodecInfo(valueFormat);
        if (vci == null) {
            throw new JsonException("no ValueCodec registered for type '" + clazz.getName() +
                    "' with valueFormat '" + valueFormat + "'");
        }
        return vci;
    }

    /// POJO

    /**
     * Returns object binding metadata or throws when the class cannot be bound as an object.
     */
    public static PojoInfo registerPojoOrElseThrow(Class<?> clazz) {
        return registerTypeInfo(clazz, true).pojoInfo;
    }


    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> newMapContainer(Class<?> mapClazz, boolean fallback) {
        if (mapClazz == null || mapClazz == Object.class || mapClazz == Map.class || mapClazz == LinkedHashMap.class) {
            return new LinkedHashMap<>();
        }
        ContainerInfo ci = registerTypeInfo(mapClazz).containerInfo;
        if (ci == null || ci.kind != NodeKind.OBJECT_MAP) {
            if (fallback) {
                return new LinkedHashMap<>();
            }
            throw new BindingException("unsupported Map target type '" + mapClazz.getName() + "'");
        }
        return (Map<String, T>) ci.newContainer();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> newListContainer(Class<?> listClazz, boolean fallback) {
        if (listClazz == null || listClazz == Object.class || listClazz == List.class || listClazz == ArrayList.class) {
            return new ArrayList<>();
        }
        ContainerInfo ci = registerTypeInfo(listClazz).containerInfo;
        if (ci == null || ci.kind != NodeKind.ARRAY_LIST) {
            if (fallback) {
                return new ArrayList<>();
            }
            throw new BindingException("unsupported List target type '" + listClazz.getName() + "'");
        }
        return (List<T>) ci.newContainer();
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> newSetContainer(Class<?> setClazz, boolean fallback) {
        if (setClazz == null || setClazz == Object.class || setClazz == Set.class || setClazz == LinkedHashSet.class) {
            return new LinkedHashSet<>();
        }
        ContainerInfo ci = registerTypeInfo(setClazz).containerInfo;
        if (ci == null || ci.kind != NodeKind.ARRAY_SET) {
            if (fallback) {
                return new LinkedHashSet<>();
            }
            throw new BindingException("unsupported Set target type '" + setClazz.getName() + "'");
        }
        return (Set<T>) ci.newContainer();
    }


    public static class PojoCreationSession {
        private final CreatorInfo creatorInfo;
        private final int pendingCapacity;
        private Object pojo;
        private Object[] args;
        private boolean[] argAssigned;
        private int remainingArgs;
        private FieldInfo[] pendingProperties;
        private Object[] pendingFieldValues;
        private int pendingFieldSize;
        private String[] pendingNames;
        private Object[] pendingNameValues;
        private int pendingNameSize;

        public PojoCreationSession(CreatorInfo creatorInfo, int pendingCapacity) {
            this.creatorInfo = creatorInfo;
            this.pendingCapacity = Math.max(pendingCapacity, 6);
            if (creatorInfo.hasNoArgsCreator()) {
                this.pojo = creatorInfo.newPojoNoArgs();
                return;
            }
            int argCount = creatorInfo.argNames == null ? 0 : creatorInfo.argNames.length;
            this.args = new Object[argCount];
            this.argAssigned = new boolean[argCount];
            this.remainingArgs = argCount;
        }

        public void acceptCtorArg(int argIndex, Object value) {
            if (args == null || argIndex < 0) return;
            if (argAssigned[argIndex]) {
                String argName = creatorInfo.argNames != null && argIndex < creatorInfo.argNames.length
                        ? creatorInfo.argNames[argIndex]
                        : String.valueOf(argIndex);
                throw new BindingException("duplicate creator argument assignment for '" + argName + "'");
            }
            argAssigned[argIndex] = true;
            args[argIndex] = value;
            if (--remainingArgs == 0 && pojo == null) {
                pojo = creatorInfo.newPojoWithArgs(args);
                _replayPendingProperties();
                _replayPendingJsonEntries();
            }
        }

        public void acceptProperty(FieldInfo fieldInfo, Object value) {
            if (pojo != null) {
                fieldInfo.invokeSetterIfPresent(pojo, value);
            } else {
                _ensurePendingPropertyCapacity();
                pendingProperties[pendingFieldSize] = fieldInfo;
                pendingFieldValues[pendingFieldSize] = value;
                pendingFieldSize++;
            }
        }

        public void acceptDynamic(String key, Object value) {
            if (pojo != null) {
                ((JsonObject) pojo).put(key, value);
            } else {
                _ensurePendingNameCapacity();
                pendingNames[pendingNameSize] = key;
                pendingNameValues[pendingNameSize] = value;
                pendingNameSize++;
            }
        }

        public Object finish() {
            if (pojo == null) {
                pojo = creatorInfo.newPojoWithArgs(args);
            }
            _replayPendingProperties();
            _replayPendingJsonEntries();
            return pojo;
        }

        private void _replayPendingProperties() {
            if (pendingFieldSize == 0) return;
            for (int i = 0; i < pendingFieldSize; i++) {
                pendingProperties[i].invokeSetterIfPresent(pojo, pendingFieldValues[i]);
            }
            pendingFieldSize = 0;
        }

        private void _replayPendingJsonEntries() {
            if (pendingNameSize == 0) return;
            JsonObject jo = (JsonObject) pojo;
            for (int i = 0; i < pendingNameSize; i++) {
                jo.put(pendingNames[i], pendingNameValues[i]);
            }
            pendingNameSize = 0;
        }

        private void _ensurePendingPropertyCapacity() {
            if (pendingProperties == null || pendingFieldValues == null) {
                int cap = pendingCapacity;
                pendingProperties = new FieldInfo[cap];
                pendingFieldValues = new Object[cap];
                return;
            }
            if (pendingFieldSize < pendingProperties.length) return;
            int newCap = pendingProperties.length << 1;
            FieldInfo[] newProperties = new FieldInfo[newCap];
            Object[] newValues = new Object[newCap];
            System.arraycopy(pendingProperties, 0, newProperties, 0, pendingFieldSize);
            System.arraycopy(pendingFieldValues, 0, newValues, 0, pendingFieldSize);
            pendingProperties = newProperties;
            pendingFieldValues = newValues;
        }

        private void _ensurePendingNameCapacity() {
            if (pendingNames == null || pendingNameValues == null) {
                int cap = pendingCapacity;
                pendingNames = new String[cap];
                pendingNameValues = new Object[cap];
                return;
            }
            if (pendingNameSize < pendingNames.length) return;
            int newCap = pendingNames.length << 1;
            String[] newNames = new String[newCap];
            Object[] newValues = new Object[newCap];
            System.arraycopy(pendingNames, 0, newNames, 0, pendingNameSize);
            System.arraycopy(pendingNameValues, 0, newValues, 0, pendingNameSize);
            pendingNames = newNames;
            pendingNameValues = newValues;
        }

    }

    @FunctionalInterface
    public interface Func1 {
        Object apply(Object a1);
    }

    @FunctionalInterface
    public interface Func2 {
        Object apply(Object a1, Object a2);
    }

    @FunctionalInterface
    public interface Func3 {
        Object apply(Object a1, Object a2, Object a3);
    }

    @FunctionalInterface
    public interface Func4 {
        Object apply(Object a1, Object a2, Object a3, Object a4);
    }

    @FunctionalInterface
    public interface Func5 {
        Object apply(Object a1, Object a2, Object a3, Object a4, Object a5);
    }

}
