package org.sjf4j;

import java.util.LinkedHashMap;

public final class InternalAccess {
    private InternalAccess() {
    }

    public static LinkedHashMap<String, Object> dynamicMembers(JsonObject jo) {
        return jo.dynamicMembers;
    }

    public static void dynamicMembers(JsonObject jo, LinkedHashMap<String, Object> members) {
        jo.dynamicMembers = members;
    }

}
