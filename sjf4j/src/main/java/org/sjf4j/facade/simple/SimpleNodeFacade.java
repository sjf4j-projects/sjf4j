package org.sjf4j.facade.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SimpleNodeFacade implements NodeFacade {

    @SuppressWarnings("unchecked")
    @Override
    public Object readNode(Object node, Type type) {
        if (node == null) return null;

        Class<?> rawClazz = Types.rawBox(type);
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return rawClazz.isInstance(node) ? vci.copy(node) : vci.decode(node);
        }

        // Object.class means deep copy
        if (rawClazz == Object.class) {
            return deepNode(node);
        }

        if (node instanceof CharSequence || node instanceof Character) {
            return readString(node.toString(), rawClazz);
        }
        if (node instanceof Enum) {
            return readString(((Enum<?>) node).name(), rawClazz);
        }

        if (node instanceof Number) {
            if (Number.class.isAssignableFrom(rawClazz)) {
                return Numbers.to((Number) node, rawClazz);
            }
            throw new JsonException("Cannot deserialize Number value '" + node + "' (" + Types.name(node) +
                    ") to target type " + rawClazz.getName());
        }

        if (node instanceof Boolean) {
            if (rawClazz == Boolean.class) {
                return node;
            }
            throw new JsonException("Cannot deserialize Boolean value '" + node + "' to target type " + rawClazz.getName());
        }

        if (node instanceof Map) {
            return readFromMap((Map<String, Object>) node, rawClazz, type);
        }

        if (node instanceof JsonObject) {
            return readFromJsonObject((JsonObject) node, rawClazz, type);
        }

        if (node instanceof List) {
            return readFromList((List<Object>) node, rawClazz, type);
        }

        if (node instanceof JsonArray) {
            return readFromJsonArray((JsonArray) node, rawClazz, type);
        }

        if (node.getClass().isArray()) {
            return readFromArray(node, rawClazz, type);
        }

        if (node instanceof Set) {
            return readFromSet((Set<Object>) node, rawClazz, type);
        }

        NodeRegistry.PojoInfo oldPi = NodeRegistry.registerPojo(node.getClass());
        if (oldPi != null) {
            return readFromPojo(node, oldPi, rawClazz, type);
        }

        throw new JsonException("Cannot deserialize value of type '" + Types.name(node) +
                "' to target type " + type);
    }

    // Object -> deep copied Object
    @SuppressWarnings("unchecked")
    @Override
    public Object deepNode(Object node) {
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
            NodeRegistry.CreatorInfo ci = NodeRegistry.registerPojoOrElseThrow(nodeClazz).getCreatorInfo();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            JsonObject newJo = (JsonObject) (useArgsCreator ? null : ci.newPojoNoArgs());
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            String[] pendingKeys = null;
            Object[] pendingValues = null;

            for (Map.Entry<String, Object> entry : srcJo.entrySet()) {
                String key = entry.getKey();
                int argIdx = -1;
                if (newJo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
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
                    : (JsonArray) NodeRegistry.registerPojoOrElseThrow(nodeClazz).getCreatorInfo().forceNewPojo();
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
            NodeRegistry.CreatorInfo ci = pi.getCreatorInfo();
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            Object pojo = useArgsCreator ? null : ci.newPojoNoArgs();
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;

            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : fields.entrySet()) {
                String key = entry.getKey();
                NodeRegistry.FieldInfo fi = entry.getValue();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
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
                        for (int i = 0; i < pendingSize; i++) {
                            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
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
                        int cap = fields.size();
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
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readString(String s, Class<?> rawClazz) {
        if (rawClazz.isAssignableFrom(String.class)) {
            return s;
        } else if (rawClazz == Character.class) {
            return s.length() > 0 ? s.charAt(0) : null;
        } else if (rawClazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new JsonException("Cannot deserialize String value '" + s + "' to target type " + rawClazz.getName());
    }

    // Map -> Map/JsonObject/JOJO/POJO
    private Object readFromMap(Map<String, Object> oldMap, Class<?> rawClazz, Type type) {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldMap.size());
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                Object vv = readNode(entry.getValue(), vt);
                map.put(entry.getKey(), vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                Object vv = readNode(entry.getValue(), Object.class);
                jo.put(entry.getKey(), vv);
            }
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.CreatorInfo ci = pi.getCreatorInfo();
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Map<String, NodeRegistry.FieldInfo> aliasFields = pi.getAliasFields();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            Object pojo = useArgsCreator ? null : ci.newPojoNoArgs();
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;
            boolean isJojo = JsonObject.class.isAssignableFrom(pi.getType());

            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                String key = entry.getKey();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    assert args != null;
                    Type argType = ci.getArgTypes()[argIdx];
                    args[argIdx] = readNode(entry.getValue(), argType);
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        pojo = ci.newPojoWithArgs(args);
                        for (int i = 0; i < pendingSize; i++) {
                            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                        }
                        pendingSize = 0;
                    }
                    continue;
                }

                NodeRegistry.FieldInfo fi = aliasFields != null ? aliasFields.get(key) : fields.get(key);
                if (fi != null) {
                    Object vv = readNode(entry.getValue(), fi.getType());
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = fields.size();
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (isJojo) {
                    if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    Object vv = readNode(entry.getValue(), Object.class);
                    dynamicMap.put(key, vv);
                }
            }

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }
        throw new JsonException("Cannot deserialize Map value to target type " + rawClazz.getName());
    }

    // JsonObject -> Map/JsonObject/JOJO/POJO
    private Object readFromJsonObject(JsonObject oldJo, Class<?> rawClazz, Type type) {
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldJo.size());
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            oldJo.forEach((k, v) -> {
                Object vv = readNode(v, vt);
                map.put(k, vv);
            });
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            oldJo.forEach((k, v) -> {
                Object vv = readNode(v, Object.class);
                jo.put(k, vv);
            });
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.CreatorInfo ci = pi.getCreatorInfo();
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Map<String, NodeRegistry.FieldInfo> aliasFields = pi.getAliasFields();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            Object pojo = useArgsCreator ? null : ci.newPojoNoArgs();
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;
            boolean isJojo = JsonObject.class.isAssignableFrom(rawClazz);

            for (Map.Entry<String, Object> entry : oldJo.entrySet()) {
                String key = entry.getKey();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    assert args != null;
                    Type argType = ci.getArgTypes()[argIdx];
                    args[argIdx] = readNode(entry.getValue(), argType);
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        pojo = ci.newPojoWithArgs(args);
                        for (int i = 0; i < pendingSize; i++) {
                            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                        }
                        pendingSize = 0;
                    }
                    continue;
                }

                NodeRegistry.FieldInfo fi = aliasFields != null ? aliasFields.get(key) : fields.get(key);
                if (fi != null) {
                    Object vv = readNode(entry.getValue(), fi.getType());
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = fields.size();
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (isJojo) {
                    if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    Object vv = readNode(entry.getValue(), Object.class);
                    dynamicMap.put(key, vv);
                }
            }

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }
        throw new JsonException("Cannot deserialize JsonObject value to target type " + rawClazz.getName());
    }

    // List -> List/JsonArray/JAJO/Array/Set
    private Object readFromList(List<?> oldList, Class<?> rawClazz, Type type) {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(oldList.size());
            for (Object v : oldList) {
                Object vv = readNode(v, vt);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (Object v : oldList) {
                Object vv = readNode(v, Object.class);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.getCreatorInfo().forceNewPojo();
            for (Object v : oldList) {
                Object vv = readNode(v, Object.class);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, oldList.size());
            int i = 0;
            for (Object v : oldList) {
                Object vv = readNode(v, vt);
                Array.set(array, i++, vv);
            }
            return array;
        }
        if (Set.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(oldList.size());
            for (Object v : oldList) {
                Object vv = readNode(v, vt);
                set.add(vv);
            }
            return set;
        }
        throw new JsonException("Cannot deserialize List value to target type " + rawClazz.getName());
    }

    // JsonArray -> List/JsonArray/JAJO/Array/Set
    private Object readFromJsonArray(JsonArray oldJa, Class<?> rawClazz, Type type) {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(oldJa.size());
            oldJa.forEach(v -> list.add(readNode(v, vt)));
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            oldJa.forEach(v -> ja.add(readNode(v, Object.class)));
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.getCreatorInfo().forceNewPojo();
            oldJa.forEach(v -> jajo.add(readNode(v, Object.class)));
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, oldJa.size());
            oldJa.forEach((i, v) -> Array.set(array, i, readNode(v, vt)));
            return array;
        }
        if (Set.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(oldJa.size());
            oldJa.forEach(v -> set.add(readNode(v, vt)));
            return set;
        }
        throw new JsonException("Cannot deserialize JsonArray value to target type " + rawClazz.getName());
    }

    // Array -> List/JsonArray/JAJO/Array/Set
    private Object readFromArray(Object node, Class<?> rawClazz, Type type) {
        int len = Array.getLength(node);
        if (List.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(len);
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                Object vv = readNode(v, vt);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                Object vv = readNode(v, Object.class);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.getCreatorInfo().forceNewPojo();
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                Object vv = readNode(v, Object.class);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz == Object.class
                    ? node.getClass().getComponentType()
                    : rawClazz.getComponentType();
            Object array = Array.newInstance(vt, len);
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                Object vv = readNode(v, vt);
                Array.set(array, i, vv);
            }
            return array;
        }
        if (Set.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(len);
            for (int i = 0; i < len; i++) {
                Object v = Array.get(node, i);
                Object vv = readNode(v, vt);
                set.add(vv);
            }
            return set;
        }
        throw new JsonException("Cannot deserialize Array value to target type " + rawClazz.getName());
    }

    // Set -> List/JsonArray/JAJO/Array/Set
    private Object readFromSet(Set<Object> oldSet, Class<?> rawClazz, Type type) {
        if (List.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = Sjf4jConfig.global().listSupplier.create(oldSet.size());
            for (Object v : oldSet) {
                Object vv = readNode(v, vt);
                list.add(vv);
            }
            return list;
        }
        if (rawClazz == JsonArray.class) {
            JsonArray ja = new JsonArray();
            for (Object v : oldSet) {
                Object vv = readNode(v, Object.class);
                ja.add(vv);
            }
            return ja;
        }
        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray jajo = (JsonArray) pi.getCreatorInfo().forceNewPojo();
            for (Object v : oldSet) {
                Object vv = readNode(v, Object.class);
                jajo.add(vv);
            }
            return jajo;
        }
        if (rawClazz.isArray()) {
            Class<?> vt = rawClazz.getComponentType();
            Object array = Array.newInstance(vt, oldSet.size());
            int i = 0;
            for (Object v : oldSet) {
                Object vv = readNode(v, vt);
                Array.set(array, i++, vv);
            }
            return array;
        }
        if (Set.class.isAssignableFrom(rawClazz)) {
            Type vt = Types.resolveTypeArgument(type, Set.class, 0);
            Set<Object> set = Sjf4jConfig.global().setSupplier.create(oldSet.size());
            for (Object v : oldSet) {
                Object vv = readNode(v, vt);
                set.add(vv);
            }
            return set;
        }
        throw new JsonException("Cannot deserialize Set value to target type " + rawClazz.getName());
    }

    // POJO -> Map/JsonObject/JOJO/POJO
    private Object readFromPojo(Object node, NodeRegistry.PojoInfo oldPi, Class<?> rawClazz, Type type) {
        Map<String, NodeRegistry.FieldInfo> oldFields = oldPi.getFields();
        if (Map.class.isAssignableFrom(rawClazz)) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldFields.size());
            Type vt = Types.resolveTypeArgument(type, Map.class, 1);
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldFields.entrySet()) {
                NodeRegistry.FieldInfo fi = entry.getValue();
                Object v = fi.invokeGetter(node);
                Object vv = readNode(v, vt);
                map.put(entry.getKey(), vv);
            }
            return map;
        }

        if (rawClazz == JsonObject.class) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldFields.entrySet()) {
                NodeRegistry.FieldInfo fi = entry.getValue();
                Object v = fi.invokeGetter(node);
                Object vv = readNode(v, Object.class);
                jo.put(entry.getKey(), vv);
            }
            return jo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null && !JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.CreatorInfo ci = pi.getCreatorInfo();
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Map<String, NodeRegistry.FieldInfo> aliasFields = pi.getAliasFields();
            boolean useArgsCreator = !ci.hasNoArgsCtor();
            Object pojo = useArgsCreator ? null : ci.newPojoNoArgs();
            Object[] args = useArgsCreator ? new Object[ci.getArgNames().length] : null;
            int remainingArgs = useArgsCreator ? args.length : 0;
            int pendingSize = 0;
            NodeRegistry.FieldInfo[] pendingFields = null;
            Object[] pendingValues = null;
            Map<String, Object> dynamicMap = null;
            boolean isJojo = JsonObject.class.isAssignableFrom(rawClazz);

            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldFields.entrySet()) {
                String key = entry.getKey();

                int argIdx = -1;
                if (pojo == null) {
                    argIdx = ci.getArgIndex(key);
                    if (argIdx < 0 && ci.getAliasMap() != null) {
                        String origin = ci.getAliasMap().get(key); // alias -> origin
                        if (origin != null) {
                            argIdx = ci.getArgIndex(origin);
                        }
                    }
                }
                if (argIdx >= 0) {
                    assert args != null;
                    Type argType = ci.getArgTypes()[argIdx];
                    Object v = entry.getValue().invokeGetter(node);
                    args[argIdx] = readNode(v, argType);
                    remainingArgs--;
                    if (remainingArgs == 0) {
                        pojo = ci.newPojoWithArgs(args);
                        for (int i = 0; i < pendingSize; i++) {
                            pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                        }
                        pendingSize = 0;
                    }
                    continue;
                }

                NodeRegistry.FieldInfo fi = aliasFields != null ? aliasFields.get(key) : fields.get(key);
                if (fi != null) {
                    Object v = entry.getValue().invokeGetter(node);
                    Object vv = readNode(v, fi.getType());
                    if (pojo != null) {
                        fi.invokeSetterIfPresent(pojo, vv);
                    } else {
                        if (pendingFields == null) {
                            int cap = fields.size();
                            pendingFields = new NodeRegistry.FieldInfo[cap];
                            pendingValues = new Object[cap];
                        }
                        pendingFields[pendingSize] = fi;
                        pendingValues[pendingSize] = vv;
                        pendingSize++;
                    }
                    continue;
                }

                if (isJojo) {
                    if (dynamicMap == null) dynamicMap = Sjf4jConfig.global().mapSupplier.create();
                    Object v = entry.getValue().invokeGetter(node);
                    Object vv = readNode(v, Object.class);
                    dynamicMap.put(key, vv);
                }
            }

            if (pojo == null) {
                pojo = ci.newPojoWithArgs(args);
                for (int i = 0; i < pendingSize; i++) {
                    pendingFields[i].invokeSetterIfPresent(pojo, pendingValues[i]);
                }
            }
            if (isJojo) ((JsonObject) pojo).setDynamicMap(dynamicMap);
            return pojo;
        }
        throw new JsonException("Cannot deserialize POJO value to target type " + rawClazz.getName());
    }

    /// Write

    @SuppressWarnings("unchecked")
    @Override
    public Object writeNode(Object node) {
        if (node == null) return null;

        Class<?> rawClazz = node.getClass();
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            return vci.encode(node);
        }

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
                Object vv = writeNode(entry.getValue());
                newMap.put(entry.getKey(), vv);
            }
            return newMap;
        }

        if (node instanceof List) {
            List<Object> oldList = (List<Object>) node;
            List<Object> newList = Sjf4jConfig.global().listSupplier.create(oldList.size());
            for (int i = 0, len = oldList.size(); i < len; i++) {
                Object vv = writeNode(oldList.get(i));
                newList.add(vv);
            }
            return newList;
        }

        if (node instanceof JsonObject) {
            JsonObject jo = (JsonObject) node;
            Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(jo.size());
            jo.forEach((k, v) -> {
                Object vv = writeNode(v);
                newMap.put(k, vv);
            });
            return newMap;
        }

        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            List<Object> newList = Sjf4jConfig.global().listSupplier.create(ja.size());
            for (int i = 0, len = ja.size(); i < len; i++) {
                Object vv = writeNode(ja.getNode(i));
                newList.add(vv);
            }
            return newList;
        }

        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            List<Object> newList = Sjf4jConfig.global().listSupplier.create(len);
            for (int i = 0; i < len; i++) {
                Object vv = writeNode(Array.get(node, i));
                newList.add(vv);
            }
            return newList;
        }

        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            List<Object> newList = Sjf4jConfig.global().listSupplier.create(set.size());
            for (Object v : set) {
                Object vv = writeNode(v);
                newList.add(vv);
            }
            return newList;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null) {
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Map<String, Object> newMap = Sjf4jConfig.global().mapSupplier.create(fields.size());
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : fields.entrySet()) {
                Object v = entry.getValue().invokeGetter(node);
                Object vv = writeNode(v);
                newMap.put(entry.getKey(), vv);
            }
            return newMap;
        }

        throw new IllegalStateException("Unsupported node type '" + Types.name(node) + "'");
    }

}
