package org.sjf4j;

import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.node.TypeReference;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathCache;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import java.util.Properties;

/**
 * Instance-scoped entry point for JSON/YAML/properties IO and node conversion.
 * <p>
 * This is the transitional instance API that will eventually replace the static
 * {@link Sjf4j} facade. It snapshots a {@link Sjf4jConfig} at build time so a
 * caller can pass around a concrete runtime instead of relying on global state.
 */
public final class Sjf4jRuntime {

    private final Sjf4jConfig config;
    private final JsonFacade<?, ?> jsonFacade;
    private final YamlFacade<?, ?> yamlFacade;
    private final PropertiesFacade propertiesFacade;
    private final NodeFacade nodeFacade;

    Sjf4jRuntime(Sjf4jConfig config) {
        Objects.requireNonNull(config, "config");
        this.config = new Sjf4jConfig.Builder(config).build();
        this.jsonFacade = this.config.getJsonFacade();
        this.yamlFacade = this.config.getYamlFacade();
        this.propertiesFacade = this.config.getPropertiesFacade();
        this.nodeFacade = this.config.getNodeFacade();
    }

    /**
     * Creates a runtime from an existing configuration snapshot.
     */
    public static Sjf4jRuntime of(Sjf4jConfig config) {
        return new Sjf4jRuntime(config);
    }

    /**
     * Creates a runtime builder with default settings.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a runtime builder initialized from existing config.
     */
    public static Builder builder(Sjf4jConfig config) {
        return new Builder(config);
    }

    /**
     * Returns the immutable config snapshot held by this runtime.
     */
    public Sjf4jConfig config() {
        return config;
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

    /**
     * Compiles a JSONPath or JSON Pointer expression through this runtime's cache.
     */
    public JsonPath cachedPath(String expr) {
        Objects.requireNonNull(expr, "expr");
        return config.pathCache.getOrCompile(expr, JsonPath::compile);
    }

    public static final class Builder {
        private final Sjf4jConfig.Builder configBuilder;

        public Builder() {
            this.configBuilder = new Sjf4jConfig.Builder();
        }

        public Builder(Sjf4jConfig config) {
            this.configBuilder = new Sjf4jConfig.Builder(config);
        }

        public Builder jsonFacade(JsonFacade<?, ?> jsonFacade) {
            configBuilder.jsonFacade(jsonFacade);
            return this;
        }

        public Builder yamlFacade(YamlFacade<?, ?> yamlFacade) {
            configBuilder.yamlFacade(yamlFacade);
            return this;
        }

        public Builder propertiesFacade(PropertiesFacade propertiesFacade) {
            configBuilder.propertiesFacade(propertiesFacade);
            return this;
        }

        public Builder nodeFacade(NodeFacade nodeFacade) {
            configBuilder.nodeFacade(nodeFacade);
            return this;
        }

        public Builder instantFormat(Sjf4jConfig.InstantFormat instantFormat) {
            configBuilder.instantFormat(instantFormat);
            return this;
        }

        public Builder pathCache(PathCache pathCache) {
            configBuilder.pathCache(pathCache);
            return this;
        }

        public Builder bindingPath(boolean bindingPath) {
            configBuilder.bindingPath(bindingPath);
            return this;
        }

        public Sjf4jConfig buildConfig() {
            return configBuilder.build();
        }

        public Sjf4jRuntime build() {
            return new Sjf4jRuntime(buildConfig());
        }
    }
}
