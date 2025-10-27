package org.sjf4j;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.Setter;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.snake.SnakeYamlFacade;


public class FacadeFactory {

    private static boolean fastjson2Present;
    private static boolean jacksonPresent;
    private static boolean gsonPresent;
    @Setter
    private static JsonFacade defaultJsonFacade;


    private static boolean snakePresent;
    @Setter
    private static YamlFacade defaultYamlFacade;

    static {
        ClassLoader loader = FacadeFactory.class.getClassLoader();

        try {
            loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
            jacksonPresent = true;
        } catch (Throwable e) {
            jacksonPresent = false;
        }

        try {
            loader.loadClass("com.google.gson.Gson");
            gsonPresent = true;
        } catch (Throwable e) {
            gsonPresent = false;
        }

        try {
            loader.loadClass("com.alibaba.fastjson2.JSON");
            fastjson2Present = true;
        } catch (Throwable e) {
            fastjson2Present = false;
        }

        try {
            loader.loadClass("org.yaml.snakeyaml.Yaml");
            snakePresent = true;
        } catch (Throwable e) {
            snakePresent = false;
        }

    }

    public static JsonFacade getDefaultJsonFacade() {
        if (defaultJsonFacade == null) {
            if (jacksonPresent) {
                usingJacksonAsDefault();
            } else if (gsonPresent) {
                usingGsonAsDefault();
            } else if (fastjson2Present) {
                usingFastjson2AsDefault();
            } else {
                throw new JsonException("No supported JSON library found: Please add Jackson/Gson/Fastjson2... to the classpath");
            }
        }
        return defaultJsonFacade;
    }

    public static void usingJacksonAsDefault() {
        defaultJsonFacade = new JacksonJsonFacade(new ObjectMapper());
    }

    public static void usingGsonAsDefault() {
        defaultJsonFacade = new GsonJsonFacade(new Gson());
    }

    public static void usingFastjson2AsDefault() {
        defaultJsonFacade = new Fastjson2JsonFacade();
    }


    public static YamlFacade getDefaultYamlFacade() {
        if (defaultYamlFacade == null) {
            if (snakePresent) {
                usingSnakeAsDefault();
            } else {
                throw new JsonException("No supported YAML library found: Please add SnakeYaml/... to the classpath");
            }
        }
        return defaultYamlFacade;
    }

    public static void usingSnakeAsDefault() {
        defaultYamlFacade = new SnakeYamlFacade();
    }
}


