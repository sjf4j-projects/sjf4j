package org.sjf4j;


import org.sjf4j.node.TypeReference;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import java.util.Properties;


/**
 * Static facade for JSON/YAML/properties IO and node conversion.
 * <p>
 * All operations delegate to facades from global {@link Sjf4jConfig}.
 */
public class Sjf4j {

    /// JSON

    /**
     * Parses JSON from reader into target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(Reader input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }
    /**
     * Parses JSON from reader into target type reference.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(Reader input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from reader to generic object node.
     */
    public static Object fromJson(Reader input) {
        return fromJson(input, Object.class);
    }

    /**
     * Parses JSON from string into target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }
    /**
     * Parses JSON from string into target type reference.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from string to generic object node.
     */
    public static Object fromJson(String input) {
        return fromJson(input, Object.class);
    }

    /**
     * Parses JSON from input stream into target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(InputStream input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    /**
     * Parses JSON from input stream into target type reference.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(InputStream input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON from input stream to generic object node.
     */
    public static Object fromJson(InputStream input) {
        return fromJson(input, Object.class);
    }

    /**
     * Parses JSON bytes into target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(byte[] input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, clazz);
    }

    /**
     * Parses JSON bytes into target type reference.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(byte[] input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getJsonFacade().readNode(input, type.getType());
    }

    /**
     * Parses JSON bytes to generic object node.
     */
    public static Object fromJson(byte[] input) {
        return fromJson(input, Object.class);
    }

    /**
     * Writes node as JSON to writer.
     */
    public static void toJson(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        Sjf4jConfig.global().getJsonFacade().writeNode(output, node);
    }

    /**
     * Writes node as JSON to output stream.
     */
    public static void toJson(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        Sjf4jConfig.global().getJsonFacade().writeNode(output, node);
    }

    /**
     * Serializes node to JSON string.
     */
    public static String toJsonString(Object node) {
        return Sjf4jConfig.global().getJsonFacade().writeNodeAsString(node);
    }

    /**
     * Serializes node to JSON bytes.
     */
    public static byte[] toJsonBytes(Object node) {
        return Sjf4jConfig.global().getJsonFacade().writeNodeAsBytes(node);
    }


    /// YAML

    /**
     * Parses YAML from reader into target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(Reader input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, clazz);
    }

    /**
     * Parses YAML from reader into target type reference.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(Reader input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, type.getType());
    }

    /**
     * Parses YAML from reader to generic object node.
     */
    public static Object fromYaml(Reader input) {
        return fromYaml(input, Object.class);
    }

    /**
     * Parses YAML from string into target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(String input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, clazz);
    }

    /**
     * Parses YAML from string into target type reference.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromYaml(String input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getYamlFacade().readNode(input, type.getType());
    }

    /**
     * Parses YAML from string to generic object node.
     */
    public static Object fromYaml(String input) {
        return fromYaml(input, Object.class);
    }

    /**
     * Writes node as YAML to writer.
     */
    public static void toYaml(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        Sjf4jConfig.global().getYamlFacade().writeNode(output, node);
    }

    /**
     * Serializes node to YAML string.
     */
    public static String toYamlString(Object node) {
        return Sjf4jConfig.global().getYamlFacade().writeNodeAsString(node);
    }

    /**
     * Serializes node to YAML bytes.
     */
    public static byte[] toYamlBytes(Object node) {
        return Sjf4jConfig.global().getYamlFacade().writeNodeAsBytes(node);
    }

    /// Node

    /**
     * Converts a node to target class via node facade.
     * <p>
     * Uses strict conversion semantics.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromNode(Object node, Class<T> clazz) {
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(node, clazz, true);
    }

    /**
     * Converts a node to target type reference via node facade.
     * <p>
     * Uses strict conversion semantics.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromNode(Object node, TypeReference<T> type) {
        Objects.requireNonNull(type, "type");
        return (T) Sjf4jConfig.global().getNodeFacade().readNode(node, type.getType(), true);
    }

    /**
     * Deep-copies a node through node facade.
     * <p>
     * Container nodes and nested children are recursively copied.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepNode(T node) {
        return (T) Sjf4jConfig.global().getNodeFacade().deepNode(node);
    }

    /**
     * Converts node to raw Java structures.
     * <p>
     * Result consists of JSON-friendly primitives plus Map/List containers.
     */
    public static Object toRaw(Object node) {
        return Sjf4jConfig.global().getNodeFacade().writeNode(node);
    }

    /// Properties

    /**
     * Converts properties to generic object node.
     */
    public static Object fromProperties(Properties props) {
        Objects.requireNonNull(props, "props");
        return Sjf4jConfig.global().getPropertiesFacade().readNode(props);
    }

    /**
     * Converts properties to target class.
     */
    public static <T> T fromProperties(Properties props, Class<T> clazz) {
        Objects.requireNonNull(props, "props");
        JsonObject jo = Sjf4jConfig.global().getPropertiesFacade().readNode(props);
        return fromNode(jo, clazz);
    }

    /**
     * Converts properties to target type reference.
     */
    public static <T> T fromProperties(Properties props, TypeReference<T> type) {
        Objects.requireNonNull(props, "props");
        Objects.requireNonNull(type, "type");
        JsonObject jo = Sjf4jConfig.global().getPropertiesFacade().readNode(props);
        return fromNode(jo, type);
    }

    /**
     * Converts node to {@link Properties} via properties facade.
     */
    public static Properties toProperties(Object node) {
        Properties props = new Properties();
        Sjf4jConfig.global().getPropertiesFacade().writeNode(props, node);
        return props;
    }


}
