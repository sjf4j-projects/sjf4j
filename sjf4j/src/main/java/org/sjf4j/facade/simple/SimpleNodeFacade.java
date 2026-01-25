package org.sjf4j.facade.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Numbers;
import org.sjf4j.node.Types;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class SimpleNodeFacade implements NodeFacade {

    @Override
    public Object readNode(Object node, Type type, boolean deepCopy) {
        Class<?> rawClazz = Types.getRawClass(type);
        if (!deepCopy) {
            if (rawClazz == Object.class || (type instanceof Class && rawClazz.isInstance(node))) {
                return node;
            }
        }

        NodeRegistry.ValueCodecInfo vci = NodeRegistry.getValueCodecInfo(rawClazz);
        if (vci != null) {
            if (rawClazz.isInstance(node)) {
                return deepCopy ? vci.copy(node) : node;
            } else {
                return vci.decode(node);
            }
        }

        if (node == null) {
            return readNull(rawClazz);
        } else if (node instanceof CharSequence || node instanceof Character || node instanceof Enum) {
            return readString(node.toString(), rawClazz);
        } else if (node instanceof Number) {
            return readNumber((Number) node, rawClazz);
        } else if (node instanceof Boolean) {
            return readBoolean((Boolean) node, rawClazz);
        } else if (node instanceof Map || node instanceof JsonObject || NodeRegistry.isPojo(node.getClass())) {
            return readObject(node, type, deepCopy);
        } else if (node instanceof List || node instanceof JsonArray || node.getClass().isArray()) {
            return readArray(node, type, deepCopy);
        }

        throw new JsonException("Cannot deserialize value of type '" + node.getClass().getName() +
                "' to target type '" + type + "'.");
    }

    private Object readNull(Class<?> rawClazz) {
        return  null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readString(String s, Class<?> rawClazz) {
        if (rawClazz.isAssignableFrom(String.class)) {
            return s;
        } else if (rawClazz == Character.class || rawClazz == char.class) {
            return s.charAt(0);
        } else if (rawClazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }
        throw new JsonException("Cannot deserialize String value '" + s + "' to target type '" +
                rawClazz.getName() + "'. Expected String, char, enum, or a registered Converter.");
    }


    private Object readNumber(Number n, Class<?> rawClazz) {
        if (rawClazz.isAssignableFrom(Number.class)) {
            return n;
        } else if (Number.class.isAssignableFrom(rawClazz) ||
                (rawClazz.isPrimitive() && rawClazz != boolean.class && rawClazz != char.class)) {
            return Numbers.as(n, rawClazz);
        }
        throw new JsonException("Cannot deserialize Number value '" + n + "' (" + n.getClass().getName() +
                ") to target type '" + rawClazz.getName() + "'. Expected Number type or a registered Converter.");
    }

    private Object readBoolean(Boolean b, Class<?> rawClazz) {
        if (rawClazz == boolean.class || rawClazz.isAssignableFrom(Boolean.class)) {
            return b;
        }
        throw new JsonException("Cannot deserialize Boolean value '" + b + "' to target type '" +
                rawClazz.getName() + "'. Expected boolean/Boolean or a registered Converter.");
    }


    private Object readObject(Object container, Type type, boolean deepCopy) {
        Class<?> rawClazz = Types.getRawClass(type);

        if (rawClazz.isAssignableFrom(Map.class) || Map.class.isAssignableFrom(rawClazz) ) {
            Type valueType = Types.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            Nodes.visitObject(container, (k, v) -> {
                Object vv = readNode(v, valueType, deepCopy);
                map.put(k, vv);
            });
            return map;
        }

        if (rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            Nodes.visitObject(container, (k, v) -> {
                Object vv = readNode(v, Object.class, deepCopy);
                jo.put(k, vv);
            });
            return jo;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            Nodes.visitObject(container, (k, v) -> {
                NodeRegistry.FieldInfo fi = fields.get(k);
                if (fi != null) {
                    Object vv = readNode(v, fi.getType(), deepCopy);
                    fi.invokeSetter(jojo, vv);
                } else {
                    Object vv = readNode(v, Object.class, deepCopy);
                    jojo.put(k, vv);
                }
            });
            return jojo;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(rawClazz);
        if (pi != null) {
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            Object pojo = pi.newInstance();
            Nodes.visitObject(container, (k, v) -> {
                NodeRegistry.FieldInfo fi = fields.get(k);
                if (fi != null) {
                    Object vv = readNode(v, fi.getType(), deepCopy);
                    fi.invokeSetter(pojo, vv);
                }
            });
            return pojo;
        }

        throw new JsonException("Cannot deserialize object value to target type '" + rawClazz.getName() +
                "'. Expected Map, JsonObject, JOJO, POJO, or a registered convertible object.");
    }


    private Object readArray(Object container, Type type, boolean deepCopy) {
        Class<?> rawClazz = Types.getRawClass(type);

        if (rawClazz.isAssignableFrom(List.class) || List.class.isAssignableFrom(rawClazz)) {
            Type valueType = Types.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            Nodes.visitArray(container, (i, v) -> {
                Object vv = readNode(v, valueType, deepCopy);
                list.add(vv);
            });
            return list;
        }

        if (rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            Nodes.visitArray(container, (i, v) -> {
                Object vv = readNode(v, Object.class, deepCopy);
                ja.add(vv);
            });
            return ja;
        }

        if (JsonArray.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            JsonArray ja = (JsonArray) pi.newInstance();
            Nodes.visitArray(container, (i, v) -> {
                Object vv = readNode(v, ja.elementType(), deepCopy);
                ja.add(vv);
            });
            return ja;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            Nodes.visitArray(container, (i, v) -> {
                Object vv = readNode(v, valueClazz, deepCopy);
                list.add(vv);
            });
            Object array = Array.newInstance(valueClazz, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(array, i, list.get(i));
            }
            return array;
        }

        throw new JsonException("Cannot deserialize array value to target type '" + rawClazz.getName() +
                "'. Expected List, JsonArray, array type, or a registered Converter.");
    }

}
