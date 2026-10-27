package org.sjf4j.binding;

import org.sjf4j.JsonObject;
import java.util.Properties;


/**
 * Properties binder for mapping {@link Properties} to object-rooted {@link JsonObject}s.
 *
 * <p>{@link Properties} does not preserve key order.
 * Scalar leaves are written with {@link String#valueOf(Object)} and read as
 * strings. Null object members and empty containers have no properties and
 * are therefore omitted. Each top-level property in a write replaces its prior
 * flattened properties, while unrelated top-level properties remain. Null and
 * empty array elements are rejected because they cannot be represented without
 * changing array shape; this is distinct from omitted object members and empty
 * containers, which are allowed.
 */
public interface PropertiesBinder {

    /**
     * Reads flattened properties into an object-rooted node.
     *
     * @throws org.sjf4j.exception.BindingException if a property path is invalid
     */
    JsonObject readNode(Properties props);

    /**
     * Writes an object-rooted node into the supplied Properties instance.
     * Unsupported array entries fail instead of changing the array's shape.
     */
    void writeNode(Properties props, Object node);

}
