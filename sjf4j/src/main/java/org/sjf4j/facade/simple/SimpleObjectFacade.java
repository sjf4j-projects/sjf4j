package org.sjf4j.facade.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeRegistry;
import org.sjf4j.facade.ObjectFacade;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class SimpleObjectFacade implements ObjectFacade {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object readNode(Object object, Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);

        if (object == null) {
            return null;
        } else if (object instanceof CharSequence || object instanceof Character) {
            if (rawClazz == null || rawClazz.isAssignableFrom(String.class)) {
                return object.toString();
            } else if (rawClazz.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) rawClazz, object.toString());
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (object instanceof Number) {
            return NumberUtil.as((Number) object, rawClazz);
        } else if (object instanceof Boolean) {
            if (rawClazz == null || rawClazz == Object.class ||
                    rawClazz == boolean.class || rawClazz == Boolean.class) {
                return object;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (object instanceof JsonObject) {
            if (rawClazz == null || rawClazz.isAssignableFrom(JsonObject.class)) {
                JsonObject jo = new JsonObject();
                ((JsonObject) object).forEach((k, v) -> {
                    Object vv = readNode(v, Object.class);
                    jo.put(k, vv);
                });
                return jo;
            } else if (rawClazz.isAssignableFrom(Map.class) || Map.class.isAssignableFrom(rawClazz)) {
                Type vtype = TypeUtil.resolveTypeArgument(type, Map.class, 1);
                Map<String, Object> map = JsonConfig.global().mapSupplier.create();
                ((JsonObject) object).forEach((k, v) -> {
                    Object vv = readNode(v, vtype);
                    map.put(k, vv);
                });
                return map;
            } else if (JsonObject.class.isAssignableFrom(rawClazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                JsonObject pjo = (JsonObject) pi.newInstance();
                Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
                ((JsonObject) object).forEach((k, v) -> {
                    NodeRegistry.FieldInfo fi = fields.get(k);
                    if (fi != null) {
                        Object vv = readNode(v, fi.getType());
                        fi.invokeSetter(pjo, vv);
                    } else {
                        Object vv = readNode(v, Object.class);
                        pjo.put(k, vv);
                    }
                });
                return pjo;
            } else if (NodeRegistry.isPojo(rawClazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                Object pojo = pi.newInstance();
                ((JsonObject) object).forEach((k, v) -> {
                    NodeRegistry.FieldInfo fi = pi.getFields().get(k);
                    if (fi != null) {
                        Object vv = readNode(v, fi.getType());
                        fi.invokeSetter(pojo, vv);
                    }
                });
                return pojo;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (object instanceof Map) {
            if (rawClazz == null || rawClazz == JsonObject.class) {
                JsonObject jo = new JsonObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    Object vv = readNode(entry.getValue(), Object.class);
                    jo.put(entry.getKey().toString(), vv);
                }
                return jo;
            } else if (rawClazz.isAssignableFrom(Map.class) || Map.class.isAssignableFrom(rawClazz)) {
                Type vtype = TypeUtil.resolveTypeArgument(type, Map.class, 1);
                Map<String, Object> map = JsonConfig.global().mapSupplier.create();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    Object vv = readNode(entry.getValue(), vtype);
                    map.put(entry.getKey().toString(), vv);
                }
                return map;
            } else if (JsonObject.class.isAssignableFrom(rawClazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                JsonObject pjo = (JsonObject) pi.newInstance();
                Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    String k = entry.getKey().toString();
                    NodeRegistry.FieldInfo fi = fields.get(k);
                    if (fi != null) {
                        Object vv = readNode(entry.getValue(), fi.getType());
                        fi.invokeSetter(pjo, vv);
                    } else {
                        Object vv = readNode(entry.getValue(), Object.class);
                        pjo.put(k, vv);
                    }
                }
                return pjo;
            } else if (NodeRegistry.isPojo(rawClazz)) {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                Object pojo = pi.newInstance();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    NodeRegistry.FieldInfo fi = pi.getFields().get(entry.getKey().toString());
                    if (fi != null) {
                        Object vv = readNode(entry.getValue(), fi.getType());
                        fi.invokeSetter(pojo, vv);
                    }
                }
                return pojo;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (object instanceof JsonArray) {
            if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
                JsonArray ja = new JsonArray();
                for (Object v : (JsonArray) object) {
                    Object vv = readNode(v, Object.class);
                    ja.add(vv);
                }
                return ja;
            } else if (rawClazz.isAssignableFrom(List.class) || List.class.isAssignableFrom(rawClazz)) {
                Type vtype = TypeUtil.resolveTypeArgument(type, List.class, 0);
                List<Object> list = new ArrayList<>();
                for (Object v : (JsonArray) object) {
                    Object vv = readNode(v, vtype);
                    list.add(vv);
                }
                return list;
            } else if (rawClazz.isArray()) {
                JsonArray ja = (JsonArray) object;
                Object arr = Array.newInstance(rawClazz.getComponentType(), ja.size());
                for (int i = 0; i < ja.size(); i++) {
                    Object vv = readNode(ja.getNode(i), rawClazz.getComponentType());
                    Array.set(arr, i, vv);
                }
                return arr;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (object instanceof List) {
            if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
                JsonArray ja = new JsonArray();
                for (Object v : (List<?>) object) {
                    Object vv = readNode(v, Object.class);
                    ja.add(vv);
                }
                return ja;
            } else if (rawClazz.isAssignableFrom(List.class) || List.class.isAssignableFrom(rawClazz)) {
                Type vtype = TypeUtil.resolveTypeArgument(type, List.class, 0);
                List<Object> list = new ArrayList<>();
                for (Object v : (List<?>) object) {
                    Object vv = readNode(v, vtype);
                    list.add(vv);
                }
                return list;
            } else if (rawClazz.isArray()) {
                List<?> list = (List<?>) object;
                Object arr = Array.newInstance(rawClazz.getComponentType(), list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object vv = readNode(list.get(i), rawClazz.getComponentType());
                    Array.set(arr, i, vv);
                }
                return arr;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (object.getClass().isArray()) {
            if (rawClazz == null || rawClazz.isAssignableFrom(JsonArray.class)) {
                JsonArray ja = new JsonArray();
                for (int i = 0; i < Array.getLength(object); i++) {
                    Object vv = readNode(Array.get(object, i), Object.class);
                    ja.add(vv);
                }
                return ja;
            } else if (rawClazz.isAssignableFrom(List.class) || List.class.isAssignableFrom(rawClazz)) {
                Type vtype = TypeUtil.resolveTypeArgument(type, List.class, 0);
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < Array.getLength(object); i++) {
                    Object vv = readNode(Array.get(object, i), vtype);
                    list.add(vv);
                }
                return list;
            } else if (rawClazz.isArray()) {
                Object arr = Array.newInstance(rawClazz.getComponentType(), Array.getLength(object));
                for (int i = 0; i < Array.getLength(object); i++) {
                    Object vv = readNode(Array.get(object, i), rawClazz.getComponentType());
                    Array.set(arr, i, vv);
                }
                return arr;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (NodeRegistry.isPojo(object.getClass())) {
            if (rawClazz == null || rawClazz.isAssignableFrom(JsonObject.class)) {
                JsonObject jo = new JsonObject();
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(object.getClass());
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                    Object v = entry.getValue().invokeGetter(object);
                    Object vv = readNode(v, Object.class);
                    jo.put(entry.getKey(), vv);
                }
                return jo;
            } else if (rawClazz.isAssignableFrom(Map.class) || Map.class.isAssignableFrom(rawClazz)) {
                Type vtype = TypeUtil.resolveTypeArgument(type, Map.class, 1);
                Map<String, Object> map = JsonConfig.global().mapSupplier.create();
                NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(object.getClass());
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                    Object v = entry.getValue().invokeGetter(object);
                    Object vv = readNode(v, vtype);
                    map.put(entry.getKey(), vv);
                }
                return map;
            } else if (JsonObject.class.isAssignableFrom(rawClazz)) {
                NodeRegistry.PojoInfo newPi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                Map<String, NodeRegistry.FieldInfo> newFields = newPi.getFields();
                JsonObject newJo = (JsonObject) newPi.newInstance();
                NodeRegistry.PojoInfo oldPi = NodeRegistry.registerPojoOrElseThrow(object.getClass());
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.getFields().entrySet()) {
                    String k = entry.getKey();
                    Object v = entry.getValue().invokeGetter(object);
                    NodeRegistry.FieldInfo fi = newFields.get(k);
                    if (fi != null) {
                        Object vv = readNode(v, fi.getType());
                        fi.invokeSetter(newJo, vv);
                    } else {
                        Object vv = readNode(v, Object.class);
                        newJo.put(k, vv);
                    }
                }
                return newJo;
            } else if (NodeRegistry.isPojo(rawClazz)) {
                NodeRegistry.PojoInfo newPi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
                Map<String, NodeRegistry.FieldInfo> newFields = newPi.getFields();
                Object newPojo = newPi.newInstance();
                NodeRegistry.PojoInfo oldPi = NodeRegistry.registerPojoOrElseThrow(object.getClass());
                for (Map.Entry<String, NodeRegistry.FieldInfo> entry : oldPi.getFields().entrySet()) {
                    String k = entry.getKey();
                    NodeRegistry.FieldInfo fi = newFields.get(k);
                    if (fi != null) {
                        Object v = entry.getValue().invokeGetter(object);
                        Object vv = readNode(v, fi.getType());
                        fi.invokeSetter(newPojo, vv);
                    }
                }
                return newPojo;
            } else {
                throw new JsonException("Cannot read from object '" + object.getClass() + "' to node '" + type + "'");
            }
        } else if (rawClazz == null || rawClazz.isInstance(object)) {
            return object;
        } else {
            throw new JsonException("Unexpected value type '" + object.getClass() + "'");
        }
    }

}
