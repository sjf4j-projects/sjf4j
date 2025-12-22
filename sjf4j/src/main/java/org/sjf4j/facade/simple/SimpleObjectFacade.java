package org.sjf4j.facade.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeWalker;
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

    @Override
    public Object readNode(Object object, Type type) {

        if (object == null) {
            return readNull(type);
        } else if (object instanceof CharSequence || object instanceof Character || object instanceof Enum) {
            return readString(object.toString(), type);
        } else if (object instanceof Number) {
            return readNumber((Number) object, type);
        } else if (object instanceof Boolean) {
            return readBoolean((Boolean) object, type);
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(object.getClass());
        if (ci != null) {
            Class<?> rawClazz = TypeUtil.getRawClass(type);
            if (rawClazz == Object.class) {
                return ci.convert(object);
            } else if (rawClazz.isInstance(object)) {
                return ci.copy(object);
            } else {
                throw new JsonException("Cannot convert object from '" + object.getClass() + "' to '" + rawClazz + "'");
            }
        }

        if (object instanceof Map || object instanceof JsonObject || NodeRegistry.isPojo(object.getClass())) {
            return readObject(object, type);
        } else if (object instanceof List || object instanceof JsonArray || object.getClass().isArray()) {
            return readArray(object, type);
        }

        throw new JsonException("Cannot convert value of type '" + object.getClass().getName() +
                "' to target type '" + type + "'. No built-in mapping, POJO support, or Converter was found."
        );
    }

    private Object readNull(Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            return ci.unconvert(null);
        }
        return  null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readString(String s, Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isAssignableFrom(String.class)) {
            return s;
        }
        if (rawClazz == Character.class || rawClazz == char.class) {
            return s.charAt(0);
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            return ci.unconvert(s);
        }

        if (rawClazz.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) rawClazz, s);
        }

        throw new JsonException("Cannot deserialize String value '" + s + "' to target type '" +
                rawClazz.getName() + "'. Expected String, char, enum, or a registered Converter.");
    }


    private Object readNumber(Number n, Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz.isAssignableFrom(Number.class)) {
            return n;
        }
        if (Number.class.isAssignableFrom(rawClazz) ||
                (rawClazz.isPrimitive() && rawClazz != boolean.class && rawClazz != char.class)) {
            return NumberUtil.as(n, rawClazz);
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            return ci.unconvert(n);
        }

        throw new JsonException("Cannot deserialize Number value '" + n + "' (" + n.getClass().getName() +
                ") to target type '" + rawClazz.getName() + "'. Expected Number type or a registered Converter.");
    }

    private Object readBoolean(Boolean b, Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);
        if (rawClazz == boolean.class || rawClazz.isAssignableFrom(Boolean.class)) {
            return b;
        }

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (ci != null) {
            return ci.unconvert(b);
        }

        throw new JsonException("Cannot deserialize Boolean value '" + b + "' to target type '" +
                rawClazz.getName() + "'. Expected boolean/Boolean or a registered Converter.");
    }


    private Object readObject(Object container, Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (rawClazz.isAssignableFrom(Map.class) || ci != null) {
            Type valueType = TypeUtil.resolveTypeArgument(type, Map.class, 1);
            Map<String, Object> map = JsonConfig.global().mapSupplier.create();
            NodeWalker.visitObject(container, (k, v) -> {
                Object vv = readNode(v, valueType);
                map.put(k, vv);
            });
            return ci != null ? ci.unconvert(map) : map;
        }

        if (rawClazz.isAssignableFrom(JsonObject.class)) {
            JsonObject jo = new JsonObject();
            NodeWalker.visitObject(container, (k, v) -> {
                Object vv = readNode(v, Object.class);
                jo.put(k, vv);
            });
            return jo;
        }

        if (JsonObject.class.isAssignableFrom(rawClazz)) {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(rawClazz);
            Map<String, NodeRegistry.FieldInfo> fields = pi.getFields();
            JsonObject jojo = (JsonObject) pi.newInstance();
            NodeWalker.visitObject(container, (k, v) -> {
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
            NodeWalker.visitObject(container, (k, v) -> {
                NodeRegistry.FieldInfo fi = fields.get(k);
                if (fi != null) {
                    Object vv = readNode(v, fi.getType());
                    fi.invokeSetter(pojo, vv);
                }
            });
            return pojo;
        }

        throw new JsonException("Cannot deserialize object value to target type '" + rawClazz.getName() +
                "'. Expected Map, JsonObject, JOJO, POJO, or a registered Converter.");
    }


    private Object readArray(Object container, Type type) {
        Class<?> rawClazz = TypeUtil.getRawClass(type);

        NodeRegistry.ConvertibleInfo ci = NodeRegistry.getConvertibleInfo(rawClazz);
        if (rawClazz.isAssignableFrom(List.class) || ci != null) {
            Type valueType = TypeUtil.resolveTypeArgument(type, List.class, 0);
            List<Object> list = new ArrayList<>();
            NodeWalker.visitArray(container, (i, v) -> {
                Object vv = readNode(v, valueType);
                list.add(vv);
            });
            return ci != null ? ci.unconvert(list) : list;
        }

        if (rawClazz.isAssignableFrom(JsonArray.class)) {
            JsonArray ja = new JsonArray();
            NodeWalker.visitArray(container, (i, v) -> {
                Object vv = readNode(v, Object.class);
                ja.add(vv);
            });
            return ja;
        }

        if (rawClazz.isArray()) {
            Class<?> valueClazz = rawClazz.getComponentType();
            List<Object> list = new ArrayList<>();
            NodeWalker.visitArray(container, (i, v) -> {
                Object vv = readNode(v, valueClazz);
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
