package org.sjf4j.facades;

import lombok.NonNull;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonWalker;

import java.util.Map;
import java.util.Properties;


/**
 * Warning: java.util.Properties does not maintain key order (extends Hashtable).
 * As a result, converting Properties to and from JSON may lead to mismatches
 */
public class PropertiesFacade {


    public static JsonObject fromProps(@NonNull Properties props) {
        JsonObject jo = new JsonObject();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue() == null ? null : entry.getValue().toString();
            jo.putByPath(propKey2JsonPath(key), value);
        }
        return jo;
    }


    public static void toProps(@NonNull Properties props, @NonNull JsonObject jo) {
        JsonWalker.walkValues(jo, (path, value) -> {
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
