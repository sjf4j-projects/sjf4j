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

//    public static Object readNodeFromJson(@NonNull Reader input, Type type) {
//        JsonFacade<?, ?> facade = JsonConfig.global().getJsonFacade();
//        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.STREAMING_GENERAL) {
//            return facade.readNode(input, type);
//        } else {
//            if (facade instanceof JacksonJsonFacade) {
//                JacksonJsonFacade jacksonFacade = (JacksonJsonFacade) facade;
//                return jacksonFacade.readNode(input, type);
//            }
//            if (facade instanceof GsonJsonFacade) {
//                GsonJsonFacade gsonFacade = (GsonJsonFacade) facade;
//                return gsonFacade.readNode(input, type);
//            }
//            if (facade instanceof Fastjson2JsonFacade) {
//                Fastjson2JsonFacade fastFacade = (Fastjson2JsonFacade) facade;
//                return fastFacade.readNode(input, type);
//            }
//            throw new JsonException("No JsonFacade found '" + facade.getClass() + "'");
//        }
//    }

//    public static void writeNodeToJson(@NonNull Writer output, Object node) {
//        JsonFacade<?, ?> facade = JsonConfig.global().getJsonFacade();
//        if (JsonConfig.global().facadeMode == JsonConfig.FacadeMode.STREAMING_GENERAL) {
//            facade.writeNode(output, node);
//        } else {
//            if (facade instanceof JacksonJsonFacade) {
//                JacksonJsonFacade jacksonFacade = (JacksonJsonFacade) facade;
//                jacksonFacade.writeNode(output, node);
//                return;
//            }
//            if (facade instanceof GsonJsonFacade) {
//                GsonJsonFacade gsonFacade = (GsonJsonFacade) facade;
//                gsonFacade.writeNode(output, node);
//                return;
//            }
//            if (facade instanceof Fastjson2JsonFacade) {
//                Fastjson2JsonFacade fastFacade = (Fastjson2JsonFacade) facade;
//                fastFacade.writeNode(output, node);
//                return;
//            }
//            throw new JsonException("No JsonFacade found '" + facade.getClass() + "'");
//        }
//    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(@NonNull Reader input, @NonNull Class<T> clazz) {
        return (T) JsonConfig.global().getJsonFacade().readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(@NonNull Reader input, @NonNull TypeReference<T> type) {
        return (T) JsonConfig.global().getJsonFacade().readNode(input, type.getType());
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

    public static void toJson(@NonNull Writer output, @NonNull Object node) {
        JsonConfig.global().getJsonFacade().writeNode(output, node);
    }

    public static String toJson(@NonNull Object node) {
        StringWriter out = new StringWriter();
        toJson(out, node);
        return out.toString();
    }


    /// YAML

//    public static Object readNodeFromYaml(@NonNull Reader input, Type type) {
//        YamlFacade<?, ?> facade = JsonConfig.global().getYamlFacade();
//        if (facade instanceof SnakeYamlFacade) {
//            SnakeYamlFacade snakeFacade = (SnakeYamlFacade) facade;
//            return snakeFacade.readNode(input, type);
//        }
//        throw new JsonException("No YamlFacade found");
//    }

//    public static void writeNodeToYaml(@NonNull Writer output, Object node) {
//        YamlFacade<?, ?> facade = JsonConfig.global().getYamlFacade();
//        if (facade instanceof SnakeYamlFacade) {
//            SnakeYamlFacade snakeFacade = (SnakeYamlFacade) facade;
//            snakeFacade.writeNode(output, node);
//            return;
//        }
//        throw new JsonException("No YamlFacade found");
//    }

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(@NonNull Reader input, @NonNull Class<T> clazz) {
        return (T) JsonConfig.global().getYamlFacade().readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(@NonNull Reader input, @NonNull TypeReference<T> type) {
        return (T) JsonConfig.global().getYamlFacade().readNode(input, type.getType());
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

    public static void toYaml(@NonNull Writer out, @NonNull Object node) {
        JsonConfig.global().getYamlFacade().writeNode(out, node);
    }

    public static String toYaml(@NonNull Object node) {
        StringWriter out = new StringWriter();
        toYaml(out, node);
        return out.toString();
    }

    /// POJO

    @SuppressWarnings("unchecked")
    public static <T> T fromPojo(@NonNull Object pojo, Class<T> clazz) {
        return (T) JsonConfig.global().getObjectFacade().readNode(pojo, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromPojo(@NonNull Object pojo, TypeReference<T> type) {
        return (T) JsonConfig.global().getObjectFacade().readNode(pojo, type.getType());
    }

    public static JsonObject fromPojo(@NonNull Object pojo) {
        return fromPojo(pojo, JsonObject.class);
    }

    /// Properties

    public static JsonObject fromProperties(@NonNull Properties props) {
        return JsonConfig.global().getPropertiesFacade().readNode(props);
    }

    public static void toProperties(@NonNull Properties props, JsonObject node) {
        JsonConfig.global().getPropertiesFacade().writeNode(props, node);
    }


}
