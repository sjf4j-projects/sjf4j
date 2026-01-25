package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathToken;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
            Nodes.visitObject(container, (key, node) -> {
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
            Nodes.visitArray(container, (idx, node) -> {
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
            for (int i = 0, len = ja.size(); i < len; i++) {
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
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
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
