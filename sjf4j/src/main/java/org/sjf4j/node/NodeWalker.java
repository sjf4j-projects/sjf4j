package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathToken;
import org.sjf4j.util.TypeUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public class NodeWalker {

    /// Walk
    public enum Order { TOP_DOWN, BOTTOM_UP }
    public enum Target { ANY, CONTAINER, VALUE }
    public enum Control { CONTINUE, STOP }

    public static void walk(Object container,
                            BiFunction<JsonPath, Object, Control> visitor) {
        walk(container, Target.ANY, Order.TOP_DOWN, 0, visitor);
    }

    public static void walk(Object container,
                            Target target,
                            BiFunction<JsonPath, Object, Control> visitor) {
        walk(container, target, Order.TOP_DOWN, 0, visitor);
    }

    public static void walk(Object container,
                            Target target,
                            NodeWalker.Order order,
                            BiFunction<JsonPath, Object, Control> visitor) {
        walk(container, target, order, 0, visitor);
    }

    public static void walk(Object container,
                            Target target,
                            NodeWalker.Order order,
                            int maxDepth,
                            BiFunction<JsonPath, Object, Control> visitor) {
        if (container == null) throw new IllegalArgumentException("Container must not be null");
        if (target == null) throw new IllegalArgumentException("Target must not be null");
        if (order == null) throw new IllegalArgumentException("Order must not be null");
        if (visitor == null) throw new IllegalArgumentException("Visitor must not be null");
        _walk(container, new JsonPath(), visitor, target, order, maxDepth);
    }


    public static void walk2(Object container, Target target, NodeWalker.Order order, int maxDepth,
                             BiConsumer<JsonPath, Object> consumer) {
        _walk2(container, new JsonPath(), consumer, target, order, maxDepth);
    }


    /// Visit

    @SuppressWarnings("unchecked")
    public static void visitObject(Object container, BiConsumer<String, Object> visitor) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(visitor, "visitor is null");
        if (container instanceof Map) {
            ((Map<String, Object>) container).forEach(visitor);
            return;
        }
        if (container instanceof JsonObject) {
            ((JsonObject) container).forEach(visitor);
            return;
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                Object node = entry.getValue().invokeGetter(container);
                visitor.accept(entry.getKey(), node);
            }
            return;
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }


    @SuppressWarnings("unchecked")
    public static void visitArray(Object container, BiConsumer<Integer, Object> visitor) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(visitor, "visitor is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                visitor.accept(i, list.get(i));
            }
            return;
        }
        if (container instanceof JsonArray) {
            ((JsonArray) container).forEach(visitor);
            return;
        }
        if (container.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(container); i++) {
                visitor.accept(i, Array.get(container, i));
            }
            return;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean anyMatchInArray(Object container, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(i, list.get(i))) return true;
            }
            return false;
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                if (predicate.test(i, ja.get(i))) return true;
            }
            return false;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(container, i))) return true;
            }
            return false;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());

    }

    @SuppressWarnings("unchecked")
    public static boolean allMatchInArray(Object container, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                if (!predicate.test(i, list.get(i))) return false;
            }
            return true;
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                if (!predicate.test(i, ja.get(i))) return false;
            }
            return true;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                if (!predicate.test(i, Array.get(container, i))) return false;
            }
            return true;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean noneMatchInArray(Object container, BiPredicate<Integer, Object> predicate) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(predicate, "predicate is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(i, list.get(i))) return false;
            }
            return true;
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                if (predicate.test(i, ja.get(i))) return false;
            }
            return true;
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(container, i))) return false;
            }
            return true;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    public static int sizeInObject(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof Map) {
            return ((Map<?, ?>) container).size();
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).size();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            return pi.getFields().size();
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    public static int sizeInArray(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            return ((List<?>) container).size();
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).size();
        }
        if (container.getClass().isArray()) {
            return Array.getLength(container);
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Set<String> keySetInObject(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).keySet();
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).keySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            return pi.getFields().keySet();
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object container) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).entrySet();
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).entrySet();
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            Set<Map.Entry<String, Object>> entrySet = new LinkedHashSet<>();
            for (Map.Entry<String, NodeRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object node = fi.getValue().invokeGetter(container);
                entrySet.add(new AbstractMap.SimpleEntry<>(fi.getKey(), node));
            }
            return entrySet;
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean containsInObject(Object container, String key) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).containsKey(key);
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).containsKey(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            return pi.getFields().containsKey(key);
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static boolean containsInArray(Object container, int idx) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            return idx >= 0 && idx < list.size();
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).containsIndex(idx);
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            return idx >= 0 && idx < len;
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    public static Object getInObject(Object container, String key) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof Map) {
            return ((Map<?, ?>) container).get(key);
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).get(key);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.getFields().get(key);
            return fi != null ? fi.invokeGetter(container) : null;
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }


    @SuppressWarnings("unchecked")
    public static Object getInArray(Object container, int idx) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.get(idx);
            }
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).get(idx);
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx < 0 || idx >= len) {
                return null;
            } else {
                return Array.get(container, idx);
            }
        }
        throw new JsonException("Invalid array container: " + container.getClass());
    }

    // return null, indicates that the value is a POJO without the key, and no additional keys can be inserted.
    // return TypedNode.of(null) means the value of the key is null, and you can insert it.
    @SuppressWarnings("unchecked")
    public static TypedNode getTypedInObject(TypedNode typedNode, String key) {
        Objects.requireNonNull(typedNode, "typedNode is null");
        Objects.requireNonNull(key, "key is null");
        Object node = typedNode.getNode();
        if (node instanceof Map) {
            Type subtype = TypeUtil.resolveTypeArgument(typedNode.getClazzType(), Map.class, 1);
            return TypedNode.of(((Map<String, Object>) node).get(key), subtype);
        }
        if (node.getClass() == JsonObject.class) {
            return TypedNode.infer(((JsonObject) node).getNode(key));
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(node.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.getFields().get(key);
            if (fi != null) {
                return TypedNode.of(fi.invokeGetter(node), fi.getType());
            } else if (node instanceof JsonObject) {
                return TypedNode.infer(((JsonObject) node).getNode(key));
            } else {
                return null;
            }
        }
        throw new JsonException("Invalid object typedNode: " + typedNode.getClass().getName());
    }

    // return null, indicates that the index of JsonArray/List/Array is invalid, and you can not set it.
    // return TypedNode.of(null), means the value of the index is null, and you can insert it.
    @SuppressWarnings("unchecked")
    public static TypedNode getTypedInArray(TypedNode typedNode, int idx) {
        Objects.requireNonNull(typedNode, "typedNode is null");
        Object node = typedNode.getNode();
        if (node instanceof List) {
            Type subtype = TypeUtil.resolveTypeArgument(typedNode.getClazzType(), List.class, 0);
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx >= 0 && idx < list.size()) {
                return TypedNode.of(list.get(idx), subtype);
            } else if (idx == list.size()){
                return TypedNode.nullOf(subtype);
            } else {
                return null;
            }
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            idx = idx < 0 ? ja.size() + idx : idx;
            if (idx >= 0 && idx <= ja.size()) {
                return TypedNode.infer(ja.getNode(idx));
            } else {
                return null;
            }
        }
        if (typedNode.getClass().isArray()) {
            Type subtype = typedNode.getClass().getComponentType();
            int len = Array.getLength(typedNode);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 || idx < len) {
                return TypedNode.of(Array.get(typedNode, idx), subtype);
            } else {
                return null;
            }
        }
        throw new JsonException("Invalid array typedNode: " + typedNode.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Object putInObject(Object container, String key, Object node) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof Map) {
            return ((Map<String, Object>) container).put(key, node);
        }
        if (container instanceof JsonObject) {
            return ((JsonObject) container).put(key, node);
        }
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            NodeRegistry.FieldInfo fi = pi.getFields().get(key);
            if (fi != null) {
                Object old = fi.invokeGetter(container);
                fi.invokeSetter(container, node);
                return old;
            } else {
                throw new JsonException("Not found field '" + key + "' in POJO container " +
                        container.getClass().getName());
            }
        }
        throw new JsonException("Invalid object container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Object setInArray(Object container, int idx, Object node) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx == list.size()) {
                list.add(node);
                return null;
            } else if (idx >= 0 && idx < list.size()) {
                return list.set(idx, node);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in List of size " +
                        list.size() + " (index < size: modify; index == size: append)");
            }
        }
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            if (idx == ja.size()) {
                ja.add(node);
                return null;
            } else if (ja.containsIndex(idx)) {
                return ja.set(idx, node);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in JsonArray of size " +
                        ja.size() + " (index < size: modify; index == size: append)");
            }
        }
        if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                Object old = Array.get(container, idx);
                Array.set(container, idx, node);
                return old;
            } else {
                throw new JsonException("Cannot set index " + idx + " in Array of size " +
                        len + " (index < size: modify)");
            }
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static void addInArray(Object container, Object node) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            ((List<Object>) container).add(node);
            return;
        }
        if (container instanceof JsonArray) {
            ((JsonArray) container).add(node);
            return;
        }
        if (container.getClass().isArray()) {
            throw new JsonException("Array do not support append operation");
        }
        throw new JsonException("Invalid array container: " + container.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static void addInArray(Object container, int idx, Object node) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            ((List<Object>) container).add(idx, node);
            return;
        }
        if (container instanceof JsonArray) {
            ((JsonArray) container).add(idx, node);
            return;
        }
        if (container.getClass().isArray()) {
            throw new JsonException("Java Array do not support add operation");
        }
        throw new JsonException("Invalid array container: " + container.getClass());
    }

    @SuppressWarnings("unchecked")
    public static Object removeInObject(Object container, String key) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(key, "key is null");
        if (container instanceof JsonObject) {
            return ((JsonObject) container).remove(key);
        }
        if (container instanceof Map) {
            return ((Map<String, Object>) container).remove(key);
        }
        if (NodeRegistry.isPojo(container.getClass())) {
            throw new JsonException("Cannot remove field '" + key + "' in POJO container '" +
                    container.getClass() + "'");
        }
        throw new JsonException("Invalid object container: " + container.getClass());
    }

    @SuppressWarnings("unchecked")
    public static Object removeInArray(Object container, int idx) {
        Objects.requireNonNull(container, "container is null");
        if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.remove(idx);
            }
        }
        if (container instanceof JsonArray) {
            return ((JsonArray) container).remove(idx);
        }
        if (container.getClass().isArray()) {
            throw new JsonException("Cannot remove index " + idx + " in Array container '" +
                    container.getClass().getComponentType() + "'");
        }
        throw new JsonException("Invalid array container: " + container.getClass());
    }



    /// Private

    private static void _walk(Object container, JsonPath path,
                              BiFunction<JsonPath, Object, Control> visitor,
                              Target target, Order order, int maxDepth) {
        if (maxDepth > 0 && path.depth() > maxDepth) return;

        NodeType nt = NodeType.of(container);
        if (nt.isObject()) {
            if (order == Order.TOP_DOWN && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
            NodeWalker.visitObject(container, (key, node) -> {
                if (node != null) {
                    path.push(new PathToken.Name(key));
                    _walk(node, path, visitor, target, order, maxDepth);
                    path.pop();
                }
            });
            if (order == Order.BOTTOM_UP && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
        } else if (nt.isArray()) {
            if (order == Order.TOP_DOWN && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
            NodeWalker.visitArray(container, (idx, node) -> {
                if (node != null) {
                    path.push(new PathToken.Index(idx));
                    _walk(node, path, visitor, target, order, maxDepth);
                    path.pop();
                }
            });
            if (order == Order.BOTTOM_UP && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
        } else {
            if (target == Target.VALUE || target == Target.ANY) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
        }
    }


    private static void _walk2(Object container, JsonPath path,
                               BiConsumer<JsonPath, Object> consumer,
                               Target target, Order order, int maxDepth) {
        if (maxDepth > 0 && path.depth() > maxDepth) return;

        if (container instanceof JsonObject) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            ((JsonObject) container).forEach((key, node) -> {
                if (node != null) {
                    path.push(new PathToken.Name(key));
                    _walk2(node, path, consumer, target, order, maxDepth);
                    path.pop();
                }
            });
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container instanceof Map) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                Object node = entry.getValue();
                if (node != null) {
                    path.push(new PathToken.Name(entry.getKey().toString()));
                    _walk2(node, path, consumer, target, order, maxDepth);
                    path.pop();
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container instanceof JsonArray) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                Object node = ja.getNode(i);
                if (node != null) {
                    path.push(new PathToken.Index(i));
                    _walk2(node, path, consumer, target, order, maxDepth);
                    path.pop();
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container instanceof List) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            List<?> list = (List<?>) container;
            for (int i = 0; i < list.size(); i++) {
                Object node = list.get(i);
                if (node != null) {
                    path.push(new PathToken.Index(i));
                    _walk2(node, path, consumer, target, order, maxDepth);
                    path.pop();
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container != null && container.getClass().isArray()) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            for (int i = 0; i < Array.getLength(container); i++) {
                Object node = Array.get(container, i);
                if (node != null) {
                    path.push(new PathToken.Index(i));
                    _walk2(node, path, consumer, target, order, maxDepth);
                    path.pop();
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container != null && NodeRegistry.isPojo(container.getClass())) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            NodeRegistry.PojoInfo pi = NodeRegistry.getPojoInfo(container.getClass());
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                Object node = entry.getValue().invokeGetter(container);
                if (node != null) {
                    path.push(new PathToken.Name(entry.getKey()));
                    _walk2(node, path, consumer, target, order, maxDepth);
                    path.pop();
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else {
            if (target == Target.VALUE) {
                consumer.accept(path, container);
            }
        }
    }

}
