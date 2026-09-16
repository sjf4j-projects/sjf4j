package org.sjf4j.binding.simple;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.binding.PropertiesBinder;
import org.sjf4j.exception.BindingException;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.PathSyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Properties facade based on JSONPath expansion.
 */
public final class SimplePropertiesBinder implements PropertiesBinder {

    /**
     * Reads flat properties into an object, rejecting ambiguous or sparse paths.
     */
    @Override
    public JsonObject readNode(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        List<PropertyPath> paths = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            paths.add(new PropertyPath(key, _parsePath(key)));
        }
        paths.sort(PropertyPath.ORDER);

        JsonObject jo = new JsonObject();
        for (PropertyPath path : paths) {
            _put(jo, path, properties.getProperty(path.key));
        }
        return jo;
    }

    /**
     * Writes an object-rooted node as flattened scalar properties.
     */
    @Override
    public void writeNode(Properties properties, Object node) {
        Objects.requireNonNull(properties, "properties");
        if (!JsonType.of(node).isObject()) {
            throw new BindingException("Properties binding requires an object root");
        }

        Properties flattened = new Properties();
        Set<String> names = new HashSet<>();
        Nodes.forEachObject(node, (name, value) -> {
            names.add(name);
            if (value != null) _write(flattened, value, new PathSegment.Name(PathSegment.Root.INSTANCE, name));
        });

        // Flattening above validates the complete input before this replacement mutates properties.
        List<String> remove = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (properties.containsKey(key) && _isUnderTopLevelName(key, names)) remove.add(key);
        }
        for (String key : remove) properties.remove(key);
        properties.putAll(flattened);
    }


    /// private

    private static PathSegment[] _parsePath(String key) {
        try {
            PathSegment[] path = PathSyntax.parsePath(_propKey2JsonPath(key));
            for (PathSegment segment : path) {
                if (!(segment instanceof PathSegment.Root) && !(segment instanceof PathSegment.Name)
                        && !(segment instanceof PathSegment.Index)) {
                    throw new BindingException("invalid Properties path '" + key + "'");
                }
                if (segment instanceof PathSegment.Index && ((PathSegment.Index) segment).index < 0) {
                    throw new BindingException("invalid Properties path '" + key + "'");
                }
            }
            return path;
        } catch (BindingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BindingException("invalid Properties path '" + key + "'", e);
        }
    }

    private static String _propKey2JsonPath(String key) {
        return key.startsWith("[") ? "$" + key : "$." + key;
    }

    private static String _jsonPath2PropKey(PathSegment path) {
        String expression = PathSyntax.rootedPathExpr(path);
        return expression.startsWith("$.") ? expression.substring(2) : expression.substring(1);
    }

    /** Returns whether this subtree emitted a scalar property. */
    private static boolean _write(Properties properties, Object node, PathSegment path) {
        JsonType type = JsonType.of(node);
        if (type.isObject()) {
            boolean[] written = {false};
            Nodes.forEachObject(node, (key, value) -> {
                if (value != null && _write(properties, value, new PathSegment.Name(path, key))) {
                    written[0] = true;
                }
            });
            return written[0];
        } else if (type.isArray()) {
            boolean[] written = {false};
            Nodes.forEachArray(node, (index, value) -> {
                if (value == null)
                    throw new BindingException("arrays cannot contain null elements at index " + index);
                if (!_write(properties, value, new PathSegment.Index(path, index)))
                    throw new BindingException("arrays cannot contain empty elements at index " + index);
                written[0] = true;
            });
            return written[0];
        } else {
            properties.setProperty(_jsonPath2PropKey(path), String.valueOf(node));
            return true;
        }
    }

    private static boolean _isUnderTopLevelName(String key, Set<String> names) {
        try {
            PathSegment[] path = _parsePath(key);
            return path.length > 1 && path[1] instanceof PathSegment.Name
                    && names.contains(((PathSegment.Name) path[1]).name);
        } catch (BindingException ignored) {
            return false;
        }
    }

    private static void _put(JsonObject root, PropertyPath property, String value) {
        Object current = root;
        PathSegment[] path = property.path;
        for (int i = 1; i < path.length; i++) {
            PathSegment segment = path[i];
            boolean last = i == path.length - 1;
            if (segment instanceof PathSegment.Name) {
                String name = ((PathSegment.Name) segment).name;
                if (!(current instanceof JsonObject)) throw _collision(property.key);
                JsonObject object = (JsonObject) current;
                if (last) {
                    if (object.containsKey(name)) throw _collision(property.key);
                    object.put(name, value);
                    return;
                }
                current = _child(object, name, path[i + 1], property.key);
            } else {
                int index = ((PathSegment.Index) segment).index;
                if (index < 0 || !(current instanceof JsonArray)) throw _collision(property.key);
                JsonArray array = (JsonArray) current;
                if (index > array.size())
                    throw new BindingException("sparse array Properties path '" + property.key + "'");
                if (last) {
                    if (index != array.size()) throw _collision(property.key);
                    array.add(value);
                    return;
                }
                if (index == array.size()) array.add(_container(path[i + 1]));
                current = array.getNode(index);
                if (!_matches(current, path[i + 1])) throw _collision(property.key);
            }
        }
        throw new BindingException("Properties path '" + property.key + "' has no value segment");
    }

    private static Object _child(JsonObject object, String name, PathSegment next, String key) {
        if (!object.containsKey(name)) {
            Object container = _container(next);
            object.put(name, container);
            return container;
        }
        Object value = object.getNode(name);
        if (!_matches(value, next)) throw _collision(key);
        return value;
    }

    private static Object _container(PathSegment next) {
        return next instanceof PathSegment.Name ? new JsonObject() : new JsonArray();
    }

    private static boolean _matches(Object value, PathSegment next) {
        return next instanceof PathSegment.Name ? value instanceof JsonObject : value instanceof JsonArray;
    }

    private static BindingException _collision(String key) {
        return new BindingException("scalar/container path collision at Properties path '" + key + "'");
    }



    private static final class PropertyPath {
        static final Comparator<PropertyPath> ORDER = (left, right) -> {
            PathSegment[] a = left.path, b = right.path;
            for (int i = 1, limit = Math.min(a.length, b.length); i < limit; i++) {
                PathSegment x = a[i], y = b[i];
                if (x instanceof PathSegment.Index && y instanceof PathSegment.Index) {
                    int compare = Integer.compare(((PathSegment.Index) x).index, ((PathSegment.Index) y).index);
                    if (compare != 0) return compare;
                } else if (x instanceof PathSegment.Name && y instanceof PathSegment.Name) {
                    int compare = ((PathSegment.Name) x).name.compareTo(((PathSegment.Name) y).name);
                    if (compare != 0) return compare;
                } else if (x instanceof PathSegment.Name) return -1;
                else return 1;
            }
            return Integer.compare(a.length, b.length);
        };

        final String key;
        final PathSegment[] path;

        PropertyPath(String key, PathSegment[] path) {
            this.key = key;
            this.path = path;
        }
    }

}
