package org.sjf4j;

import java.util.ArrayList;
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

    public static ArrayList<Object> dynamicElements(JsonArray ja) {
        return ja.dynamicElements;
    }

    public static void dynamicElements(JsonArray ja, ArrayList<Object> elements) {
        ja.dynamicElements = elements;
    }

}
