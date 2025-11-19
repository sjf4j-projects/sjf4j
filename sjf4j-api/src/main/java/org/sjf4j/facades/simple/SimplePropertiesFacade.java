package org.sjf4j.facades.simple;

import lombok.NonNull;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonWalker;
import org.sjf4j.facades.PropertiesFacade;

import java.util.Properties;
import java.util.TreeSet;

public class SimplePropertiesFacade implements PropertiesFacade {

    @Override
    public JsonObject readNode(@NonNull Properties props) {
        JsonObject jo = new JsonObject();
        TreeSet<String> sortedKeys = new TreeSet<>(props.stringPropertyNames());
        for (String key : sortedKeys) {
            String value = props.getProperty(key);
            jo.putByPath(propKey2JsonPath(key), value);
        }
        return jo;
    }

    @Override
    public void writeNode(@NonNull Properties props, @NonNull JsonObject node) {
        JsonWalker.walkValues(node, (path, value) -> {
            if (value != null) {
                props.setProperty(jsonPath2PropKey(path.toString()), value.toString());
            }
        });
    }


    /// private

    private static String propKey2JsonPath(@NonNull String key) {
        return "$." + key;
    }

    private static String jsonPath2PropKey(@NonNull String path) {
        return path.substring(2);
    }

}
