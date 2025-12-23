package org.sjf4j;


import org.sjf4j.util.TypeReference;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;

/**
 * The main entry point for the SJF4J (Simple JSON Facade for Java) library.
 * <p>
 * This class provides a unified API for working with JSON, YAML, POJOs, and Properties,
 * allowing seamless conversion between these formats without being tied to a specific
 * underlying implementation.
 * <p>
 * SJF4J follows the facade design pattern, abstracting away the complexities of different
 * JSON/YAML libraries (Jackson, Gson, Fastjson2, SnakeYAML) and providing a simple,
 * consistent interface for common operations.
 */
public class Sjf4j {

    /// JSON

    /**
     * Parses JSON from a Reader into an object of the specified class.
     *
     * @param <T> The type of the object to parse
     * @param input The Reader containing the JSON input
     * @param clazz The class of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(Reader input, Class<T> clazz) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    /**
     * Parses JSON from a Reader into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The Reader containing the JSON input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(Reader input, TypeReference<T> type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (type == null) throw new IllegalArgumentException("Type must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from a Reader into a JsonObject.
     *
     * @param input The Reader containing the JSON input
     * @return The parsed JsonObject
     */
    public static JsonObject fromJson(Reader input) {
        return fromJson(input, JsonObject.class);
    }

    /**
     * Parses JSON from a String into an object of the specified class.
     *
     * @param <T> The type of the object to parse
     * @param input The String containing the JSON input
     * @param clazz The class of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String input, Class<T> clazz) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    /**
     * Parses JSON from a String into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The String containing the JSON input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String input, TypeReference<T> type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (type == null) throw new IllegalArgumentException("Type must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from a String into a JsonObject.
     *
     * @param input The String containing the JSON input
     * @return The parsed JsonObject
     */
    public static JsonObject fromJson(String input) {
        return fromJson(input, JsonObject.class);
    }

    /**
     * Parses JSON from a InputStream into an object of the specified class.
     *
     * @param <T> The type of the object to parse
     * @param input The InputStream containing the JSON input
     * @param clazz The class of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(InputStream input, Class<T> clazz) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    /**
     * Parses JSON from a InputStream into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The InputStream containing the JSON input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(InputStream input, TypeReference<T> type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (type == null) throw new IllegalArgumentException("Type must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from a InputStream into a JsonObject.
     *
     * @param input The InputStream containing the JSON input
     * @return The parsed JsonObject
     */
    public static JsonObject fromJson(InputStream input) {
        return fromJson(input, JsonObject.class);
    }

    /**
     * Parses JSON from a byte[] into an object of the specified class.
     *
     * @param <T> The type of the object to parse
     * @param input The byte[] containing the JSON input
     * @param clazz The class of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(byte[] input, Class<T> clazz) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    /**
     * Parses JSON from a byte[] into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The byte[] containing the JSON input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(byte[] input, TypeReference<T> type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (type == null) throw new IllegalArgumentException("Type must not be null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from a byte[] into a JsonObject.
     *
     * @param input The byte[] containing the JSON input
     * @return The parsed JsonObject
     */
    public static JsonObject fromJson(byte[] input) {
        return fromJson(input, JsonObject.class);
    }

    /**
     * Writes an object as JSON to the given Writer.
     *
     * @param output The Writer to write the JSON to
     * @param node The object to write as JSON
     */
    public static void toJson(Writer output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        Sjf4jConfig.global().getJsonFacade().writeNode(output, node);
    }

    /**
     * Writes an object as JSON to the given OutputStream.
     *
     * @param output The OutputStream to write the JSON to
     * @param node The object to write as JSON
     */
    public static void toJson(OutputStream output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        Sjf4jConfig.global().getJsonFacade().writeNode(output, node);
    }

    /**
     * Converts an object to its JSON string representation.
     *
     * @param node The object to convert to JSON
     * @return The JSON string representation of the object
     */
    public static String toJson(Object node) {
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        StringWriter out = new StringWriter();
        toJson(out, node);
        return out.toString();
    }


    /// YAML

    /**
     * Parses YAML from a reader into an object of the specified class.
     *
     * @param <T> The type of the object to parse
     * @param input The reader containing the YAML input
     * @param clazz The class of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(Reader input, Class<T> clazz) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (clazz == null) throw new IllegalArgumentException("Clazz must not be null");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, clazz);
    }

    /**
     * Parses YAML from a reader into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The reader containing the YAML input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(Reader input, TypeReference<T> type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        if (type == null) throw new IllegalArgumentException("Type must not be null");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, type.getType());
    }

    /**
     * Parses YAML from a reader into a JsonObject.
     *
     * @param input The reader containing the YAML input
     * @return The parsed JsonObject
     */
    public static JsonObject fromYaml(Reader input) {
        return fromYaml(input, JsonObject.class);
    }

    /**
     * Parses YAML from a string into an object of the specified class.
     *
     * @param <T> The type of the object to parse
     * @param input The string containing the YAML input
     * @param clazz The class of the object to parse
     * @return The parsed object
     */
    public static <T> T fromYaml(String input, Class<T> clazz) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        return fromYaml(new StringReader(input), clazz);
    }

    /**
     * Parses YAML from a string into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The string containing the YAML input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    public static <T> T fromYaml(String input, TypeReference<T> type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        return fromYaml(new StringReader(input), type);
    }

    /**
     * Parses YAML from a string into a JsonObject.
     *
     * @param input The string containing the YAML input
     * @return The parsed JsonObject
     */
    public static JsonObject fromYaml(String input) {
        return fromYaml(new StringReader(input));
    }

    /**
     * Writes an object as YAML to the given writer.
     *
     * @param output The writer to write the YAML to
     * @param node The object to write as YAML
     */
    public static void toYaml(Writer output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        Sjf4jConfig.global().getYamlFacade().writeNode(output, node);
    }

    /**
     * Converts an object to its YAML string representation.
     *
     * @param node The object to convert to YAML
     * @return The YAML string representation of the object
     */
    public static String toYaml(Object node) {
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        StringWriter out = new StringWriter();
        toYaml(out, node);
        return out.toString();
    }

    /// POJO

    /**
     * Converts a POJO to an object of the specified class.
     *
     * @param <T> The type of the object to convert to
     * @param pojo The POJO to convert from
     * @param clazz The class of the object to convert to
     * @return The converted object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromPojo(Object pojo, Class<T> clazz) {
        return (T) Sjf4jConfig.global().getObjectFacade().readNode(pojo, clazz);
    }

    /**
     * Converts a POJO to an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to convert to
     * @param pojo The POJO to convert from
     * @param type The type reference of the object to convert to
     * @return The converted object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromPojo(Object pojo, TypeReference<T> type) {
        if (pojo == null) throw new IllegalArgumentException("Pojo must not be null");
        return (T) Sjf4jConfig.global().getObjectFacade().readNode(pojo, type.getType());
    }

    /**
     * Converts a POJO to a JsonObject.
     *
     * @param pojo The POJO to convert from
     * @return The converted JsonObject
     */
    public static JsonObject fromPojo(Object pojo) {
        if (pojo == null) throw new IllegalArgumentException("Pojo must not be null");
        return fromPojo(pojo, JsonObject.class);
    }

    /// Properties

    /**
     * Converts a Properties object to a JsonObject.
     *
     * @param props The Properties object to convert from
     * @return The converted JsonObject
     */
    public static JsonObject fromProperties(Properties props) {
        if (props == null) throw new IllegalArgumentException("Props must not be null");
        return Sjf4jConfig.global().getPropertiesFacade().readNode(props);
    }

    /**
     * Converts a JsonObject to a Properties object.
     *
     * @param props The Properties object to convert to
     * @param node The JsonObject to convert from
     */
    public static void toProperties(Properties props, JsonObject node) {
        if (props == null) throw new IllegalArgumentException("Props must not be null");
        if (node == null) throw new IllegalArgumentException("Node must not be null");
        Sjf4jConfig.global().getPropertiesFacade().writeNode(props, node);
    }


    public static Map<String, Object> createMap() {
        return Sjf4jConfig.global().mapSupplier.create();
    }


}