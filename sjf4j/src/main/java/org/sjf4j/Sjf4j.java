package org.sjf4j;


import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.mapper.NodeMapperBuilder;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;
import org.sjf4j.node.Types;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import java.util.Properties;


/**
 * Main instance entry point for JSON/YAML/properties IO and node conversion.
 * <p>
 * Typical object targets are regular POJOs, {@link JsonObject}/{@link JsonArray},
 * and their structured subtypes such as JOJO and JAJO models.
 * <p>
 * Use {@link #global()} for the process-wide default instance, or {@link #builder()}
 * to create an isolated instance with custom facades and formatting behavior.
 */
public final class Sjf4j {

    private static final Sjf4j GLOBAL = new Builder().build();

    private final JsonFacade<?, ?> jsonFacade;
    private final YamlFacade<?, ?> yamlFacade;
    private final PropertiesFacade propertiesFacade;
    private final NodeFacade nodeFacade;

    private Sjf4j(Builder builder) {
        this.nodeFacade = builder.nodeFacade != null
                ? builder.nodeFacade
                : FacadeFactory.getDefaultNodeFacade();
        this.jsonFacade = builder.jsonFacade != null ? builder.jsonFacade : FacadeFactory.getDefaultJsonFacade();
        this.yamlFacade = builder.yamlFacade != null ? builder.yamlFacade : FacadeFactory.getDefaultYamlFacade();
        this.propertiesFacade = builder.propertiesFacade != null
                ? builder.propertiesFacade
                : FacadeFactory.getDefaultPropertiesFacade();
    }

    /**
     * Returns the immutable process-wide default instance.
     */
    public static Sjf4j global() {
        return GLOBAL;
    }

    /**
     * Creates a builder with default settings.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized from an existing instance.
     */
    public static Builder builder(Sjf4j sjf4j) {
        return new Builder(sjf4j);
    }

    public JsonFacade<?, ?> getJsonFacade() {
        return jsonFacade;
    }

    public YamlFacade<?, ?> getYamlFacade() {
        return yamlFacade;
    }

    public PropertiesFacade getPropertiesFacade() {
        return propertiesFacade;
    }

    public NodeFacade getNodeFacade() {
        return nodeFacade;
    }

    /// JSON

    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(Reader input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(String input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(InputStream input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(byte[] input) {
        return fromJson(input, Object.class);
    }

    public void toJson(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        jsonFacade.writeNode(output, node);
    }

    public void toJson(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output");
        jsonFacade.writeNode(output, node);
    }

    public String toJsonString(Object node) {
        return jsonFacade.writeNodeAsString(node);
    }

    public byte[] toJsonBytes(Object node) {
        return jsonFacade.writeNodeAsBytes(node);
    }

    /// YAML

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) yamlFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) yamlFacade.readNode(input, type.getType());
    }

    public Object fromYaml(Reader input) {
        return fromYaml(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, Class<T> clazz) {
        Objects.requireNonNull(input, "input");
        return (T) yamlFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, TypeReference<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        return (T) yamlFacade.readNode(input, type.getType());
    }

    public Object fromYaml(String input) {
        return fromYaml(input, Object.class);
    }

    public void toYaml(Writer output, Object node) {
        Objects.requireNonNull(output, "output");
        yamlFacade.writeNode(output, node);
    }

    public String toYamlString(Object node) {
        return yamlFacade.writeNodeAsString(node);
    }

    public byte[] toYamlBytes(Object node) {
        return yamlFacade.writeNodeAsBytes(node);
    }

    /// Node

    @SuppressWarnings("unchecked")
    public <T> T fromNode(Object node, Class<T> clazz) {
        return (T) nodeFacade.readNode(node, clazz, true);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromNode(Object node, TypeReference<T> type) {
        Objects.requireNonNull(type, "type");
        return (T) nodeFacade.readNode(node, type.getType(), true);
    }

    @SuppressWarnings("unchecked")
    public <T> T deepNode(T node) {
        return (T) nodeFacade.deepNode(node);
    }

    public Object toRaw(Object node) {
        return nodeFacade.writeNode(node);
    }

    @SuppressWarnings("unchecked")
    public <T> T toPojo(Object node, Class<T> clazz) {
        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
        if (ti.pojoInfo == null && ti.anyOfInfo == null) {
            throw new org.sjf4j.exception.JsonException("Class '" + clazz.getName() + "' is not a POJO");
        }
        return (T) nodeFacade.readNode(node, clazz, false);
    }

    @SuppressWarnings("unchecked")
    public <T> T toPojo(Object node, TypeReference<T> type) {
        Objects.requireNonNull(type, "type");
        Class<?> rawBox = Types.rawBox(type.getType());
        NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(rawBox);
        if (ti.pojoInfo == null && ti.anyOfInfo == null) {
            throw new org.sjf4j.exception.JsonException("Type '" + type.getType() + "' is not a POJO");
        }
        return (T) nodeFacade.readNode(node, type.getType(), false);
    }

    public <S, T> NodeMapperBuilder<S, T> mapperBuilder(Class<S> sourceType, Class<T> targetType) {
        return new NodeMapperBuilder<>(sourceType, targetType);
    }

    /// Properties

    public Object fromProperties(Properties props) {
        Objects.requireNonNull(props, "props");
        return propertiesFacade.readNode(props);
    }

    public <T> T fromProperties(Properties props, Class<T> clazz) {
        Objects.requireNonNull(props, "props");
        JsonObject jo = propertiesFacade.readNode(props);
        return fromNode(jo, clazz);
    }

    public <T> T fromProperties(Properties props, TypeReference<T> type) {
        Objects.requireNonNull(props, "props");
        Objects.requireNonNull(type, "type");
        JsonObject jo = propertiesFacade.readNode(props);
        return fromNode(jo, type);
    }

    public Properties toProperties(Object node) {
        Properties props = new Properties();
        propertiesFacade.writeNode(props, node);
        return props;
    }

    public String inspect() {
        return Nodes.inspect(this);
    }

    public static final class Builder {
        private JsonFacade<?, ?> jsonFacade;
        private YamlFacade<?, ?> yamlFacade;
        private PropertiesFacade propertiesFacade;
        private NodeFacade nodeFacade;

        public Builder() {}

        public Builder(Sjf4j sjf4j) {
            Objects.requireNonNull(sjf4j, "sjf4j");
            this.jsonFacade = sjf4j.jsonFacade;
            this.yamlFacade = sjf4j.yamlFacade;
            this.propertiesFacade = sjf4j.propertiesFacade;
            this.nodeFacade = sjf4j.nodeFacade;
        }

        public Builder jsonFacade(JsonFacade<?, ?> jsonFacade) {
            this.jsonFacade = jsonFacade;
            return this;
        }

        public Builder yamlFacade(YamlFacade<?, ?> yamlFacade) {
            this.yamlFacade = yamlFacade;
            return this;
        }

        public Builder propertiesFacade(PropertiesFacade propertiesFacade) {
            this.propertiesFacade = propertiesFacade;
            return this;
        }

        public Builder nodeFacade(NodeFacade nodeFacade) {
            this.nodeFacade = nodeFacade;
            return this;
        }

        public Sjf4j build() {
            return new Sjf4j(this);
        }
    }
}
