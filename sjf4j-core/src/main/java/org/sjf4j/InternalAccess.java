package org.sjf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Framework-only access to owned JsonObject/JsonArray storage.
 * Setters transfer ownership of a freshly created, unshared container without
 * copying or validation. Callers must never pass externally owned collections;
 * doing so bypasses the defensive-copy and JAJO element validation contracts.
 * This class is not intended for application-level mutation.
 */
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
