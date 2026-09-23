package org.sjf4j;

import java.util.Map;

public final class InternalAccess {
    private InternalAccess() {
    }

    public static Map<String, Object> dynamicProperties(JsonObject jo) {
        return jo.dynamicProperties;
    }

    public static void dynamicProperties(JsonObject jo, Map<String, Object> properties) {
        jo.dynamicProperties = properties;
    }

}
