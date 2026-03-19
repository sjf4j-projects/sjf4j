package org.sjf4j.facade.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Nodes;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Strings;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Node conversion facade backed by core node utilities.
 */
public class SimpleNodeFacade implements NodeFacade {

    /**
     * Converts node into target type.
     */
    @Override
    public Object readNode(Object node, Type type, boolean deepCopy) {
        try {
            Class<?> rawBox = Types.rawBox(type);
            return _readNode(node, type, rawBox, null, deepCopy,
                    Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
        } catch (Exception e) {
            throw new JsonException("Failed to read node from '" + Types.name(node) + "' to '" + type + "'", e);
        }
    }

    /**
     * Internal read conversion with binding path support.
     */
    @SuppressWarnings("unchecked")
    private Object _readNode(Object node, Type type, Class<?> rawClazz,
                             NodeRegistry.AnyOfInfo anyOfInfo, boolean deepCopy, PathSegment ps) {
        try {
            if (node == null) return null;

            if (anyOfInfo != null) {
                return _readAnyOf(node, rawClazz, anyOfInfo, deepCopy, ps);
            }

            if (rawClazz == Object.class || rawClazz.isInstance(node)) {
                return deepCopy ? _deepNode(node, type, ps) : node;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
            if (vci != null) {
                return rawClazz.isInstance(node) ? vci.valueCopy(node) : vci.rawToValue(node);
            }
            anyOfInfo = ti.anyOfInfo;
            if (anyOfInfo != null) {
                return _readAnyOf(node, rawClazz, anyOfInfo, deepCopy, ps);
            }

            if (node instanceof String || node instanceof Character) {
                return _readString(node.toString(), rawClazz, ps);
            }
            if (node instanceof Enum) {
                return _readString(((Enum<?>) node).name(), rawClazz, ps);
            }

            if (node instanceof Number) {
                if (Number.class.isAssignableFrom(rawClazz)) {
                    return Numbers.to((Number) node, rawClazz);
                }
                throw new BindingException("Cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);
            }

            if (node instanceof Boolean) {
                if (rawClazz == Boolean.class) {
                    return node;
                }
                throw new BindingException("Cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);
            }

            if (node instanceof Map) {
                return _readFromMap((Map<String, Object>) node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof JsonObject) {
                return _readFromJsonObject((JsonObject) node, rawClazz, type, deepCopy, ps);
            }

            if (node instanceof List) {
                return _readFromList((List<Object>) node, rawClazz, type, deepCopy, ps);
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

            NodeRegistry.PojoInfo oldPi = NodeRegistry.registerPojo(node.getClass()); // source pi
            if (oldPi != null) {
                return _readFromPojo(node, oldPi, rawClazz, type, deepCopy, ps);
            }

            throw new BindingException("Cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps, e);
        }
    }

    private Object _readAnyOf(Object node, Class<?> rawClazz,
                              NodeRegistry.AnyOfInfo anyOfInfo, boolean deepCopy, PathSegment ps) {
        Class<?> targetClazz;

        if (anyOfInfo.hasDiscriminator) {
            if (anyOfInfo.scope != AnyOf.Scope.SELF) {
                throw new BindingException("AnyOf scope '" + anyOfInfo.scope + "' is not supported", ps);
            }

            if (!(node instanceof Map) && !(node instanceof JsonObject)) {
                if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("Node must be a JSON object, when AnyOf has a SELF discriminator", ps);
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
                if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("Not found value for discriminator key '" + anyOfInfo.key + "'", ps);
            }

            targetClazz = anyOfInfo.resolveByWhen(discriminatorValue);
            if (targetClazz == null) {
                if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("AnyOf discriminator has no matching mapping: value='" + discriminatorValue + "'", ps);
            }
        } else {
            JsonType jsonType = JsonType.of(node);
            targetClazz = anyOfInfo.resolveByJsonType(jsonType);
            if (targetClazz == null) {
                if (anyOfInfo.onNoMatch == AnyOf.OnNoMatch.FAILBACK_NULL) return null;
                throw new BindingException("AnyOf mapping does not support jsonType=" + jsonType +
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

            Class<?> nodeClazz = node.getClass();
            if (node instanceof Map) {
                Map<String, Object> srcMap = (Map<String, Object>) node;
                Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(srcMap.size());
                Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
                srcMap.forEach((k, v) -> {
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, nodeClazz, k);
                    newMap.put(k, _deepNode(v, valueType, cps));
                });
                return newMap;
            }

            if (node.getClass() == JsonObject.class) {
                JsonObject srcJo = (JsonObject) node;
                JsonObject newJo = new JsonObject();
                srcJo.forEach((k, v) -> {
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, nodeClazz, k);
                    newJo.put(k, _deepNode(v, Object.class, cps));
                });
                return newJo;
            }

            if (node instanceof JsonObject) {
                JsonObject srcJo = (JsonObject) node;
                NodeRegistry.PojoInfo pojoInfo = NodeRegistry.registerPojoOrElseThrow(nodeClazz);
                NodeRegistry.CreatorInfo ci = pojoInfo.creatorInfo;
                NodeRegistry.PojoCreationSession session = pojoInfo.newCreationSession(srcJo.size());

                for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                    String key = entry.getKey();
                    int argIdx = session.resolveArgIndex(key);
                    if (argIdx >= 0) {
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, nodeClazz, key);
                        Object vv = _deepNode(entry.getValue(), ci.argTypes[argIdx], cps);
                        session.acceptResolvedJsonEntry(argIdx, key, vv);
                        continue;
                    }

                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, nodeClazz, key);
                    Object vv = _deepNode(entry.getValue(), Object.class, cps);
                    session.acceptResolvedJsonEntry(argIdx, key, vv);
                }

                JsonObject newJo = session.finishJsonObject();
                return newJo;
            }

            if (node instanceof List) {
                List<Object> srcList = (List<Object>) node;
                List<Object> newList = Sjf4jConfig.global().listSupplier.create(srcList.size());
                Type elemType = Types.resolveTypeArgument(type, List.class, 0);
                for (int i = 0; i < srcList.size(); i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, nodeClazz, i);
                    newList.add(_deepNode(srcList.get(i), elemType, cps));
                }
                return newList;
            }
            if (node instanceof JsonArray) {
                JsonArray srcJa = (JsonArray) node;
                JsonArray newJa = nodeClazz == JsonArray.class ? new JsonArray()
                        : (JsonArray) NodeRegistry.registerPojoOrElseThrow(nodeClazz).creatorInfo.forceNewPojo();
                Type elemType = Types.resolveTypeArgument(type, List.class, 0);
                for (int i = 0; i < srcJa.size(); i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, nodeClazz, i);
                    newJa.add(_deepNode(srcJa.getNode(i), elemType, cps));
                }
                return newJa;
            }
            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Object newArr = Array.newInstance(nodeClazz.getComponentType(), len);
                Type compType = nodeClazz.getComponentType();
                for (int i = 0; i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, nodeClazz, i);
                    Object vv = _deepNode(Array.get(node, i), compType, cps);
                    Array.set(newArr, i, vv);
                }
                return newArr;
            }
            if (node instanceof Set) {
                Set<Object> srcSet = (Set<Object>) node;
                Set<Object> newSet = Sjf4jConfig.global().setSupplier.create(srcSet.size());
                Type elemType = Types.resolveTypeArgument(type, Set.class, 0);
                int i = 0;
                for (Object v : srcSet) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, nodeClazz, i++);
                    newSet.add(_deepNode(v, elemType, cps));
                }
                return newSet;
            }

            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(nodeClazz);
            if (pi != null) {
                NodeRegistry.CreatorInfo ci = pi.creatorInfo;
                NodeRegistry.PojoCreationSession session = pi.newCreationSession(pi.fieldCount);

                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    String key = entry.getKey();
                    NodeRegistry.FieldInfo fi = entry.getValue();

                    int argIdx = session.resolveArgIndex(key);
                    if (argIdx >= 0) {
                        Object v = fi.invokeGetter(node);
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, nodeClazz, key);
                        session.acceptResolvedField(argIdx, _deepNode(v, ci.argTypes[argIdx], cps), fi);
                        continue;
                    }

                    Object v = fi.invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, nodeClazz, key);
                    Object vv = _deepNode(v, fi.type, cps);
                    session.acceptResolvedField(argIdx, vv, fi);
                }
                Object pojo = session.finishField();
                return pojo;
            }

            return node;

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to deep copy node '" + Types.name(node) + "'", ps, e);
        }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object _readString(String s, Class<?> rawClazz, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == String.class) {
            return s;
        }
        if (rawClazz == Character.class) {
            return s.length() > 0 ? s.charAt(0) : null;
        }
        if (rawClazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new BindingException("Cannot convert String '" + Strings.truncate(s) + "' to '" +
                rawClazz.getName() + "'", ps);
    }

    private interface IndexedSource {
        int size();
        Object get(int i);
    }

    private interface EntrySource {
        int size();
        Iterable<Map.Entry<String, Object>> entries();
    }

    // Map -> Map/JsonObject/JOJO/POJO
    private Object _readFromMap(Map<String, Object> oldMap, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        return _readFromObjectEntries(new EntrySource() {
            @Override
            public int size() {
                return oldMap.size();
            }

            @Override
            public Iterable<Map.Entry<String, Object>> entries() {
                return oldMap.entrySet();
            }
        }, "Map", rawClazz, type, deepCopy, ps);
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private Object _readFromJsonObject(JsonObject oldJo, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        return _readFromObjectEntries(new EntrySource() {
            @Override
            public int size() {
                return oldJo.size();
            }

            @Override
            public Iterable<Map.Entry<String, Object>> entries() {
                return oldJo.entrySet();
            }
        }, "JsonObject", rawClazz, type, deepCopy, ps);
    }

    private Object _readFromObjectEntries(EntrySource source,
                                          String sourceName,
                                          Class<?> rawClazz,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(source.size());
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> vc = Types.rawBox(vt);
            NodeRegistry.AnyOfInfo va = NodeRegistry.registerTypeInfo(vc).anyOfInfo;
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, entry.getKey());
                Object vv = _readNode(entry.getValue(), vt, vc, va, deepCopy, cps);
                map.put(entry.getKey(), vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : source.entries()) {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, entry.getKey());
                Object vv = _readNode(entry.getValue(), Object.class, Object.class, null, deepCopy, cps);
                jo.put(entry.getKey(), vv);
            }
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo) {
            return _readPojoFromObjectEntries(source.entries(), rawClazz, pi, deepCopy, ps);
        }
        throw new BindingException("Cannot convert " + sourceName + " to '" + rawClazz.getName() + "'", ps);
    }

    private Object _readPojoFromObjectEntries(Iterable<Map.Entry<String, Object>> entries,
                                              Class<?> rawClazz,
                                              NodeRegistry.PojoInfo pi,
                                              boolean deepCopy,
                                              PathSegment ps) {
        NodeRegistry.CreatorInfo ci = pi.creatorInfo;
        Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
        Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
        int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
        int pendingSize = 0;
        NodeRegistry.FieldInfo[] pendingFields = null;
        Object[] pendingValues = null;
        Map<String, Object> dynamicMap = null;

        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();

            int argIdx = -1;
            if (pojo == null) {
                argIdx = ci.getArgIndex(key);
                if (argIdx < 0 && ci.aliasMap != null) {
                    String origin = ci.aliasMap.get(key); // alias -> origin
                    if (origin != null) {
                        argIdx = ci.getArgIndex(origin);
                    }
                }
            }
            if (argIdx >= 0) {
                assert args != null;
                Type argType = ci.argTypes[argIdx];
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Class<?> argRaw = Types.rawBox(argType);
                NodeRegistry.AnyOfInfo argAnyOf = NodeRegistry.registerTypeInfo(argRaw).anyOfInfo;
                args[argIdx] = _readNode(rawValue, argType, argRaw, argAnyOf, deepCopy, cps);
                remainingArgs--;
                if (remainingArgs == 0) {
                    pojo = ci.newPojoWithArgs(args);
                    for (int j = 0; j < pendingSize; j++) {
                        pendingFields[j].invokeSetterIfPresent(pojo, pendingValues[j]);
                    }
                    pendingSize = 0;
                }
                continue;
            }

            NodeRegistry.FieldInfo fi = pi.aliasFields != null ? pi.aliasFields.get(key) : pi.fields.get(key);
            if (fi != null) {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object vv = _readNode(rawValue, fi.type, fi.rawClazz, fi.anyOfInfo, deepCopy, cps);
                if (pojo != null) {
                    fi.invokeSetterIfPresent(pojo, vv);
                } else {
                    if (pendingFields == null) {
                        int cap = pi.fieldCount;
                        pendingFields = new NodeRegistry.FieldInfo[cap];
                        pendingValues = new Object[cap];
                    }
                    pendingFields[pendingSize] = fi;
                    pendingValues[pendingSize] = vv;
                    pendingSize++;
                }
                continue;
            }

            if (pi.isJojo) {
                if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object vv = _readNode(rawValue, Object.class, Object.class, null, deepCopy, cps);
                dynamicMap.put(key, vv);
            }
        }

        if (pojo == null) {
            pojo = ci.newPojoWithArgs(args);
            for (int j = 0; j < pendingSize; j++) {
                pendingFields[j].invokeSetterIfPresent(pojo, pendingValues[j]);
            }
        }
        if (pi.isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
        return pojo;
    }

    // List -> List/JsonArray/JAJO/Array/Set
    private Object _readFromList(List<?> oldList, Class<?> rawClazz, Type type, boolean deepCopy, PathSegment ps) {
        return _readFromIndexedSource(new IndexedSource() {
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
        return _readFromIndexedSource(new IndexedSource() {
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
        return _readFromIndexedSource(new IndexedSource() {
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
        List<Object> values = new ArrayList<>(oldSet.size());
        for (Object v : oldSet) values.add(v);
        return _readFromIndexedSource(new IndexedSource() {
            @Override
            public int size() {
                return values.size();
            }

            @Override
            public Object get(int i) {
                return values.get(i);
            }
        }, "Set", rawClazz, type, deepCopy, ps);
    }

    private Object _readFromIndexedSource(IndexedSource source,
                                          String sourceName,
                                          Class<?> rawClazz,
                                          Type type,
                                          boolean deepCopy,
                                          PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            Class<?> vc = Types.rawBox(vt);
            NodeRegistry.AnyOfInfo va = NodeRegistry.registerTypeInfo(vc).anyOfInfo;
            List<Object> list = Sjf4jConfig.global().listSupplier.create(source.size());
            for (int i = 0; i < source.size(); i++) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0; i < source.size(); i++) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object v = source.get(i);
                Object vv = _readNode(v, Object.class, Object.class, null, deepCopy, cps);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            for (int i = 0; i < source.size(); i++) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object v = source.get(i);
                Object vv = _readNode(v, Object.class, Object.class, null, deepCopy, cps);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Class<?> vc = Types.rawBox(vt);
            NodeRegistry.AnyOfInfo va = NodeRegistry.registerTypeInfo(vc).anyOfInfo;
            Object array = Array.newInstance(vt, source.size());
            for (int i = 0; i < source.size(); i++) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                Array.set(array, i, vv);
            }
            return array;
        }
        if (rawClazz == Set.class) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Class<?> vc = Types.rawBox(vt);
            NodeRegistry.AnyOfInfo va = NodeRegistry.registerTypeInfo(vc).anyOfInfo;
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(source.size());
            for (int i = 0; i < source.size(); i++) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object v = source.get(i);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                set.add(vv);
            }
            return set;
        }
        throw new BindingException("Cannot convert " + sourceName + " to '" + rawClazz.getName() + "'", ps);
    }

    // POJO -> Map/JsonObject/JOJO/POJO
    private Object _readFromPojo(Object node, NodeRegistry.PojoInfo oldPi, Class<?> rawClazz,
                                 Type type, boolean deepCopy, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldPi.fieldCount);
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            Class<?> vc = Types.rawBox(vt);
            NodeRegistry.AnyOfInfo va = NodeRegistry.registerTypeInfo(vc).anyOfInfo;
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.fields.entrySet()) {
                String key = entry.getKey();
                Object v = entry.getValue().invokeGetter(node);
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object vv = _readNode(v, vt, vc, va, deepCopy, cps);
                map.put(key, vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.fields.entrySet()) {
                String key = entry.getKey();
                Object v = entry.getValue().invokeGetter(node);
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object vv = _readNode(v, Object.class, Object.class, null, deepCopy, cps);
                jo.put(key, vv);
            }
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo) {
            Map<String, Object> sourceValues = Sjf4jConfig.global().mapSupplier.create(oldPi.fieldCount);
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.fields.entrySet()) {
                sourceValues.put(entry.getKey(), entry.getValue().invokeGetter(node));
            }
            return _readPojoFromObjectEntries(sourceValues.entrySet(), rawClazz, pi, deepCopy, ps);
        }
        throw new BindingException("Cannot convert POJO to '" + rawClazz.getName() + "'", ps);
    }


    /// Write

    /**
     * Converts runtime object into writable node tree.
     */
    @Override
    public Object writeNode(Object node) {
        return _writeNode(node,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
    }

    /**
     * Internal write conversion with binding path support.
     */
    @SuppressWarnings("unchecked")
    public Object _writeNode(Object node, PathSegment ps) {
        try {
            if (node == null) return null;

            Class<?> rawClazz = node.getClass();

            if (node instanceof String || node instanceof Character) {
                return node.toString();
            }
            if (node instanceof Enum) {
                return ((Enum<?>) node).name();
            }

            if (node instanceof Number) {
                return node;
            }

            if (node instanceof Boolean) {
                return node;
            }

            if (node instanceof Map) {
                Map<String, Object> oldMap = (Map<String, Object>) node;
                Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(oldMap.size());
                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, entry.getKey());
                    Object vv = _writeNode(entry.getValue(), cps);
                    newMap.put(entry.getKey(), vv);
                }
                return newMap;
            }

            if (node instanceof List) {
                List<Object> oldList = (List<Object>) node;
                List<Object> newList = Sjf4jConfig.global().listSupplier.create(oldList.size());
                for (int i = 0, len = oldList.size(); i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    Object vv = _writeNode(oldList.get(i), cps);
                    newList.add(vv);
                }
                return newList;
            }

            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;
                Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(jo.size());
                jo.forEach((k, v) -> {
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, k);
                    Object vv = _writeNode(v, cps);
                    newMap.put(k, vv);
                });
                return newMap;
            }

            if (node instanceof JsonArray) {
                JsonArray ja = (JsonArray) node;
                List<Object> newList = Sjf4jConfig.global().listSupplier.create(ja.size());
                for (int i = 0, len = ja.size(); i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    Object vv = _writeNode(ja.getNode(i), cps);
                    newList.add(vv);
                }
                return newList;
            }

            if (node.getClass().isArray()) {
                int len = Array.getLength(node);
                List<Object> newList = Sjf4jConfig.global().listSupplier.create(len);
                for (int i = 0; i < len; i++) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                    Object vv = _writeNode(Array.get(node, i), cps);
                    newList.add(vv);
                }
                return newList;
            }

            if (node instanceof Set) {
                Set<Object> set = (Set<Object>) node;
                List<Object> newList = Sjf4jConfig.global().listSupplier.create(set.size());
                int i = 0;
                for (Object v : set) {
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                    Object vv = _writeNode(v, cps);
                    newList.add(vv);
                }
                return newList;
            }

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
            if (vci != null) {
                return vci.valueToRaw(node);
            }

            NodeRegistry.PojoInfo pi = ti.pojoInfo;
            if (pi != null) {
                Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(pi.fieldCount);
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    String key = entry.getKey();
                    Object v = entry.getValue().invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _writeNode(v, cps);
                    newMap.put(key, vv);
                }
                return newMap;
            }

            throw new BindingException("Unsupported node type '" + Types.name(node) + "'", ps);

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Cannot convert node from '" + Types.name(node) +
                    "' to raw (Map/List/String/Number/Boolean/null)", ps, e);
        }
    }

}
