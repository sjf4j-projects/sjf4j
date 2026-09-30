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
    private static Object _convert(Object node, Type toType, Class<?> toBoxed,
                                   OneOfInfo oneOfInfo, boolean deepCopy, PathSegment ps, RuntimeContext context) {
        try {
            if (toBoxed == Optional.class) {
                TypeRegistry.registerTypeInfo(toBoxed);
            }
            if (node == null) {
                return null;
            }

            if (oneOfInfo != null) {
                return _convertOneOf(node, toBoxed, oneOfInfo, deepCopy, ps, context);
            }

            if (toBoxed == Object.class) {
                return deepCopy ? _deepCopy(node, toType, toBoxed, ps, context) : node;
            }

            // Compatible values can only be returned as-is when the target has no
            // generic structure to honor. Parameterized containers/POJOs still need
            // traversal so their declared element/member types are converted.
            if (toBoxed.isInstance(node) && !Types.hasGenericStructure(toType)) {
                return deepCopy ? _deepCopy(node, toType, toBoxed, ps, context) : node;
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(toBoxed);
            oneOfInfo = ti.oneOfInfo;
            if (oneOfInfo != null) {
                return _convertOneOf(node, toBoxed, oneOfInfo, deepCopy, ps, context);
            }

            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(toBoxed);
                ValueInfo valueInfo = ti.getNodeValueInfo(valueFormat);
                if (valueInfo != null) {
                    return toBoxed.isInstance(node)
                            ? valueInfo.valueCopy(node)
                            : valueInfo.rawToValue(node);
                }
            }

            if (node instanceof String) {
                return _convertString(node.toString(), toBoxed, ps);
            }

            if (node instanceof Number) {
                if (Number.class.isAssignableFrom(toBoxed)) {
                    return Numbers.to((Number) node, toBoxed);
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + toType + "'", ps);
            }

            if (node instanceof Boolean) {
                if (toBoxed == Boolean.class) {
                    return node;
                }
                throw new BindingException("cannot convert node from '" +
                        Types.name(node) + "' to '" + toType + "'", ps);
            }

            if (node instanceof Map) {
                return _convertFromMap((Map<String, Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof List) {
                return _convertFromList((List<Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof JsonObject) {
                return _convertFromJsonObject((JsonObject) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof JsonArray) {
                return _convertFromJsonArray((JsonArray) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node.getClass().isArray()) {
                return _convertFromArray(node, toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof Set) {
                return _convertFromSet((Set<Object>) node,
                        toBoxed, toType, deepCopy, ps, context);
            }

            if (node instanceof Character) {
                return _convertString(node.toString(), toBoxed, ps);
            }

            if (node instanceof Enum) {
                return _convertString(((Enum<?>) node).name(), toBoxed, ps);
            }

            PojoInfo sourceInfo = TypeRegistry.registerTypeInfo(node.getClass()).pojoInfo;
            if (sourceInfo != null) {
                return _convertFromPojo(node, sourceInfo,
                        toBoxed, toType, deepCopy, ps, context);
            }

            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + toType + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" +
                    Types.name(node) + "' to '" + toType + "'", ps, e);
        }
    }


    private static Object _convertOneOf(Object node, Class<?> toBoxed,
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
                                " for type '" + toBoxed.getName() + "'", ps);
            }
        }

        return _convert(node, targetClazz, Types.rawBox(targetClazz),
                null, deepCopy, ps, context);
    }


    /**
     * Internal deep structural copy with path support.
     */
    @SuppressWarnings("unchecked")
    private static Object _deepCopy(Object node, Type toType, Class<?> toBoxed, PathSegment ps, RuntimeContext context) {
        try {
            if (node == null) return null;

            if (toBoxed != Object.class && !toBoxed.isInstance(node)) {
                return _convert(node, toType, toBoxed, null, true, ps, context);
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.externalNode != null) {
                return ti.externalNode.deepCopy(node);
            }
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
                Type valueType = Types.resolveTypeArgument(toType, Map.class, 1);
                Class<?> valueBoxed = Types.rawBox(valueType);
                for (Map.Entry<String, Object> entry : srcMap.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    newMap.put(entry.getKey(), _deepCopy(entry.getValue(), valueType, valueBoxed, cps, context));
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> srcList = (List<Object>) node;
                List<Object> newList =
                        TypeRegistry.newListContainer(nodeClazz, srcList.size(), true);
                Type elemType = Types.resolveTypeArgument(toType, List.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                for (int i = 0; i < srcList.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_deepCopy(srcList.get(i), elemType, elemBoxed, cps, context));
                }
                return newList;
            }

            if (nodeClazz == JsonObject.class) {
                JsonObject srcJo = (JsonObject) node;
                JsonObject newJo = new JsonObject();
                srcJo.forEach((k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, k);
                    newJo.put(k, _deepCopy(v, Object.class, Object.class, cps, context));
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
                        Type argType = Types.resolveMemberType(toType, toBoxed, ci.argTypes[argIdx]);
                        Class<?> argBoxed = Types.rawBox(argType);

                        ValueInfo valueInfo = ci.argValueCodecs[argIdx];
                        Object vv = value != null && valueInfo != null
                                ? valueInfo.valueCopy(value)
                                : _deepCopy(value, argType, argBoxed, cps, context);

                        session.acceptCtorArg(argIdx, vv);
                        continue;
                    }

                    FieldInfo fi = pojoInfo.aliasProperties != null
                            ? pojoInfo.aliasProperties.get(key)
                            : pojoInfo.properties.get(key);

                    if (fi != null) {
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type fieldType = fi.genericDependent
                                ? Types.resolveMemberType(toType, toBoxed, fi.type)
                                : fi.type;
                        Class<?> fieldBoxed = Types.rawBox(fieldType);

                        Object vv = value != null
                                && fi.oneOfInfo == null
                                && fi.valueInfo != null
                                ? fi.valueInfo.valueCopy(value)
                                : _deepCopy(value, fieldType, fieldBoxed, cps, context);

                        session.acceptProperty(fi, vv);
                        continue;
                    }

                    PathSegment cps = new PathSegment.Name(ps, key);
                    session.acceptDynamic(key, _deepCopy(value, Object.class, Object.class, cps, context));
                }

                return session.finish();
            }

            if (node instanceof JsonArray) {
                JsonArray srcJa = (JsonArray) node;
                JsonArray newJa = nodeClazz == JsonArray.class
                        ? new JsonArray()
                        : (JsonArray) TypeRegistry.registerPojoOrElseThrow(nodeClazz)
                        .creatorInfo.forceNewPojo();

                Type elemType = Types.resolveTypeArgument(toType, List.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                for (int i = 0; i < srcJa.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newJa.add(_deepCopy(srcJa.getNode(i), elemType, elemBoxed, cps, context));
                }
                return newJa;
            }

            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Object newArr = Array.newInstance(nodeClazz.getComponentType(), len);
                Type compType = nodeClazz.getComponentType();
                Class<?> compBoxed = Types.rawBox(compType);
                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Array.set(newArr, i, _deepCopy(Array.get(node, i), compType, compBoxed, cps, context));
                }
                return newArr;
            }

            if (node instanceof Set) {
                Set<Object> srcSet = (Set<Object>) node;
                Set<Object> newSet =
                        TypeRegistry.newSetContainer(nodeClazz, srcSet.size(), true);
                Type elemType = Types.resolveTypeArgument(toType, Set.class, 0);
                Class<?> elemBoxed = Types.rawBox(elemType);
                int i = 0;
                for (Object value : srcSet) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newSet.add(_deepCopy(value, elemType, elemBoxed, cps, context));
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
                        Type argType = Types.resolveMemberType(toType, toBoxed, ci.argTypes[argIdx]);
                        Class<?> argBoxed = Types.rawBox(argType);
                        ValueInfo valueInfo = ci.argValueCodecs[argIdx];
                        Object vv = value != null && valueInfo != null
                                ? valueInfo.valueCopy(value)
                                : _deepCopy(value, argType, argBoxed, cps, context);

                        session.acceptCtorArg(argIdx, vv);
                        continue;
                    }

                    PathSegment cps = new PathSegment.Name(ps, key);
                    Type fieldType = fi.genericDependent
                            ? Types.resolveMemberType(toType, toBoxed, fi.type)
                            : fi.type;
                    Class<?> fieldBoxed = Types.rawBox(fieldType);

                    Object vv = value != null
                            && fi.oneOfInfo == null
                            && fi.valueInfo != null
                            ? fi.valueInfo.valueCopy(value)
                            : _deepCopy(value, fieldType, fieldBoxed, cps, context);

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
    private static Object _convertString(String value, Class<?> toBoxed, PathSegment ps) {
        if (toBoxed == String.class) {
            return value;
        }
        if (toBoxed == Character.class) {
            return !value.isEmpty() ? value.charAt(0) : null;
        }
        if (toBoxed.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) toBoxed, value);
        }
        throw new BindingException(
                "cannot convert String '" + Strings.truncate(value) +
                        "' to '" + toBoxed.getName() + "'", ps);
    }


    // Map -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromMap(Map<String, Object> oldMap,
                                          Class<?> toBoxed,
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
        }, "Map", toBoxed, type, deepCopy, ps, context);
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromJsonObject(JsonObject oldJo,
                                                 Class<?> toBoxed,
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
        }, "JsonObject", toBoxed, type, deepCopy, ps, context);
    }

    private static Object _convertFromObjectSource(ObjectSource source,
                                                   String sourceName,
                                                   Class<?> toBoxed,
                                                   Type type,
                                                   boolean deepCopy,
                                                   PathSegment ps,
                                                   RuntimeContext context) {
        if (Map.class.isAssignableFrom(toBoxed)) {
            Map<String, Object> map =
                    TypeRegistry.newMapContainer(toBoxed, source.size(), false);

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

        if (toBoxed == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object value = _convert(entry.getValue(),
                        Object.class, Object.class, null, deepCopy, cps, context);
                jo.put(entry.getKey(), value);
            }
            return jo;
        }

        PojoInfo pi = TypeRegistry.registerTypeInfo(toBoxed).pojoInfo;
        if (pi != null && !pi.isJajo) {
            return _convertPojoFromEntries(
                    source.entries(), type, toBoxed, pi, deepCopy, ps, context);
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertPojoFromEntries(
            Iterable<Map.Entry<String, Object>> entries,
            Type type,
            Class<?> toBoxed,
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
                        Types.resolveMemberType(type, toBoxed, ci.argTypes[argIdx]);
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
                        ? Types.resolveMemberType(type, toBoxed, fi.type)
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
                                           Class<?> toBoxed,
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
        }, "List", toBoxed, type, deepCopy, ps, context);
    }

    // JsonArray -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromJsonArray(JsonArray oldJa,
                                                Class<?> toBoxed,
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
        }, "JsonArray", toBoxed, type, deepCopy, ps, context);
    }

    // Array -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromArray(Object node,
                                            Class<?> toBoxed,
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
        }, "Array", toBoxed, type, deepCopy, ps, context);
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private static Object _convertFromSet(Set<Object> oldSet,
                                          Class<?> toBoxed,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps,
                                          RuntimeContext context) {
        int size = oldSet.size();

        if (List.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            List<Object> list =
                    TypeRegistry.newListContainer(toBoxed, size, false);

            int i = 0;
            for (Object value : oldSet) {
                list.add(_convert(value, valueType, valueRaw, valueOneOf,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return list;
        }

        if (toBoxed == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object value : oldSet) {
                ja.add(_convert(value,
                        Object.class, Object.class, null, deepCopy,
                        new PathSegment.Index(ps, i++), context));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(toBoxed)) {
            PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(toBoxed);
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

        if (toBoxed.isArray()) {
            Class<?> valueType = toBoxed.getComponentType();
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

        if (Set.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            Set<Object> set =
                    TypeRegistry.newSetContainer(toBoxed, size, false);

            int i = 0;
            for (Object value : oldSet) {
                set.add(_convert(value, valueType, valueRaw, valueOneOf,
                        deepCopy, new PathSegment.Index(ps, i++), context));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert Set to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertFromIndexedSource(IndexedSource source,
                                                    String sourceName,
                                                    Class<?> toBoxed,
                                                    Type type,
                                                    boolean deepCopy,
                                                    PathSegment ps,
                                                    RuntimeContext context) {
        if (List.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int size = source.size();
            List<Object> list =
                    TypeRegistry.newListContainer(toBoxed, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                list.add(_convert(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return list;
        }

        if (toBoxed == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                ja.add(_convert(source.get(i),
                        Object.class, Object.class, null, deepCopy, cps, context));
            }
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(toBoxed)) {
            PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(toBoxed);
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

        if (toBoxed.isArray()) {
            Class<?> valueType = toBoxed.getComponentType();
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

        if (Set.class.isAssignableFrom(toBoxed)) {
            Type valueType = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> valueRaw = Types.rawBox(valueType);
            OneOfInfo valueOneOf =
                    TypeRegistry.registerTypeInfo(valueRaw).oneOfInfo;

            int size = source.size();
            Set<Object> set =
                    TypeRegistry.newSetContainer(toBoxed, size, false);

            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                set.add(_convert(source.get(i),
                        valueType, valueRaw, valueOneOf, deepCopy, cps, context));
            }
            return set;
        }

        throw new BindingException(
                "cannot convert " + sourceName + " to '" +
                        toBoxed.getName() + "'", ps);
    }


    // POJO -> Map/JsonObject/JOJO/POJO
    private static Object _convertFromPojo(Object node,
                                           PojoInfo sourceInfo,
                                           Class<?> toBoxed,
                                           Type type,
                                           boolean deepCopy,
                                           PathSegment ps,
                                           RuntimeContext context) {
        if (Map.class.isAssignableFrom(toBoxed)) {
            Map<String, Object> map =
                    TypeRegistry.newMapContainer(
                            toBoxed, sourceInfo.readablePropertyCount, false);

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

        if (toBoxed == JsonObject.class) {
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

        PojoInfo targetInfo = TypeRegistry.registerTypeInfo(toBoxed).pojoInfo;
        if (targetInfo != null && !targetInfo.isJajo) {
            return _convertPojoFromProperties(
                    node, sourceInfo, type, toBoxed,
                    targetInfo, deepCopy, ps, context);
        }

        throw new BindingException(
                "cannot convert POJO to '" + toBoxed.getName() + "'", ps);
    }

    private static Object _convertPojoFromProperties(Object source,
                                                     PojoInfo sourceInfo,
                                                     Type type,
                                                     Class<?> toBoxed,
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
                        Types.resolveMemberType(type, toBoxed, ci.argTypes[argIdx]);
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
                        ? Types.resolveMemberType(type, toBoxed, fi.type)
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
