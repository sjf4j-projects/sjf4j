package org.sjf4j;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.util.NumberUtil;
import org.sjf4j.util.TriConsumer;
import org.sjf4j.util.TypeUtil;
import org.sjf4j.util.TypedNode;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

@Slf4j
public class JsonWalker {

    public enum WalkOrder { TOP_DOWN, BOTTOM_UP }
    public enum Target { ANY, VALUE, CONTAINER }

    /// Walk

    public static void walkValues(@NonNull Object container,
                                  @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk(container, new JsonPath(), consumer, Target.VALUE, WalkOrder.TOP_DOWN, 0);
    }

    public static void walkContainersBottomUp(@NonNull Object container,
                                              @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk(container, new JsonPath(), consumer, Target.CONTAINER, WalkOrder.BOTTOM_UP, 0);
    }

    public static void walk(@NonNull Object container,
                            @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk(container, new JsonPath(), consumer, Target.ANY, WalkOrder.TOP_DOWN, 0);
    }

    public static void walk(@NonNull Object container, @NonNull Target target,
                            @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk(container, new JsonPath(), consumer, target, WalkOrder.TOP_DOWN, 0);
    }

    public static void walk(@NonNull Object container, @NonNull Target target, @NonNull WalkOrder order,
                            @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk(container, new JsonPath(), consumer, target, order, 0);
    }

    public static void walk(@NonNull Object container, @NonNull Target target, @NonNull WalkOrder order, int maxDepth,
                            @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk(container, new JsonPath(), consumer, target, order, maxDepth);
    }

    public static void walk2(@NonNull Object container, @NonNull Target target, @NonNull WalkOrder order, int maxDepth,
                             @NonNull BiConsumer<JsonPath, Object> consumer) {
        _walk2(container, new JsonPath(), consumer, target, order, maxDepth);
    }

    /// Container Util

    @SuppressWarnings("unchecked")
    public static void visitObject(@NonNull Object container, @NonNull BiConsumer<String, Object> visitor) {
        if (container instanceof JsonObject) {
            ((JsonObject) container).forEach(visitor);
        } else if (container instanceof Map) {
            ((Map<String, Object>) container).forEach(visitor);
        } else if (PojoRegistry.isPojo(container.getClass())) {
            PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(container.getClass());
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                Object node = entry.getValue().invokeGetter(container);
                visitor.accept(entry.getKey(), node);
            }
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }


    @SuppressWarnings("unchecked")
    public static void visitArray(@NonNull Object container, @NonNull BiConsumer<Integer, Object> visitor) {
        if (container instanceof JsonArray) {
            ((JsonArray) container).forEach(visitor);
        } else if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            for (int i = 0; i < list.size(); i++) {
                visitor.accept(i, list.get(i));
            }
        } else if (container.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(container); i++) {
                visitor.accept(i, Array.get(container, i));
            }
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }


    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entrySetInObject(@NonNull Object container) {
        if (container instanceof JsonObject) {
            return ((JsonObject) container).entrySet();
        } else if (container instanceof Map) {
            return ((Map<String, Object>) container).entrySet();
        } else if (PojoRegistry.isPojo(container.getClass())) {
            Set<Map.Entry<String, Object>> entrySet = new LinkedHashSet<>();
            PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(container.getClass());
            for (Map.Entry<String, PojoRegistry.FieldInfo> fi : pi.getFields().entrySet()) {
                Object node = fi.getValue().invokeGetter(container);
                entrySet.add(new AbstractMap.SimpleEntry<>(fi.getKey(), node));
            }
            return entrySet;
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }


    public static int sizeInObject(@NonNull Object container) {
        if (container instanceof JsonObject) {
            return ((JsonObject) container).size();
        } else if (container instanceof Map) {
            return ((Map<?, ?>) container).size();
        } else if (PojoRegistry.isPojo(container.getClass())) {
            return PojoRegistry.getPojoInfo(container.getClass()).getFields().size();
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }

    public static int sizeInArray(@NonNull Object container) {
        if (container instanceof JsonArray) {
            return ((JsonArray) container).size();
        } else if (container instanceof List) {
            return ((List<?>) container).size();
        } else if (container.getClass().isArray()) {
            return Array.getLength(container);
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static Object getInObject(@NonNull Object container, @NonNull String key) {
        if (container instanceof JsonObject) {
            return ((JsonObject) container).get(key);
        } else if (container instanceof Map) {
            return ((Map<String, Object>) container).get(key);
        } else if (PojoRegistry.isPojo(container.getClass())) {
            PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(container.getClass(), key);
            if (fi != null) {
                return fi.invokeGetter(container);
            } else {
                return null;
            }
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static Object getInArray(@NonNull Object container, int idx) {
        if (container instanceof JsonArray) {
            return ((JsonArray) container).get(idx);
        } else if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.get(idx);
            }
        } else if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx < 0 || idx >= len) {
                return null;
            } else {
                return Array.get(container, idx);
            }
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }

    // return null, indicates that the value is a POJO without the key, and no additional keys can be inserted.
    // return TypedNode.of(null) means the value of the key is null, and you can insert it.
    @SuppressWarnings("unchecked")
    public static TypedNode getInObjectTyped(@NonNull TypedNode container, @NonNull String key) {
        Object node = container.getNode();
        if (PojoRegistry.isPojo(node.getClass())) {
            PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(node.getClass(), key);
            if (fi != null) {
                return TypedNode.of(fi.invokeGetter(node), fi.getType());
            } else if (node instanceof JsonObject) {
                return TypedNode.infer(((JsonObject) node).getJsonObject(key));
            } else {
                return null;
            }
        } else if (node instanceof Map) {
            Type subtype = TypeUtil.resolveTypeArgument(container.getType(), Map.class, 1);
            return TypedNode.of(((Map<String, Object>) node).get(key), subtype);
        } else if (node instanceof JsonObject) {
            return TypedNode.infer(((JsonObject) node).get(key));
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }

    // return null, indicates that the index of JsonArray/List/Array is invalid, and you can not set it.
    // return TypedNode.of(null), means the value of the index is null, and you can insert it.
    @SuppressWarnings("unchecked")
    public static TypedNode getInArrayTyped(@NonNull TypedNode container, int idx) {
        Object node = container.getNode();
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            idx = idx < 0 ? ja.size() + idx : idx;
            if (idx >= 0 && idx <= ja.size()) {
                return TypedNode.infer(ja.getObject(idx));
            } else {
                return null;
            }
        } else if (node instanceof List) {
            Type subtype = TypeUtil.resolveTypeArgument(container.getType(), List.class, 0);
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx >= 0 && idx < list.size()) {
                return TypedNode.of(list.get(idx), subtype);
            } else if (idx == list.size()){
                return TypedNode.nullOf(subtype);
            } else {
                return null;
            }
        } else if (container.getClass().isArray()) {
            Type subtype = container.getClass().getComponentType();
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 || idx < len) {
                return TypedNode.of(Array.get(container, idx), subtype);
            } else {
                return null;
            }
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static void putInObject(@NonNull Object container, @NonNull String key, Object node) {
        if (container instanceof JsonObject) {
            ((JsonObject) container).put(key, node);
        } else if (container instanceof Map) {
            ((Map<String, Object>) container).put(key, node);
        } else if (PojoRegistry.isPojo(container.getClass())) {
            PojoRegistry.FieldInfo fi = PojoRegistry.getFieldInfo(container.getClass(), key);
            if (fi != null) {
                fi.invokeSetter(container, node);
            } else {
                throw new JsonException("Not found field '" + key + "' of POJO container type '" +
                        container.getClass() + "'");
            }
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static void setInArray(@NonNull Object container, int idx, Object node) {
        if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            if (idx == ja.size()) {
                ja.add(node);
            } else if (ja.containsIndex(idx)) {
                ja.set(idx, node);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in JsonArray of size " +
                        ja.size() + " (index < size: modify; index == size: append)");
            }
        } else if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx == list.size()) {
                list.add(node);
            } else if (idx >= 0 && idx < list.size()) {
                list.set(idx, node);
            } else {
                throw new JsonException("Cannot set/add index " + idx + " in List of size " +
                        list.size() + " (index < size: modify; index == size: append)");
            }
        } else if (container.getClass().isArray()) {
            int len = Array.getLength(container);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                Array.set(container, idx, node);
            } else {
                throw new JsonException("Cannot set index " + idx + " in Array of size " +
                        len + " (index < size: modify)");
            }
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static void addInArray(@NonNull Object container, Object node) {
        if (container instanceof JsonArray) {
            ((JsonArray) container).add(node);
        } else if (container instanceof List) {
            ((List<Object>) container).add(node);
        } else if (container.getClass().isArray()) {
            throw new JsonException("Java arrays do not support append");
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static Object removeInObject(@NonNull Object container, @NonNull String key) {
        if (container instanceof JsonObject) {
            return ((JsonObject) container).remove(key);
        } else if (container instanceof Map) {
            return ((Map<String, Object>) container).remove(key);
        } else if (PojoRegistry.isPojo(container.getClass())) {
            throw new JsonException("Cannot remove field '" + key + "' in POJO container '" +
                    container.getClass() + "'");
        } else {
            throw new JsonException("Invalid object container: " + container.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public static Object removeInArray(@NonNull Object container, int idx) {
        if (container instanceof JsonArray) {
            return ((JsonArray) container).remove(idx);
        } else if (container instanceof List) {
            List<Object> list = (List<Object>) container;
            idx = idx < 0 ? list.size() + idx : idx;
            if (idx < 0 || idx >= list.size()) {
                return null;
            } else {
                return list.remove(idx);
            }
        } else if (container.getClass().isArray()) {
            throw new JsonException("Cannot remove index " + idx + " in Array container '" +
                    container.getClass().getComponentType() + "'");
        } else {
            throw new JsonException("Invalid array container: " + container.getClass());
        }
    }

    public static boolean equals(Object source, Object target) {
        if (target == source) return true;
        if (source == null || target == null) return false;
        NodeType ntSource = NodeType.of(source);
        NodeType ntTarget = NodeType.of(target);
        if (ntSource.isNumber() && ntTarget.isNumber()) {
            return NumberUtil.equals((Number) source, (Number) target);
        } else if (ntSource.isValue() && ntTarget.isValue()) {
            return source.equals(target);
        } else if (ntSource == NodeType.OBJECT_POJO) {
            return source.equals(target);
        } else if (ntTarget == NodeType.OBJECT_POJO) {
            return target.equals(source);
        } else if (ntSource.isObject() && ntTarget.isObject()) {
            if ((ntSource == NodeType.OBJECT_JOJO || ntTarget == NodeType.OBJECT_JOJO) &&
                    source.getClass() != target.getClass()) {
                return false;
            }
            if (sizeInObject(source) != sizeInObject(target)) return false;
            for (Map.Entry<String, Object> entry : entrySetInObject(source)) {
                Object subSrouce = entry.getValue();
                Object subTarget = getInObject(target, entry.getKey());
                if (!equals(subSrouce, subTarget)) return false;
            }
            return true;
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            if (sizeInArray(source) != sizeInArray(target)) return false;
            int size = sizeInArray(source);
            for (int i = 0; i < size; i++) {
                if (!equals(getInArray(source, i), getInArray(target, i))) return false;
            }
            return true;
        } else if (ntSource.isUnknown() && ntTarget.isUnknown()) {
            return source.equals(target);
        }
        return false;
    }

    public static Object deepCopy(Object container) {
        // TODO:
        return container;
    }

    public static void merge(Object source, Object target, boolean preferTarget, boolean needCopy) {
        if (source == null || target == null) return;
        NodeType ntSource = NodeType.of(source);
        NodeType ntTarget = NodeType.of(target);
        if (ntSource.isObject() && ntTarget.isObject()) {
            visitObject(target, (key, subTarget) -> {
                Object subSource = getInObject(source, key);
                NodeType ntSubSource = NodeType.of(subSource);
                NodeType ntSubTarget = NodeType.of(subTarget);
                if (ntSubTarget.isObject()) {
                    if (ntSubSource.isObject()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            putInObject(source, key, deepCopy(subTarget));
                        } else {
                            putInObject(source, key, subTarget);
                        }
                    }
                } else if (ntSubTarget.isArray()) {
                    if (ntSubSource.isArray()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            putInObject(source, key, deepCopy(subTarget));
                        } else {
                            putInObject(source, key, subTarget);
                        }
                    }
                } else if (preferTarget || subSource == null) {
                    putInObject(source, key, subTarget);
                }
            });
        } else if (ntSource.isArray() && ntTarget.isArray()) {
            visitArray(target, (i, subTarget) -> {
                Object subSource = getInArray(source, i);
                NodeType ntSubSource = NodeType.of(subSource);
                NodeType ntSubTarget = NodeType.of(subTarget);
                if (ntSubTarget.isObject()) {
                    if (ntSubSource.isObject()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            setInArray(source, i, deepCopy(subTarget));
                        } else {
                            setInArray(source, i, subTarget);
                        }
                    }
                } else if (ntSubTarget.isArray()) {
                    if (ntSubSource.isArray()) {
                        merge(subSource, subTarget, preferTarget, needCopy);
                    } else if (preferTarget || subSource == null) {
                        if (needCopy) {
                            setInArray(source, i, deepCopy(subTarget));
                        } else {
                            setInArray(source, i, subTarget);
                        }
                    }
                } else if (preferTarget || subSource == null) {
                    setInArray(source, i, subTarget);
                }
            });
        }
    }

    /// Private

    private static void _walk(Object container, JsonPath path,
                              BiConsumer<JsonPath, Object> consumer,
                              Target target, WalkOrder order, int maxDepth) {
        if (maxDepth > 0 && path.getDepth() > maxDepth) return;

        NodeType nt = NodeType.of(container);

        if (nt.isObject()) {
            if (order == WalkOrder.TOP_DOWN && (target == Target.CONTAINER || target == Target.ANY)) {
                consumer.accept(path, container);
            }
            visitObject(container, (key, node) -> {
                JsonPath newPath = path.copy().push(new PathToken.Name(key));
                if (node != null) {
                    _walk(node, newPath, consumer, target, order, maxDepth);
                }
            });
            if (order == WalkOrder.BOTTOM_UP && (target == Target.CONTAINER || target == Target.ANY)) {
                consumer.accept(path, container);
            }
        } else if (nt.isArray()) {
            if (order == WalkOrder.TOP_DOWN && (target == Target.CONTAINER || target == Target.ANY)) {
                consumer.accept(path, container);
            }
            visitArray(container, (idx, node) -> {
                JsonPath newPath = path.copy().push(new PathToken.Index(idx));
                if (node != null) {
                    _walk(node, newPath, consumer, target, order, maxDepth);
                }
            });
            if (order == WalkOrder.BOTTOM_UP && (target == Target.CONTAINER || target == Target.ANY)) {
                consumer.accept(path, container);
            }
        } else {
            if (target == Target.VALUE || target == Target.ANY) {
                consumer.accept(path, container);
            }
        }
    }


    private static void _walk2(Object container, JsonPath path,
                               BiConsumer<JsonPath, Object> consumer,
                               Target target, WalkOrder order, int maxDepth) {
        if (maxDepth > 0 && path.getDepth() > maxDepth) return;

        if (container instanceof JsonObject) {
            if (order == WalkOrder.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            ((JsonObject) container).forEach((key, node) -> {
                JsonPath newPath = path.copy().push(new PathToken.Name(key));
                if (node != null) {
                    _walk2(node, newPath, consumer, target, order, maxDepth);
                }
            });
            if (order == WalkOrder.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container instanceof Map) {
            if (order == WalkOrder.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                JsonPath newPath = path.copy().push(new PathToken.Name(entry.getKey().toString()));
                Object node = entry.getValue();
                if (node != null) {
                    _walk2(node, newPath, consumer, target, order, maxDepth);
                }
            }
            if (order == WalkOrder.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container instanceof JsonArray) {
            if (order == WalkOrder.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = ja.getObject(i);
                if (node != null) {
                    _walk2(node, newPath, consumer, target, order, maxDepth);
                }
            }
            if (order == WalkOrder.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container instanceof List) {
            if (order == WalkOrder.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            List<?> list = (List<?>) container;
            for (int i = 0; i < list.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = list.get(i);
                if (node != null) {
                    _walk2(node, newPath, consumer, target, order, maxDepth);
                }
            }
            if (order == WalkOrder.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container != null && container.getClass().isArray()) {
            if (order == WalkOrder.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            for (int i = 0; i < Array.getLength(container); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = Array.get(container, i);
                if (node != null) {
                    _walk2(node, newPath, consumer, target, order, maxDepth);
                }
            }
            if (order == WalkOrder.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else if (container != null && PojoRegistry.isPojo(container.getClass())) {
            if (order == WalkOrder.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            PojoRegistry.PojoInfo pi = PojoRegistry.getPojoInfo(container.getClass());
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry : pi.getFields().entrySet()) {
                JsonPath newPath = path.copy().push(new PathToken.Name(entry.getKey()));
                Object node = entry.getValue().invokeGetter(container);
                if (node != null) {
                    _walk2(node, newPath, consumer, target, order, maxDepth);
                }
            }
            if (order == WalkOrder.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
        } else {
            if (target == Target.VALUE) {
                consumer.accept(path, container);
            }
        }

    }

}
