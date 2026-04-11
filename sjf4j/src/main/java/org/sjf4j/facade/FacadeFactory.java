package org.sjf4j.facade;


import jakarta.json.spi.JsonProvider;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.facade.simple.SimplePropertiesFacade;
import org.sjf4j.facade.simple.SimpleYamlFacade;
import org.sjf4j.facade.snake.SnakeYamlFacade;


/**
 * Factory for facade implementations (JSON/YAML/properties).
 */
public final class FacadeFactory {


    /**
     * Flag indicating whether Jackson3 library is present in the classpath.
     */
    private static final boolean JACKSON3_PRESENT;

    /**
     * Flag indicating whether Jackson2 library is present in the classpath.
     */
    private static final boolean JACKSON2_PRESENT;

    /**
     * Flag indicating whether Gson library is present in the classpath.
     */
    private static final boolean GSON_PRESENT;

    /**
     * Flag indicating whether Fastjson2 library is present in the classpath.
     */
    private static final boolean FASTJSON2_PRESENT;

    /**
     * Flag indicating whether JSON-P and its implemented library is present in the classpath.
     */
    private static final boolean JSONP_PRESENT;

    /**
     * Flag indicating whether SnakeYAML library is present in the classpath.
     */
    private static final boolean SNAKE_PRESENT;

    /**
     * Detects optional facade dependencies available in classpath.
     */
    static {
        ClassLoader loader = FacadeFactory.class.getClassLoader();

        boolean jackson3Present = false;
        try {
            loader.loadClass("tools.jackson.databind.json.JsonMapper");
            jackson3Present = true;
        } catch (Throwable ignored) {}
        JACKSON3_PRESENT = jackson3Present;

        boolean jackson2Present = false;
        try {
            loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
            jackson2Present = true;
        } catch (Throwable ignored) {}
        JACKSON2_PRESENT = jackson2Present;

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

        boolean jsonpPresent = false;
        try {
            loader.loadClass("jakarta.json.spi.JsonProvider");
            JsonProvider.provider();
            jsonpPresent = true;
        } catch (Throwable ignored) {}
        JSONP_PRESENT = jsonpPresent;

        boolean snakePresent = false;
        try {
            loader.loadClass("org.yaml.snakeyaml.Yaml");
            snakePresent = true;
        } catch (Throwable ignored) {}
        SNAKE_PRESENT = snakePresent;

    }

    /**
     * Returns default JSON facade by priority: Jackson3 > Jackson2 > Gson > Fastjson2 > JSON-P > Simple.
     */
    public static JsonFacade<?, ?> createJsonFacade() {
        if (JACKSON3_PRESENT) {
            return new Jackson3JsonFacade();
        } else if (JACKSON2_PRESENT) {
            return new Jackson2JsonFacade();
        } else if (GSON_PRESENT) {
            return new GsonJsonFacade();
        } else if (FASTJSON2_PRESENT) {
            return new Fastjson2JsonFacade();
        } else if (JSONP_PRESENT) {
            return new JsonpJsonFacade();
        } else {
            System.err.println("SJF4J: Failed to detect any supported JSON library (Jackson3, Jackson2, Gson, " +
                    "Fastjson2, JSON-P), falling back to build-in slower JSON implementation.");
            return new SimpleJsonFacade();
        }
    }


    /**
     * Returns default YAML facade (SnakeYAML).
     */
    public static YamlFacade<?, ?> createYamlFacade() {
        if (SNAKE_PRESENT) {
            return new SnakeYamlFacade();
        } else {
            System.err.println("SJF4J: Failed to detect any supported YAML library (SnakeYAML).");
            return new SimpleYamlFacade();
        }
    }


    /**
     * Creates a fresh properties facade instance.
     */
    public static PropertiesFacade createPropertiesFacade() {
        return new SimplePropertiesFacade();
    }

    /**
     * Creates a fresh node facade instance.
     */
    public static NodeFacade createNodeFacade() {
        return new SimpleNodeFacade();
    }


    private static final NodeFacade DEFAULT_NODE_FACADE = new SimpleNodeFacade();

    /**
     * Returns the shared default node facade instance.
     */
    public static NodeFacade defaultNodeFacade() {
        return DEFAULT_NODE_FACADE;
    }

}
