package org.sjf4j.binding.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.NodeBinder;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.CreatorState;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.Nodes;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.OneOfInfo;
import org.sjf4j.node.FieldInfo;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.Types;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Strings;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


/**
 * Node binding implementation backed by core node utilities.
 *
 */
public final class SimpleNodeBinder implements NodeBinder {

    private final StreamingContext context;

    /**
     * Creates a binding with the default conversion pipeline.
     */
    public SimpleNodeBinder() {
        this(StreamingContext.EMPTY);
    }

    /**
     * Creates a binding with the supplied streaming configuration.
     */
    public SimpleNodeBinder(StreamingContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }


    /**
     * Converts node into target type.
     */
    @Override
    public Object readNode(Object node, Type type, boolean deepCopy) {
        try {
            Class<?> rawBox = Types.rawBox(type);
            return _readNode(node, type, rawBox, null, deepCopy, PathSegment.Root.INSTANCE);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to read node from '" + Types.name(node) + "' to '" + type + "'", e);
        }
    }

    /**
     * Internal read conversion with binding path support.
     */
    @SuppressWarnings("unchecked")
    private Object _readNode(Object node, Type type, Class<?> rawClazz,
                             OneOfInfo anyOfInfo, boolean deepCopy, PathSegment ps) {
        try {
            if (node == null) {
                if (rawClazz == Optional.class) return Optional.empty();
                return null;
            }

            if (anyOfInfo != null) {
                return _readOneOf(node, rawClazz, anyOfInfo, deepCopy, ps);
            }

            if (rawClazz == Object.class) {
                return deepCopy ? _deepNode(node, type, ps) : node;
            }
            // Raw-compatible values can only be returned as-is when the target has no
            // generic structure to honor. Parameterized containers/POJOs still need a
            // shallow/deep traversal so their declared element/member types are bound.
            if (rawClazz.isInstance(node) && !Types.hasGenericStructure(type)) {
                if (!deepCopy) return node;

                TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
                if (ti.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(rawClazz);
                    ValueInfo vci = ti.getNodeValueInfo(valueFormat);
                    if (vci != null) return vci.valueCopy(node);
                }
                return _deepNode(node, type, ps);
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(rawClazz);
            anyOfInfo = ti.oneOfInfo;
            if (anyOfInfo != null) {
                return _readOneOf(node, rawClazz, anyOfInfo, deepCopy, ps);
            }
            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(rawClazz);
                ValueInfo vci = ti.getNodeValueInfo(valueFormat);
                if (vci != null) {
                    return rawClazz.isInstance(node) ? vci.valueCopy(node) : vci.rawToValue(node);
                }
            }

            if (node instanceof String) {
                return _readString(node.toString(), rawClazz, ps);
            }
            if (node instanceof Number) {
                if (Number.class.isAssignableFrom(rawClazz)) {
                    return Numbers.to((Number) node, rawClazz);
                }
                throw new BindingException("cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);
            }
            if (node instanceof Boolean) {
                if (rawClazz == Boolean.class) {
                    return node;
                }
                throw new BindingException("cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);
            }

            if (node instanceof Map) {
                return _readFromMap((Map<String, Object>) node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof List) {
                return _readFromList((List<Object>) node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof JsonObject) {
                return _readFromJsonObject((JsonObject) node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof JsonArray) {
                return _readFromJsonArray((JsonArray) node, rawClazz, type, deepCopy, ps);
            }

            if (node.getClass().isArray()) {
                return _readFromArray(node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof Set) {
                return _readFromSet((Set<Object>) node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof Character) {
                return _readString(node.toString(), rawClazz, ps);
            }
            if (node instanceof Enum) {
                return _readString(((Enum<?>) node).name(), rawClazz, ps);
            }

            PojoInfo oldPi = TypeRegistry.registerTypeInfo(node.getClass()).pojoInfo; // source pi
            if (oldPi != null) {
                return _readFromPojo(node, oldPi, rawClazz, type, deepCopy, ps);
            }

            throw new BindingException("cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps, e);
        }
    }


    private Object _readOneOf(Object node, Class<?> rawClazz,
                              OneOfInfo anyOfInfo, boolean deepCopy, PathSegment ps) {
        Class<?> targetClazz;

        if (anyOfInfo.hasDiscriminator) {
            if (anyOfInfo.scope != OneOf.Scope.CURRENT) {
                throw new BindingException("oneOf scope '" + anyOfInfo.scope + "' is not supported", ps);
            }

            if (!(node instanceof Map) && !(node instanceof JsonObject)) {
                if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("node must be a JSON object, when OneOf has a CURRENT discriminator", ps);
            }

            Object discriminatorValue;
            if (!anyOfInfo.key.isEmpty()) {
                discriminatorValue = Nodes.getInObject(node, anyOfInfo.key);
            } else if (!anyOfInfo.path.isEmpty()) {
                discriminatorValue = anyOfInfo.compiledPath.getNode(node);
            } else {
                discriminatorValue = null;
            }

            if (discriminatorValue == null) {
                if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("not found value for discriminator key '" + anyOfInfo.key + "'", ps);
            }

            targetClazz = anyOfInfo.matchByWhen(discriminatorValue);
            if (targetClazz == null) {
                if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("oneOf discriminator has no matching mapping: value='" + discriminatorValue + "'", ps);
            }
        } else {
            JsonType jsonType = JsonType.of(node);
            targetClazz = anyOfInfo.matchByJsonType(jsonType);
            if (targetClazz == null) {
                if (anyOfInfo.onNoMatch == OneOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("oneOf mapping does not support jsonType=" + jsonType +
                        " for type '" + rawClazz.getName() + "'", ps);
            }
        }
        return _readNode(node, targetClazz, Types.rawBox(targetClazz), null, deepCopy, ps);
    }

    // Object -> deep copied Object
    /**
     * Internal deep copy with binding path support.
     */
    @SuppressWarnings("unchecked")
    private Object _deepNode(Object node, Type type, PathSegment ps) {
        try {
            if (node == null) return null;

            Class<?> targetRaw = type == null ? Object.class : Types.rawBox(type);
            if (targetRaw != Object.class && !targetRaw.isInstance(node)) {
                return _readNode(node, type, targetRaw, null, true, ps);
            }

            TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
            if (ti.isNodeValue()) {
                String valueFormat = context.defaultValueFormat(node.getClass());
                ValueInfo vci = ti.getNodeValueInfo(valueFormat);
                if (vci != null) return vci.valueCopy(node);
            }

            if (node instanceof String || node instanceof Number || node instanceof Boolean) {
                return node;
            }

            Class<?> nodeClazz = node.getClass();
            if (node instanceof Map) {
                Map<String, Object> srcMap = (Map<String, Object>) node;
                Map<String, Object> newMap = TypeRegistry.newMapContainer(nodeClazz, srcMap.size(), true);
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                srcMap.forEach((k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, k);
                    newMap.put(k, _deepNode(v, valueType, cps));
                });
                return newMap;
            }

            if (node instanceof List) {
                List<Object> srcList = (List<Object>) node;
                List<Object> newList = TypeRegistry.newListContainer(nodeClazz, srcList.size(), true);
                Type elemType = Types.resolveTypeArgument(type, List.class, 0);
                for (int i = 0; i < srcList.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newList.add(_deepNode(srcList.get(i), elemType, cps));
                }
                return newList;
            }

            if (node.getClass() == JsonObject.class) {
                JsonObject srcJo = (JsonObject) node;
                JsonObject newJo = new JsonObject();
                srcJo.forEach((k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, k);
                    newJo.put(k, _deepNode(v, Object.class, cps));
                });
                return newJo;
            }

            if (node instanceof JsonObject) {
                JsonObject srcJo = (JsonObject) node;
                PojoInfo pojoInfo = TypeRegistry.registerPojoOrElseThrow(nodeClazz);
                CreatorInfo ci = pojoInfo.creatorInfo;
                TypeRegistry.PojoCreationSession session = new TypeRegistry.PojoCreationSession(pojoInfo.creatorInfo, srcJo.size());

                for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                    String key = entry.getKey();
                    int argIdx = ci.getArgIndexOrAlias(key);
                    if (argIdx >= 0) {
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type argType = Types.resolveMemberType(type, targetRaw, ci.argTypes[argIdx]);
                        Object vv = _deepNode(entry.getValue(), argType, cps);
                        session.acceptCtorArg(argIdx, vv);
                        continue;
                    }

                    FieldInfo fi = pojoInfo.aliasProperties != null
                            ? pojoInfo.aliasProperties.get(key)
                            : pojoInfo.properties.get(key);
                    if (fi != null) {
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type fieldType = fi.genericDependent ? Types.resolveMemberType(type, targetRaw, fi.type) : fi.type;
                        Object vv = _deepNode(entry.getValue(), fieldType, cps);
                        session.acceptProperty(fi, vv);
                        continue;
                    }

                    PathSegment cps = new PathSegment.Name(ps, key);
                    Object vv = _deepNode(entry.getValue(), Object.class, cps);
                    session.acceptDynamic(key, vv);
                }

                return session.finish();
            }
            if (node instanceof JsonArray) {
                JsonArray srcJa = (JsonArray) node;
                JsonArray newJa = nodeClazz == JsonArray.class ? new JsonArray()
                        : (JsonArray) TypeRegistry.registerPojoOrElseThrow(nodeClazz).creatorInfo.forceNewPojo();
                Type elemType = Types.resolveTypeArgument(type, List.class, 0);
                for (int i = 0; i < srcJa.size(); i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    newJa.add(_deepNode(srcJa.getNode(i), elemType, cps));
                }
                return newJa;
            }
            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Object newArr = Array.newInstance(nodeClazz.getComponentType(), len);
                Type compType = nodeClazz.getComponentType();
                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Object vv = _deepNode(Array.get(node, i), compType, cps);
                    Array.set(newArr, i, vv);
                }
                return newArr;
            }
            if (node instanceof Set) {
                Set<Object> srcSet = (Set<Object>) node;
                Set<Object> newSet = TypeRegistry.newSetContainer(nodeClazz, srcSet.size(), true);
                Type elemType = Types.resolveTypeArgument(type, Set.class, 0);
                int i = 0;
                for (Object v : srcSet) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    newSet.add(_deepNode(v, elemType, cps));
                }
                return newSet;
            }

            PojoInfo pi = TypeRegistry.registerTypeInfo(nodeClazz).pojoInfo;
            if (pi != null) {
                CreatorInfo ci = pi.creatorInfo;
                TypeRegistry.PojoCreationSession session = new TypeRegistry.PojoCreationSession(pi.creatorInfo, pi.readablePropertyCount);

                for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()) {
                    String key = entry.getKey();
                    FieldInfo fi = entry.getValue();

                    int argIdx = ci.getArgIndexOrAlias(key);
                    if (argIdx >= 0) {
                        Object v = fi.invokeGetter(node);
                        PathSegment cps = new PathSegment.Name(ps, key);
                        Type argType = Types.resolveMemberType(type, targetRaw, ci.argTypes[argIdx]);
                        session.acceptCtorArg(argIdx, _deepNode(v, argType, cps));
                        continue;
                    }

                    Object v = fi.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, key);
                    Type fieldType = fi.genericDependent ? Types.resolveMemberType(type, targetRaw, fi.type) : fi.type;
                    Object vv = _deepNode(v, fieldType, cps);
                    session.acceptProperty(fi, vv);
                }
                return session.finish();
            }

            return node;

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("failed to deep copy node '" + Types.name(node) + "'", ps, e);
        }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object _readString(String s, Class<?> rawClazz, PathSegment ps) {
        if (rawClazz == String.class) {
            return s;
        }
        if (rawClazz == Character.class) {
            return !s.isEmpty() ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new BindingException("cannot convert String '" + Strings.truncate(s) + "' to '" +
                rawClazz.getName() + "'", ps);
    }

    // Map -> Map/JsonObject/JOJO/POJO
    private Object _readFromMap(Map<String, Object> oldMap, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        if (!deepCopy && rawClazz == JsonObject.class) {
            return new JsonObject(oldMap);
        }
        return _readFromObjectSource(new ObjectSource() {
            @Override public Iterable<Map.Entry<String, Object>> entries() { return oldMap.entrySet(); }
            @Override public int size() { return oldMap.size(); }
        }, "Map", rawClazz, type, deepCopy, ps);
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private Object _readFromJsonObject(JsonObject oldJo, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        return _readFromObjectSource(new ObjectSource() {
            @Override public Iterable<Map.Entry<String, Object>> entries() { return oldJo.entrySet(); }
            @Override public int size() { return oldJo.size(); }
        }, "JsonObject", rawClazz, type, deepCopy, ps);
    }

    private Object _readFromObjectSource(ObjectSource source,
                                         String sourceName,
                                         Class<?> rawClazz,
                                         Type type,
                                         boolean deepCopy,
                                         PathSegment ps) {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map = TypeRegistry.newMapContainer(rawClazz, source.size(), false);
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object vv = _readNode(entry.getValue(), vt, vc, va, deepCopy, cps);
                map.put(entry.getKey(), vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                Object vv = _readNode(entry.getValue(), Object.class, Object.class, null, deepCopy, cps);
                jo.put(entry.getKey(), vv);
            }
            return jo;
        }

        PojoInfo pi = TypeRegistry.registerTypeInfo(rawClazz).pojoInfo;
        if (pi != null && !pi.isJajo) {
            return _readPojoFromEntries(source.entries(), type, rawClazz, pi, deepCopy, ps);
        }
        throw new BindingException("cannot convert " + sourceName + " to '" + rawClazz.getName() + "'", ps);
    }

    private Object _readPojoFromEntries(Iterable<Map.Entry<String, Object>> entries,
                                        Type type, Class<?> rawClazz, PojoInfo pi,
                                        boolean deepCopy, PathSegment ps) {
        CreatorInfo ci = pi.creatorInfo;
        CreatorState state = new CreatorState(ci);

        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(type, rawClazz, ci.argTypes[argIdx]);
                PathSegment cps = new PathSegment.Name(ps, key);
                Class<?> argRaw = Types.rawBox(argType);

                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                ValueInfo argVci = ci.argValueCodecs[argIdx];
                if (argVci == null && ti.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argVci = ti.getNodeValueInfo(valueFormat);
                }
                if (ti.oneOfInfo == null && argVci != null) {
                    state.acceptCtorArg(argIdx, argRaw.isInstance(rawValue)
                            ? argVci.valueCopy(rawValue) : argVci.rawToValue(rawValue));
                } else {
                    state.acceptCtorArg(argIdx, _readNode(rawValue, argType, argRaw, ti.oneOfInfo, deepCopy, cps));
                }
                continue;
            }

            FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (fi != null) {
                if (!fi.hasSetter()) continue;

                PathSegment cps = new PathSegment.Name(ps, key);
                Type fieldType = fi.genericDependent ? Types.resolveMemberType(type, rawClazz, fi.type) : fi.type;
                Class<?> fieldRaw = fi.genericDependent ? Types.rawBox(fieldType) : fi.boxed;
                Object vv;
                if (fi.oneOfInfo == null && fi.valueInfo != null) {
                    vv = fieldRaw.isInstance(rawValue) ? fi.valueInfo.valueCopy(rawValue) :
                            fi.valueInfo.rawToValue(rawValue);
                } else {
                    vv = _readNode(rawValue, fieldType, fieldRaw, fi.oneOfInfo, deepCopy, cps);
                }

                if (state.isCreated()) {
                    fi.invokeSetter(state.pojo(), vv);
                } else {
                    state.bufferProperty(fi, vv);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                PathSegment cps = new PathSegment.Name(ps, key);
                Object vv = _readNode(rawValue, Object.class, Object.class, null, deepCopy, cps);
                state.acceptDynamic(key, vv);
            }
        }

        return state.finish();
    }

    // List -> List/JsonArray/JAJO/Array/Set
    private Object _readFromList(List<?> oldList, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        if (!deepCopy && rawClazz == JsonArray.class) {
            return new JsonArray(oldList);
        }
        return _readFromArraySource(new ArraySource() {
            @Override
            public int size() {
                return oldList.size();
            }

            @Override
            public Object get(int i) {
                return oldList.get(i);
            }
        }, "List", rawClazz, type, deepCopy, ps);
    }

    // JsonArray -> List/JsonArray/JAJO/Array/Set
    private Object _readFromJsonArray(JsonArray oldJa, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        return _readFromArraySource(new ArraySource() {
            @Override
            public int size() {
                return oldJa.size();
            }

            @Override
            public Object get(int i) {
                return oldJa.getNode(i);
            }
        }, "JsonArray", rawClazz, type, deepCopy, ps);
    }

    // Array -> List/JsonArray/JAJO/Array/Set
    private Object _readFromArray(Object node, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        return _readFromArraySource(new ArraySource() {
            @Override
            public int size() {
                return Array.getLength(node);
            }

            @Override
            public Object get(int i) {
                return Array.get(node, i);
            }
        }, "Array", rawClazz, type, deepCopy, ps);
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private Object _readFromSet(Set<Object> oldSet, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        int size = oldSet.size();
        if (List.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            List<Object> list = TypeRegistry.newListContainer(rawClazz, size, false);
            int i = 0;
            for (Object v : oldSet) {
                list.add(_readNode(v, vt, vc, va, deepCopy, new PathSegment.Index(ps, i++)));
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object v : oldSet) {
                ja.add(_readNode(v, Object.class, Object.class, null, deepCopy, new PathSegment.Index(ps, i++)));
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> vc = jajo.elementClass();
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            int i = 0;
            for (Object v : oldSet) {
                jajo.add(_readNode(v, vc, vc, va, deepCopy, new PathSegment.Index(ps, i++)));
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            Object array = Array.newInstance(vt, size);
            int i = 0;
            for (Object v : oldSet) {
                Array.set(array, i, _readNode(v, vt, vc, va, deepCopy, new PathSegment.Index(ps, i++)));
            }
            return array;
        }
        if (Set.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            Set<Object> set = TypeRegistry.newSetContainer(rawClazz, size, false);
            int i = 0;
            for (Object v : oldSet) {
                set.add(_readNode(v, vt, vc, va, deepCopy, new PathSegment.Index(ps, i++)));
            }
            return set;
        }
        throw new BindingException("cannot convert Set to '" + rawClazz.getName() + "'", ps);
    }

    private Object _readFromArraySource(ArraySource source, String sourceName, Class<?> rawClazz, Type type,
                                        boolean deepCopy, PathSegment ps) {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            int size = source.size();
            List<Object> list = TypeRegistry.newListContainer(rawClazz, size, false);
            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Object v = source.get(i);
                Object vv = _readNode(v, Object.class, Object.class, null, deepCopy, cps);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            Class<?> vc = jajo.elementClass();
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vc, vc, va, deepCopy, cps);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            Object array = Array.newInstance(vt, source.size());
            for (int i = 0, size = source.size(); i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                Array.set(array, i, vv);
            }
            return array;
        }
        if (Set.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            int size = source.size();
            Set<Object> set = TypeRegistry.newSetContainer(rawClazz, size, false);
            for (int i = 0; i < size; i++) {
                PathSegment cps = new PathSegment.Index(ps, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                set.add(vv);
            }
            return set;
        }
        throw new BindingException("cannot convert " + sourceName + " to '" + rawClazz.getName() + "'", ps);
    }

    // POJO -> Map/JsonObject/JOJO/POJO
    private Object _readFromPojo(Object node, PojoInfo oldPi, Class<?> rawClazz,
                                 Type type, boolean deepCopy, PathSegment ps) {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map = TypeRegistry.newMapContainer(rawClazz, oldPi.readablePropertyCount, false);
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> vc = Types.rawBox(vt);
            OneOfInfo va = TypeRegistry.registerTypeInfo(vc).oneOfInfo;
            for (Map.Entry<String, FieldInfo> entry : oldPi.readableProperties.entrySet()) {
                String key = entry.getKey();
                Object v = entry.getValue().invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, key);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                map.put(key, vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, FieldInfo> entry : oldPi.readableProperties.entrySet()) {
                String key = entry.getKey();
                Object v = entry.getValue().invokeGetter(node);
                PathSegment cps = new PathSegment.Name(ps, key);
                Object vv = _readNode(v, Object.class, Object.class, null, deepCopy, cps);
                jo.put(key, vv);
            }
            return jo;
        }

        PojoInfo pi = TypeRegistry.registerTypeInfo(rawClazz).pojoInfo;
        if (pi != null && !pi.isJajo) {
            return _readPojoFromProperties(node, oldPi, type, rawClazz, pi, deepCopy, ps);
        }
        throw new BindingException("cannot convert POJO to '" + rawClazz.getName() + "'", ps);
    }

    private Object _readPojoFromProperties(Object source, PojoInfo sourceInfo,
                                           Type type, Class<?> rawClazz, PojoInfo pi,
                                           boolean deepCopy, PathSegment ps) {
        Object[] sourceValues = new Object[sourceInfo.readablePropertyCount];
        int sourceIndex = 0;
        for (FieldInfo sourceField : sourceInfo.readableProperties.values()) {
            sourceValues[sourceIndex++] = sourceField.invokeGetter(source);
        }

        CreatorInfo ci = pi.creatorInfo;
        CreatorState state = new CreatorState(ci);
        sourceIndex = 0;
        for (Map.Entry<String, FieldInfo> entry : sourceInfo.readableProperties.entrySet()) {
            String key = entry.getKey();
            Object rawValue = sourceValues[sourceIndex++];

            int argIdx = ci.getArgIndexOrAlias(key);
            if (argIdx >= 0) {
                Type argType = Types.resolveMemberType(type, rawClazz, ci.argTypes[argIdx]);
                PathSegment cps = new PathSegment.Name(ps, key);
                Class<?> argRaw = Types.rawBox(argType);

                TypeInfo ti = TypeRegistry.registerTypeInfo(argRaw);
                ValueInfo argVci = ci.argValueCodecs[argIdx];
                if (argVci == null && ti.isNodeValue()) {
                    String valueFormat = context.defaultValueFormat(argRaw);
                    argVci = ti.getNodeValueInfo(valueFormat);
                }
                if (ti.oneOfInfo == null && argVci != null) {
                    state.acceptCtorArg(argIdx, argRaw.isInstance(rawValue)
                            ? argVci.valueCopy(rawValue) : argVci.rawToValue(rawValue));
                } else {
                    state.acceptCtorArg(argIdx, _readNode(rawValue, argType, argRaw, ti.oneOfInfo, deepCopy, cps));
                }
                continue;
            }

            FieldInfo fi = pi.aliasProperties != null ? pi.aliasProperties.get(key) : pi.properties.get(key);
            if (fi != null) {
                if (!fi.hasSetter()) continue;

                PathSegment cps = new PathSegment.Name(ps, key);
                Type fieldType = fi.genericDependent ? Types.resolveMemberType(type, rawClazz, fi.type) : fi.type;
                Class<?> fieldRaw = fi.genericDependent ? Types.rawBox(fieldType) : fi.boxed;
                Object vv;
                if (fi.oneOfInfo == null && fi.valueInfo != null) {
                    vv = fieldRaw.isInstance(rawValue)
                            ? fi.valueInfo.valueCopy(rawValue)
                            : fi.valueInfo.rawToValue(rawValue);
                } else {
                    vv = _readNode(rawValue, fieldType, fieldRaw, fi.oneOfInfo, deepCopy, cps);
                }

                if (state.isCreated()) {
                    fi.invokeSetter(state.pojo(), vv);
                } else {
                    state.bufferProperty(fi, vv);
                }
                continue;
            }

            if (pi.isJojo && pi.readDynamic) {
                PathSegment cps = new PathSegment.Name(ps, key);
                state.acceptDynamic(key, _readNode(rawValue, Object.class, Object.class, null, deepCopy, cps));
            }
        }
        return state.finish();
    }

    private interface ArraySource {
        int size();
        Object get(int i);
    }

    private interface ObjectSource {
        Iterable<Map.Entry<String, Object>> entries();
        int size();
    }

    /// Write

    /**
     * Converts runtime object into writable node tree.
     */
    @Override
    public Object writeNode(Object node) {
        return _writeNode(node, PathSegment.Root.INSTANCE);
    }

    /**
     * Internal write conversion with binding path support.
     */
    @SuppressWarnings("unchecked")
    public Object _writeNode(Object node, PathSegment ps) {
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
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, oldMap.size(), false);
                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    PathSegment cps = new PathSegment.Name(ps, entry.getKey());
                    Object vv = _writeNode(entry.getValue(), cps);
                    newMap.put(entry.getKey(), vv);
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> oldList = (List<Object>) node;
                List<Object> newList = new ArrayList<>(oldList.size());
                for (int i = 0, len = oldList.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Object vv = _writeNode(oldList.get(i), cps);
                    newList.add(vv);
                }
                return newList;
            }

            Class<?> rawClazz = node.getClass();
            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, jo.size(), false);
                if (rawClazz != JsonObject.class) {
                    PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(rawClazz);
                    if (!pi.writeDynamic) {
                        for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()) {
                            String key = entry.getKey();
                            PathSegment cps = new PathSegment.Name(ps, key);
                            Object vv = _writeNode(entry.getValue().invokeGetter(node), cps);
                            newMap.put(key, vv);
                        }
                        return newMap;
                    }
                }
                jo.forEach((k, v) -> {
                    PathSegment cps = new PathSegment.Name(ps, k);
                    Object vv = _writeNode(v, cps);
                    newMap.put(k, vv);
                });
                return newMap;
            }

            if (node instanceof JsonArray) {
                JsonArray ja = (JsonArray) node;
                List<Object> newList = new ArrayList<>(ja.size());
                for (int i = 0, len = ja.size(); i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Object vv = _writeNode(ja.getNode(i), cps);
                    newList.add(vv);
                }
                return newList;
            }

            if (rawClazz.isArray()) {
                int len = Array.getLength(node);
                List<Object> newList = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    PathSegment cps = new PathSegment.Index(ps, i);
                    Object vv = _writeNode(Array.get(node, i), cps);
                    newList.add(vv);
                }
                return newList;
            }

            if (node instanceof Set) {
                Set<Object> set = (Set<Object>) node;
                List<Object> newList = new ArrayList<>(set.size());
                int i = 0;
                for (Object v : set) {
                    PathSegment cps = new PathSegment.Index(ps, i++);
                    Object vv = _writeNode(v, cps);
                    newList.add(vv);
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
                ValueInfo vci = ti.getNodeValueInfo(valueFormat);
                if (vci != null) {
                    return vci.valueToRaw(node);
                }
            }

            PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                Map<String, Object> newMap = TypeRegistry.newMapContainer(LinkedHashMap.class, pi.readablePropertyCount, false);
                for (Map.Entry<String, FieldInfo> entry : pi.readableProperties.entrySet()) {
                    String key = entry.getKey();
                    FieldInfo fi = entry.getValue();
                    Object v = fi.invokeGetter(node);
                    PathSegment cps = new PathSegment.Name(ps, key);
                    Object vv = fi.valueInfo != null
                            ? fi.valueInfo.valueToRaw(v)
                            : _writeNode(v, cps);
                    newMap.put(key, vv);
                }
                return newMap;
            }

            throw new BindingException("unsupported node type '" + Types.name(node) + "'", ps);

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("cannot convert node from '" + Types.name(node) +
                    "' to raw (Map/List/String/Number/Boolean/null)", ps, e);
        }
    }

}
