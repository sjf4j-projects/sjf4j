package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.NodeException;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;
import org.sjf4j.util.IdxConsumer;
import org.sjf4j.util.IdxPredicate;
import org.sjf4j.util.Types;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Structural access and traversal over native and supported external OBNT
  * representations. Gettable POJO properties participate in object views; indexed
 * mutations depend on whether an array representation supports them.
 */
public class NodeWalker {


    /**
     * Visits each gettable member in an object node.
     */
    @SuppressWarnings("unchecked")
    public static void forEachObject(Object node, BiConsumer<String, Object> consumer) {
        Asserts.notNull(consumer, "consumer");
        if (node == null) return;
        if (node instanceof Map) {
            ((Map<String, Object>) node).forEach(consumer);
            return;
        }
        if (node instanceof JsonObject) {
            ((JsonObject) node).forEach(consumer);
            return;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            JsonType jt = ti.externalNode.jsonType(node);
            if (jt == JsonType.NULL) return;
            if (jt == JsonType.OBJECT) {
                ti.externalNode.forEachObject(node, consumer);
                return;
            }
        }
        if (ti.pojoInfo != null) {
            for (PropertyInfo propertyInfo : ti.pojoInfo.gettableProperties) {
                Object value = propertyInfo.invokeGetter(node);
                consumer.accept(propertyInfo.name, value);
            }
            return;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }


    /**
     * Returns true if any gettable object entry matches the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean anyMatchInObject(Object node, BiPredicate<String, Object> predicate) {
        Asserts.notNull(node, "node");
        Asserts.notNull(predicate, "predicate");
        if (node instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) node).entrySet()) {
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).anyMatch(predicate);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.anyMatchObject(node, predicate);
            }
        }
        if (ti.pojoInfo != null) {
            for (PropertyInfo propertyInfo : ti.pojoInfo.gettableProperties) {
                Object value = propertyInfo.invokeGetter(node);
                if (predicate.test(propertyInfo.name, value)) {
                    return true;
                }
            }
            return false;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }


    /**
     * Replaces object-entry values in place using the given mapper.
     * <p>
     * The mapper receives each gettable-and-settable entry key and current value,
     * and returns the new value to store. Returns true when at least one entry
     * changes by reference.
     */
    @SuppressWarnings("unchecked")
    public static boolean replaceAllInObject(Object node, BiFunction<String, Object, Object> replacer) {
        Asserts.notNull(node, "node");
        Asserts.notNull(replacer, "replacer");
        if (node instanceof Map) {
            boolean changed = false;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) node).entrySet()) {
                Object oldValue = entry.getValue();
                Object newValue = replacer.apply(entry.getKey(), oldValue);
                if (oldValue != newValue) {
                    entry.setValue(newValue);
                    changed = true;
                }
            }
            return changed;
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).replaceAll(replacer);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.replaceAllInObject(node, replacer);
            }
        }
        if (ti.pojoInfo != null) {
            boolean changed = false;
            for (PropertyInfo propertyInfo : ti.pojoInfo.gettableProperties) {
                if (!propertyInfo.settable) continue;
                Object oldValue = propertyInfo.invokeGetter(node);
                Object newValue = replacer.apply(propertyInfo.name, oldValue);
                if (oldValue != newValue) {
                    propertyInfo.invokeSetter(node, newValue);
                    changed = true;
                }
            }
            return changed;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Removes object members that match the predicate.
     * <p>
     * This operation applies to removable object members only. Declared POJO
     * properties are not considered removable members and therefore are left
     * unchanged. For supported backend-native object tree representations,
     * matching keys are collected first and removed afterward so live key views
     * remain safe to traverse.
     */
    @SuppressWarnings("unchecked")
    public static boolean removeIfInObject(Object node, BiPredicate<String, Object> predicate) {
        Asserts.notNull(node, "node");
        Asserts.notNull(predicate, "predicate");

        if (node instanceof Map) {
            return ((Map<String, Object>) node).entrySet().removeIf(entry ->
                    predicate.test(entry.getKey(), entry.getValue()));
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.removeIfInObject(node, predicate);
            }
        }
        if (ti.pojoInfo != null) {
            return false;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }


    /**
     * Visits each element in an array node.
     */
    @SuppressWarnings("unchecked")
    public static void forEachArray(Object node, IdxConsumer<Object> consumer) {
        Asserts.notNull(node, "node");
        Asserts.notNull(consumer, "consumer");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0, len = list.size(); i < len; i++) consumer.accept(i, list.get(i));
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).forEach(consumer);
            return;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) consumer.accept(i, Array.get(node, i));
            return;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) consumer.accept(i++, v);
            return;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                ti.externalNode.forEachArray(node, consumer);
                return;
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Returns true if any element matches the predicate.
     */
    @SuppressWarnings("unchecked")
    public static boolean anyMatchInArray(Object node, IdxPredicate<Object> predicate) {
        Asserts.notNull(node, "node");
        Asserts.notNull(predicate, "predicate");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0, len = list.size(); i < len; i++) {
                if (predicate.test(i, list.get(i))) return true;
            }
            return false;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            for (int i = 0, len = ja.size(); i < len; i++) {
                if (predicate.test(i, ja.getNode(i))) return true;
            }
            return false;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            for (int i = 0; i < len; i++) {
                if (predicate.test(i, Array.get(node, i))) return true;
            }
            return false;
        }
        if (node instanceof Set) {
            Set<Object> set = (Set<Object>) node;
            int i = 0;
            for (Object v : set) {
                if (predicate.test(i++, v)) return true;
            }
            return false;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                return ti.externalNode.anyMatchInArray(node, predicate);
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Returns the number of gettable members in an object node.
     */
    public static int sizeInObject(Object node) {
        Asserts.notNull(node, "node");
        if (node instanceof Map) {
            return ((Map<?, ?>) node).size();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).size();
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.sizeInObject(node);
            }
        }
        if (ti.pojoInfo != null) {
            return ti.pojoInfo.gettableProperties.length;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Returns the number of elements in an array node.
     */
    public static int sizeInArray(Object node) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            return ((List<?>) node).size();
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).size();
        }
        if (node.getClass().isArray()) {
            return Array.getLength(node);
        }
        if (node instanceof Set) {
            return ((Set<?>) node).size();
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                return ti.externalNode.sizeInArray(node);
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Returns the gettable key set for an object node.
     * <p>
     * Map and {@link JsonObject} inputs return their live key views. POJO inputs
      * return the structural gettable-property key view; callers must treat it as
     * read-only metadata.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> keySetInObject(Object node) {
        Asserts.notNull(node, "node");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).keySet();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).keySet();
        }
        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.keySetInObject(node);
            }
        }
        if (ti.pojoInfo != null) {
            PojoInfo pi = ti.pojoInfo;
            if (pi.gettableProperties.length == 0) return Collections.emptySet();
            return new AbstractSet<String>() {
                @Override
                public Iterator<String> iterator() {
                    return new Iterator<String>() {
                        private int index;

                        @Override
                        public boolean hasNext() {
                            return index < pi.gettableProperties.length;
                        }

                        @Override
                        public String next() {
                            if (!hasNext()) throw new NoSuchElementException();
                            return pi.gettableProperties[index++].name;
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.gettableProperties.length;
                }

                @Override
                public boolean contains(Object key) {
                    if (!(key instanceof String)) {
                        return false;
                    }

                    PropertyInfo property = pi.getPropertyNoAlias((String) key);
                    return property != null && property.gettable;
                }
            };

        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Returns the gettable member set for an object node.
     * <p>
     * Map and {@link JsonObject} inputs return live entry views. POJO inputs
      * return a read-only projection of gettable properties; modifying returned
     * entries does not write back to the POJO.
     */
    @SuppressWarnings("unchecked")
    public static Set<Map.Entry<String, Object>> entrySetInObject(Object node) {
        Asserts.notNull(node, "node");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).entrySet();
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).entrySet();
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.entrySetInObject(node);
            }
        }
        if (ti.pojoInfo != null) {
            PojoInfo pi = ti.pojoInfo;
            if (pi.gettableProperties.length == 0) {
                return Collections.emptySet();
            }
            return new AbstractSet<Map.Entry<String, Object>>() {
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Iterator<Map.Entry<String, Object>>() {
                        private int index;
                        @Override
                        public boolean hasNext() {
                            return index < pi.gettableProperties.length;
                        }
                        @Override
                        public Map.Entry<String, Object> next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            PropertyInfo propertyInfo = pi.gettableProperties[index++];
                            return new AbstractMap.SimpleImmutableEntry<>(propertyInfo.name, propertyInfo.invokeGetter(node));
                        }
                    };
                }

                @Override
                public int size() {
                    return pi.gettableProperties.length;
                }
            };
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Returns an iterator over an array node.
     */
    @SuppressWarnings("unchecked")
    public static Iterator<Object> iteratorInArray(Object node) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            return ((List<Object>) node).iterator();
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).iterator();
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            return new Iterator<Object>() {
                int i = 0;
                public boolean hasNext() {
                    return i < len;
                }
                public Object next() {
                    if (i >= len) throw new NoSuchElementException();
                    return Array.get(node, i++);
                }
            };
        }
        if (node instanceof Set) {
            return ((Set<Object>) node).iterator();
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                return ti.externalNode.iteratorInArray(node);
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Returns true when an object node contains a gettable key.
     */
    @SuppressWarnings("unchecked")
    public static boolean containsInObject(Object node, String key) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).containsKey(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).containsKey(key);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.containsInObject(node, key);
            }
        }
        if (ti.pojoInfo != null) {
            PropertyInfo propertyInfo = ti.pojoInfo.getPropertyNoAlias(key);
            return propertyInfo != null && propertyInfo.gettable;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Returns whether an index is valid for an array node.
     * <p>
     * Negative indexes are normalized against current size.
     */
    public static boolean containsInArray(Object node, int idx) {
        int len = sizeInArray(node);
        idx = idx < 0 ? len + idx : idx;
        return idx >= 0 && idx < len;
    }

    /**
     * Gets a value by key from an object node.
     * <p>
     * Only gettable members participate in this view. For POJO representations, properties
     * without a getter behave as absent and return {@code null}.
     */
    public static Object getInObject(Object node, String key) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        if (node instanceof Map) {
            return ((Map<?, ?>) node).get(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).getNode(key);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.getInObject(node, key);
            }
        }
        if (ti.pojoInfo != null) {
            PropertyInfo propertyInfo = ti.pojoInfo.getPropertyNoAlias(key);
            return propertyInfo != null && propertyInfo.gettable ? propertyInfo.invokeGetter(node) : null;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Gets a value by key from an object node and converts it to the
     * requested target type.
     */
    public static <T> T getInObject(Object node, String key, Class<T> clazz) {
        Asserts.notNull(clazz, "clazz");
        Object sub = getInObject(node, key);
        return NodeMapper.to(sub, clazz, RuntimeContext.EMPTY);
    }

    /**
     * Gets a value by index from an array node.
     * <p>
     * Negative indexes are supported ({@code -1} means last element). For List,
     * Array, and Set, out-of-range access returns {@code null}. JsonArray behavior
     * follows {@link JsonArray#getNode(int)}.
     */
    @SuppressWarnings("unchecked")
    public static Object getInArray(Object node, int idx) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            int size = list.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx < 0 || idx >= size) {
                return null;
            } else {
                return list.get(idx);
            }
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).getNode(idx);
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx < 0 || idx >= len) {
                return null;
            } else {
                return Array.get(node, idx);
            }
        }
        if (node instanceof Set) {
            throw new NodeException("cannot call getInArray() on an unordered Java Set");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                return ti.externalNode.getInArray(node, idx);
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Gets a value by index from an array node and converts it to the
     * requested target type.
     */
    public static <T> T getInArray(Object node, int idx, Class<T> clazz) {
        Asserts.notNull(clazz, "clazz");
        Object sub = getInArray(node, idx);
        return NodeMapper.to(sub, clazz, RuntimeContext.EMPTY);
    }

    /**
     * Mutable holder used by access helpers to report child-value metadata.
     * <p>
     * Callers typically reuse one instance across repeated lookups to avoid
     * allocating short-lived result wrappers.
     * <p>
     * {@code getAccess*} methods fill {@link #node}/{@link #present} for read
     * paths. {@code putAccess*} methods fill {@link #node}/{@link #type}/
     * {@link #puttable} for write and auto-create paths.
     */
    public static final class Access {
        /** child value (can be null) */
        public Object node;
        /** static Type of child resolved by the access helper */
        public Type type;
        /** whether the child location currently exists, even when its value is null */
        public boolean present;
        /**
         * Indicates whether public Nodes/JsonPath write operations can put or
         * auto-create at this location.
         * false means the container is locked (e.g. POJO without such field).
         */
        public boolean puttable;
    }

    /**
     * Resolves gettable object-child access and fills {@link Access}.
     * <p>
     * The output distinguishes present-null from missing values under read
      * semantics. Settable-only POJO properties are not gettable and therefore are
     * reported as absent.
     */
    @SuppressWarnings("unchecked")
    public static void getAccessInObject(Object node, String key, Access out) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        Asserts.notNull(out, "out");

        out.node = null;
        out.present = false;
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            out.node = map.get(key);
            out.present = out.node != null || map.containsKey(key);
            return;
        }
        if (node.getClass() == JsonObject.class) {
            JsonObject jo = (JsonObject) node;
            out.node = jo.getNode(key);
            out.present = out.node != null || jo.containsKey(key);
            return;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                ti.externalNode.getAccessInObject(node, key, out);
                return;
            }
        }
        if (ti.pojoInfo != null) {
            PropertyInfo propertyInfo = ti.pojoInfo.getPropertyNoAlias(key);
            if (propertyInfo != null) {
                if (!propertyInfo.gettable) return;
                out.node = propertyInfo.invokeGetter(node);
                out.present = true;
                return;
            }
            if (ti.pojoInfo.isJojo) {
                JsonObject jo = (JsonObject) node;
                out.node = jo.getNode(key);
                out.present = out.node != null || jo.containsKey(key);
            }
            return;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Resolves settable object-child access and fills {@link Access} with node/type metadata.
     * <p>
     * The output describes the current child value, inferred static type, and
     * whether public write APIs can create or replace content at this location.
     */
    @SuppressWarnings("unchecked")
    public static void putAccessInObject(Object node, Type type, String key, Access out) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        Asserts.notNull(out, "out");

        out.node = null;
        out.puttable = false;
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            out.node = map.get(key);
            out.type = Types.resolveTypeArgument(type, Map.class, 1);
            out.puttable = true;
            return;
        }
        if (node.getClass() == JsonObject.class) {
            JsonObject jo = (JsonObject) node;
            out.node = jo.getNode(key);
            out.type = Object.class;
            out.puttable = true;
            return;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                ti.externalNode.putAccessInObject(node, key, out);
                return;
            }
        }
        if (ti.pojoInfo != null) {
            PropertyInfo propertyInfo = ti.pojoInfo.getPropertyNoAlias(key);
            if (propertyInfo != null) {
                out.type = propertyInfo.type;
                out.puttable = propertyInfo.settable;
                if (propertyInfo.gettable) {
                    out.node = propertyInfo.invokeGetter(node);
                }
                return;
            }
            if (ti.pojoInfo.isJojo) {
                JsonObject jo = (JsonObject) node;
                out.node = jo.getNode(key);
                out.type = Object.class;
                out.puttable = true;
                return;
            }
            out.node = null;
            out.type = Object.class;
            out.puttable = false;
            return;
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Resolves gettable array-child access and fills {@link Access}.
     * <p>
     * The output distinguishes present-null from missing values under read
     * semantics. Negative indexes are normalized.
     */
    @SuppressWarnings("unchecked")
    public static void getAccessInArray(Object node, int idx, Access out) {
        Asserts.notNull(node, "node");
        Asserts.notNull(out, "out");

        out.node = null;
        out.present = false;
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            int size = list.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx >= 0 && idx < size) {
                out.node = list.get(idx);
                out.present = true;
            }
            return;
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            int size = ja.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx >= 0 && idx < size) {
                out.node = ja.getNode(idx);
                out.present = true;
            }
            return;
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                out.node = Array.get(node, idx);
                out.present = true;
            }
            return;
        }
        if (node instanceof Set) {
            throw new NodeException("cannot call getAccessInArray() on an unordered Java Set");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                ti.externalNode.getAccessInArray(node, idx, out);
                return;
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Resolves settable array-child access and fills {@link Access} with node/type metadata.
     * <p>
     * Negative indexes are normalized. Indexed access follows {@link #putInArray(Object, int, Object)}
     * put semantics: existing normalized indexes are puttable, and for appendable array nodes
     * {@code idx == size} is also puttable.
     * <p>
     * If {@code idx == null}, it is treated as append-to-tail and remains puttable
     * with {@code node == null}.
     */
    @SuppressWarnings("unchecked")
    public static void putAccessInArray(Object node, Type type, Integer idx, Access out) {
        Asserts.notNull(node, "node");
        Asserts.notNull(out, "out");

        out.type = Object.class;
        out.node = null;
        out.puttable = true;
        if (node instanceof List) {
            out.type = Types.resolveTypeArgument(type, List.class, 0);
            if (idx == null) return;
            List<Object> list = (List<Object>) node;
            int size = list.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx == size) {
                return;
            } else if (idx >= 0 && idx < size) {
                out.node = list.get(idx);
                return;
            }
            out.puttable = false;
            return;
        }
        if (node instanceof JsonArray) {
            if (idx == null) return;
            JsonArray ja = (JsonArray) node;
            int size = ja.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx == size) {
                return;
            } else if (idx >= 0 && idx < size) {
                out.node = ja.getNode(idx);
                return;
            }
            out.puttable = false;
            return;
        }
        if (node.getClass().isArray()) {
            out.type = node.getClass().getComponentType();
            if (idx != null) {
                int len = Array.getLength(node);
                idx = idx < 0 ? len + idx : idx;
                if (idx >= 0 && idx < len) {
                    out.node = Array.get(node, idx);
                    return;
                }
            }
            out.puttable = false;
            return;
        }
        if (node instanceof Set) {
            if (idx == null) return;
            throw new NodeException("cannot call putAccessInArray() on an unordered Java Set");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                ti.externalNode.putAccessInArray(node, idx, out);
                return;
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Creates a missing object node representation for path ensure operations.
     */
    public static Object createObjectNode(Class<?> clazz) {
        if (clazz == null || clazz == Object.class || Map.class.isAssignableFrom(clazz)) {
            return TypeRegistry.newMapContainer(clazz, 0, false);
        }
        if (clazz == JsonObject.class) {
            return new JsonObject();
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.externalNode != null) {
            if (clazz == ti.externalNode.nodeType() ||
                    ti.externalNode.jsonTypeOfClass(clazz) == JsonType.OBJECT) {
                return ti.externalNode.createObjectNode(clazz);
            }
        }
        if (ti.pojoInfo != null) {
            return ti.pojoInfo.creatorInfo.forceNewPojo();
        }
        throw new NodeException("cannot create object node of type '" + clazz + "'");
    }

    /**
     * Creates a missing array node representation for path ensure operations.
     */
    public static Object createArrayNode(Class<?> clazz) {
        if (clazz == null || clazz == Object.class || List.class.isAssignableFrom(clazz)) {
            return TypeRegistry.newListContainer(clazz, 0, false);
        }
        if (clazz == JsonArray.class) {
            return new JsonArray();
        }
        if (JsonArray.class.isAssignableFrom(clazz)) {
            return TypeRegistry.requireRegisteredPojoInfo(clazz).creatorInfo.forceNewPojo();
        }
        if (Set.class.isAssignableFrom(clazz)) {
            return TypeRegistry.newSetContainer(clazz, 0, false);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.externalNode != null) {
            if (clazz == ti.externalNode.nodeType() ||
                    ti.externalNode.jsonTypeOfClass(clazz) == JsonType.ARRAY) {
                return ti.externalNode.createArrayNode(clazz);
            }
        }
        throw new NodeException("cannot create array node of type '" + clazz + "'");
    }


    /**
     * Puts a value into an object node and returns the previous value when
     * the target shape exposes one.
     * <p>
     * For POJO representations, only settable declared properties can be written; unknown keys fail.
     * POJO property writes do not read back the old value and therefore return
     * {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static Object putInObject(Object node, String key, Object value) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).put(key, value);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).put(key, value);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.putInObject(node, key, value);
            }
        }
        if (ti.pojoInfo != null) {
            PropertyInfo fi = ti.pojoInfo.getPropertyNoAlias(key);
            if (fi != null) {
                if (fi.settable) {
                    fi.invokeSetter(node, value);
                    return null;
                }
                throw new NodeException("property '" + key + "' is not settable");
            } else {
                throw new NodeException("unknown field '" + key + "' in POJO '" +
                        node.getClass().getName() + "'");
            }
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Removes a key from an object node and returns the previous value.
     * <p>
     * Removal is supported for Map/JsonObject. POJO fields are structural and
     * cannot be removed.
     */
    @SuppressWarnings("unchecked")
    public static Object removeInObject(Object node, String key) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        if (node instanceof Map) {
            return ((Map<String, Object>) node).remove(key);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).remove(key);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                return ti.externalNode.removeInObject(node, key);
            }
        }
        if (ti.pojoInfo != null) {
            throw new NodeException("cannot remove field '" + key + "' from POJO '" +
                    node.getClass().getName() + "'");
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Computes and stores an object value when the current value is null.
     * <p>
     * Semantics follow {@link Map#computeIfAbsent(Object, Function)}: a non-null
     * current value is returned as-is; otherwise the mapping function is invoked,
     * and a non-null result is stored before being returned.
     */
    @SuppressWarnings("unchecked")
    public static <T> T computeIfAbsentInObject(Object node, String key, Function<String, T> computer) {
        Asserts.notNull(node, "node");
        Asserts.notNull(key, "key");
        Asserts.notNull(computer, "computer");
        if (node instanceof Map) {
            return ((Map<String, T>) node).computeIfAbsent(key, computer);
        }
        if (node instanceof JsonObject) {
            return ((JsonObject) node).computeIfAbsent(key, computer);
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.OBJECT) {
                T old = (T) ti.externalNode.getInObject(node, key);
                if (old != null) return old;
                T newNode = computer.apply(key);
                if (newNode != null) ti.externalNode.putInObject(node, key, newNode);
                return newNode;
            }
        }
        if (ti.pojoInfo != null) {
            PropertyInfo propertyInfo = ti.pojoInfo.getPropertyNoAlias(key);
            if (propertyInfo != null) {
                T old = propertyInfo.gettable ? (T) propertyInfo.invokeGetter(node) : null;
                if (old != null) {
                    return old;
                }
                T newNode = computer.apply(key);
                if (newNode != null) {
                    propertyInfo.invokeSetter(node, newNode);
                }
                return newNode;
            } else {
                throw new NodeException("unknown property '" + key + "' in POJO '" +
                        node.getClass().getName() + "'");
            }
        }
        throw new NodeException("expected object node, but was " + Types.name(node));
    }

    /**
     * Sets at an existing index or appends when the index equals the array size.
     * <p>
     * For {@code List} and {@code JsonArray}: when {@code idx == size} the value is appended;
     * otherwise delegates to {@link #setInArray(Object, int, Object)} (which rejects out-of-range
     * indices). Negative indices are resolved by {@code setInArray}, not treated as append.
     *
     * @return the previous value at the index when replacing, or {@code null} when appending
     */
    public static Object putInArray(Object node, int idx, Object value) {
        return _putInArray(node, idx, value, true);
    }

    /**
     * Sets a value in an array node by index.
     * <p>
     * For List/JsonArray: only existing normalized indexes may be replaced.
     * For Java arrays: only in-range replacement is allowed. Negative indexes are
     * supported.
     */
    public static Object setInArray(Object node, int idx, Object value) {
        return _putInArray(node, idx, value, false);
    }

    @SuppressWarnings("unchecked")
    private static Object _putInArray(Object node, int idx, Object value, boolean allowAppend) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            int size = list.size();
            idx = idx < 0 ? size + idx : idx;
            if (idx >= 0 && idx < size) {
                return list.set(idx, value);
            }
            if (allowAppend && idx == size) {
                list.add(value);
                return null;
            }
            throw new NodeException("cannot set at index " + idx + " in List of size " + size);
        }
        if (node instanceof JsonArray) {
            JsonArray ja = (JsonArray) node;
            if (ja.containsIndex(idx)) {
                return ja.set(idx, value);
            }
            if (allowAppend && idx == ja.size()) {
                ja.add(value);
                return null;
            }
            throw new NodeException("cannot set at index " + idx + " in JsonArray of size " + ja.size());
        }
        if (node.getClass().isArray()) {
            int len = Array.getLength(node);
            idx = idx < 0 ? len + idx : idx;
            if (idx >= 0 && idx < len) {
                Object old = Array.get(node, idx);
                Array.set(node, idx, value);
                return old;
            }
            if (allowAppend && idx == len) {
                throw new NodeException("cannot append to a Java array");
            }
            throw new NodeException("cannot set at index " + idx + " in Java array of size " + len);
        }
        if (node instanceof Set) {
            throw new NodeException("cannot set by index on an unordered Java Set");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                if (allowAppend && ti.externalNode.sizeInArray(node) == idx) {
                    ti.externalNode.addInArray(node, value);
                    return null;
                }
                return ti.externalNode.setInArray(node, idx, value);
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Appends a value to an array node.
     * <p>
     * Java arrays are fixed-size and therefore not appendable.
     */
    @SuppressWarnings("unchecked")
    public static void addInArray(Object node, Object value) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            ((List<Object>) node).add(value);
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).add(value);
            return;
        }
        if (node.getClass().isArray()) {
            throw new NodeException("cannot append to a Java array");
        }
        if (node instanceof Set) {
            ((Set<Object>) node).add(value);
            return;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                ti.externalNode.addInArray(node, value);
                return;
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Inserts a value at the given index of an array node.
     * <p>
     * Indexed insert is supported by List/JsonArray only. Set and Java array
     * inputs are rejected because they are unordered or fixed-size.
     */
    @SuppressWarnings("unchecked")
    public static void addInArray(Object node, int idx, Object value) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            list.add(idx, value);
            return;
        }
        if (node instanceof JsonArray) {
            ((JsonArray) node).add(idx, value);
            return;
        }
        if (node.getClass().isArray()) {
            throw new NodeException("cannot insert into a Java array");
        }
        if (node instanceof Set) {
            throw new NodeException("cannot call addInArray() with an index on an unordered Java Set");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                ti.externalNode.addInArray(node, idx, value);
                return;
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }

    /**
     * Removes an element by index from an array node.
     * <p>
     * Negative indexes are supported for List. Java arrays and Set do not support
     * index-based removal.
     */
    @SuppressWarnings("unchecked")
    public static Object removeInArray(Object node, int idx) {
        Asserts.notNull(node, "node");
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            idx = idx < 0 ? list.size() + idx : idx;
            return list.remove(idx);
        }
        if (node instanceof JsonArray) {
            return ((JsonArray) node).remove(idx);
        }
        if (node.getClass().isArray()) {
            throw new NodeException("cannot remove at index " + idx +
                    " from Java array of component type '" + node.getClass().getComponentType().getName() + "'");
        }
        if (node instanceof Set) {
            throw new NodeException("cannot call removeInArray() on an unordered Java Set");
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(node.getClass());
        if (ti.externalNode != null) {
            if (ti.externalNode.jsonType(node) == JsonType.ARRAY) {
                return ti.externalNode.removeInArray(node, idx);
            }
        }
        throw new NodeException("expected array node, but was " + Types.name(node));
    }


    /*
     * --------------------------------------------------------------
     * Walks
     * --------------------------------------------------------------
     */

    /** Traversal order relative to child nodes. */
    public enum WalkOrder { TOP_DOWN, BOTTOM_UP }
    /** Node selection mode for visitor callbacks. */
    public enum WalkTarget { ANY, CONTAINER, OBJECT, ARRAY, VALUE, STRING, NUMBER, BOOLEAN, NULL, UNKNOWN }

    /**
     * Walks an OBNT tree in top-down order and visits containers and value nodes.
     */
    public static void walk(Object container,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        walk(container, WalkTarget.ANY, WalkOrder.TOP_DOWN, -1, visitor);
    }

    /**
     * Walks an OBNT tree with full traversal controls.
     * <p>
     * {@code maxDepth < 0} means unlimited depth. Traversal starts at root path.
     * Returning {@link Boolean} stops traversal of the current branch.
     */
    public static void walk(Object container, WalkTarget target,
                            WalkOrder order, int maxDepth,
                            BiFunction<PathSegment, Object, Boolean> visitor) {
        Asserts.notNull(container, "container");
        Asserts.notNull(target, "target");
        Asserts.notNull(order, "order");
        Asserts.notNull(visitor, "visitor");
        _walk(container, PathSegment.Root.INSTANCE, visitor, target, order, maxDepth);
    }

    private static void _walk(Object node, PathSegment path,
                              BiFunction<PathSegment, Object, Boolean> visitor,
                              WalkTarget target, WalkOrder order, int remainingDepth) {
        if (remainingDepth == 0) return;

        JsonType jt = JsonType.of(node);
        if (jt.isObject()) {
            if (order == WalkOrder.TOP_DOWN &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.OBJECT)) {
                if (!visitor.apply(path, node)) return;
            }

            forEachObject(node, (key, subNode) -> {
                PathSegment childPath = new PathSegment.Name(path, key);
                _walk(subNode, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == WalkOrder.BOTTOM_UP &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.OBJECT)) {
                visitor.apply(path, node);
            }
        } else if (jt.isArray()) {
            if (order == WalkOrder.TOP_DOWN &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.ARRAY)) {
                if (!visitor.apply(path, node)) return;
            }

            forEachArray(node, (idx, subNode) -> {
                PathSegment childPath = new PathSegment.Index(path, idx);
                _walk(subNode, childPath, visitor, target, order, remainingDepth - 1);
            });
            if (order == WalkOrder.BOTTOM_UP &&
                    (target == WalkTarget.ANY || target == WalkTarget.CONTAINER || target == WalkTarget.ARRAY)) {
                visitor.apply(path, node);
            }
        } else if (jt.isString()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.STRING ) {
                visitor.apply(path, node);
            }
        } else if (jt.isNumber()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.NUMBER ) {
                visitor.apply(path, node);
            }
        } else if (jt.isBoolean()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.BOOLEAN ) {
                visitor.apply(path, node);
            }
        } else if (jt.isNull()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.NULL ) {
                visitor.apply(path, node);
            }
        } else if (jt.isUnknown()) {
            if (target == WalkTarget.ANY || target == WalkTarget.VALUE || target == WalkTarget.UNKNOWN ) {
                visitor.apply(path, node);
            }
        }
    }

}
