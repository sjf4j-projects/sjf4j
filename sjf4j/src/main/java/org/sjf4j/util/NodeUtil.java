package org.sjf4j.util;

import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;


/**
 * Utility class for handling JSON node operations and type conversions.
 * 
 * <p>This class provides a set of static methods for safely converting between different
 * JSON node types, with strict type checking and appropriate exception handling. It
 * distinguishes between "toXxx" methods (which perform strict type checking) and "asXxx"
 * methods (which perform more flexible conversions).
 */
public class NodeUtil {

    /// Output

    /**
     * Converts a node to a String with strict type checking.
     *
     * @param node the node to convert
     * @return the string representation
     * @throws JsonException if the node is not a CharSequence, Character, or Enum
     */
    public static String toString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        throw new JsonException("Expected node type CharSequence or Character, but got " + node.getClass());
    }

    /**
     * Converts a node to a String with flexible type conversion.
     *
     * @param node the node to convert
     * @return the string representation
     */
    public static String asString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character) {
            return node.toString();
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).name();
        }
        return node.toString();
    }

    /**
     * Converts a node to a Number with strict type checking.
     *
     * @param node the node to convert
     * @return the number representation
     * @throws JsonException if the node is not a Number
     */
    public static Number toNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass());
    }

    /**
     * Converts a node to a Number with flexible type conversion.
     *
     * @param node the node to convert
     * @return the number representation
     * @throws JsonException if the node cannot be converted to a Number
     */
    public static Number asNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        } else if (node instanceof CharSequence || node instanceof Character) {
            String str = node.toString();
            if (NumberUtil.isNumeric(str)) {
                return NumberUtil.toNumber(node.toString());
            }
            throw new JsonException("Cannot convert String '" + node + "' to Number: not a number");
        } else if (node.getClass().isEnum()) {
            return ((Enum<?>) node).ordinal();
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Number: unsupported type");
    }


    /**
     * Converts a node to a Long with strict type checking.
     *
     * @param node the node to convert
     * @return the Long representation
     * @throws JsonException if the node is not a Number
     */
    public static Long toLong(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asLong((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    /**
     * Converts a node to a Long with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Long representation
     */
    public static Long asLong(Object node) {
        return NumberUtil.asLong(asNumber(node));
    }

    /**
     * Converts a node to an Integer with strict type checking.
     *
     * @param node the node to convert
     * @return the Integer representation
     * @throws JsonException if the node is not a Number
     */
    public static Integer toInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    /**
     * Converts a node to an Integer with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Integer representation
     */
    public static Integer asInteger(Object node) {
        return NumberUtil.asInteger(asNumber(node));
    }

    /**
     * Converts a node to a Short with strict type checking.
     *
     * @param node the node to convert
     * @return the Short representation
     * @throws JsonException if the node is not a Number
     */
    public static Short toShort(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asShort((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    /**
     * Converts a node to a Short with flexible type conversion.
     *
     * @param node the node to convert
     * @return the Short representation
     */
    public static Short asShort(Object node) {
        return NumberUtil.asShort(asNumber(node));
    }

    public static Byte toByte(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asByte((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Byte asByte(Object node) {
        return NumberUtil.asByte(asNumber(node));
    }

    public static Double toDouble(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asDouble((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Double asDouble(Object node) {
        return NumberUtil.asDouble(asNumber(node));
    }

    public static Float toFloat(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asFloat((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Float asFloat(Object node) {
        return NumberUtil.asFloat(asNumber(node));
    }

    public static BigInteger toBigInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asBigInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigInteger asBigInteger(Object node) {
        return NumberUtil.asBigInteger(asNumber(node));
    }

    public static BigDecimal toBigDecimal(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.asBigDecimal((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigDecimal asBigDecimal(Object node) {
        return NumberUtil.asBigDecimal(asNumber(node));
    }

    public static Boolean toBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        }
        throw new JsonException("Expected type Boolean, but got " + node.getClass().getName());
    }

    public static Boolean asBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        } else if (node instanceof String) {
            String str = ((String) node).toLowerCase();
            if ("true".equals(str) || "yes".equals(str) || "on".equals(str) || "1".equals(str)) return true;
            if ("false".equals(str) || "no".equals(str) || "off".equals(str) || "0".equals(str)) return false;
            throw new JsonException("Cannot convert String '" + node +
                    "' to Boolean: supported formats: true/false, yes/no, on/off, 1/0");
        } else if (node instanceof Number) {
            int i = ((Number) node).intValue();
            if (i == 1) return true;
            if (i == 0) return false;
            throw new JsonException("Cannot convert Number '" + node +
                    "' to Boolean: numeric values other than 0 or 1");
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Boolean: unsupported type");
    }

    public static JsonObject toJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        throw new JsonException("Expected node type JsonObject, but got " + node.getClass().getName());
    }

    public static JsonObject asJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        return new JsonObject(node);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Map) {
            return (Map<String, Object>) node;
        }
        throw new JsonException("Expected node type Map, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Map) {
            return (Map<String, Object>) node;
        } else if (node instanceof JsonObject) {
            return ((JsonObject) node).toMap();
        } else if (NodeRegistry.isPojo(node.getClass())) {
            Map<String, Object> map = Sjf4jConfig.global().mapSupplier.create();
            NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(node.getClass());
            for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object v = fi.getValue().invokeGetter(node);
                map.put(fi.getKey(), v);
            }
            return map;
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Map: unsupported type");
    }

    public static JsonArray toJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        throw new JsonException("Expected node type JsonArray, but got " + node.getClass().getName());
    }

    public static JsonArray asJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        return new JsonArray(node);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> toList(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof List) {
            return (List<Object>) node;
        }
        throw new JsonException("Expected node type List, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof List) {
            return (List<Object>) node;
        } else if (node instanceof JsonArray) {
            return ((JsonArray) node).toList();
        } else if (node.getClass().isArray()) {
            List<Object> list = Sjf4jConfig.global().listSupplier.create();
            int len = Array.getLength(node);
            if (len > 0) {
                for (int i = 0; i < len; i++) {
                    list.add(Array.get(node, i));
                }
            }
            return list;
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to List: unsupported type");
    }

    public static Object[] toArray(Object node) {
        if (node == null) {
            return null;
        } else if (node.getClass().isArray()) {
            if (node.getClass().getComponentType().isPrimitive()) {
                int length = Array.getLength(node);
                Object[] arr = new Object[length];
                for (int i = 0; i < length; i++) {
                    arr[i] = Array.get(node, i); // Auto boxing
                }
                return arr;
            } else {
                return (Object[]) node;
            }
        }
        throw new JsonException("Expected node type Array, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Object[] asArray(Object node) {
        if (node == null) {
            return null;
        } else if (node.getClass().isArray()) {
            if (node.getClass().getComponentType().isPrimitive()) {
                int length = Array.getLength(node);
                Object[] arr = new Object[length];
                for (int i = 0; i < length; i++) {
                    arr[i] = Array.get(node, i); // Auto boxing
                }
                return arr;
            } else {
                return (Object[]) node;
            }
        } else if (node instanceof List) {
            return ((List<Object>) node).toArray();
        } else if (node instanceof JsonArray) {
            return ((JsonArray) node).toArray();
        }
        throw new JsonException("Cannot convert " + node.getClass().getName() + " '" + node +
                "' to Array: unsupported type");
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T to(Object node, Class<T> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        if (node == null) {
            return null;
        } else if (clazz.isInstance(node)) {
            return (T) node;
        } else if (clazz == JsonObject.class) {
            return (T) toJsonObject(node);
        } else if (clazz == Map.class) {
            return (T) toMap(node);
        } else if (clazz == JsonArray.class) {
            return (T) toJsonArray(node);
        } else if (clazz == List.class) {
            return (T) toList(node);
        } else if (clazz.isArray()) {
            return (T) toArray(node);
        } else if (clazz == String.class) {
            return (T) toString(node);
        } else if (clazz == Boolean.class) {
            return (T) toBoolean(node);
        } else if (Number.class.isAssignableFrom(clazz)) {
            Number n = toNumber(node);
            return NumberUtil.as(n, clazz);
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class) {
                return (T) toBoolean(node);
            } else if (clazz == char.class && node instanceof Character) {
                return (T) node;
            } else {
                Number n = toNumber(node);
                return NumberUtil.as(n, clazz);
            }
        } else if (clazz.isEnum()) {
            if (node.getClass().isEnum()) return (T) node;
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                node.getClass().getName());
    }


    /// Only support JsonObject/JsonArray, but not POJO/Map/List
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T as(Object node, Class<T> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        if (node == null) {
            return null;
        } else if (clazz.isInstance(node)) {
            return (T) node;
        } else if (clazz == JsonObject.class) {
            return (T) asJsonObject(node);
        } else if (clazz == JsonArray.class) {
            return (T) asJsonArray(node);
        } else if (clazz == String.class) {
            return (T) asString(node);
        } else if (clazz == Boolean.class) {
            return (T) asBoolean(node);
        } else if (Number.class.isAssignableFrom(clazz)) {
            Number n = asNumber(node);
            return NumberUtil.as(n, clazz);
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class) {
                return (T) asBoolean(node);
            } else if (clazz == char.class && node instanceof Character) {
                return (T) (Character) asString(node).charAt(0);
            } else {
                Number n = asNumber(node);
                return NumberUtil.as(n, clazz);
            }
        } else if (clazz.isEnum()) {
            if (node.getClass().isEnum()) return (T) node;
            else return (T) Enum.valueOf((Class<? extends Enum>) clazz, asString(node));
        }

        try {
            return Sjf4j.fromNode(node, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to convert " + node.getClass().getName()  +
                    " to " + clazz.getName(), e);
        }

    }

    /// Basic

    public static boolean equals(Object source, Object target) {
        if (target == source) return true;
        if (source == null || target == null) return false;

        NodeType ntSource = NodeType.of(source);
        NodeType ntTarget = NodeType.of(target);
        if (ntSource.isNumber() && ntTarget.isNumber()) {
            return NumberUtil.compare((Number) source, (Number) target) == 0;
        } else if (ntSource.isValue() && ntTarget.isValue()) {
            return source.equals(target);
        } else if (ntSource == NodeType.OBJECT_POJO) {
            return source.equals(target);
        } else if (ntTarget == NodeType.OBJECT_POJO) {
            return target.equals(source);
        } else if (ntSource.isObject() && ntTarget.isObject()) {
            if ((ntSource == NodeType.OBJECT_JOJO || ntTarget == NodeType.OBJECT_JOJO)
                    && source.getClass() != target.getClass()) {
                return false;
            }
            if (NodeWalker.sizeInObject(source) != NodeWalker.sizeInObject(target)) return false;
            for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(source)) {
                Object subSrouce = entry.getValue();
                Object subTarget = NodeWalker.getInObject(target, entry.getKey());
                if (!equals(subSrouce, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            if ((ntSource == NodeType.ARRAY_JAJO || ntTarget == NodeType.ARRAY_JAJO)
                    && source.getClass() != target.getClass()) {
                return false;
            }
            if (NodeWalker.sizeInArray(source) != NodeWalker.sizeInArray(target)) return false;
            int size = NodeWalker.sizeInArray(source);
            for (int i = 0; i < size; i++) {
                if (!equals(NodeWalker.getInArray(source, i), NodeWalker.getInArray(target, i))) return false;
            }
            return true;
        } else if (ntSource.isUnknown() && ntTarget.isUnknown()) {
            return source.equals(target);
        }
        return false;
    }


}
