package org.sjf4j;


import lombok.NonNull;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.snake.SnakeYamlFacade;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class Sjf4j {

    /// JSON

    public static Object readNodeFromJson(@NonNull Reader input, Type type) throws IOException {
        JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
        return facade.readNode(input, type);
//        if (facade instanceof JacksonJsonFacade) {
//            JacksonJsonFacade jacksonFacade = (JacksonJsonFacade) facade;
//            return jacksonFacade.readNode(input, type);
//        }
//
//        if (facade instanceof GsonJsonFacade) {
//            GsonJsonFacade gsonFacade = (GsonJsonFacade) facade;
//            return gsonFacade.readNode(input, type);
//        }
//
//        if (facade instanceof Fastjson2JsonFacade) {
//            Fastjson2JsonFacade fastFacade = (Fastjson2JsonFacade) facade;
//            return fastFacade.readNode(input, type);
//        }
//        throw new JsonException("No JsonFacade found '" + facade.getClass() + "'");
    }

    @SuppressWarnings("unchecked")
    public static <T> T readObjectFromJson(@NonNull Reader input, @NonNull Class<T> clazz) {
        try {
            return (T) readNodeFromJson(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + clazz + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static  <T> T readObjectFromJson(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNodeFromJson(input, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    public static JsonObject readObjectFromJson(@NonNull Reader input) {
        return readObjectFromJson(input, JsonObject.class);
    }

    public static void writeNodeToJson(@NonNull Writer output, Object node) {
        JsonFacade<?, ?> facade = FacadeFactory.getDefaultJsonFacade();
        if (facade instanceof JacksonJsonFacade) {
            JacksonJsonFacade jacksonFacade = (JacksonJsonFacade) facade;
            jacksonFacade.writeNode(output, node);
            return;
        }

        if (facade instanceof GsonJsonFacade) {
            GsonJsonFacade gsonFacade = (GsonJsonFacade) facade;
            gsonFacade.writeNode(output, node);
            return;
        }

        if (facade instanceof Fastjson2JsonFacade) {
            Fastjson2JsonFacade fastFacade = (Fastjson2JsonFacade) facade;
            fastFacade.writeNode(output, node);
            return;
        }

        throw new JsonException("No JsonFacade found");
    }

    /// YAML

    public static Object readNodeFromYaml(@NonNull Reader input, Type type) {
        YamlFacade<?, ?> facade = FacadeFactory.getDefaultYamlFacade();
        if (facade instanceof SnakeYamlFacade) {
            SnakeYamlFacade snakeFacade = (SnakeYamlFacade) facade;
            return snakeFacade.readNode(input, type);
        }
        throw new JsonException("No YamlFacade found");
    }

    @SuppressWarnings("unchecked")
    public static <T> T readObjectFromYaml(@NonNull Reader input, @NonNull Class<T> clazz) {
        try {
            return (T) readNodeFromYaml(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + clazz + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static  <T> T readObjectFromYaml(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNodeFromYaml(input, type);
        } catch (Exception e) {
            throw new JsonException("Failed to read streaming into node of type '" + type + "'", e);
        }
    }

    public static JsonObject readObjectFromYaml(@NonNull Reader input) {
        return readObjectFromYaml(input, JsonObject.class);
    }

    public static void writeNodeToYaml(@NonNull Writer output, Object node) {
        YamlFacade<?, ?> facade = FacadeFactory.getDefaultYamlFacade();
        if (facade instanceof SnakeYamlFacade) {
            SnakeYamlFacade snakeFacade = (SnakeYamlFacade) facade;
            snakeFacade.writeNode(output, node);
            return;
        }
        throw new JsonException("No YamlFacade found");
    }




}
