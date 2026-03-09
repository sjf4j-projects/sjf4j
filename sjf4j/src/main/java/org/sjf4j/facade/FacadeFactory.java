package org.sjf4j.facade;


import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.facade.simple.SimplePropertiesFacade;
import org.sjf4j.facade.snake.SnakeYamlFacade;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;


/**
 * Factory for facade implementations (JSON/YAML/properties).
 */
public class FacadeFactory {

    /**
     * Flag indicating whether Jackson library is present in the classpath.
     */
    private static final boolean JACKSON_PRESENT;

    /**
     * Flag indicating whether Fastjson2 library is present in the classpath.
     */
    private static final boolean FASTJSON2_PRESENT;


    /**
     * Flag indicating whether Gson library is present in the classpath.
     */
    private static final boolean GSON_PRESENT;

    /**
     * Flag indicating whether SnakeYAML library is present in the classpath.
     */
    private static final boolean SNAKE_PRESENT;

    /**
     * Detects optional facade dependencies available in classpath.
     */
    static {
        ClassLoader loader = FacadeFactory.class.getClassLoader();

        boolean jacksonPresent = false;
        try {
            loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
            jacksonPresent = true;
        } catch (Throwable ignored) {}
        JACKSON_PRESENT = jacksonPresent;

        boolean gsonPresent = false;
        try {
            loader.loadClass("com.google.gson.Gson");
            gsonPresent = true;
        } catch (Throwable ignored) {}
        GSON_PRESENT = gsonPresent;

        boolean fastjson2Present = false;
        try {
            loader.loadClass("com.alibaba.fastjson2.JSON");
            fastjson2Present = true;
        } catch (Throwable ignored) {}
        FASTJSON2_PRESENT = fastjson2Present;

        boolean snakePresent = false;
        try {
            loader.loadClass("org.yaml.snakeyaml.Yaml");
            snakePresent = true;
        } catch (Throwable ignored) {}
        SNAKE_PRESENT = snakePresent;
    }

    /**
     * Returns default JSON facade by priority: Jackson > Gson > Fastjson2 > Simple.
     */
    public static JsonFacade<?, ?> getDefaultJsonFacade() {
        if (JACKSON_PRESENT) {
            return new JacksonJsonFacade();
        } else if (GSON_PRESENT) {
            return new GsonJsonFacade();
        } else if (FASTJSON2_PRESENT) {
            return new Fastjson2JsonFacade();
        } else {
            System.err.println("SJF4J: Failed to detect any supported JSON library (Jackson, Gson, Fastjson2).");
            System.err.println("SJF4J: Falling back to build-in slower JSON implementation.");
            return new SimpleJsonFacade();
        }
    }


    /**
     * Returns default YAML facade (SnakeYAML).
     */
    public static YamlFacade<?, ?> getDefaultYamlFacade() {
        if (SNAKE_PRESENT) {
            return createSnakeFacade();
        } else {
            throw new JsonException("No supported YAML library found. Please include one of SnakeYaml / ...");
        }
    }

    /**
     * Creates SnakeYAML facade with default options.
     */
    public static YamlFacade<?, ?> createSnakeFacade() {
        return new SnakeYamlFacade();
    }

    /**
     * Creates SnakeYAML facade with custom loader/dumper options.
     */
    public static YamlFacade<?, ?> createSnakeFacade(
            LoaderOptions loaderOptions,
            DumperOptions dumperOptions) {
        return new SnakeYamlFacade(loaderOptions, dumperOptions);
    }


    /**
     * Returns default properties facade.
     */
    public static PropertiesFacade getDefaultPropertiesFacade() {
        return new SimplePropertiesFacade();
    }

    /**
     * Returns default node facade.
     */
    public static NodeFacade getDefaultNodeFacade() {
        return new SimpleNodeFacade();
    }


}
