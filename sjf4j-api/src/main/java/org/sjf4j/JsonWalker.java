package org.sjf4j;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Slf4j
public class JsonWalker {

    public static void walkValues(@NonNull Object container,
                                  @NonNull BiConsumer<JsonPath, Object> consumer) {
        JsonPath path = new JsonPath();
        walkValuesRecursively(container, path, consumer);
    }

    public static void walkContainersBottomUp(@NonNull Object container,
                                              @NonNull BiConsumer<JsonPath, Object> consumer) {
        JsonPath path = new JsonPath();
        walkContainersBottomUpRecursively(container, path, consumer);
    }


    /// private

    private static void walkValuesRecursively(Object container, @NonNull JsonPath path,
                                              @NonNull BiConsumer<JsonPath, Object> consumer) {
        if (container == null) {
            consumer.accept(path, null);
        } else if (container instanceof JsonObject) {
            ((JsonObject) container).forEach((k, v) -> {
                JsonPath newPath = path.copy().push(new PathToken.Name(k));
                walkValuesRecursively(v, newPath, consumer);
            });
        } else if (container instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                JsonPath newPath = path.copy().push(new PathToken.Name(entry.getKey().toString()));
                Object node = entry.getValue();
                walkValuesRecursively(node, newPath, consumer);
            }
        } else if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = ja.getObject(i);
                walkValuesRecursively(node, newPath, consumer);
            }
        } else if (container instanceof List) {
            List<?> list = (List<?>) container;
            for (int i = 0; i < list.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = list.get(i);
                walkValuesRecursively(node, newPath, consumer);
            }
        } else if (container.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(container); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = Array.get(container, i);
                walkValuesRecursively(node, newPath, consumer);
            }
        } else {
            consumer.accept(path, container);
        }
    }

    private static void walkContainersBottomUpRecursively(Object container, @NonNull JsonPath path,
                                                          @NonNull BiConsumer<JsonPath, Object> consumer) {
        if (container instanceof JsonObject) {
            ((JsonObject) container).forEach((k, v) -> {
                JsonPath newPath = path.copy().push(new PathToken.Name(k));
                walkContainersBottomUpRecursively(v, newPath, consumer);
            });
            consumer.accept(path, container);
        } else if (container instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                JsonPath newPath = path.copy().push(new PathToken.Name(entry.getKey().toString()));
                Object node = entry.getValue();
                walkContainersBottomUpRecursively(node, newPath, consumer);
            }
            consumer.accept(path, container);
        } else if (container instanceof JsonArray) {
            JsonArray ja = (JsonArray) container;
            for (int i = 0; i < ja.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = ja.getObject(i);
                walkContainersBottomUpRecursively(node, newPath, consumer);
            }
            consumer.accept(path, container);
        } else if (container instanceof List) {
            List<?> list = (List<?>) container;
            for (int i = 0; i < list.size(); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = list.get(i);
                walkContainersBottomUpRecursively(node, newPath, consumer);
            }
            consumer.accept(path, container);
        } else if (container != null && container.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(container); i++) {
                JsonPath newPath = path.copy().push(new PathToken.Index(i));
                Object node = Array.get(container, i);
                walkContainersBottomUpRecursively(node, newPath, consumer);
            }
            consumer.accept(path, container);
        }
    }



}
