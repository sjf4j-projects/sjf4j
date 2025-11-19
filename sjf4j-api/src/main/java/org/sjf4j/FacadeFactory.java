package org.sjf4j;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.ObjectFacade;
import org.sjf4j.facades.PropertiesFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.simple.SimpleObjectFacade;
import org.sjf4j.facades.simple.SimplePropertiesFacade;
import org.sjf4j.facades.snake.SnakeYamlFacade;


public class FacadeFactory {

    private static boolean fastjson2Present;
    private static boolean jacksonPresent;
    private static boolean gsonPresent;
    private static boolean snakePresent;

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

    public static JsonFacade<?, ?> getDefaultJsonFacade() {
        if (jacksonPresent) {
            return createJacksonFacade();
        } else if (gsonPresent) {
            return createGsonFacade();
        } else if (fastjson2Present) {
            return createFastjson2Facade();
        } else {
            throw new JsonException("No supported JSON library found: Please add Jackson/Gson/Fastjson2... to the classpath");
        }
    }

    public static JsonFacade<?, ?> createJacksonFacade() {
        return new JacksonJsonFacade(new ObjectMapper());
    }

    public static JsonFacade<?, ?> createGsonFacade() {
        return new GsonJsonFacade(new Gson());
    }

    public static JsonFacade<?, ?> createFastjson2Facade() {
        return new Fastjson2JsonFacade();
    }



    public static YamlFacade<?, ?> getDefaultYamlFacade() {
        if (snakePresent) {
            return createSnakeFacade();
        } else {
            throw new JsonException("No supported YAML library found: Please add SnakeYaml/... to the classpath");
        }
    }

    public static YamlFacade<?, ?> createSnakeFacade() {
        return new SnakeYamlFacade();
    }


    public static PropertiesFacade getDefaultPropertiesFacade() {
        return new SimplePropertiesFacade();
    }

    public static ObjectFacade getDefaultObjectFacade() {
        return new SimpleObjectFacade();
    }


}


