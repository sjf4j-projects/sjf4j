package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.ConverterRegistry;
import org.sjf4j.PojoRegistry;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class ObjectUtil {

    /// Input

    public static Object wrapObject(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof JsonObject.Builder) {
            return ((JsonObject.Builder) object).build();
        } else if (object.getClass().isEnum()) {
            return object.toString();
        }
        return object;
    }

    public static boolean isValidOrConvertible(Object object) {
        return object == null || isValidOrConvertible(object.getClass());
    }

    public static boolean isValidOrConvertible(@NonNull Class<?> type) {
        return type.isPrimitive() ||
                CharSequence.class.isAssignableFrom(type) || Character.class == type ||
                Number.class.isAssignableFrom(type) ||
                Boolean.class == type ||
                JsonObject.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) ||
                JsonArray.class.isAssignableFrom(type) || List.class.isAssignableFrom(type) || type.isArray() ||
                ConverterRegistry.hasConverter(type) ||
                PojoRegistry.isPojo(type);
    }


    /// Facade

    public static Object object2Value(Object object) {
        Object value = ConverterRegistry.tryWrap2Pure(object);
        if (value == null) {
            return null;
        } else if (value instanceof CharSequence || value instanceof Character || 
                value instanceof Number || value instanceof Boolean) {
            return value;
        } else if (value.getClass().isEnum()) {
            return value.toString();
        } else if (value instanceof JsonObject) {
            JsonObject jo = new JsonObject();
            ((JsonObject) value).forEach((k, v) -> {
                jo.put(k, object2Value(v));
            });
            return jo;
        } else if (value instanceof Map) {
            JsonObject jo = new JsonObject();
            ((Map<?, ?>) value).forEach((k, v) -> {
                jo.put(k.toString(), object2Value(v));
            });
            return jo;
        } else if (value instanceof JsonArray) {
            JsonArray ja = new JsonArray();
            ((JsonArray) value).forEach(v -> {
                ja.add(object2Value(v));
            });
            return ja;
        } else if (value instanceof List) {
            JsonArray ja = new JsonArray();
            ((List<?>) value).forEach(v -> {
                ja.add(object2Value(v));
            });
            return ja;
        } else if (value.getClass().isArray()) {
            JsonArray ja = new JsonArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                ja.add(object2Value(Array.get(value, i)));
            }
            return ja;
        } else if (PojoRegistry.isPojo(value.getClass())) {
            JsonObject jo = new JsonObject();
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(value.getClass()).getFields().entrySet()) {
                Object vv = entry.getValue().invokeGetter(value);
                jo.put(entry.getKey(), object2Value(vv));
            }
            return jo;
        } else {
            throw new IllegalStateException("Unsupported object type '" + object.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ObjectRegistry or a valid POJO, or a Map/List/Array of such elements.");
        }
    }


    public static Object value2Object(Object value, Type objectType) {
        if (objectType == null || objectType instanceof Class<?>) {
            return value2Object(value, (Class<?>) objectType);
        } else if (objectType instanceof ParameterizedType) {
            return value2Object(value, (ParameterizedType) objectType);
        } else if (objectType instanceof GenericArrayType) {
            return value2Object(value, (GenericArrayType) objectType);
        } else {
            throw new JsonException("Unsupported objectType '" + objectType + "'");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object value2Object(Object value, Class<?> objectType) {
        if (ConverterRegistry.hasConverter(objectType)) {
            return ConverterRegistry.tryPure2Wrap(value, objectType);
        }

        if (value == null) {
            return null;
        } else if (value instanceof CharSequence || value instanceof Character) {
            if (objectType == null || objectType.isAssignableFrom(value.getClass())) {
                return value;
            } else if (objectType == String.class) {
                return value.toString();
            } else if (objectType.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) objectType, value.toString());
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof Number) {
            return NumberUtil.numberAs((Number) value, objectType);
        } else if (value instanceof Boolean) {
            if (objectType == null || objectType == boolean.class || objectType.isAssignableFrom(value.getClass())) {
                return value;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof JsonObject) {
            if (objectType == null || objectType.isAssignableFrom(JsonObject.class)) {
                JsonObject jo = new JsonObject();
                ((JsonObject) value).forEach((k, v) -> {
                    jo.put(k, value2Object(v, Object.class));
                });
                return jo;
            } else if (objectType.isAssignableFrom(Map.class)) {
                Map<String, Object> map = new LinkedHashMap<>();
                ((JsonObject) value).forEach((k, v) -> {
                    map.put(k, value2Object(v, Object.class));
                });
                return map;
            } else if (JsonObject.class.isAssignableFrom(objectType)) {
                PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(objectType);
                JsonObject pjo = (JsonObject) pi.newInstance();
                Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
                ((JsonObject) value).forEach((k, v) -> {
                    PojoRegistry.FieldInfo fi = fields.get(k);
                    if (fi != null) {
                        Object vv = value2Object(v, fi.getType());
                        fi.invokeSetter(pjo, vv);
                    } else {
                        pjo.put(k, value2Object(v, Object.class));
                    }
                });
                return pjo;
            } else if (PojoRegistry.isPojo(objectType)) {
                PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(objectType);
                Object pojo = pi.newInstance();
                ((JsonObject) value).forEach((k, v) -> {
                    PojoRegistry.FieldInfo fi = pi.getFields().get(k);
                    if (fi != null) {
                        Object vv = value2Object(v, fi.getType());
                        fi.invokeSetter(pojo, vv);
                    }
                });
                return pojo;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof Map) {
            if (objectType == null || objectType.isAssignableFrom(JsonObject.class)) {
                JsonObject jo = new JsonObject();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    jo.put(entry.getKey().toString(), value2Object(entry.getValue(), Object.class));
                }
                return jo;
            } else if (objectType.isAssignableFrom(Map.class)) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(entry.getKey().toString(), value2Object(entry.getValue(), Object.class));
                }
                return map;
            } else if (JsonObject.class.isAssignableFrom(objectType)) {
                PojoRegistry.PojoInfo pi = PojoRegistry.registerOrElseThrow(objectType);
                JsonObject pjo;
                try {
                    pjo = (JsonObject) pi.getConstructor().invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke constructor in JsonObject " + objectType, e);
                }
                Map<String, PojoRegistry.FieldInfo> fields = pi.getFields();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    String k = entry.getKey().toString();
                    PojoRegistry.FieldInfo fi = fields.get(k);
                    if (fi != null) {
                        Object vv = value2Object(entry.getValue(), fi.getType());
                        fi.invokeSetter(pjo, vv);
                    } else {
                        pjo.put(k, value2Object(entry.getValue(), Object.class));
                    }
                }
                return pjo;
            } else if (PojoRegistry.isPojo(objectType)) {
                PojoRegistry.PojoInfo pi = PojoRegistry.register(objectType);
                Object pojo;
                try {
                    pojo = pi.getConstructor().invoke();
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke constructor in POJO " + objectType, e);
                }
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    PojoRegistry.FieldInfo fi = pi.getFields().get(entry.getKey().toString());
                    if (fi != null) {
                        Object vv = value2Object(entry.getValue(), fi.getType());
                        fi.invokeSetter(pojo, vv);
                    }
                }
                return pojo;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof JsonArray) {
            if (objectType == null || objectType.isAssignableFrom(JsonArray.class)) {
                JsonArray ja = new JsonArray();
                for (Object v : (JsonArray) value) {
                    ja.add(value2Object(v, Object.class));
                }
                return ja;
            } else if (objectType.isAssignableFrom(List.class)) {
                List<Object> list = new ArrayList<>();
                for (Object v : (JsonArray) value) {
                    list.add(value2Object(v, Object.class));
                }
                return list;
            } else if (objectType.isArray()) {
                JsonArray ja = (JsonArray) value;
                Object arr = Array.newInstance(objectType.getComponentType(), ja.size());
                for (int i = 0; i < ja.size(); i++) {
                    Array.set(arr, i, value2Object(ja.getObject(i), objectType.getComponentType()));
                }
                return arr;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof List) {
            if (objectType == null || objectType.isAssignableFrom(JsonArray.class)) {
                JsonArray ja = new JsonArray();
                for (Object v : (List<?>) value) {
                    ja.add(value2Object(v, Object.class));
                }
                return ja;
            } else if (objectType.isAssignableFrom(List.class)) {
                List<Object> list = new ArrayList<>();
                for (Object v : (List<?>) value) {
                    list.add(value2Object(v, Object.class));
                }
                return list;
            } else if (objectType.isArray()) {
                List<?> list = (List<?>) value;
                Object arr = Array.newInstance(objectType.getComponentType(), list.size());
                for (int i = 0; i < list.size(); i++) {
                    Array.set(arr, i, value2Object(list.get(i), objectType.getComponentType()));
                }
                return arr;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value.getClass().isArray()) {
            if (objectType == null || objectType.isAssignableFrom(JsonArray.class)) {
                JsonArray ja = new JsonArray();
                for (int i = 0; i < Array.getLength(value); i++) {
                    ja.add(value2Object(Array.get(value, i), value.getClass().getComponentType()));
                }
                return ja;
            } else if (objectType.isAssignableFrom(List.class)) {
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < Array.getLength(value); i++) {
                    list.add(value2Object(Array.get(value, i), value.getClass().getComponentType()));
                }
                return list;
            } else if (objectType.isArray()) {
                Object arr = Array.newInstance(objectType.getComponentType(), Array.getLength(value));
                for (int i = 0; i < Array.getLength(value); i++) {
                    Array.set(arr, i, value2Object(Array.get(value, i), objectType.getComponentType()));
                }
                return arr;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else {
            throw new JsonException("Unexpected value type '" + value.getClass() + "'");
        }
    }

    public static Object value2Object(Object value, @NonNull ParameterizedType objectType) {
//        if (ConverterRegistry.hasConverter(objectType)) {
//            return ConverterRegistry.tryNode2Object(value, objectType);
//        }

        if (value == null) {
            return null;
        } else if (value instanceof JsonObject) {
            if (objectType.getRawType() == Map.class) {
                Type vtype = objectType.getActualTypeArguments()[1];
                Map<String, Object> map = new LinkedHashMap<>();
                ((JsonObject) value).forEach((k, v) -> {
                    map.put(k, value2Object(v, vtype));
                });
                return map;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof Map) {
            if (objectType.getRawType() == Map.class) {
                Type vtype = objectType.getActualTypeArguments()[1];
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(entry.getKey().toString(), value2Object(entry.getValue(), vtype));
                }
                return map;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof JsonArray) {
            if (objectType.getRawType() == List.class) {
                Type vtype = objectType.getActualTypeArguments()[0];
                List<Object> list = new ArrayList<>();
                for (Object v : (JsonArray) value) {
                    list.add(value2Object(v, vtype));
                }
                return list;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value instanceof List) {
            if (objectType.getRawType() == List.class) {
                Type vtype = objectType.getActualTypeArguments()[0];
                List<Object> list = new ArrayList<>();
                for (Object v : (List<?>) value) {
                    list.add(value2Object(v, vtype));
                }
                return list;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else if (value.getClass().isArray()) {
            if (objectType.getRawType() == List.class) {
                Type vtype = objectType.getActualTypeArguments()[0];
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < Array.getLength(value); i++) {
                    list.add(value2Object(Array.get(value, i), vtype));
                }
                return list;
            } else {
                throw new JsonException("Cannot convert value " + value.getClass() + " to object " + objectType);
            }
        } else {
            throw new JsonException("Unexpected value type '" + value.getClass() + "' with objectType '" + objectType + "'");
        }
    }

    public static Object value2Object(Object value, @NonNull GenericArrayType objectType) {
//        if (ConverterRegistry.hasConverter(objectType)) {
//            return ConverterRegistry.tryNode2Object(value, objectType);
//        }

        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            Type vtype = objectType.getGenericComponentType();
            List<Object> list = new ArrayList<>();
            for (Object v : (JsonArray) value) {
                list.add(value2Object(v, vtype));
            }
            return list;
        } else if (value instanceof List) {
            Type vtype = objectType.getGenericComponentType();
            List<Object> list = new ArrayList<>();
            for (Object v : (List<?>) value) {
                list.add(value2Object(v, vtype));
            }
            return list;
        } else if (value.getClass().isArray()) {
            Type vtype = objectType.getGenericComponentType();
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                list.add(value2Object(Array.get(value, i), vtype));
            }
            return list;
        } else {
            throw new JsonException("Unexpected value type '" + value.getClass() + "' with objectType '" + objectType + "'");
        }
    }


}
