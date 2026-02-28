package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.path.PathSegment;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Traversal utilities for JSON containers with path tracking.
 */
public class NodeWalker {

    /// Walk
    /** Traversal order relative to child nodes. */
    public enum Order { TOP_DOWN, BOTTOM_UP }
    /** Node selection mode for visitor callbacks. */
    public enum Target { ANY, CONTAINER, VALUE }
    /** Visitor flow control. */
    public enum Control { CONTINUE, STOP }

    /**
     * Walks the node tree in top-down order and visits both containers and values.
     */
    public static void walk(Object container,
                            BiFunction<PathSegment, Object, Control> visitor) {
        walk(container, Target.ANY, Order.TOP_DOWN, -1, visitor);
    }

    /**
     * Walks the node tree in top-down order with explicit target selection.
     */
    public static void walk(Object container,
                            Target target,
                            BiFunction<PathSegment, Object, Control> visitor) {
        walk(container, target, Order.TOP_DOWN, -1, visitor);
    }

    /**
     * Walks the node tree with explicit target and traversal order.
     */
    public static void walk(Object container,
                            Target target,
                            NodeWalker.Order order,
                            BiFunction<PathSegment, Object, Control> visitor) {
        walk(container, target, order, -1, visitor);
    }

    /**
     * Walks a node tree with full traversal controls.
     * <p>
     * {@code maxDepth < 0} means unlimited depth. Traversal starts at root path.
     * Returning {@link Control#STOP} stops traversal of the current branch.
     */
    public static void walk(Object container,
                            Target target,
                            NodeWalker.Order order,
                            int maxDepth,
                            BiFunction<PathSegment, Object, Control> visitor) {
        Objects.requireNonNull(container, "container is null");
        Objects.requireNonNull(target, "target is null");
        Objects.requireNonNull(order, "order is null");
        Objects.requireNonNull(visitor, "visitor is null");
        _walk(container, new PathSegment.Root(null, container.getClass()), visitor, target, order, maxDepth);
    }


    /**
     * Walk variant based on {@link BiConsumer}, without stop-control support.
     */
    public static void walk2(Object container, Target target, NodeWalker.Order order, int maxDepth,
                             BiConsumer<PathSegment, Object> consumer) {
        _walk2(container, new PathSegment.Root(null, container.getClass()), consumer, target, order, maxDepth);
    }



    /// Private

    private static void _walk(Object container, PathSegment path,
                              BiFunction<PathSegment, Object, Control> visitor,
                              Target target, Order order, int remainingDepth) {
        if (remainingDepth == 0) return;

        JsonType jt = JsonType.of(container);
        if (jt.isObject()) {
            if (order == Order.TOP_DOWN && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
            Nodes.visitObject(container, (key, node) -> {
                PathSegment childPath = new PathSegment.Name(path, container.getClass(), key);
                _walk(node, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == Order.BOTTOM_UP && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
        } else if (jt.isArray()) {
            if (order == Order.TOP_DOWN && (target == Target.CONTAINER || target == Target.ANY)) {
                Control control = visitor.apply(path, container);
                if (control == Control.STOP) return;
            }
            Nodes.visitArray(container, (idx, node) -> {
                PathSegment childPath = new PathSegment.Index(path, container.getClass(), idx);
                _walk(node, childPath, visitor, target, order, remainingDepth - 1);
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


    private static void _walk2(Object container, PathSegment path,
                               BiConsumer<PathSegment, Object> consumer,
                               Target target, Order order, int remainingDepth) {
        if (remainingDepth == 0) return;
        if (container == null) {
            if (target == Target.VALUE) consumer.accept(path, container);
            return;
        }

        if (container instanceof JsonObject) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            ((JsonObject) container).forEach((key, node) -> {
                if (node != null) {
                    PathSegment childPath = new PathSegment.Name(path, container.getClass(), key);
                    _walk2(node, childPath, consumer, target, order, remainingDepth - 1);
                }
            });
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            return;
        }

        if (container instanceof Map) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                Object node = entry.getValue();
                if (node != null) {
                    PathSegment childPath = new PathSegment.Name(path, container.getClass(), entry.getKey().toString());
                    _walk2(node, childPath, consumer, target, order, remainingDepth - 1);
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            return;
        }

        if (container instanceof JsonArray) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            JsonArray ja = (JsonArray) container;
            for (int i = 0, len = ja.size(); i < len; i++) {
                Object node = ja.getNode(i);
                if (node != null) {
                    PathSegment childPath = new PathSegment.Index(path, container.getClass(), i);
                    _walk2(node, childPath, consumer, target, order, remainingDepth - 1);
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            return;
        }

        if (container instanceof List) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            List<?> list = (List<?>) container;
            for (int i = 0; i < list.size(); i++) {
                Object node = list.get(i);
                if (node != null) {
                    PathSegment childPath = new PathSegment.Index(path, container.getClass(), i);
                    _walk2(node, childPath, consumer, target, order, remainingDepth - 1);
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            return;
        }

        if (container.getClass().isArray()) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            int len = Array.getLength(container);
            for (int i = 0; i < len; i++) {
                Object node = Array.get(container, i);
                if (node != null) {
                    PathSegment childPath = new PathSegment.Index(path, container.getClass(), i);
                    _walk2(node, childPath, consumer, target, order, remainingDepth - 1);
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            return;
        }

        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojo(container.getClass());
        if (pi != null) {
            if (order == Order.TOP_DOWN && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            for (Map.Entry<String, NodeRegistry.FieldInfo> entry : pi.fields.entrySet()) {
                Object node = entry.getValue().invokeGetter(container);
                if (node != null) {
                    PathSegment childPath = new PathSegment.Name(path, container.getClass(), entry.getKey());
                    _walk2(node, childPath, consumer, target, order, remainingDepth - 1);
                }
            }
            if (order == Order.BOTTOM_UP && target == Target.CONTAINER) {
                consumer.accept(path, container);
            }
            return;
        }

        if (target == Target.VALUE) {
            consumer.accept(path, container);
        }
    }

}
