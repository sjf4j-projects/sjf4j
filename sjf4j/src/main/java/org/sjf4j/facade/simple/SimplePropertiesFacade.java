package org.sjf4j.facade.simple;

import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.facade.PropertiesFacade;

import java.util.Properties;
import java.util.TreeSet;

public class SimplePropertiesFacade implements PropertiesFacade {

    @Override
    public JsonObject readNode(Properties properties) {
        if (properties == null) throw new IllegalArgumentException("Properties must not be null");
        JsonObject jo = new JsonObject();
        TreeSet<String> sortedKeys = new TreeSet<>(properties.stringPropertyNames());
        for (String key : sortedKeys) {
            String value = properties.getProperty(key);
            String path = propKey2JsonPath(key);
            jo.ensurePutByPath(path, value);
        }
        return jo;
    }

    @Override
    public void writeNode(Properties properties, Object node) {
        if (properties == null) throw new IllegalArgumentException("Properties must not be null");
        NodeWalker.walk(node, NodeWalker.Target.VALUE, NodeWalker.Order.TOP_DOWN, (path, value) -> {
            if (value != null) {
                properties.setProperty(jsonPath2PropKey(path.toString()), value.toString());
            }
            return NodeWalker.Control.CONTINUE;
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
