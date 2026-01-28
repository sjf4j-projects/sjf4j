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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SimpleNodeFacade implements NodeFacade {


    @SuppressWarnings("unchecked")
    @Override
    public Object readNode(Object node, Type type) {
        Class<?> rawClazz = Types.rawClazz(type);
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            if (rawClazz.isInstance(node)) {
                return vci.copy(node);
            } else {
                return vci.decode(node);
            }
        }

        if (node == null) return null;

        if (node instanceof CharSequence || node instanceof Character) {
            return readString(node.toString(), rawClazz);
        }
        if (node instanceof Enum) {
            return readString(((Enum<?>) node).name(), rawClazz);
        }

        if (node instanceof Number) {
            if (rawClazz.isAssignableFrom(Number.class)) {
                return node;
            }
            if (Number.class.isAssignableFrom(rawClazz)) {
                return Numbers.to((Number) node, rawClazz);
            }
            throw new JsonException("Cannot deserialize Number value '" + node + "' (" + node.getClass().getName() +
                    ") to target type " + rawClazz.getName());
        }

        if (node instanceof Boolean) {
            if (rawClazz.isAssignableFrom(Boolean.class)) {
                return node;
            }
            throw new JsonException("Cannot deserialize Boolean value '" + node + "' to target type " + rawClazz.getName());
        }

        // Map -> Map/JsonObject/JOJO/POJO
        if (node instanceof Map) {
            Map<String, Object> oldMap = (Map<String, Object>) node;
            if (Map.class.isAssignableFrom(rawClazz) || rawClazz == Object.class) {
                Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldMap.size());
                Type vType = Types.resolveTypeArgument(type, Map.class, 1);
                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    Object vv = readNode(entry.getValue(), vType);
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
            if (JsonObject.class.isAssignableFrom(rawClazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
                JsonObject jojo = (JsonObject) pi.newInstance();
                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    NodeRegistry.FieldInfo fi = fields.get(entry.getKey());
                    if (fi != null) {
                        Object vv = readNode(entry.getValue(), fi.getType());
                        fi.invokeSetter(jojo, vv);
                    } else {
                        Object vv = readNode(entry.getValue(), Object.class);
                        jojo.put(entry.getKey(), vv);
                    }
                }
                return jojo;
            }
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
            if (pi != null) {
                Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
                Object pojo = pi.newInstance();
                for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                    NodeRegistry.FieldInfo fi = fields.get(entry.getKey());
                    if (fi != null) {
                        Object vv = readNode(entry.getValue(), fi.getType());
                        fi.invokeSetter(pojo, vv);
                    }
                }
                return pojo;
            }
            throw new JsonException("Cannot deserialize Map value to target type " + rawClazz.getName());
        }

        // JsonObject -> Map/JsonObject/JOJO/POJO
        if (node instanceof JsonObject) {
            JsonObject oldJo = (JsonObject) node;
            if (Map.class.isAssignableFrom(rawClazz)) {
                Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldJo.size());
                Type vType = Types.resolveTypeArgument(type, Map.class, 1);
                oldJo.forEach((k, v) -> {
                    Object vv = readNode(v, vType);
                    map.put(k, vv);
                });
                return map;
            }
            if (rawClazz == JsonObject.class || (rawClazz == Object.class && node.getClass() == JsonObject.class)) {
                JsonObject jo = new JsonObject();
                oldJo.forEach((k, v) -> {
                    Object vv = readNode(v, Object.class);
                    jo.put(k, vv);
                });
                return jo;
            }
            if (JsonObject.class.isAssignableFrom(rawClazz) || rawClazz == Object.class) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(
                        rawClazz == Object.class ? node.getClass() : rawClazz);
                Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
                JsonObject jojo = (JsonObject) pi.newInstance();
                oldJo.forEach((k, v) -> {
                    NodeRegistry.FieldInfo fi = fields.get(k);
                    if (fi != null) {
                        Object vv = readNode(v, fi.getType());
                        fi.invokeSetter(jojo, vv);
                    } else {
                        Object vv = readNode(v, Object.class);
                        jojo.put(k, vv);
                    }
                });
                return jojo;
            }
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
            if (pi != null) {
                Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
                Object pojo = pi.newInstance();
                oldJo.forEach((k, v) -> {
                    NodeRegistry.FieldInfo fi = fields.get(k);
                    if (fi != null) {
                        Object vv = readNode(v, fi.getType());
                        fi.invokeSetter(pojo, vv);
                    }
                });
                return pojo;
            }
            throw new JsonException("Cannot deserialize JsonObject value to target type " + rawClazz.getName());
        }

        // POJO -> Map/JsonObject/JOJO/POJO
        NodeRegistry.PojoInfo oldPi = NodeRegistry.registerPojo(node.getClass());
        if (oldPi != null) {
            Map<String, NodeRegistry.FieldInfo> oldFields = oldPi.getFields();
            if (Map.class.isAssignableFrom(rawClazz)) {
                Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create(oldFields.size());
                Type vType = Types.resolveTypeArgument(type, Map.class, 1);
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldFields.entrySet()) {
                    NodeRegistry.FieldInfo fi = entry.getValue();
                    Object v = fi.invokeGetter(node);
                    Object vv = readNode(v, vType);
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
            if (JsonObject.class.isAssignableFrom(rawClazz)) {
                NodeRegistry.PojoInfo newPi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                Map<String, NodeRegistry.FieldInfo> newFields = newPi.getFields();
                JsonObject jojo = (JsonObject) newPi.newInstance();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldFields.entrySet()) {
                    NodeRegistry.FieldInfo oldFi = entry.getValue();
                    Object v = oldFi.invokeGetter(node);
                    NodeRegistry.FieldInfo newFi = newFields.get(entry.getKey());
                    if (newFi != null) {
                        Object vv = readNode(v, newFi.getType());
                        newFi.invokeSetter(jojo, vv);
                    } else {
                        Object vv = readNode(v, Object.class);
                        jojo.put(entry.getKey(), vv);
                    }
                }
                return jojo;
            }
            NodeRegistry.PojoInfo newPi = NodeRegistry.registerPojo(rawClazz);
            if (newPi != null || rawClazz == Object.class) {
                newPi = oldPi;
                Map<String, NodeRegistry.FieldInfo> newFields = newPi.getFields();
                Object pojo = newPi.newInstance();
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldFields.entrySet()) {
                    NodeRegistry.FieldInfo newFi = newFields.get(entry.getKey());
                    if (newFi != null) {
                        NodeRegistry.FieldInfo oldFi = entry.getValue();
                        Object v = oldFi.invokeGetter(node);
                        Object vv = readNode(v, newFi.getType());
                        newFi.invokeSetter(pojo, vv);
                    }
                }
                return pojo;
            }
            throw new JsonException("Cannot deserialize POJO value to target type " + rawClazz.getName());
        }

        // List -> List/JsonArray/JAJO/Array/Set
        if (node instanceof List) {
            List<Object> oldList = (List<Object>) node;
            if (List.class.isAssignableFrom(rawClazz) || rawClazz == Object.class) {
                Type vType = Types.resolveTypeArgument(type, List.class, 0);
                List<Object> list = Sjf4jConfig.global().listSupplier.create(oldList.size());
                for (Object v : oldList) {
                    Object vv = readNode(v, vType);
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
                JsonArray jajo = (JsonArray) pi.newInstance();
                for (Object v : oldList) {
                    Object vv = readNode(v, Object.class);
                    jajo.add(vv);
                }
                return jajo;
            }
            if (rawClazz.isArray()) {
                Class<?> vType = rawClazz.getComponentType();
                Object array = Array.newInstance(vType, oldList.size());
                int i = 0;
                for (Object v : oldList) {
                    Object vv = readNode(v, vType);
                    Array.set(array, i++, vv);
                }
                return array;
            }
            if (Set.class.isAssignableFrom(rawClazz)) {
                Type vType = Types.resolveTypeArgument(type, Set.class, 0);
                Set<Object> set = new LinkedHashSet<>();
                for (Object v : oldList) {
                    Object vv = readNode(v, vType);
                    set.add(vv);
                }
                return set;
            }
            throw new JsonException("Cannot deserialize List value to target type " + rawClazz.getName());
        }

        // JsonArray -> List/JsonArray/JAJO/Array/Set
        if (node instanceof JsonArray) {
            JsonArray oldJa = (JsonArray) node;
            if (List.class.isAssignableFrom(rawClazz)) {
                Type vType = Types.resolveTypeArgument(type, List.class, 0);
                List<Object> list = Sjf4jConfig.global().listSupplier.create(oldJa.size());
                oldJa.forEach(v -> {
                    Object vv = readNode(v, vType);
                    list.add(vv);
                });
                return list;
            }
            if (rawClazz == JsonArray.class || (rawClazz == Object.class && node.getClass() == JsonArray.class)) {
                JsonArray ja = new JsonArray();
                oldJa.forEach(v -> {
                    Object vv = readNode(v, Object.class);
                    ja.add(vv);
                });
                return ja;
            }
            if (JsonArray.class.isAssignableFrom(rawClazz) || rawClazz == Object.class) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                JsonArray jajo = (JsonArray) pi.newInstance();
                oldJa.forEach(v -> {
                    Object vv = readNode(v, Object.class);
                    jajo.add(vv);
                });
                return jajo;
            }
            if (rawClazz.isArray()) {
                Class<?> vType = rawClazz.getComponentType();
                Object array = Array.newInstance(vType, oldJa.size());
                oldJa.forEach((i, v) -> {
                    Object vv = readNode(v, vType);
                    Array.set(array, i, vv);
                });
                return array;
            }
            if (Set.class.isAssignableFrom(rawClazz)) {
                Type vType = Types.resolveTypeArgument(type, Set.class, 0);
                Set<Object> set = new LinkedHashSet<>();
                oldJa.forEach(v -> {
                    Object vv = readNode(v, vType);
                    set.add(vv);
                });
                return set;
            }
            throw new JsonException("Cannot deserialize JsonArray value to target type " + rawClazz.getName());
        }

        // Array -> List/JsonArray/JAJO/Array/Set
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            if (List.class.isAssignableFrom(rawClazz)) {
                Type vType = Types.resolveTypeArgument(type, List.class, 0);
                List<Object> list = Sjf4jConfig.global().listSupplier.create(len);
                for (int i = 0; i < len; i++) {
                    Object v = Array.get(node, i);
                    Object vv = readNode(v, vType);
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
                JsonArray jajo = (JsonArray) pi.newInstance();
                for (int i = 0; i < len; i++) {
                    Object v = Array.get(node, i);
                    Object vv = readNode(v, Object.class);
                    jajo.add(vv);
                }
                return jajo;
            }
            if (rawClazz.isArray() || rawClazz == Object.class) {
                Class<?> vType = rawClazz.getComponentType();
                Object array = Array.newInstance(vType, len);
                for (int i = 0; i < len; i++) {
                    Object v = Array.get(node, i);
                    Object vv = readNode(v, vType);
                    Array.set(array, i, vv);
                }
                return array;
            }
            if (Set.class.isAssignableFrom(rawClazz)) {
                Type vType = Types.resolveTypeArgument(type, Set.class, 0);
                Set<Object> set = new LinkedHashSet<>();
                for (int i = 0; i < len; i++) {
                    Object v = Array.get(node, i);
                    Object vv = readNode(v, vType);
                    set.add(vv);
                }
                return set;
            }
            throw new JsonException("Cannot deserialize Array value to target type " + rawClazz.getName());
        }

        // Set -> List/JsonArray/JAJO/Array/Set
        if (node instanceof Set) {
            Set<Object> oldSet = (Set<Object>) node;
            if (List.class.isAssignableFrom(rawClazz)) {
                Type vType = Types.resolveTypeArgument(type, List.class, 0);
                List<Object> list = Sjf4jConfig.global().listSupplier.create(oldSet.size());
                for (Object v : oldSet) {
                    Object vv = readNode(v, vType);
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
                JsonArray jajo = (JsonArray) pi.newInstance();
                for (Object v : oldSet) {
                    Object vv = readNode(v, Object.class);
                    jajo.add(vv);
                }
                return jajo;
            }
            if (rawClazz.isArray()) {
                Class<?> vType = rawClazz.getComponentType();
                Object array = Array.newInstance(vType, oldSet.size());
                int i = 0;
                for (Object v : oldSet) {
                    Object vv = readNode(v, vType);
                    Array.set(array, i++, vv);
                }
                return array;
            }
            if (Set.class.isAssignableFrom(rawClazz) || rawClazz == Object.class) {
                Type vType = Types.resolveTypeArgument(type, Set.class, 0);
                Set<Object> set = new LinkedHashSet<>(oldSet.size());
                for (Object v : oldSet) {
                    Object vv = readNode(v, vType);
                    set.add(vv);
                }
                return set;
            }
            throw new JsonException("Cannot deserialize Set value to target type " + rawClazz.getName());
        }

        throw new JsonException("Cannot deserialize value of type '" + node.getClass().getName() +
                "' to target type " + type);
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

//    private Object readNumber(Number n, Class<?> rawClazz) {
//        if (Number.class.isAssignableFrom(rawClazz)) {
//            return Numbers.to(n, rawClazz);
//        }
//        throw new JsonException("Cannot deserialize Number value '" + n + "' (" + n.getClass().getName() +
//                ") to target type " + rawClazz.getName());
//    }
//
//    private Object readBoolean(Boolean b, Class<?> rawClazz) {
//        if (rawClazz == Boolean.class) {
//            return b;
//        }
//        throw new JsonException("Cannot deserialize Boolean value '" + b + "' to target type " + rawClazz.getName());
//    }


//    private Object readObject(Object container, Class<?> rawClazz, Type type, boolean deepCopy) {
//        if (rawClazz == Map.class || (rawClazz == Object.class && container instanceof Map)) {
//            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
//            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
//            Nodes.visitObject(container, (k, v) -> {
//                Object vv = readNode(v, valueType);
//                map.put(k, vv);
//            });
//            return map;
//        }
//
//        if (rawClazz == JsonObject.class) {
//            JsonObject jo = new JsonObject();
//            Nodes.visitObject(container, (k, v) -> {
//                Object vv = readNode(v, Object.class);
//                jo.put(k, vv);
//            });
//            return jo;
//        }
//
//        if (JsonObject.class.isAssignableFrom(rawClazz)) {
//            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
//            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
//            JsonObject jojo = (JsonObject) pi.newInstance();
//            Nodes.visitObject(container, (k, v) -> {
//                NodeRegistry.FieldInfo fi = fields.get(k);
//                if (fi != null) {
//                    Object vv = readNode(v, fi.getType());
//                    fi.invokeSetter(jojo, vv);
//                } else {
//                    Object vv = readNode(v, Object.class);
//                    jojo.put(k, vv);
//                }
//            });
//            return jojo;
//        }
//
//        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
//        if (pi != null) {
//            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
//            Object pojo = pi.newInstance();
//            Nodes.visitObject(container, (k, v) -> {
//                NodeRegistry.FieldInfo fi = fields.get(k);
//                if (fi != null) {
//                    Object vv = readNode(v, fi.getType());
//                    fi.invokeSetter(pojo, vv);
//                }
//            });
//            return pojo;
//        }
//
//        throw new JsonException("Cannot deserialize Object value to target type " + rawClazz.getName());
//    }


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

        throw new IllegalStateException("Unsupported node type '" + node.getClass().getName() + "'");
    }

}
