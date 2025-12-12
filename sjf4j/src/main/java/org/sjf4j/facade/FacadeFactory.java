package org.sjf4j.facade;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import org.sjf4j.JsonException;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleObjectFacade;
import org.sjf4j.facade.simple.SimplePropertiesFacade;
import org.sjf4j.facade.snake.SnakeYamlFacade;


/**
 * Factory class responsible for creating and managing facade implementations for SJF4J.
 * <p>
 * This class provides methods to create various facade implementations (JSON, YAML, Properties, Object) 
 * and manages the default facade selection based on the libraries available in the classpath.
 * <p>
 * It dynamically detects the presence of supported libraries (Jackson, Gson, Fastjson2, SnakeYAML)
 * and selects the appropriate facade implementation accordingly.
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
     * Static initialization block that detects the presence of supported libraries in the classpath.
     * <p>
     * This block attempts to load key classes from each supported library to determine
     * which libraries are available for use.
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
     * Gets the default JSON facade implementation based on the available libraries.
     * <p>
     * The method follows this priority order: Jackson > Gson > Fastjson2.
     *
     * @return The default JSON facade implementation
     * @throws JsonException If no supported JSON library is found in the classpath
     */
    public static JsonFacade<?, ?> getDefaultJsonFacade() {
        if (JACKSON_PRESENT) {
            return createJacksonFacade();
        } else if (GSON_PRESENT) {
            return createGsonFacade();
        } else if (FASTJSON2_PRESENT) {
            return createFastjson2Facade();
        } else {
            System.err.println("SJF4J: Failed to detect any supported JSON library (Jackson, Gson, Fastjson2).");
            System.err.println("SJF4J: Falling back to build-in (Simple and Slow) JSON implementation.");
            return createSimpleJsonFacade();
        }
    }

    /**
     * Creates a new Jackson JSON facade instance.
     *
     * @return A new Jackson JSON facade instance
     */
    public static JsonFacade<?, ?> createJacksonFacade() {
        return new JacksonJsonFacade(new ObjectMapper());
    }

    /**
     * Creates a new Gson JSON facade instance.
     *
     * @return A new Gson JSON facade instance
     */
    public static JsonFacade<?, ?> createGsonFacade() {
        return new GsonJsonFacade(new GsonBuilder());
    }

    /**
     * Creates a new Fastjson2 JSON facade instance.
     *
     * @return A new Fastjson2 JSON facade instance
     */
    public static JsonFacade<?, ?> createFastjson2Facade() {
        return new Fastjson2JsonFacade();
    }

    /**
     * Creates a new Simple build-in JSON facade instance.
     *
     * @return A new Simple build-in JSON facade instance
     */
    public static JsonFacade<?, ?> createSimpleJsonFacade() {
        return new SimpleJsonFacade();
    }



    /**
     * Gets the default YAML facade implementation based on the available libraries.
     * <p>
     * Currently, this method only checks for SnakeYAML.
     *
     * @return The default YAML facade implementation
     * @throws JsonException If no supported YAML library is found in the classpath
     */
    public static YamlFacade<?, ?> getDefaultYamlFacade() {
        if (SNAKE_PRESENT) {
            return createSnakeFacade();
        } else {
            throw new JsonException("No supported YAML library found. Please include one of SnakeYaml / ...");
        }
    }

    /**
     * Creates a new SnakeYAML facade instance.
     *
     * @return A new SnakeYAML facade instance
     */
    public static YamlFacade<?, ?> createSnakeFacade() {
        return new SnakeYamlFacade();
    }


    /**
     * Gets the default Properties facade implementation.
     *
     * @return The default Properties facade implementation
     */
    public static PropertiesFacade getDefaultPropertiesFacade() {
        return new SimplePropertiesFacade();
    }

    /**
     * Gets the default Object facade implementation.
     *
     * @return The default Object facade implementation
     */
    public static ObjectFacade getDefaultObjectFacade() {
        return new SimpleObjectFacade();
    }


}