package org.sjf4j;


import org.sjf4j.node.TypeReference;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import java.util.Properties;


public class Sjf4j {

    /// JSON


    @SuppressWarnings("unchecked")
    public static <T> T fromJson(Reader input, Class<T> clazz) {
        Objects.requireNonNull(input, "input is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }


    @SuppressWarnings("unchecked")
    public static <T> T fromJson(Reader input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input is null");
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    public static Object fromJson(Reader input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String input, Class<T> clazz) {
        Objects.requireNonNull(input, "input is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }


    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input is null");
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    public static Object fromJson(String input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(InputStream input, Class<T> clazz) {
        Objects.requireNonNull(input, "input is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(InputStream input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input is null");
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    public static Object fromJson(InputStream input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(byte[] input, Class<T> clazz) {
        Objects.requireNonNull(input, "input is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(byte[] input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input is null");
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    public static Object fromJson(byte[] input) {
        return fromJson(input, Object.class);
    }

    public static void toJson(Writer output, Object node) {
        Objects.requireNonNull(output, "output is null");
        Sjf4jConfig.global().getJsonFacade().writeNode(output, node);
    }

    public static void toJson(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output is null");
        Sjf4jConfig.global().getJsonFacade().writeNode(output, node);
    }

    public static String toJsonString(Object node) {
        return Sjf4jConfig.global().getJsonFacade().writeNodeAsString(node);
    }

    public static byte[] toJsonBytes(Object node) {
        return Sjf4jConfig.global().getJsonFacade().writeNodeAsBytes(node);
    }


    /// YAML

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(Reader input, Class<T> clazz) {
        Objects.requireNonNull(input, "input is null");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(Reader input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input is null");
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, type.getType());
    }

    public static Object fromYaml(Reader input) {
        return fromYaml(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(String input, Class<T> clazz) {
        Objects.requireNonNull(input, "input is null");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, clazz);
    }

    /**
     * Parses YAML from a string into an object of the specified type using TypeReference.
     *
     * @param <T> The type of the object to parse
     * @param input The string containing the YAML input
     * @param type The type reference of the object to parse
     * @return The parsed object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(String input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input is null");
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, type.getType());
    }

    /**
     * Parses YAML from a string into a JsonObject.
     *
     * @param input The string containing the YAML input
     * @return The parsed JsonObject
     */
    public static Object fromYaml(String input) {
        return fromYaml(input, Object.class);
    }

    /**
     * Writes an object as YAML to the given writer.
     *
     * @param output The writer to write the YAML to
     * @param node The object to write as YAML
     */
    public static void toYaml(Writer output, Object node) {
        Objects.requireNonNull(output, "output is null");
        Sjf4jConfig.global().getYamlFacade().writeNode(output, node);
    }

    /**
     * Converts an object to its YAML string representation.
     *
     * @param node The object to convert to YAML
     * @return The YAML string representation of the object
     */
    public static String toYamlString(Object node) {
        return Sjf4jConfig.global().getYamlFacade().writeNodeAsString(node);
    }

    public static byte[] toYamlBytes(Object node) {
        return Sjf4jConfig.global().getYamlFacade().writeNodeAsBytes(node);
    }

    /// Node

    @SuppressWarnings("unchecked")
    public static <T> T fromNode(Object node, Class<T> clazz) {
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(node, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromNode(Object node, TypeReference<T> type) {
        Objects.requireNonNull(type, "type is null");
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(node, type.getType());
    }

    @SuppressWarnings("unchecked")
    public static <T> T deepNode(T node) {
        return (T) Sjf4jConfig.global().getNodeFacade().deepNode(node);
    }

    public static Object toRaw(Object node) {
        return Sjf4jConfig.global().getNodeFacade().writeNode(node);
    }

    /// Properties

    /**
     * Converts a Properties object to a JsonObject.
     *
     * @param props The Properties object to convert from
     * @return The converted JsonObject
     */
    public static Object fromProperties(Properties props) {
        Objects.requireNonNull(props, "props is null");
        return Sjf4jConfig.global().getPropertiesFacade().readNode(props);
    }

    public static <T> T fromProperties(Properties props, Class<T> clazz) {
        Objects.requireNonNull(props, "props is null");
        JsonObject jo = Sjf4jConfig.global().getPropertiesFacade().readNode(props);
        return fromNode(jo, clazz);
    }

    public static <T> T fromProperties(Properties props, TypeReference<T> type) {
        Objects.requireNonNull(props, "props is null");
        Objects.requireNonNull(type, "type is null");
        JsonObject jo = Sjf4jConfig.global().getPropertiesFacade().readNode(props);
        return fromNode(jo, type);
    }

    /**
     * Converts a JsonObject to a Properties object.
     *
     * @param node The JsonObject to convert from
     */
    public static Properties toProperties(Object node) {
        Properties props = new Properties();
        Sjf4jConfig.global().getPropertiesFacade().writeNode(props, node);
        return props;
    }


}