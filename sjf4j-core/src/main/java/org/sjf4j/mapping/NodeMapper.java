package org.sjf4j.mapping;

import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.Types;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;
import org.sjf4j.util.Strings;
import org.sjf4j.value.ValueInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/**
 * Runtime mapper for converting between OBNT node representations.
 *
 * <p>Supports structural conversion between POJOs, JOJOs, maps, lists,
 * arrays, sets, {@link JsonObject}, and {@link JsonArray}, including
 * {@link org.sjf4j.value.ValueCodec ValueCodec} and {@link OneOf} semantics.
 */
public final class NodeMapper {

    private NodeMapper() {
    }


    /**
     * Converts a node to the target class using the default runtime context.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object node, Class<T> type, boolean deepCopy) {
        return (T) convert(node, type, deepCopy, RuntimeContext.EMPTY);
    }


    /**
     * Converts a node to the captured generic target type using the default
     * runtime context.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object node, TypeReference<T> type, boolean deepCopy) {
        return (T) convert(node, type.getType(), deepCopy, RuntimeContext.EMPTY);
    }



    /**
     * Root conversion entry with deep-copy control.
     */
    public static Object convert(Object node, Type type, boolean deepCopy, RuntimeContext context) {
        Asserts.notNull(type, "type");
        Asserts.notNull(context, "context");
        try {
            Class<?> rawBox = Types.rawBox(type);
            return _convert(node, type, rawBox, null, deepCopy, PathSegment.Root.INSTANCE, context);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to convert node from '" + Types.name(node) + "' to '" + type + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deepcopy(Object node) {
        if (node == null) return null;
        return (T) convert(node, node.getClass(), true, RuntimeContext.EMPTY);
    }

    /**
     * Converts a node to its raw JSON-compatible representation using the
     * supplied runtime context.
     *
     * <p>Object nodes become maps, array nodes become lists, and node values
     * are converted through their configured value codecs.
     */
    public static Object convertToRaw(Object node, RuntimeContext context) {
        Asserts.notNull(context, "context");
        return _convertToRaw(node, PathSegment.Root.INSTANCE, context);
    }


    /**
     * Internal conversion with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _convert(Object node, Type type, Class<?> rawClazz,
                                   OneOfInfo oneOfInfo, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        try {
            if (rawClazz == Optional.class) {
                TypeRegistry.registerTypeInfo(rawClazz);
            }
            if (node == null) {
                return null;
            }

            if (oneOfInfo != null) {
                return _convertOneOf(node, rawClazz, oneOfInfo, deepCopy, ps, context);
            }

            if (rawClazz == Object.class) {
                return deepCopy ? _copy(node, type, ps, context) : node;
            }

            // Compatible values can only be returned as-is when the target has no
            // generic structure to honor. Parameterized containers/POJOs still need
            // traversal so their declared element/member types are converted.
            if (rawClazz.isInstance(node) && !Types.hasGenericStructure(type)) {
                return deepCopy ? _copy(node, type, ps, context) : node;
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
            oneOfInfo = ti.oneOfInfo;
            if (oneOfInfo != null) {
                return _convertOneOf(node, rawClazz, oneOfInfo, deepCopy, ps, context);
            }

            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                ValueInfo valueInfo = ti.getNodeValueInfo(valueFormat);
                if (valueInfo != null) {
                    return rawClazz.isInstance(node)
                            ? valueInfo.valueCopy(node)
                            : valueInfo.rawToValue(node);
                }
            }

            if (node instanceof String) {
                return _convertString(node.toString(), rawClazz, ps);
            }

            if (node instanceof Number) {
                if (Number.class.isAssignableFrom(rawClazz)) {
                    return Numbers.to((Number) node, rawClazz);
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + type + "'", ps);
            }

            if (node instanceof Boolean) {
                if (rawClazz == Boolean.class) {
                    return node;
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + type + "'", ps);
            }

            if (node instanceof Map) {
                return _convertFromMap((Map<String, Object>) node,
                        rawClazz, type, deepCopy, ps, context);
            }

            if (node instanceof List) {
                return _convertFromList((List<Object>) node,
                        rawClazz, type, deepCopy, ps, context);
            }

            if (node instanceof JsonObject) {
                return _convertFromJsonObject((JsonObject) node,
                        rawClazz, type, deepCopy, ps, context);
            }

            if (node instanceof JsonArray) {
                return _convertFromJsonArray((JsonArray) node,
                        rawClazz, type, deepCopy, ps, context);
            }

            if (node.getClass().isArray()) {
                return _convertFromArray(node, rawClazz, type, deepCopy, ps, context);
            }

            if (node instanceof Set) {
                return _convertFromSet((Set<Object>) node,
                        rawClazz, type, deepCopy, ps, context);
            }

            if (node instanceof Character) {
                return _convertString(node.toString(), rawClazz, ps);
            }

            if (node instanceof Enum) {
                return _convertString(((Enum<?>) node).name(), rawClazz, ps);
            }

            PojoInfo sourceInfo = TypeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
            if (sourceInfo != null) {
                return _convertFromPojo(node, sourceInfo,
                        rawClazz, type, deepCopy, ps, context);
            }

            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + type + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + type + "'", ps, e);
        }
    }


    private static Object _convertOneOf(Object node, Class<?> rawClazz,
                                        OneOfInfo oneOfInfo, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        Class<?> targetClazz;

        if (oneOfInfo.hasDiscriminator) {
            if (oneOfInfo.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf scope '" +
                        oneOfInfo.scope + "' is not supported", ps);
            }

            if (!JsonType.of(node).isObject()) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "node must be a JSON object, when OneOf has a CURRENT discriminator", ps);
            }

            Object discriminatorValue;
            if (!oneOfInfo.key.isEmpty()) {
                discriminatorValue = Nodes.getInObject(node, oneOfInfo.key);
            } else if (!oneOfInfo.path.isEmpty()) {
                discriminatorValue = oneOfInfo.compiledPath.getNode(node);
            } else {
                discriminatorValue = null;
            }

            if (discriminatorValue == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "not found value for discriminator key '" + oneOfInfo.key + "'", ps);
            }

            targetClazz = oneOfInfo.matchByWhen(discriminatorValue);
            if (targetClazz == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "oneOf discriminator has no matching mapping: value='" +
                                discriminatorValue + "'", ps);
            }
        } else {
            JsonType jsonType = JsonType.of(node);
            targetClazz = oneOfInfo.matchByJsonType(jsonType);
            if (targetClazz == null) {
                if (oneOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) {
                    return null;
                }
                throw new BindingException(
                        "oneOf mapping does not support jsonType=" + jsonType +
                                " for type '" + rawClazz.getName() + "'", ps);
            }
        }

        return _convert(node, targetClazz, Types.rawBox(targetClazz),
                null, deepCopy, ps, context);
    }


    /**
     * Internal deep structural copy with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _copy(Object node, Type type, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) return null;

            Class<?> targetRaw = type == null ? Object.class : Types.rawBox(type);
            if (targetRaw != Object.class && !targetRaw.isInstance(node)) {
                return _convert(node, type, targetRaw, null, true, ps, context);
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(node.getClass());
                ValueInfo valueInfo = ti.getNodeValueInfo(valueFormat);
                if (valueInfo != null) {
                    return valueInfo.valueCopy(node);
                }
            }

            if (node instanceof String || node instanceof Number || node instanceof Boolean) {
                return node;
            }

            Class<?> nodeClazz = node.getClass();

            if (node instanceof Map) {
                Map<String, Object> srcMap = (Map<String, Object>) node;
                Map<String, Object> newMap =
                        TypeRegistry.newMapContainer(nodeClazz, srcMap.size(), true);
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                for (Map.Entry<String, Object> entry : srcMap.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    newMap.put(entry.getKey(), _copy(entry.getValue(), valueType, cps, context));
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> srcList = (List<Object>) node;
                List<Object> newList =
                        TypeRegistry.newListContainer(nodeClazz, srcList.size(), true);
                Type elemType = Types.resolveTypeArgument(type, List.class, 0);
                for (int i = 0; i < srcList.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_copy(srcList.get(i), elemType, cps, context));
                }
                return newList;
            }

            if (nodeClazz == JsonObject.class) {
                JsonObject srcJo = (JsonObject) node;
                JsonObject newJo = new JsonObject();
                srcJo.forEach((k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, k);
                    newJo.put(k, _copy(v, Object.class, cps, context));
                });
                return newJo;
            }

            if (node instanceof JsonObject) {
                JsonObject srcJo = (JsonObject) node;
                PojoInfo pojoInfo = TypeRegistry.registerPojoOrElseThrow(nodeClazz);
                CreatorInfo ci = pojoInfo.creatorInfo;
                TypeRegistry.PojoCreationSession session =
                        new TypeRegistry.PojoCreationSession(ci, srcJo.size());

                for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    int argIdx = ci.getArgIndexOrAlias(key);
                    if (argIdx >= 0) {
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type argType =
                                Types.resolveMemberType(type, targetRaw, ci.argTypes[argIdx]);

                        ValueInfo valueInfo = ci.argValueCodecs[argIdx];
                        Object vv = value != null && valueInfo != null
                                ? valueInfo.valueCopy(value)
                                : _copy(value, argType, cps, context);

                        session.acceptCtorArg(argIdx, vv);
                        continue;
                    }

                    FieldInfo fi = pojoInfo.aliasProperties != null
                            ? pojoInfo.aliasProperties.get(key)
                            : pojoInfo.properties.get(key);

                    if (fi != null) {
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type fieldType = fi.genericDependent
                                ? Types.resolveMemberType(type, targetRaw, fi.type)
                                : fi.type;

                        Object vv = value != null
                                && fi.oneOfInfo == null
                                && fi.valueInfo != null
                                ? fi.valueInfo.valueCopy(value)
                                : _copy(value, fieldType, cps, context);

                        session.acceptProperty(fi, vv);
                        continue;
                    }

                    PathSegment cps = new PathSegment.Name(ps, key);
                    session.acceptDynamic(key, _copy(value, Object.class, cps, context));
                }

                return session.finish();
            }

            if (node instanceof JsonArray) {
                JsonArray srcJa = (JsonArray) node;
                JsonArray newJa = nodeClazz == JsonArray.class
                        ? new JsonArray()
                        : (JsonArray) TypeRegistry.registerPojoOrElseThrow(nodeClazz)
                        .creatorInfo.forceNewPojo();

                Type elemType = Types.resolveTypeArgument(type, List.class, 0);
                for (int i = 0; i < srcJa.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newJa.add(_copy(srcJa.getNode(i), elemType, cps, context));
                }
                return newJa;
            }

            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Object newArr = Array.newInstance(nodeClazz.getComponentType(), len);
                Type compType = nodeClazz.getComponentType();
                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Array.set(newArr, i, _copy(Array.get(node, i), compType, cps, context));
                }
                return newArr;
            }

            if (node instanceof Set) {
                Set<Object> srcSet = (Set<Object>) node;
                Set<Object> newSet =
                        TypeRegistry.newSetContainer(nodeClazz, srcSet.size(), true);
                Type elemType = Types.resolveTypeArgument(type, Set.class, 0);
                int i = 0;
                for (Object value : srcSet) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newSet.add(_copy(value, elemType, cps, context));
                }
                return newSet;
            }

            PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                CreatorInfo ci = pi.creatorInfo;
                TypeRegistry.PojoCreationSession session =
                        new TypeRegistry.PojoCreationSession(ci, pi.readablePropertyCount);

                for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()) {
                    String key = entry.getKey();
                    FieldInfo fi = entry.getValue();
                    Object value = fi.invokeGetter(node);

                    int argIdx = ci.getArgIndexOrAlias(key);
                    if (argIdx >= 0) {
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type argType =
                                Types.resolveMemberType(type, targetRaw, ci.argTypes[argIdx]);

                        ValueInfo valueInfo = ci.argValueCodecs[argIdx];
                        Object vv = value != null && valueInfo != null
                                ? valueInfo.valueCopy(value)
                                : _copy(value, argType, cps, context);

                        session.acceptCtorArg(argIdx, vv);
                        continue;
                    }

                    PathSegment cps = new PathSegment.Name(ps, key);
                    Type fieldType = fi.genericDependent
                            ? Types.resolveMemberType(type, targetRaw, fi.type)
                            : fi.type;

                    Object vv = value != null
                            && fi.oneOfInfo == null
                            && fi.valueInfo != null
                            ? fi.valueInfo.valueCopy(value)
                            : _copy(value, fieldType, cps, context);

                    session.acceptProperty(fi, vv);
                }

                return session.finish();
            }

            return node;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(
                    "failed to deep copy node '" + Types.name(node) + "'", ps, e);
        }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object _convertString(String value, Class<?> rawClazz, PathSegment ps) {
        if (rawClazz == String.class) {
            return value;
        }
        if (rawClazz == Character.class) {
            return !value.isEmpty() ? value.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawClazz, value);
        }
        throw new BindingException(
                "cannot convert String '" + Strings.truncate(value) +
                        "' to '" + rawClazz.getName() + "'", ps);
    }


    // Map -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromMap(Map<String, Object> oldMap,
                                          Class<?> rawClazz,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps,
                                          RuntimeContext context) {
        return _convertFromObjectSource(new ObjectSource() {
            @Override
            public Iterable<Map.Entry<String, Object>> entries() {
                return oldMap.entrySet();
            }

            @Override
            public int size() {
                return oldMap.size();
            }
        }, "Map", rawClazz, type, deepCopy, ps, context);
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromJsonObject(JsonObject oldJo,
                                                 Class<?> rawClazz,
                                                 Type type,
                                                 boolean deepCopy,
                                                 PathSegment ps,
                                                 RuntimeContext context) {
        return _convertFromObjectSource(new ObjectSource() {
            @Override
            public Iterable<Map.Entry<String, Object>> entries() {
                return oldJo.entrySet();
            }

            @Override
            public int size() {
                return oldJo.size();
            }
        }, "JsonObject", rawClazz, type, deepCopy, ps, context);
    }

    private static Object _convertFromObjectSource(ObjectSource source,
                                                   String sourceName,
                                                   Class<?> rawClazz,
                                                   Type type,
                                                   boolean deepCopy,
                                                   PathSegment ps,
                                                   RuntimeContext context) {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map =
                    TypeRegistry.newMapContainer(rawClazz, source.size(), false);

            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convert(entry.getValue(),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context);
                map.put(entry.getKey(), value);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convert(entry.getValue(),
                        Object.class, Object.class, null, deepCopy, cps, context);
                jo.put(entry.getKey(), value);
            }
            return jo;
        }

        PojoInfo pi = TypeRegistry.registerTypeInfo(rawClazz).pojoInfo;
        if (pi != null && !pi.isJajo) {
            return _convertPojoFromEntries(
                    source.entries(), type, rawClazz, pi, deepCopy, ps, context);
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" + rawClazz.getName() + "'", ps);
    }

    private static Object _convertPojoFromEntries(
            Iterable<Map.Entry<String, Object>> entries,
            Type type,
            Class<?> rawClazz,
            PojoInfo pi,
            boolean deepCopy,
            PathSegment ps,
            RuntimeContext context) {

        CreatorInfo ci = pi.creatorInfo;
        CreatorState state = new CreatorState(ci);

        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType =
                        Types.resolveMemberType(type, rawClazz, ci.argTypes[argIdx]);
                PathSegment cps = new PathSegment.Name(ps, key);
                Class<?> argRaw = Types.rawBox(argType);

                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                ValueInfo argValueInfo = ci.argValueCodecs[argIdx];
                if (argValueInfo == null && ti.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argValueInfo = ti.getNodeValueInfo(valueFormat);
                }

                if (ti.oneOfInfo == null && argValueInfo != null) {
                    state.acceptCtorArg(argIdx, argRaw.isInstance(rawValue)
                            ? argValueInfo.valueCopy(rawValue)
                            : argValueInfo.rawToValue(rawValue));
                } else {
                    state.acceptCtorArg(argIdx, _convert(
                            rawValue, argType, argRaw,
                            ti.oneOfInfo, deepCopy, cps, context));
                }
                continue;
            }

            FieldInfo fi = pi.aliasProperties != null
                    ? pi.aliasProperties.get(key)
                    : pi.properties.get(key);

            if (fi != null) {
                if (!fi.hasSetter()) continue;

                PathSegment cps = new PathSegment.Name(ps, key);
                Type fieldType = fi.genericDependent
                        ? Types.resolveMemberType(type, rawClazz, fi.type)
                        : fi.type;
                Class<?> fieldRaw = fi.genericDependent
                        ? Types.rawBox(fieldType)
                        : fi.boxed;

                Object value;
                if (fi.oneOfInfo == null && fi.valueInfo != null) {
                    value = fieldRaw.isInstance(rawValue)
                            ? fi.valueInfo.valueCopy(rawValue)
                            : fi.valueInfo.rawToValue(rawValue);
                } else {
                    value = _convert(rawValue, fieldType, fieldRaw,
                            fi.oneOfInfo, deepCopy, cps, context);
                }

                if (state.isCreated()) {
                    fi.invokeSetter(state.pojo(), value);
                } else {
                    state.bufferProperty(fi, value);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                PathSegment cps = new PathSegment.Name(ps, key);
                Object value = _convert(rawValue,
                        Object.class, Object.class, null, deepCopy, cps, context);
                state.acceptDynamic(key, value);
            }
        }

        return state.finish();
    }


    // List -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromList(List<?> oldList,
                                           Class<?> rawClazz,
                                           Type type,
                                           boolean deepCopy,
                                           PathSegment ps,
                                           RuntimeContext context) {
        return _convertFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return oldList.size();
            }

            @Override
            public Object get(int i) {
                return oldList.get(i);
            }
        }, "List", rawClazz, type, deepCopy, ps, context);
    }

    // JsonArray -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromJsonArray(JsonArray oldJa,
                                                Class<?> rawClazz,
                                                Type type,
                                                boolean deepCopy,
                                                PathSegment ps,
                                                RuntimeContext context) {
        return _convertFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return oldJa.size();
            }

            @Override
            public Object get(int i) {
                return oldJa.getNode(i);
            }
        }, "JsonArray", rawClazz, type, deepCopy, ps, context);
    }

    // Array -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromArray(Object node,
                                            Class<?> rawClazz,
                                            Type type,
                                            boolean deepCopy,
                                            PathSegment ps,
                                            RuntimeContext context) {
        return _convertFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return Array.getLength(node);
            }

            @Override
            public Object get(int i) {
                return Array.get(node, i);
            }
        }, "Array", rawClazz, type, deepCopy, ps, context);
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromSet(Set<Object> oldSet,
                                          Class<?> rawClazz,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps,
                                          RuntimeContext context) {
        int size = oldSet.size();

        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            List<Object> list =
                    TypeRegistry.newListContainer(rawClazz, size, false);

            int i = 0;
            for (Object value : oldSet) {
                list.add(_convert(value, valueType, valueRaw, valueOneOf,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return list;
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object value : oldSet) {
                ja.add(_convert(value,
                        Object.class, Object.class, null, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> valueRaw = jajo.elementClass();
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int i = 0;
            for (Object value : oldSet) {
                jajo.add(_convert(value,
                        valueRaw, valueRaw, valueOneOf, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return jajo;
        }

        if (rawClazz.isArray()) {
            Class<?> valueType = rawClazz.getComponentType();
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Object array = Array.newInstance(valueType, size);
            int i = 0;
            for (Object value : oldSet) {
                Array.set(array, i, _convert(value,
                        valueType, valueRaw, valueOneOf, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Set<Object> set =
                    TypeRegistry.newSetContainer(rawClazz, size, false);

            int i = 0;
            for (Object value : oldSet) {
                set.add(_convert(value, valueType, valueRaw, valueOneOf,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert Set to '" + rawClazz.getName() + "'", ps);
    }

    private static Object _convertFromIndexedSource(IndexedSource source,
                                                    String sourceName,
                                                    Class<?> rawClazz,
                                                    Type type,
                                                    boolean deepCopy,
                                                    PathSegment ps,
                                                    RuntimeContext context) {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int size = source.size();
            List<Object> list =
                    TypeRegistry.newListContainer(rawClazz, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                list.add(_convert(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return list;
        }

        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                ja.add(_convert(source.get(i),
                        Object.class, Object.class, null, deepCopy, cps, context));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> valueRaw = jajo.elementClass();
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                jajo.add(_convert(source.get(i),
                        valueRaw, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return jajo;
        }

        if (rawClazz.isArray()) {
            Class<?> valueType = rawClazz.getComponentType();
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Object array = Array.newInstance(valueType, source.size());
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Array.set(array, i, _convert(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return array;
        }

        if (Set.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int size = source.size();
            Set<Object> set =
                    TypeRegistry.newSetContainer(rawClazz, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                set.add(_convert(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" +
                        rawClazz.getName() + "'", ps);
    }


    // POJO -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromPojo(Object node,
                                           PojoInfo sourceInfo,
                                           Class<?> rawClazz,
                                           Type type,
                                           boolean deepCopy,
                                           PathSegment ps,
                                           RuntimeContext context) {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map =
                    TypeRegistry.newMapContainer(
                            rawClazz, sourceInfo.readablePropertyCount, false);

            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            for (Map.Entry<String, FieldInfo> entry
                    : sourceInfo.readableProperties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue().invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, key);
                map.put(key, _convert(value, valueType, valueRaw,
                        valueOneOf, deepCopy, cps, context));
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, FieldInfo> entry
                    : sourceInfo.readableProperties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue().invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, key);
                jo.put(key, _convert(value,
                        Object.class, Object.class, null, deepCopy, cps, context));
            }
            return jo;
        }

        PojoInfo targetInfo = TypeRegistry.registerTypeInfo(rawClazz).pojoInfo;
        if (targetInfo != null && !targetInfo.isJajo) {
            return _convertPojoFromProperties(
                    node, sourceInfo, type, rawClazz,
                    targetInfo, deepCopy, ps, context);
        }

        throw new BindingException(
                "cannot convert POJO to '" + rawClazz.getName() + "'", ps);
    }

    private static Object _convertPojoFromProperties(Object source,
                                                     PojoInfo sourceInfo,
                                                     Type type,
                                                     Class<?> rawClazz,
                                                     PojoInfo targetInfo,
                                                     boolean deepCopy,
                                                     PathSegment ps,
                                                     RuntimeContext context) {
        Object[] sourceValues =
                new Object[sourceInfo.readablePropertyCount];

        int sourceIndex = 0;
        for (FieldInfo sourceField : sourceInfo.readableProperties.values()) {
            sourceValues[sourceIndex++] = sourceField.invokeGetter(source);
        }

        CreatorInfo ci = targetInfo.creatorInfo;
        CreatorState state = new CreatorState(ci);

        sourceIndex = 0;
        for (Map.Entry<String, FieldInfo> entry
                : sourceInfo.readableProperties.entrySet()) {
            String key = entry.getKey();
            Object rawValue = sourceValues[sourceIndex++];

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType =
                        Types.resolveMemberType(type, rawClazz, ci.argTypes[argIdx]);
                PathSegment cps = new PathSegment.Name(ps, key);
                Class<?> argRaw = Types.rawBox(argType);

                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                ValueInfo argValueInfo = ci.argValueCodecs[argIdx];
                if (argValueInfo == null && ti.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argValueInfo = ti.getNodeValueInfo(valueFormat);
                }

                if (ti.oneOfInfo == null && argValueInfo != null) {
                    state.acceptCtorArg(argIdx, argRaw.isInstance(rawValue)
                            ? argValueInfo.valueCopy(rawValue)
                            : argValueInfo.rawToValue(rawValue));
                } else {
                    state.acceptCtorArg(argIdx, _convert(
                            rawValue, argType, argRaw,
                            ti.oneOfInfo, deepCopy, cps, context));
                }
                continue;
            }

            FieldInfo fi = targetInfo.aliasProperties != null
                    ? targetInfo.aliasProperties.get(key)
                    : targetInfo.properties.get(key);

            if (fi != null) {
                if (!fi.hasSetter()) continue;

                PathSegment cps = new PathSegment.Name(ps, key);
                Type fieldType = fi.genericDependent
                        ? Types.resolveMemberType(type, rawClazz, fi.type)
                        : fi.type;
                Class<?> fieldRaw = fi.genericDependent
                        ? Types.rawBox(fieldType)
                        : fi.boxed;

                Object value;
                if (fi.oneOfInfo == null && fi.valueInfo != null) {
                    value = fieldRaw.isInstance(rawValue)
                            ? fi.valueInfo.valueCopy(rawValue)
                            : fi.valueInfo.rawToValue(rawValue);
                } else {
                    value = _convert(rawValue, fieldType, fieldRaw,
                            fi.oneOfInfo, deepCopy, cps, context);
                }

                if (state.isCreated()) {
                    fi.invokeSetter(state.pojo(), value);
                } else {
                    state.bufferProperty(fi, value);
                }
                continue;
            }

            if (targetInfo.isJojo && targetInfo.readDynamic) {
                PathSegment cps = new PathSegment.Name(ps, key);
                state.acceptDynamic(key, _convert(rawValue,
                        Object.class, Object.class, null, deepCopy, cps, context));
            }
        }

        return state.finish();
    }


    private interface IndexedSource {
        int size();

        Object get(int i);
    }

    private interface ObjectSource {
        Iterable<Map.Entry<String, Object>> entries();

        int size();
    }


    /*
     * --------------------------------------------------------------
     * Raw conversion
     * --------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    private static Object _convertToRaw(Object node, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) return null;

            if (node instanceof String) {
                return node.toString();
            }

            if (node instanceof Number) {
                return node;
            }

            if (node instanceof Boolean) {
                return node;
            }

            if (node instanceof Map) {
                Map<String, Object> oldMap = (Map<String, Object>) node;
                Map<String, Object> newMap =
                        TypeRegistry.newMapContainer(
                                LinkedHashMap.class, oldMap.size(), false);

                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    PathSegment cps =
                            new PathSegment.Name(ps, entry.getKey());
                    newMap.put(entry.getKey(),
                            _convertToRaw(entry.getValue(), cps, context));
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> oldList = (List<Object>) node;
                List<Object> newList = new ArrayList<>(oldList.size());

                for (int i = 0, len = oldList.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(oldList.get(i), cps, context));
                }
                return newList;
            }

            Class<?> rawClazz = node.getClass();

            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;

                if (rawClazz == JsonObject.class) {
                    Map<String, Object> newMap =
                            TypeRegistry.newMapContainer(
                                    LinkedHashMap.class, jo.size(), false);

                    for (Map.Entry<String, Object> entry : jo.entrySet()) {
                        PathSegment cps =
                                new PathSegment.Name(ps, entry.getKey());
                        newMap.put(entry.getKey(),
                                _convertToRaw(entry.getValue(), cps, context));
                    }
                    return newMap;
                }

                PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(rawClazz);
                Map<String, Object> dynamic =
                        pi.writeDynamic
                                ? InternalAccess.dynamicProperties(jo)
                                : null;

                int size = pi.readablePropertyCount +
                        (dynamic == null ? 0 : dynamic.size());

                Map<String, Object> newMap =
                        TypeRegistry.newMapContainer(
                                LinkedHashMap.class, size, false);

                for (Map.Entry<String, FieldInfo> entry
                        : pi.readableProperties.entrySet()) {
                    String key = entry.getKey();
                    FieldInfo fi = entry.getValue();
                    Object value = fi.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, key);

                    Object raw = fi.valueInfo != null
                            ? fi.valueInfo.valueToRaw(value)
                            : _convertToRaw(value, cps, context);

                    newMap.put(key, raw);
                }

                if (dynamic != null) {
                    for (Map.Entry<String, Object> entry : dynamic.entrySet()) {
                        PathSegment cps =
                                new PathSegment.Name(ps, entry.getKey());
                        newMap.put(entry.getKey(),
                                _convertToRaw(entry.getValue(), cps, context));
                    }
                }

                return newMap;
            }

            if (node instanceof JsonArray) {
                JsonArray ja = (JsonArray) node;
                List<Object> newList = new ArrayList<>(ja.size());

                for (int i = 0, len = ja.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(ja.getNode(i), cps, context));
                }
                return newList;
            }

            if (rawClazz.isArray()) {
                int len = Array.getLength(node);
                List<Object> newList = new ArrayList<>(len);

                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_convertToRaw(Array.get(node, i), cps, context));
                }
                return newList;
            }

            if (node instanceof Set) {
                Set<Object> set = (Set<Object>) node;
                List<Object> newList = new ArrayList<>(set.size());

                int i = 0;
                for (Object value : set) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newList.add(_convertToRaw(value, cps, context));
                }
                return newList;
            }

            if (node instanceof Character) {
                return node.toString();
            }

            if (node instanceof Enum) {
                return ((Enum<?>) node).name();
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                ValueInfo valueInfo = ti.getNodeValueInfo(valueFormat);
                if (valueInfo != null) {
                    return valueInfo.valueToRaw(node);
                }
            }

            PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                Map<String, Object> newMap =
                        TypeRegistry.newMapContainer(
                                LinkedHashMap.class,
                                pi.readablePropertyCount,
                                false);

                for (Map.Entry<String, FieldInfo> entry
                        : pi.readableProperties.entrySet()) {
                    String key = entry.getKey();
                    FieldInfo fi = entry.getValue();
                    Object value = fi.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, key);

                    Object raw = fi.valueInfo != null
                            ? fi.valueInfo.valueToRaw(value)
                            : _convertToRaw(value, cps, context);

                    newMap.put(key, raw);
                }

                return newMap;
            }

            throw new BindingException(
                    "unsupported node type '" + Types.name(node) + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException(
                    "cannot convert node from '" + Types.name(node) +
                            "' to raw (Map/List/String/Number/Boolean/null)",
                    ps,
                    e);
        }
    }

}
