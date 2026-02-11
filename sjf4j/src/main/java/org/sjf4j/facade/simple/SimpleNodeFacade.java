package org.sjf4j.facade.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Strings;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class SimpleNodeFacade implements NodeFacade {

    @Override
    public Object readNode(Object node, Type type) {
        try {
            return _readNode(node, type,
                    Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
        } catch (Exception e) {
            throw new JsonException("Failed to read node from '" + Types.name(node) + "' to '" + type + "'", e);
        }
    }

    @Override
    public Object deepNode(Object node) {
        try {
            return _deepNode(node,
                    Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
        } catch (Exception e) {
            throw new JsonException("Failed to deep copy node '" + Types.name(node) + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Object _readNode(Object node, Type type, PathSegment ps) {
        try {
            if (node == null) return null;
            Class<?> rawClazz = Types.rawBox(type);

            // Object.class means deep copy
            if (rawClazz == Object.class) return _deepNode(node, ps);

            NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawClazz);
            NodeRegistry.ValueCodecInfo vci = ti.valueCodecInfo;
            if (vci != null) {
                return rawClazz.isInstance(node) ? vci.copy(node) : vci.decode(node);
            }

            if (node instanceof CharSequence || node instanceof Character) {
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
                return _readFromMap((Map<String, Object>) node, rawClazz, type, ps);
            }

            if (node instanceof JsonObject) {
                return _readFromJsonObject((JsonObject) node, rawClazz, type, ps);
            }

            if (node instanceof List) {
                return _readFromList((List<Object>) node, rawClazz, type, ps);
            }

            if (node instanceof JsonArray) {
                return _readFromJsonArray((JsonArray) node, rawClazz, type, ps);
            }

            if (node.getClass().isArray()) {
                return _readFromArray(node, rawClazz, type, ps);
            }

            if (node instanceof Set) {
                return _readFromSet((Set<Object>) node, rawClazz, type, ps);
            }

            NodeRegistry.PojoInfo oldPi = NodeRegistry.registerPojo(node.getClass()); // source pi
            if (oldPi != null) {
                return _readFromPojo(node, oldPi, rawClazz, type, ps);
            }

            throw new BindingException("Cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps);

        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Cannot convert node from '" + Types.name(node) + "' to '" + type + "'", ps, e);
        }
    }

    // Object -> deep copied Object
    @SuppressWarnings("unchecked")
    public Object _deepNode(Object node, PathSegment ps) {
        try {
            if (node == null) return null;

            Class<?> nodeClazz = node.getClass();
            if (node instanceof Map) {
                Map<String, Object> srcMap = (Map<String, Object>) node;
                Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(srcMap.size());
                srcMap.forEach((k, v) -> newMap.put(k, deepNode(v)));
                return newMap;
            }

            if (node.getClass() == JsonObject.class) {
                JsonObject srcJo = (JsonObject) node;
                JsonObject newJo = new JsonObject();
                srcJo.forEach((k, v) -> newJo.put(k, deepNode(v)));
                return newJo;
            }

            if (node instanceof JsonObject) {
                JsonObject srcJo = (JsonObject) node;
                NodeRegistry.CreatorInfo ci = NodeRegistry.registerPojoOrElseThrow(nodeClazz).creatorInfo;
                JsonObject newJo = (JsonObject) (ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs());
                Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
                int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
                int pendingSize = 0;
                String[] pendingKeys = null;
                Object[] pendingValues = null;

                for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                    String key = entry.getKey();
                    int argIdx = -1;
                    if (newJo == null) {
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
                        args[argIdx] = entry.getValue();
                        remainingArgs--;
                        if (remainingArgs == 0) {
                            newJo = (JsonObject) ci.newPojoWithArgs(args);
                            for (int i = 0; i < pendingSize; i++) {
                                newJo.put(pendingKeys[i], pendingValues[i]);
                            }
                        }
                        continue;
                    }

                    Object vv = deepNode(entry.getValue());
                    if (newJo != null) {
                        newJo.put(key, vv);
                    } else {
                        if (pendingKeys == null) {
                            int cap = srcJo.size();
                            pendingKeys = new String[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingKeys[pendingSize] = key;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                }

                if (newJo == null) {
                    newJo = (JsonObject) ci.newPojoWithArgs(args);
                    for (int i = 0; i < pendingSize; i++) {
                        newJo.put(pendingKeys[i], pendingValues[i]);
                    }
                }
                return newJo;
            }

            if (node instanceof List) {
                List<Object> srcList = (List<Object>) node;
                List<Object> newList = Sjf4jConfig.global().listSupplier.create(srcList.size());
                srcList.forEach(v -> newList.add(deepNode(v)));
                return newList;
            }
            if (node instanceof JsonArray) {
                JsonArray srcJa = (JsonArray) node;
                JsonArray newJa = nodeClazz == JsonArray.class ? new JsonArray()
                        : (JsonArray) NodeRegistry.registerPojoOrElseThrow(nodeClazz).creatorInfo.forceNewPojo();
                srcJa.forEach(v -> newJa.add(deepNode(v)));
                return newJa;
            }
            if (nodeClazz.isArray()) {
                int len = Array.getLength(node);
                Object newArr = Array.newInstance(nodeClazz.getComponentType(), len);
                for (int i = 0; i < len; i++) {
                    Object vv = deepNode(Array.get(node, i));
                    Array.set(newArr, i, vv);
                }
                return newArr;
            }
            if (node instanceof Set) {
                Set<Object> srcSet = (Set<Object>) node;
                Set<Object> newSet = Sjf4jConfig.global().setSupplier.create(srcSet.size());
                srcSet.forEach(v -> newSet.add(deepNode(v)));
                return newSet;
            }

            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(nodeClazz);
            if (pi != null) {
                NodeRegistry.CreatorInfo ci = pi.creatorInfo;
                Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
                Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
                int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
                int pendingSize = 0;
                NodeRegistry.FieldInfo[] pendingFields = null;
                Object[] pendingValues = null;

                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                    String key = entry.getKey();
                    NodeRegistry.FieldInfo fi = entry.getValue();

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
                        Object v = fi.invokeGetter(node);
                        Object vv = deepNode(v);
                        args[argIdx] = vv;
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

                    Object v = fi.invokeGetter(node);
                    Object vv = deepNode(v);
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
                }
                if (pojo == null) {
                    pojo = ci.newPojoWithArgs(args);
                    for (int i = 0; i < pendingSize; i++) {
                        pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                    }
                }
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

    // Map -> Map/JsonObject/JOJO/POJO
    private Object _readFromMap(Map<String, Object> oldMap, Class<?> rawClazz, Type type, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldMap.size());
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, entry.getKey());
                Object vv = _readNode(entry.getValue(), vt, cps);
                map.put(entry.getKey(), vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, entry.getKey());
                Object vv = _readNode(entry.getValue(), Object.class, cps);
                jo.put(entry.getKey(), vv);
            }
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo) {
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                String key = entry.getKey();

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
                    args[argIdx] = _readNode(entry.getValue(), argType, cps);
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
                    Object vv = _readNode(entry.getValue(), fi.type, cps);
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
                    Object vv = _readNode(entry.getValue(), Object.class, cps);
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
        throw new BindingException("Cannot convert Map to '" + rawClazz.getName() + "'", ps);
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private Object _readFromJsonObject(JsonObject oldJo, Class<?> rawClazz, Type type, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldJo.size());
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            oldJo.forEach((k, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, k);
                Object vv = _readNode(v, vt, cps);
                map.put(k, vv);
            });
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            oldJo.forEach((k, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, k);
                Object vv = _readNode(v, Object.class, cps);
                jo.put(k, vv);
            });
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo) {
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            for (Map.Entry<String, Object> entry : oldJo.entrySet()) {
                String key = entry.getKey();

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
                    args[argIdx] = _readNode(entry.getValue(), argType, cps);
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
                    Object vv = _readNode(entry.getValue(), fi.type, cps);
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
                    Object vv = _readNode(entry.getValue(), Object.class, cps);
                    dynamicMap.put(key, vv);
                }
            }

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (pi.isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }
        throw new BindingException("Cannot convert JsonObject to '" + rawClazz.getName() + "'", ps);
    }

    // List -> List/JsonArray/JAJO/Array/Set
    private Object _readFromList(List<?> oldList, Class<?> rawClazz, Type type, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(oldList.size());
            int i = 0;
            for (Object v : oldList) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, vt, cps);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object v : oldList) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, Object.class, cps);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            int i = 0;
            for (Object v : oldList) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, Object.class, cps);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, oldList.size());
            int i = 0;
            for (Object v : oldList) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, vt, cps);
                Array.set(array, i++, vv);
            }
            return array;
        }
        if (rawClazz == Set.class) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(oldList.size());
            int i = 0;
            for (Object v : oldList) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, vt, cps);
                set.add(vv);
            }
            return set;
        }
        throw new BindingException("Cannot convert List to '" + rawClazz.getName() + "'", ps);
    }

    // JsonArray -> List/JsonArray/JAJO/Array/Set
    private Object _readFromJsonArray(JsonArray oldJa, Class<?> rawClazz, Type type, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(oldJa.size());
            oldJa.forEach((i, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                list.add(_readNode(v, vt, cps));
            });
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            oldJa.forEach((i, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                ja.add(_readNode(v, Object.class, cps));
            });
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            oldJa.forEach((i, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                jajo.add(_readNode(v, Object.class, cps));
            });
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, oldJa.size());
            oldJa.forEach((i, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Array.set(array, i, _readNode(v, vt, cps));
            });
            return array;
        }
        if (rawClazz == Set.class) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(oldJa.size());
            oldJa.forEach((i, v) -> {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                set.add(_readNode(v, vt, cps));
            });
            return set;
        }
        throw new BindingException("Cannot convert JsonArray to '" + rawClazz.getName() + "'", ps);
    }

    // Array -> List/JsonArray/JAJO/Array/Set
    private Object _readFromArray(Object node, Class<?> rawClazz, Type type, PathSegment ps) {
        int len = Array.getLength(node);
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(len);
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, vt, cps);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, Object.class, cps);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, Object.class, cps);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, len);
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, vt, cps);
                Array.set(array, i, vv);
            }
            return array;
        }
        if (rawClazz == Set.class) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(len);
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, vt, cps);
                set.add(vv);
            }
            return set;
        }
        throw new BindingException("Cannot convert Array to '" + rawClazz.getName() + "'", ps);
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private Object _readFromSet(Set<Object> oldSet, Class<?> rawClazz, Type type, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == List.class) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(oldSet.size());
            int i = 0;
            for (Object v : oldSet) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, vt, cps);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            int i = 0;
            for (Object v : oldSet) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, Object.class, cps);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.creatorInfo.forceNewPojo();
            int i = 0;
            for (Object v : oldSet) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, Object.class, cps);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, oldSet.size());
            int i = 0;
            for (Object v : oldSet) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i);
                Object vv = _readNode(v, vt, cps);
                Array.set(array, i++, vv);
            }
            return array;
        }
        if (rawClazz == Set.class) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(oldSet.size());
            int i = 0;
            for (Object v : oldSet) {
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, rawClazz, i++);
                Object vv = _readNode(v, vt, cps);
                set.add(vv);
            }
            return set;
        }
        throw new BindingException("Cannot convert Set to '" + rawClazz.getName() + "'", ps);
    }

    // POJO -> Map/JsonObject/JOJO/POJO
    private Object _readFromPojo(Object node, NodeRegistry.PojoInfo oldPi, Class<?> rawClazz,
                                 Type type, PathSegment ps) {
        if (rawClazz == Object.class || rawClazz == Map.class) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldPi.fieldCount);
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.fields.entrySet()) {
                String key = entry.getKey();
                Object v = entry.getValue().invokeGetter(node);
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                Object vv = _readNode(v, vt, cps);
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
                Object vv = _readNode(v, Object.class, cps);
                jo.put(key, vv);
            }
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !pi.isJajo) {
            NodeRegistry.CreatorInfo ci = pi.creatorInfo;
            Object pojo = ci.noArgsCtorHandle == null ? null : ci.newPojoNoArgs();
            Object[] args = ci.noArgsCtorHandle == null ? new Object[ci.argNames.length] : null;
            int remainingArgs = ci.noArgsCtorHandle == null ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;

            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.fields.entrySet()) {
                String key = entry.getKey();
                NodeRegistry.FieldInfo oldFi = entry.getValue();

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
                    Object v = oldFi.invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    args[argIdx] = _readNode(v, argType, cps);
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
                    Object v = oldFi.invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readNode(v, fi.type, cps);
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
                    Object v = oldFi.invokeGetter(node);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, rawClazz, key);
                    Object vv = _readNode(v, Object.class, cps);
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
        throw new BindingException("Cannot convert POJO to '" + rawClazz.getName() + "'", ps);
    }


    /// Write

    @Override
    public Object writeNode(Object node) {
        return _writeNode(node,
                Sjf4jConfig.global().isBindingPath() ? PathSegment.Root.INSTANCE : null);
    }

    @SuppressWarnings("unchecked")
    public Object _writeNode(Object node, PathSegment ps) {
        try {
            if (node == null) return null;

            Class<?> rawClazz = node.getClass();

            if (node instanceof CharSequence || node instanceof Character) {
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
                return vci.encode(node);
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
