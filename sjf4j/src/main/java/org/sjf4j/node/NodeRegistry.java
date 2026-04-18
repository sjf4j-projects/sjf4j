package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.path.JsonPath;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Central metadata registry for SJF4J's OBNT type system.
 * <p>
 * {@code NodeRegistry} analyzes Java classes once and caches the structural
 * metadata later used by reads, writes, conversion, copying, and traversal.
 * For jar users, this is where SJF4J decides whether a class behaves as a
 * POJO, JOJO, JAJO, {@code @NodeValue}, or {@code @AnyOf} type.
 *
 * <p>Most application code does not need to call this class directly, but its
 * metadata model defines the runtime binding semantics used across
 * {@link Nodes}, {@link org.sjf4j.Sjf4j}, and facade integrations.
 */
public final class NodeRegistry {

    // All in TypeInfo
    private static final Map<Class<?>, TypeInfo> TYPE_INFO_CACHE = new ConcurrentHashMap<>();

    private static final TypeInfo NONE_INFO = new TypeInfo(Object.class, null, null, null, null);


    /**
     * Registers or returns cached metadata for a class.
     * <p>
     * This is the main entry point for runtime classification of user types.
     */
    public static TypeInfo registerTypeInfo(Class<?> clazz) {
        return registerTypeInfo(clazz, false);
    }

    /**
     * Registers type metadata and optionally enforces POJO availability.
     * <p>
     * Resolution order is: cache hit, {@code @NodeValue}/registered codec,
     * {@code @AnyOf}, POJO analysis, then NONE marker.
     *
     * @param mustPojo when true, non-POJO results are rejected
     */
    public static TypeInfo registerTypeInfo(Class<?> clazz, boolean mustPojo) {
        if (_fastNoType(clazz)) return NONE_INFO;
        TypeInfo ti = TYPE_INFO_CACHE.get(clazz);
        if (ti != null) {
            if (mustPojo && ti.pojoInfo == null) {
                throw new JsonException("Class '" + clazz.getName() + "' is not a POJO");
            }
            return ti;
        }

        ValueCodecInfo vci = ReflectUtil.analyzeNodeValue(clazz);
        if (vci != null) {
            if (mustPojo) {
                throw new JsonException("Class '" + clazz.getName() + "' is annotated with @NodeValue, not a POJO");
            }
            ti = new TypeInfo(clazz, vci, null, null, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        AnyOf ann = clazz.getAnnotation(AnyOf.class);
        if (ann != null) {
            AnyOfInfo aoi = ReflectUtil.analyzeAnyOf(clazz, ann);
            ti = new TypeInfo(clazz, null, aoi, null, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        ContainerInfo ci = ReflectUtil.analyzeContainer(clazz);
        if (ci != null) {
            if (mustPojo) {
                throw new JsonException("Class '" + clazz.getName() + "' is a container, not a POJO");
            }
            ti = new TypeInfo(clazz, null, null, ci, null);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        PojoInfo pi = ReflectUtil.analyzePojo(clazz, mustPojo);
        if (pi != null) {
            ti = new TypeInfo(clazz, null, null, null, pi);
            TYPE_INFO_CACHE.put(clazz, ti);
            return ti;
        }

        TYPE_INFO_CACHE.put(clazz, NONE_INFO);
        return NONE_INFO;
    }

    private static boolean _fastNoType(Class<?> clazz) {
        return clazz == null || clazz == Object.class || clazz == String.class || clazz == Boolean.class
                || clazz == Map.class || clazz == List.class || clazz == Set.class || clazz.isPrimitive()
                || clazz == JsonObject.class || clazz == JsonArray.class;
    }

    /// NodeValue

    // Bootstrap JDK Types
    static {
        registerValueCodec(new ValueCodec.UriValueCodec());
        registerValueCodec(new ValueCodec.UrlValueCodec());
        registerValueCodec(new ValueCodec.UuidValueCodec());
        registerValueCodec(new ValueCodec.LocaleValueCodec());
        registerValueCodec(new ValueCodec.CurrencyValueCodec());
        registerValueCodec(new ValueCodec.ZoneIdValueCodec());
        registerValueCodec(new ValueCodec.InstantStringValueCodec());
        registerValueCodec(new ValueCodec.LocalDateValueCodec());
        registerValueCodec(new ValueCodec.LocalDateTimeValueCodec());
        registerValueCodec(new ValueCodec.OffsetDateTimeValueCodec());
        registerValueCodec(new ValueCodec.ZonedDateTimeValueCodec());
        registerValueCodec(new ValueCodec.DurationValueCodec());
        registerValueCodec(new ValueCodec.PeriodValueCodec());
        registerValueCodec(new ValueCodec.PathValueCodec());
        registerValueCodec(new ValueCodec.FileValueCodec());
        registerValueCodec(new ValueCodec.PatternValueCodec());
        registerValueCodec(new ValueCodec.InetAddressValueCodec());
        registerValueCodec(new ValueCodec.DateValueCodec());
        registerValueCodec(new ValueCodec.CalendarValueCodec());
    }

    /**
     * Registers a custom {@link ValueCodec} and returns codec metadata.
     * <p>
     * The codec raw type must be a supported raw node type (String, Number,
     * Boolean, Map, List, or Object).
     */
    public static <N, R> ValueCodecInfo registerValueCodec(ValueCodec<N, R> valueCodec) {
        return _registerValueCodec(valueCodec, false);
    }

    /**
     * Replaces an existing codec for the same value type.
     * <p>
     * This is the explicit "I know I am changing global codec semantics" path.
     * Use {@link #registerValueCodec(ValueCodec)} when duplicates should fail fast.
     */
    public static <N, R> ValueCodecInfo overrideValueCodec(ValueCodec<N, R> valueCodec) {
        return _registerValueCodec(valueCodec, true);
    }

    private static <N, R> ValueCodecInfo _registerValueCodec(ValueCodec<N, R> valueCodec, boolean override) {
        Objects.requireNonNull(valueCodec, "valueCodec");
        Class<R> rawClazz = valueCodec.rawClass();
        if (rawClazz != Object.class && !NodeKind.plainOf(rawClazz).isRaw())
            throw new JsonException("Invalid raw type in ValueCodec " + valueCodec.getClass().getName() + ": " +
                    rawClazz.getName() + ". The raw type must be one of String, Number, Boolean, Map, List or Object.");
        Class<N> valueClazz = valueCodec.valueClass();
        Objects.requireNonNull(valueClazz, "valueClazz");

        ValueCodecInfo vci = new ValueCodecInfo(valueClazz, rawClazz, valueCodec);
        _storeValueCodecInfo(vci, override);
        return vci;
    }

    private static void _storeValueCodecInfo(ValueCodecInfo vci, boolean override) {
        Class<?> valueClazz = vci.valueClazz;
        // Retry until we observe a stable slot so concurrent type analysis/codec registration stays correct.
        while (true) {
            TypeInfo oldTi = TYPE_INFO_CACHE.get(valueClazz);
            if (oldTi == null) {
                TypeInfo newTi = new TypeInfo(valueClazz, vci, null, null, null);
                if (TYPE_INFO_CACHE.putIfAbsent(valueClazz, newTi) == null) {
                    return;
                }
                continue;
            }
            if (oldTi == NONE_INFO) {
                TypeInfo newTi = new TypeInfo(valueClazz, vci, null, null, null);
                if (TYPE_INFO_CACHE.replace(valueClazz, oldTi, newTi)) {
                    return;
                }
                continue;
            }
            if (oldTi.pojoInfo != null || oldTi.anyOfInfo != null || oldTi.containerInfo != null) {
                throw new JsonException("Type '" + valueClazz.getName() +
                        "' is already classified as a non-ValueCodec node type");
            }
            if (!override && oldTi.valueCodecInfo != null) {
                throw new JsonException("ValueCodec already registered for type '" + valueClazz.getName() +
                        "'. Use overrideValueCodec() to replace it");
            }
            TypeInfo newTi = new TypeInfo(valueClazz, vci, null, null, null);
            if (TYPE_INFO_CACHE.replace(valueClazz, oldTi, newTi)) {
                return;
            }
        }
    }

    /**
     * Replaces an existing codec using the type's {@code @NodeValue} declaration.
     */
    public static ValueCodecInfo overrideValueCodec(Class<?> clazz) {
        ValueCodecInfo vci = ReflectUtil.analyzeNodeValue(clazz);
        if (vci == null) {
            throw new JsonException("Class '" + clazz.getName() + "' is not annotated with @NodeValue");
        }
        _storeValueCodecInfo(vci, true);
        return vci;
    }

    /**
     * Returns value codec metadata for a class.
     */
    public static ValueCodecInfo registerValueCodecInfo(Class<?> clazz) {
        return registerTypeInfo(clazz).valueCodecInfo;
    }

    /// POJO

    public static PojoInfo registerPojo(Class<?> clazz) {
        return registerTypeInfo(clazz, false).pojoInfo;
    }

    /**
     * Registers POJO metadata or throws if class is not a POJO.
     */
    public static PojoInfo registerPojoOrElseThrow(Class<?> clazz) {
        return registerTypeInfo(clazz, true).pojoInfo;
    }

    /**
     * Returns metadata for a named POJO field.
     */
    public static FieldInfo getFieldInfo(Class<?> clazz, String fieldName) {
        return registerPojoOrElseThrow(clazz).fields.get(fieldName);
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> newMapContainer(Class<?> mapType, boolean fallback) {
        if (mapType == null || mapType == Object.class || mapType == Map.class || mapType == LinkedHashMap.class) {
            return new LinkedHashMap<>();
        }
        ContainerInfo ci = registerTypeInfo(mapType).containerInfo;
        if (ci == null || ci.kind != NodeKind.OBJECT_MAP) {
            if (fallback) {
                return new LinkedHashMap<>();
            }
            throw new JsonException("Unsupported Map target type '" + mapType.getName() + "'");
        }
        return (Map<String, T>) ci.newContainer();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> newListContainer(Class<?> listType, boolean fallback) {
        if (listType == null || listType == Object.class || listType == List.class || listType == ArrayList.class) {
            return new ArrayList<>();
        }
        ContainerInfo ci = registerTypeInfo(listType).containerInfo;
        if (ci == null || ci.kind != NodeKind.ARRAY_LIST) {
            if (fallback) {
                return new ArrayList<>();
            }
            throw new JsonException("Unsupported List target type '" + listType.getName() + "'");
        }
        return (List<T>) ci.newContainer();
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> newSetContainer(Class<?> setType, boolean fallback) {
        if (setType == null || setType == Object.class || setType == Set.class || setType == LinkedHashSet.class) {
            return new LinkedHashSet<>();
        }
        ContainerInfo ci = registerTypeInfo(setType).containerInfo;
        if (ci == null || ci.kind != NodeKind.ARRAY_SET) {
            if (fallback) {
                return new LinkedHashSet<>();
            }
            throw new JsonException("Unsupported Set target type '" + setType.getName() + "'");
        }
        return (Set<T>) ci.newContainer();
    }

    /// Info

    // TypeInfo
    public static class TypeInfo {
        public final Class<?> clazz;
        public final ValueCodecInfo valueCodecInfo;
        public final AnyOfInfo anyOfInfo;
        public final ContainerInfo containerInfo;
        public final PojoInfo pojoInfo;

        /**
         * Creates immutable type metadata holder.
         */
        public TypeInfo(Class<?> clazz, ValueCodecInfo valueCodecInfo, AnyOfInfo anyOfInfo,
                        ContainerInfo containerInfo, PojoInfo pojoInfo) {
            this.clazz = clazz;
            this.valueCodecInfo = valueCodecInfo;
            this.anyOfInfo = anyOfInfo;
            this.containerInfo = containerInfo;
            this.pojoInfo = pojoInfo;
        }

        public boolean isNone() {
            return this == NONE_INFO;
        }

        /**
         * Returns true when POJO reads must stay on the framework-owned path.
         * Native backend modules may only bypass SJF4J when this is false.
         */
        public boolean requiresPojoReader() {
            return pojoInfo != null && pojoInfo.requiresPojoReader;
        }

        /**
         * Returns true when POJO writes must stay on the framework-owned path.
         * Native backend modules may only bypass SJF4J when this is false.
         */
        public boolean requiresPojoWriter() {
            return pojoInfo != null && pojoInfo.requiresPojoWriter;
        }
    }

    // Map / List / Set
    public static final class ContainerInfo {
        public final Class<?> clazz;
        public final NodeKind kind;
        public final MethodHandle noArgsCtorHandle;
        public final Supplier<?> noArgsCtorLambda;

        public ContainerInfo(Class<?> clazz, NodeKind kind,
                             MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda) {
            if (kind != NodeKind.OBJECT_MAP && kind != NodeKind.ARRAY_LIST && kind != NodeKind.ARRAY_SET) {
                throw new JsonException("Invalid container kind '" + kind + "' for " + clazz.getName());
            }
            this.clazz = clazz;
            this.kind = kind;
            this.noArgsCtorHandle = noArgsCtorHandle;
            this.noArgsCtorLambda = noArgsCtorLambda;
        }

        public Object newContainer() {
            if (noArgsCtorLambda != null) {
                return noArgsCtorLambda.get();
            }
            if (noArgsCtorHandle != null) {
                try {
                    return noArgsCtorHandle.invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to create container instance of " + clazz.getName(), e);
                }
            }
            throw new JsonException("Failed to create container instance of " + clazz.getName());
        }
    }

    // PojoInfo
    public static class PojoInfo {
        public final Class<?> clazz;
        public final CreatorInfo creatorInfo;
        public final NamingStrategy namingStrategy;
        public final AccessStrategy accessStrategy;
        public final boolean readDynamic;
        public final boolean writeDynamic;
        public final Map<String, FieldInfo> fields;
        public final int fieldCount;
        public final Map<String, FieldInfo> aliasFields;
        public final boolean isJojo;
        public final boolean isJajo;
        public final boolean hasParentScopeAnyOf;
        public final boolean hasExplicitBinding;
        public final boolean hasCreatorBinding;
        public final boolean hasNonPublicFields;
        public final boolean hasNonPublicReaderGap;
        public final boolean hasNonPublicWriterGap;
        public final boolean requiresPojoReader;
        public final boolean requiresPojoWriter;

        /**
         * Creates immutable POJO metadata holder.
         */
        public PojoInfo(Class<?> clazz, CreatorInfo creatorInfo,
                        NamingStrategy namingStrategy,
                        AccessStrategy accessStrategy,
                        boolean readDynamic,
                        boolean writeDynamic,
                        Map<String, FieldInfo> fields,
                        Map<String, FieldInfo> aliasFields,
                        boolean hasExplicitBinding,
                        boolean hasNonPublicFields,
                        boolean hasNonPublicReaderGap,
                        boolean hasNonPublicWriterGap) {
            this.clazz = clazz;
            this.creatorInfo = creatorInfo;
            this.namingStrategy = namingStrategy;
            this.accessStrategy = accessStrategy;
            this.readDynamic = readDynamic;
            this.writeDynamic = writeDynamic;
            this.fields = fields;
            this.fieldCount = fields.size();
            this.aliasFields = aliasFields;
            this.isJojo = JsonObject.class.isAssignableFrom(clazz);
            this.isJajo = JsonArray.class.isAssignableFrom(clazz);
            boolean hasParentScopeAnyOf = false;
            for (FieldInfo fi : fields.values()) {
                AnyOfInfo aoi = fi.anyOfInfo;
                if (aoi != null && aoi.scope == AnyOf.Scope.PARENT) {
                    hasParentScopeAnyOf = true;
                    break;
                }
            }
            this.hasParentScopeAnyOf = hasParentScopeAnyOf;
            this.hasExplicitBinding = hasExplicitBinding;
            this.hasCreatorBinding = creatorInfo != null && creatorInfo.argsCreator != null;
            this.hasNonPublicFields = hasNonPublicFields;
            this.hasNonPublicReaderGap = hasNonPublicReaderGap;
            this.hasNonPublicWriterGap = hasNonPublicWriterGap;
            boolean hasTypeOwnedBinding = namingStrategy != null || accessStrategy == AccessStrategy.FIELD_BASED;
            boolean hasCustomDynamicReader = this.isJojo && !readDynamic;
            boolean hasCustomDynamicWriter = this.isJojo && !writeDynamic;
            this.requiresPojoReader = hasTypeOwnedBinding || hasParentScopeAnyOf
                    || hasExplicitBinding || this.hasCreatorBinding || hasNonPublicReaderGap || hasCustomDynamicReader;
            this.requiresPojoWriter = hasTypeOwnedBinding || hasExplicitBinding || hasNonPublicWriterGap
                    || hasCustomDynamicWriter;
        }

    }

    @FunctionalInterface
    public interface PojoPendingApplier {
        void apply(Object pojo, Object key, Object value);
    }

    public static class PojoCreationSession {
        private final CreatorInfo creatorInfo;
        private Object pojo;
        private Object[] args;
        private int remainingArgs;
        private Object[] pendingKeys;
        private Object[] pendingValues;
        private int pendingSize;
        private FieldInfo[] pendingFields;
        private Object[] pendingFieldValues;
        private int pendingFieldSize;
        private String[] pendingNames;
        private Object[] pendingNameValues;
        private int pendingNameSize;

        public PojoCreationSession(CreatorInfo creatorInfo, int pendingCapacity) {
            this.creatorInfo = creatorInfo;
            if (creatorInfo.hasNoArgsCreator()) {
                this.pojo = creatorInfo.newPojoNoArgs();
                return;
            }
            int argCount = creatorInfo.argNames == null ? 0 : creatorInfo.argNames.length;
            this.args = new Object[argCount];
            this.remainingArgs = argCount;
            int cap = Math.max(pendingCapacity, 4);
            this.pendingKeys = new Object[cap];
            this.pendingValues = new Object[cap];
        }

        public void accept(String name, Object value, Object pendingKey, PojoPendingApplier applier) {
            int argIdx = resolveArgIndex(name);
            acceptResolved(argIdx, value, pendingKey, applier);
        }

        public void acceptResolved(int argIdx, Object value, Object pendingKey, PojoPendingApplier applier) {
            if (argIdx >= 0) {
                setCtorArg(argIdx, value);
                materializeIfReady(applier);
                return;
            }
            if (pojo != null) {
                applier.apply(pojo, pendingKey, value);
            } else {
                addPending(pendingKey, value);
            }
        }

        public void acceptResolvedField(int argIdx, Object value, FieldInfo fieldInfo) {
            if (argIdx >= 0) {
                setCtorArg(argIdx, value);
                _materializeIfReadyField();
                return;
            }
            if (pojo != null) {
                fieldInfo.invokeSetterIfPresent(pojo, value);
            } else {
                _addPendingField(fieldInfo, value);
            }
        }

        public void acceptResolvedJsonEntry(int argIdx, String key, Object value) {
            if (argIdx >= 0) {
                setCtorArg(argIdx, value);
                _materializeIfReadyJsonObject();
                return;
            }
            if (pojo != null) {
                ((JsonObject) pojo).put(key, value);
            } else {
                _addPendingName(key, value);
            }
        }

        public int resolveArgIndex(String name) {
            if (pojo != null || args == null) return -1;
            return creatorInfo.getArgIndexOrAlias(name);
        }

        public void setCtorArg(int argIndex, Object value) {
            if (args == null || argIndex < 0) return;
            args[argIndex] = value;
            remainingArgs--;
        }

        public void addPending(Object key, Object value) {
            if (pojo != null) return;
            _ensurePendingCapacity();
            pendingKeys[pendingSize] = key;
            pendingValues[pendingSize] = value;
            pendingSize++;
        }

        public void materializeIfReady(PojoPendingApplier applier) {
            if (pojo == null && remainingArgs == 0) {
                pojo = creatorInfo.newPojoWithArgs(args);
                _replayPending(applier);
            }
        }

        public Object finish(PojoPendingApplier applier) {
            if (pojo == null) {
                pojo = creatorInfo.newPojoWithArgs(args);
            }
            _replayPending(applier);
            return pojo;
        }

        public Object finishField() {
            if (pojo == null) {
                pojo = creatorInfo.newPojoWithArgs(args);
            }
            _replayPendingFields();
            return pojo;
        }

        public JsonObject finishJsonObject() {
            if (pojo == null) {
                pojo = creatorInfo.newPojoWithArgs(args);
            }
            _replayPendingJsonEntries();
            return (JsonObject) pojo;
        }

        private void _replayPending(PojoPendingApplier applier) {
            if (pendingSize == 0) return;
            for (int i = 0; i < pendingSize; i++) {
                applier.apply(pojo, pendingKeys[i], pendingValues[i]);
            }
            pendingSize = 0;
        }

        private void _materializeIfReadyField() {
            if (pojo == null && remainingArgs == 0) {
                pojo = creatorInfo.newPojoWithArgs(args);
                _replayPendingFields();
            }
        }

        private void _addPendingField(FieldInfo fi, Object value) {
            _ensurePendingFieldCapacity();
            pendingFields[pendingFieldSize] = fi;
            pendingFieldValues[pendingFieldSize] = value;
            pendingFieldSize++;
        }

        private void _replayPendingFields() {
            if (pendingFieldSize == 0) return;
            for (int i = 0; i < pendingFieldSize; i++) {
                pendingFields[i].invokeSetterIfPresent(pojo, pendingFieldValues[i]);
            }
            pendingFieldSize = 0;
        }

        private void _materializeIfReadyJsonObject() {
            if (pojo == null && remainingArgs == 0) {
                pojo = creatorInfo.newPojoWithArgs(args);
                _replayPendingJsonEntries();
            }
        }

        private void _addPendingName(String key, Object value) {
            _ensurePendingNameCapacity();
            pendingNames[pendingNameSize] = key;
            pendingNameValues[pendingNameSize] = value;
            pendingNameSize++;
        }

        private void _replayPendingJsonEntries() {
            if (pendingNameSize == 0) return;
            JsonObject jo = (JsonObject) pojo;
            for (int i = 0; i < pendingNameSize; i++) {
                jo.put(pendingNames[i], pendingNameValues[i]);
            }
            pendingNameSize = 0;
        }

        private void _ensurePendingFieldCapacity() {
            if (pendingFields == null || pendingFieldValues == null) {
                int cap = Math.max(4, pendingKeys == null ? 0 : pendingKeys.length);
                pendingFields = new FieldInfo[cap];
                pendingFieldValues = new Object[cap];
                return;
            }
            if (pendingFieldSize < pendingFields.length) return;
            int newCap = pendingFields.length << 1;
            if (newCap < 4) newCap = 4;
            FieldInfo[] newFields = new FieldInfo[newCap];
            Object[] newValues = new Object[newCap];
            System.arraycopy(pendingFields, 0, newFields, 0, pendingFieldSize);
            System.arraycopy(pendingFieldValues, 0, newValues, 0, pendingFieldSize);
            pendingFields = newFields;
            pendingFieldValues = newValues;
        }

        private void _ensurePendingNameCapacity() {
            if (pendingNames == null || pendingNameValues == null) {
                int cap = Math.max(4, pendingKeys == null ? 0 : pendingKeys.length);
                pendingNames = new String[cap];
                pendingNameValues = new Object[cap];
                return;
            }
            if (pendingNameSize < pendingNames.length) return;
            int newCap = pendingNames.length << 1;
            if (newCap < 4) newCap = 4;
            String[] newNames = new String[newCap];
            Object[] newValues = new Object[newCap];
            System.arraycopy(pendingNames, 0, newNames, 0, pendingNameSize);
            System.arraycopy(pendingNameValues, 0, newValues, 0, pendingNameSize);
            pendingNames = newNames;
            pendingNameValues = newValues;
        }

        private void _ensurePendingCapacity() {
            if (pendingKeys == null || pendingValues == null) {
                pendingKeys = new Object[4];
                pendingValues = new Object[4];
                return;
            }
            if (pendingSize < pendingKeys.length) return;
            int newCap = pendingKeys.length << 1;
            if (newCap < 4) newCap = 4;
            Object[] newKeys = new Object[newCap];
            Object[] newValues = new Object[newCap];
            System.arraycopy(pendingKeys, 0, newKeys, 0, pendingSize);
            System.arraycopy(pendingValues, 0, newValues, 0, pendingSize);
            pendingKeys = newKeys;
            pendingValues = newValues;
        }
    }

    // CreatorInfo
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

    public static class CreatorInfo {
        public final Class<?> clazz;
        public final MethodHandle noArgsCtorHandle;
        public final Supplier<?> noArgsCtorLambda;
        public final Executable argsCreator;
        public final MethodHandle argsCreatorHandle;
        public final Func1 argsCreatorLambda1;
        public final Func2 argsCreatorLambda2;
        public final Func3 argsCreatorLambda3;
        public final Func4 argsCreatorLambda4;
        public final Func5 argsCreatorLambda5;
        public final String[] argNames;
        public final Type[] argTypes;
        public final Map<String, Integer> argIndexes;
        public final Map<String, String> aliasMap;
        /**
         * Creates immutable creator metadata holder.
         */
        public CreatorInfo(Class<?> clazz, MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda,
                           Executable argsCreator, MethodHandle argsCreatorHandle,
                           Func1 argsCreatorLambda1, Func2 argsCreatorLambda2,
                           Func3 argsCreatorLambda3, Func4 argsCreatorLambda4, Func5 argsCreatorLambda5,
                           String[] argNames, Type[] argTypes, Map<String, Integer> argIndexes,
                           Map<String, String> aliasMap) {
            this.clazz = clazz;
            this.noArgsCtorHandle = noArgsCtorHandle;
            this.noArgsCtorLambda = noArgsCtorLambda;
            this.argsCreator = argsCreator;
            this.argsCreatorHandle = argsCreatorHandle;
            this.argsCreatorLambda1 = argsCreatorLambda1;
            this.argsCreatorLambda2 = argsCreatorLambda2;
            this.argsCreatorLambda3 = argsCreatorLambda3;
            this.argsCreatorLambda4 = argsCreatorLambda4;
            this.argsCreatorLambda5 = argsCreatorLambda5;
            this.argNames = argNames;
            this.argTypes = argTypes;
            this.argIndexes = argIndexes;
            this.aliasMap = aliasMap;
        }

        /**
         * Returns argument index by name, or -1.
         */
        public int getArgIndex(String name) {
            if (argIndexes != null) {
                Integer idx = argIndexes.get(name);
                if (idx != null) return idx;
            }
            return -1;
        }

        public int getArgIndexOrAlias(String name) {
            int idx = getArgIndex(name);
            if (idx >= 0) return idx;
            if (aliasMap != null) {
                String origin = aliasMap.get(name);
                if (origin != null) {
                    return getArgIndex(origin);
                }
            }
            return -1;
        }

        public boolean hasNoArgsCreator() {
            return noArgsCtorLambda != null || noArgsCtorHandle != null;
        }

        /**
         * Creates a POJO using no-args constructor path.
         */
        public Object newPojoNoArgs() {
            if (noArgsCtorLambda != null) {
                return noArgsCtorLambda.get();
            } else if (noArgsCtorHandle != null) {
                try {
                    return noArgsCtorHandle.invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke constructor of " + clazz, e);
                }
            }
            throw new JsonException("Failed to create instance of " + clazz + ": Not found no-args constructor");
        }


        /**
         * Creates a POJO using argument creator path.
         */
        public Object newPojoWithArgs(Object[] args) {
            Objects.requireNonNull(args, "args");
            if (argsCreatorHandle == null) {
                throw new JsonException("Failed to create instance of " + clazz + ": No creator constructor");
            }
            try {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) {
                        Class<?> argClazz = Types.rawClazz(argTypes[i]);
                        args[i] = _missingValueOfClass(argClazz);
                    }
                }

                if (args.length == 1 && argsCreatorLambda1 != null) {
                    return argsCreatorLambda1.apply(args[0]);
                }
                if (args.length == 2 && argsCreatorLambda2 != null) {
                    return argsCreatorLambda2.apply(args[0], args[1]);
                }
                if (args.length == 3 && argsCreatorLambda3 != null) {
                    return argsCreatorLambda3.apply(args[0], args[1], args[2]);
                }
                if (args.length == 4 && argsCreatorLambda4 != null) {
                    return argsCreatorLambda4.apply(args[0], args[1], args[2], args[3]);
                }
                if (args.length == 5 && argsCreatorLambda5 != null) {
                    return argsCreatorLambda5.apply(args[0], args[1], args[2], args[3], args[4]);
                }

                return argsCreatorHandle.invokeWithArguments(args);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke creator constructor of " + clazz, e);
            }
        }

        /**
         * Creates a POJO preferring no-args constructor, then args creator.
         */
        public Object forceNewPojo() {
            if (noArgsCtorHandle != null) return newPojoNoArgs();
            Object[] args = new Object[argNames.length];
            return newPojoWithArgs(args);
        }

        /**
         * Returns default missing value for primitive classes.
         */
        private static Object _missingValueOfClass(Class<?> clazz) {
            if (clazz == null) return null;
            if (!clazz.isPrimitive()) return null;
            if (clazz == boolean.class) return false;
            if (clazz == byte.class) return (byte) 0;
            if (clazz == short.class) return (short) 0;
            if (clazz == int.class) return 0;
            if (clazz == long.class) return 0L;
            if (clazz == float.class) return 0f;
            if (clazz == double.class) return 0d;
            if (clazz == char.class) return '\0';
            return null;
        }
    }

    // RecordInfo
    public static class RecordInfo {
        public final Class<?> clazz;
        public final Constructor<?> compCtor;
        public final MethodHandle compCtorHandle;
        public final int compCount;
        public final String[] compNames;
        public final Class<?>[] compClasses;
        public final Type[] compTypes;
        /**
         * Creates immutable record metadata holder.
         */
        public RecordInfo(Class<?> clazz, Constructor<?> compCtor, MethodHandle compCtorHandle,
                          int compCount, String[] compNames, Class<?>[] compClasses, Type[] compTypes) {
            this.clazz = clazz;
            this.compCtor = compCtor;
            this.compCtorHandle = compCtorHandle;
            this.compCount = compCount;
            this.compNames = compNames;
            this.compClasses = compClasses;
            this.compTypes = compTypes;
        }
    }

    // FieldInfo
    public static class FieldInfo {
        public enum ContainerKind {
            NONE,
            LIST,
            SET,
            MAP,
            ARRAY
        }

        public final String name;
        public final Type type;
        public final Class<?> rawClazz;
        public final ContainerKind containerKind;
        public final Type argType;
        public final Class<?> argRawClazz;
        public final AnyOfInfo argAnyOfInfo;
        public final MethodHandle getter;
        public final Function<Object, Object> lambdaGetter;
        public final MethodHandle setter;
        public final BiConsumer<Object, Object> lambdaSetter;

        public final AnyOfInfo anyOfInfo;

        /**
         * Creates immutable field metadata holder.
         */
        public FieldInfo(String name, Type type,
                         MethodHandle getter, Function<Object, Object> lambdaGetter,
                         MethodHandle setter, BiConsumer<Object, Object> lambdaSetter,
                         AnyOfInfo anyOfInfo) {
            this.name = name;
            this.type = type;
            this.rawClazz = Types.rawBox(type);
            ContainerKind kind = ContainerKind.NONE;
            Type argType = null;
            Class<?> argRawType = null;
            if (List.class.isAssignableFrom(this.rawClazz)) {
                kind = ContainerKind.LIST;
                argType = Types.resolveTypeArgument(type, List.class, 0);
                argRawType = Types.rawBox(argType);
            } else if (Set.class.isAssignableFrom(this.rawClazz)) {
                kind = ContainerKind.SET;
                argType = Types.resolveTypeArgument(type, Set.class, 0);
                argRawType = Types.rawBox(argType);
            } else if (Map.class.isAssignableFrom(this.rawClazz)) {
                kind = ContainerKind.MAP;
                argType = Types.resolveTypeArgument(type, Map.class, 1);
                argRawType = Types.rawBox(argType);
            } else if (this.rawClazz.isArray()) {
                kind = ContainerKind.ARRAY;
                argType = this.rawClazz.getComponentType();
                argRawType = Types.box((Class<?>) argType);
            }
            this.containerKind = kind;
            this.argType = argType;
            this.argRawClazz = argRawType;
            this.argAnyOfInfo = argRawType == null ? null : ReflectUtil.resolveAnyOfInfo(argRawType);
            this.getter = getter;
            this.lambdaGetter = lambdaGetter;
            this.setter = setter;
            this.lambdaSetter = lambdaSetter;
            this.anyOfInfo = anyOfInfo;
        }

        /**
         * Returns true when a getter is available.
         */
        public boolean hasGetter() {
            return getter != null || lambdaGetter != null;
        }
        /**
         * Returns true when a setter is available.
         */
        public boolean hasSetter() {
            return setter != null || lambdaSetter != null;
        }


        /**
         * Invokes field getter.
         */
        public Object invokeGetter(Object receiver) {
            Objects.requireNonNull(receiver, "receiver");
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
         * Invokes setter when present and reports success.
         */
        public boolean invokeSetterIfPresent(Object receiver, Object value) {
            if (setter == null && lambdaSetter == null) return false;
            invokeSetter(receiver, value);
            return true;
        }

        /**
         * Invokes field setter.
         */
        public void invokeSetter(Object receiver, Object value) {
            Objects.requireNonNull(receiver, "receiver");
            try {
                if (lambdaSetter != null) {
                    lambdaSetter.accept(receiver, value);
                    return;
                }
                if (setter == null)
                    throw new JsonException("No setter available for field '" + name + "' of " + type);
                setter.invoke(receiver, value);
            } catch (Throwable e) {
                throw new JsonException("Failed to invoke setter for field '" + name + "' of type '" + type +
                        "' with value '" + Types.name(value) + "' (node type: " + Types.name(receiver)+ ")", e);
            }
        }

    }

    // ValueCodecInfo

    public static class ValueCodecInfo {
        public final Class<?> valueClazz;
        public final Class<?> rawClazz;
        public final ValueCodec<Object, Object> valueCodec;
        public final MethodHandle valueToRawHandle;
        public final MethodHandle rawToValueHandle;
        public final MethodHandle valueCopyHandle;
        /**
         * Creates immutable value-codec metadata holder.
         */
        @SuppressWarnings("unchecked")
        public ValueCodecInfo(Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> valueCodec,
                              MethodHandle valueToRawHandle, MethodHandle rawToValueHandle, MethodHandle valueCopyHandle) {
            this.valueClazz = valueClazz;
            this.rawClazz = rawClazz;
            this.valueCodec = (ValueCodec<Object, Object>) valueCodec;
            this.valueToRawHandle = valueToRawHandle;
            this.rawToValueHandle = rawToValueHandle;
            this.valueCopyHandle = valueCopyHandle;
        }
        /**
         * Creates value-codec metadata from codec instance.
         */
        public ValueCodecInfo(Class<?> valueClazz, Class<?> rawClazz, ValueCodec<?, ?> valueCodec) {
            this(valueClazz, rawClazz, valueCodec, null, null, null);
        }

        /**
         * Encodes value to raw representation.
         */
        public Object valueToRaw(Object value) {
            if (valueCodec != null) {
                try {
                    return valueCodec.valueToRaw(value);
                } catch (Exception e) {
                    throw new JsonException("Failed to valueToRaw() for value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (valueToRawHandle != null) {
                try {
                    return valueToRawHandle.invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to valueToRaw() for value type " + valueClazz.getName() +
                            " using annotated method " + valueToRawHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
        }

        /**
         * Decodes raw value to value representation.
         */
        public Object rawToValue(Object raw) {
            if (raw != null && !rawClazz.isInstance(raw))
                throw new JsonException("Cannot rawToValue() from raw type " + raw.getClass().getName() +
                        " to value type " + valueClazz.getName() + ". Expected raw type: " + rawClazz.getName());
            if (valueCodec != null) {
                try {
                    return valueCodec.rawToValue(raw);
                } catch (Exception e) {
                    throw new JsonException("Failed to rawToValue() to value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (rawToValueHandle != null) {
                try {
                    return rawToValueHandle.invoke(raw);
                } catch (Throwable e) {
                    throw new JsonException("Failed to rawToValue() to value type " + valueClazz.getName() +
                            " using annotated method " + rawToValueHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @" + NodeValue.class.getName()+ " annotation and no ValueCodec registered");
        }

        /**
         * Copies value using codec-defined semantics.
         */
        public Object valueCopy(Object value) {
            if (valueCodec != null) {
                try {
                    return valueCodec.valueCopy(value);
                } catch (Exception e) {
                    throw new JsonException("Failed to valueCopy() for value type " + valueClazz.getName() +
                            " using ValueCodec " + valueCodec.getClass().getName(), e);
                }
            } else if (valueCopyHandle != null) {
                try {
                    return valueCopyHandle.invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to valueCopy() for value type " + valueClazz.getName() +
                            " using annotated method " + valueCopyHandle, e);
                }
            }
            throw new JsonException("No value binding found for type " + valueClazz.getName() +
                    ": missing @" + NodeValue.class.getName() + " annotation and no ValueCodec registered");
        }

    }

    // AnyOfInfo

    public static class AnyOfInfo {
        public final Class<?> clazz;
        public final AnyOf.Mapping[] mappings;
        public final String key;
        public final String path;
        public final AnyOf.Scope scope;
        public final AnyOf.OnNoMatch onNoMatch;
        public final boolean hasDiscriminator;
        public final EnumMap<JsonType, Class<?>> byJsonType;
        public final Map<String, Class<?>> byWhen;
        public final JsonPath compiledPath;

        public AnyOfInfo(Class<?> clazz, AnyOf.Mapping[] mappings, String key,
                         String path, AnyOf.Scope scope, AnyOf.OnNoMatch onNoMatch) {
            this.clazz = clazz;
            this.mappings = mappings;
            this.key = key;
            this.path = path;
            this.scope = scope;
            this.onNoMatch = onNoMatch;
            this.compiledPath = path.isEmpty() ? null : JsonPath.compile(path);
            this.hasDiscriminator = !key.isEmpty() || !path.isEmpty();
            if (hasDiscriminator) {
                this.byJsonType = null;
                this.byWhen = new HashMap<>();
                for (AnyOf.Mapping mapping : mappings) {
                    for (String when : mapping.when()) {
                        byWhen.put(when, mapping.value());
                    }
                }
            } else {
                this.byWhen = null;
                this.byJsonType = new EnumMap<>(JsonType.class);
                for (AnyOf.Mapping mapping : mappings) {
                    byJsonType.put(JsonType.rawOf(mapping.value()), mapping.value());
                }
            }
        }

        public Class<?> resolveByJsonType(JsonType jsonType) {
            if (byJsonType == null || jsonType == null) return null;
            return byJsonType.get(jsonType);
        }

        public Class<?> resolveByWhen(Object when) {
            if (byWhen == null || when == null) return null;
            return byWhen.get(String.valueOf(when));
        }


    }


}
