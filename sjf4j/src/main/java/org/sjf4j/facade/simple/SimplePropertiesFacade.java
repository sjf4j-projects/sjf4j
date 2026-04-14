package org.sjf4j.facade.simple;

import org.sjf4j.JsonObject;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.Paths;

import java.util.Objects;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Properties facade based on JSONPath expansion.
 */
public class SimplePropertiesFacade implements PropertiesFacade {

    /**
     * Reads flat properties into JsonObject by path expansion.
     */
    @Override
    public JsonObject readNode(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        JsonObject jo = new JsonObject();
        TreeSet<String> sortedKeys = new TreeSet<>(properties.stringPropertyNames());
        for (String key : sortedKeys) {
            String value = properties.getProperty(key);
            String path = propKey2JsonPath(key);
            jo.ensurePutByPath(path, value);
        }
        return jo;
    }

    /**
     * Writes scalar nodes as flattened properties.
     */
    @Override
    public void writeNode(Properties properties, Object node) {
        Objects.requireNonNull(properties, "properties");
        Nodes.walk(node, Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1,
                (ps, value) -> {
            if (value != null) {
                properties.setProperty(jsonPath2PropKey(Paths.rootedPathExpr(ps)), value.toString());
            }
            return true;
        });
    }


    /// private

    private static String propKey2JsonPath(String key) {
        return "$." + key;
    }

    private static String jsonPath2PropKey(String path) {
        return path.substring(2);
    }

}
