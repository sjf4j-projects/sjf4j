package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeType;
import org.sjf4j.PojoRegistry;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeUtil {

    /// Output

    public static String toString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof CharSequence || node instanceof Character || node.getClass().isEnum()) {
            return node.toString();
        }
        throw new JsonException("Expected node type CharSequence or Character, but got " + node.getClass());
    }

    public static String asString(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof String || node instanceof Character) {
            return node.toString();
        }
        return node.toString();
    }

    public static Number toNumber(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return (Number) node;
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass());
    }

    public static Long asLong(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsLong((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Integer asInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Short asShort(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsShort((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Byte asByte(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsByte((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Double asDouble(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsDouble((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Float asFloat(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsFloat((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigInteger asBigInteger(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsBigInteger((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static BigDecimal asBigDecimal(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Number) {
            return NumberUtil.numberAsBigDecimal((Number) node);
        }
        throw new JsonException("Expected node type Number, but got " + node.getClass().getName());
    }

    public static Boolean toBoolean(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof Boolean) {
            return (Boolean) node;
        }
        throw new JsonException("Expected node type Boolean, but got " + node.getClass().getName());
    }

    public static JsonObject toJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        }
        throw new JsonException("Expected node type JsonObject, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static JsonObject asJsonObject(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonObject) {
            return (JsonObject) node;
        } else if (node instanceof Map) {
            return new JsonObject((Map<String, Object>) node);
        } else if (PojoRegistry.isPojo(node.getClass())) {
            return JsonObject.fromPojo(node);
        }
        throw new JsonException("Expected node type JsonObject/Map/POJO, but got '" + node.getClass() + "'");
    }

    public static JsonArray toJsonArray(Object node) {
        if (node == null) {
            return null;
        } else if (node instanceof JsonArray) {
            return (JsonArray) node;
        }
        throw new JsonException("Expected node type JsonArray, but got " + node.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static JsonArray asJsonArray(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof JsonArray) {
            return (JsonArray) value;
        } else if (value instanceof List) {
            return new JsonArray((List<Object>) value);
        } else if (value.getClass().isArray()) {
            return new JsonArray(value);
        }
        throw new JsonException("Expected node type JsonArray/List/Array, but got " + value.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T to(Object node, @NonNull Class<T> clazz) {
        if (node == null) {
            return null;
        } else if (clazz.isAssignableFrom(node.getClass())) {
            return (T) node;
        } else if (Number.class.isAssignableFrom(clazz)) {
            if (node instanceof Number) {
                return NumberUtil.numberAs((Number) node, clazz);
            }
        } else if (clazz.isPrimitive()) {
            if (clazz == boolean.class && node instanceof Boolean) {
                return (T) node;
            } else if (clazz == char.class && node instanceof Character) {
                return (T) node;
            } else if (node instanceof Number) {
                return NumberUtil.numberAs((Number) node, clazz);
            }
        } else if (clazz.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) clazz, node.toString());
        }
        throw new JsonException("Type mismatch, expected " + clazz.getName() + ", but got " +
                node.getClass().getName());
    }


    // J{email=.com, @User{@id=1, #name=Ja{a=b, c=d}}, Arr[haha, xi, 1])
    // M{..}
    // @User{..}
    // J[..]
    // L[..]
    // A[..]
    // !DateTime@12345
    public static String inspect(Object node) {
        StringBuilder sb = new StringBuilder();
        _inspect(node, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void _inspect(Object node, StringBuilder sb) {
        NodeType nt = NodeType.of(node);
        if (nt.isObject()) {
            if (node instanceof JsonObject) {
                JsonObject jo = (JsonObject) node;
                PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(node.getClass());
                if (pi == null) {
                    sb.append("J{");
                } else {
                    sb.append("@").append(node.getClass().getSimpleName()).append("{");
                }
                AtomicInteger idx = new AtomicInteger(0);
                jo.forEach((k, v) -> {
                    if (idx.getAndIncrement() > 0) sb.append(", ");
                    if (pi != null && pi.getFields().containsKey(k)) {
                        sb.append("*");
                    }
                    sb.append(k).append("=");
                    _inspect(v, sb);
                });
                sb.append("}");
            } else if (node instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) node;
                sb.append("M{");
                int idx = 0;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append(entry).append("=");
                    _inspect(entry.getValue(), sb);
                }
                sb.append("}");
            } else if (PojoRegistry.isPojo(node.getClass())) {
                PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(node.getClass());
                sb.append("@").append(node.getClass().getSimpleName()).append("{");
                int idx = 0;
                for (Map.Entry<String, PojoRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                    if (idx++ > 0) sb.append(", ");
                    sb.append("@").append(fi.getKey()).append("=");
                    Object v = fi.getValue().invokeGetter(node);
                    _inspect(v, sb);
                }
                sb.append("}");
            }
        } else if (nt.isArray()) {
            if (node instanceof JsonArray) {
                JsonArray ja = (JsonArray) node;
                sb.append("J[");
                int idx = 0;
                for (Object v : ja) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
            } else if (node instanceof List) {
                List<Object> list = (List<Object>) node;
                sb.append("L[");
                int idx = 0;
                for (Object v : list) {
                    if (idx++ > 0) sb.append(", ");
                    _inspect(v, sb);
                }
                sb.append("]");
            } else if (node.getClass().isArray()) {
                int len = Array.getLength(node);
                sb.append("A[");
                int idx = 0;
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(", ");
                    _inspect(Array.get(node, i), sb);
                }
                sb.append("]");
            }
        } else if (nt.isValue()) {
            sb.append(node);
        } else {
            sb.append("!").append(node);
        }
    }


}
