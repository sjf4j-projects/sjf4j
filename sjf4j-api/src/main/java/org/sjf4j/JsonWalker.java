package org.sjf4j;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.BiConsumer;

@Slf4j
public class JsonWalker {

    public static void walkValues(@NonNull JsonContainer container, @NonNull BiConsumer<JsonPath, Object> consumer) {
        JsonPath path = new JsonPath();
        walkValuesRecursively(container, path, consumer);
    }

    public static void walkContainersBottomUp(@NonNull JsonContainer container,
                                              @NonNull BiConsumer<JsonPath, JsonContainer> consumer) {
        JsonPath path = new JsonPath();
        walkContainersBottomUpRecursively(container, path, consumer);
    }


    /// private

    private static void walkValuesRecursively(@NonNull JsonContainer container, @NonNull JsonPath path,
                                              @NonNull BiConsumer<JsonPath, Object> consumer) {
        if (container instanceof JsonObject) {
            JsonObject jo = (JsonObject) container;
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                JsonPath newPath = path.copy().push(new PathToken.Field(entry.getKey()));
                Object node = entry.getValue();
                if (node instanceof JsonContainer) {
                    walkValuesRecursively((JsonContainer) node, newPath, consumer);
                } else {
                    consumer.accept(newPath, node);
                }
            }
        } else if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = ja.getObject(i);
                if (node instanceof JsonContainer) {
                    walkValuesRecursively((JsonContainer) node, newPath, consumer);
                } else {
                    consumer.accept(newPath, node);
                }
            }
        }
    }

    private static void walkContainersBottomUpRecursively(@NonNull JsonContainer container, @NonNull JsonPath path,
                                                          @NonNull BiConsumer<JsonPath, JsonContainer> consumer) {
        if (container instanceof JsonObject) {
            JsonObject jo = (JsonObject) container;
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                JsonPath newPath = path.copy().push(new PathToken.Field(entry.getKey()));
                Object node = entry.getValue();
                if (node instanceof JsonContainer) {
                    walkContainersBottomUpRecursively((JsonContainer) node, newPath, consumer);
                }
                consumer.accept(newPath, jo);
            }
        } else if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = ja.getObject(i);
                if (node instanceof JsonContainer) {
                    walkContainersBottomUpRecursively((JsonContainer) node, newPath, consumer);
                }
                consumer.accept(newPath, ja);
            }
        }
    }



}
