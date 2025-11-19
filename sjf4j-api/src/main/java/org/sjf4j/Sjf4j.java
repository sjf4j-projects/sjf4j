package org.sjf4j;


import lombok.NonNull;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.snake.SnakeYamlFacade;
import org.sjf4j.util.TypeReference;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Properties;

public class Sjf4j {

    /// JSON

    public static Object readNodeFromJson(@NonNull Reader input, Type type) {
        JsonFacade<?, ?> facade = JsonConfig.global().jsonFacade;
        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.STREAMING_GENERAL) {
            return facade.readNode(input, type);
        } else {
            if (facade instanceof JacksonJsonFacade) {
                JacksonJsonFacade jacksonFacade = (JacksonJsonFacade) facade;
                return jacksonFacade.readNode(input, type);
            }
            if (facade instanceof GsonJsonFacade) {
                GsonJsonFacade gsonFacade = (GsonJsonFacade) facade;
                return gsonFacade.readNode(input, type);
            }
            if (facade instanceof Fastjson2JsonFacade) {
                Fastjson2JsonFacade fastFacade = (Fastjson2JsonFacade) facade;
                return fastFacade.readNode(input, type);
            }
            throw new JsonException("No JsonFacade found '" + facade.getClass() + "'");
        }
    }

    public static void writeNodeToJson(@NonNull Writer output, Object node) {
        JsonFacade<?, ?> facade = JsonConfig.global().jsonFacade;
        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.STREAMING_GENERAL) {
            facade.writeNode(output, node);
        } else {
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
            throw new JsonException("No JsonFacade found '" + facade.getClass() + "'");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(@NonNull Reader input, @NonNull Class<T> clazz) {
        return (T) readNodeFromJson(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNodeFromJson(input, type.getType());
        } catch (Exception e) {
            throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
        }
    }

    public static JsonObject fromJson(@NonNull Reader input) {
        return fromJson(input, JsonObject.class);
    }

    public static <T> T fromJson(@NonNull String input, @NonNull Class<T> clazz) {
        return (T) fromJson(new StringReader(input), clazz);
    }

    public static <T> T fromJson(@NonNull String input, @NonNull TypeReference<T> type) {
        return (T) fromJson(new StringReader(input), type);
    }

    public static JsonObject fromJson(@NonNull String input) {
        return fromJson(new StringReader(input), JsonObject.class);
    }

    public static String toJson(@NonNull Object node) {
        try {
            StringWriter sw = new StringWriter();
            writeNodeToJson(sw, node);
            return sw.toString();
        } catch (Exception e) {
            throw new JsonException("Failed to write node of type '" + node.getClass() + "' into JSON String", e);
        }
    }


    /// YAML

    public static Object readNodeFromYaml(@NonNull Reader input, Type type) {
        YamlFacade<?, ?> facade = JsonConfig.global().yamlFacade;
        if (facade instanceof SnakeYamlFacade) {
            SnakeYamlFacade snakeFacade = (SnakeYamlFacade) facade;
            return snakeFacade.readNode(input, type);
        }
        throw new JsonException("No YamlFacade found");
    }

    public static void writeNodeToYaml(@NonNull Writer output, Object node) {
        YamlFacade<?, ?> facade = JsonConfig.global().yamlFacade;
        if (facade instanceof SnakeYamlFacade) {
            SnakeYamlFacade snakeFacade = (SnakeYamlFacade) facade;
            snakeFacade.writeNode(output, node);
            return;
        }
        throw new JsonException("No YamlFacade found");
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(@NonNull Reader input, @NonNull Class<T> clazz) {
        try {
            return (T) readNodeFromYaml(input, clazz);
        } catch (Exception e) {
            throw new JsonException("Failed to read YAML streaming into node of type '" + clazz + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(@NonNull Reader input, @NonNull TypeReference<T> type) {
        try {
            return (T) readNodeFromYaml(input, type.getType());
        } catch (Exception e) {
            throw new JsonException("Failed to read YAML streaming into node of type '" + type + "'", e);
        }
    }

    public static JsonObject fromYaml(@NonNull Reader input) {
        return fromYaml(input, JsonObject.class);
    }

    public static <T> T fromYaml(@NonNull String input, @NonNull Class<T> clazz) {
        return fromYaml(new StringReader(input), clazz);
    }

    public static <T> T fromYaml(@NonNull String input, @NonNull TypeReference<T> type) {
        return fromYaml(new StringReader(input), type);
    }

    public static JsonObject fromYaml(@NonNull String input) {
        return fromYaml(new StringReader(input));
    }

    @SuppressWarnings("unchecked")
    public static String toYaml(@NonNull Object node) {
        try {
            StringWriter sw = new StringWriter();
            writeNodeToYaml(sw, node);
            return sw.toString();
        } catch (Exception e) {
            throw new JsonException("Failed to write node of type '" + node.getClass() + "' into JSON String", e);
        }
    }


    /// Properties

    public static JsonObject fromProperties(@NonNull Properties props) {
        return JsonConfig.global().propertiesFacade.readNode(props);
    }


    public static void toProperties(@NonNull Properties props, JsonObject node) {
        JsonConfig.global().propertiesFacade.writeNode(props, node);
    }

    /// POJO

    public static Object readNodeFromPojo(@NonNull Object pojo, Type type) {
        return JsonConfig.global().objectFacade.readNode(pojo, type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromPojo(@NonNull Object pojo, Class<T> clazz) {
        return (T) JsonConfig.global().objectFacade.readNode(pojo, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromPojo(@NonNull Object pojo, TypeReference<T> type) {
        return (T) JsonConfig.global().objectFacade.readNode(pojo, type.getType());
    }

}
