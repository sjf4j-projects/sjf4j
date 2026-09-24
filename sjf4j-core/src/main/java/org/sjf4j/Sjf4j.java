package org.sjf4j;


import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.node.Types;
import org.sjf4j.util.Asserts;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;


/**
 * Main instance entry point for JSON/YAML/properties IO and node conversion.
 * <p>
 * Typical object targets are regular POJOs, {@link JsonObject}/{@link JsonArray},
 * and their structured subtypes such as JOJO and JAJO models.
 * <p>
 * Regardless of the underlying backend implementation, SJF4J is responsible for
 * keeping JSON binding and structural-processing semantics as consistent as possible
 * across runtimes.
 * <p>
 * Use {@link #global()} for the shared process-wide default instance, or {@link #builder()}
 * to create an isolated instance with custom facades and formatting behavior.
 */
public final class Sjf4j {

    private static final Sjf4j GLOBAL = new Builder().build();

    private final StreamingContext streamingContext;
    private final FacadeProvider<? extends NodeFacade> nodeFacadeProvider;
    private final FacadeProvider<? extends JsonFacade<?, ?>> jsonFacadeProvider;
    private final FacadeProvider<? extends YamlFacade<?, ?>> yamlFacadeProvider;
    private final FacadeProvider<? extends PropertiesFacade> propertiesFacadeProvider;
    private final NodeFacade nodeFacade;
    private final JsonFacade<?, ?> jsonFacade;
    private final YamlFacade<?, ?> yamlFacade;
    private final PropertiesFacade propertiesFacade;

    /**
     * Creates a runtime instance with the framework-default configuration.
     */
    public Sjf4j() {
        this(new Builder());
    }

    private Sjf4j(Builder builder) {
        StreamingContext.StreamingMode streamingMode = builder.streamingMode == null ?
                StreamingContext.StreamingMode.AUTO : builder.streamingMode;
        this.streamingContext = new StreamingContext(builder.defaultValueFormats, streamingMode, builder.includeNulls);

        this.nodeFacadeProvider = builder.nodeFacadeProvider == null
                ? FacadeFactory.nodeFacadeProvider() : builder.nodeFacadeProvider;
        this.jsonFacadeProvider = builder.jsonFacadeProvider == null
                ? FacadeFactory.jsonFacadeProvider() : builder.jsonFacadeProvider;
        this.yamlFacadeProvider = builder.yamlFacadeProvider == null
                ? FacadeFactory.yamlFacadeProvider() : builder.yamlFacadeProvider;
        this.propertiesFacadeProvider = builder.propertiesFacadeProvider == null
                ? FacadeFactory.propertiesFacadeProvider() : builder.propertiesFacadeProvider;

        this.nodeFacade = Asserts.notNull(nodeFacadeProvider.create(streamingContext), "nodeFacade");
        this.jsonFacade = Asserts.notNull(jsonFacadeProvider.create(streamingContext), "jsonFacade");
        this.yamlFacade = Asserts.notNull(yamlFacadeProvider.create(streamingContext), "yamlFacade");
        this.propertiesFacade = Asserts.notNull(propertiesFacadeProvider.create(streamingContext), "propertiesFacade");
    }

    /**
     * Returns the shared process-wide default instance.
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

    /*
     * --------------------------------------------------------------
     * Getter
     * --------------------------------------------------------------
     */

    /**
     * Returns the immutable streaming configuration used by this runtime.
     */
    public StreamingContext streamingContext() {
        return streamingContext;
    }

    /**
     * Returns the node-conversion facade used by this runtime.
     */
    public NodeFacade nodeFacade() {
        return nodeFacade;
    }

    /**
     * Returns the JSON facade used by this runtime.
     */
    public JsonFacade<?, ?> jsonFacade() {
        return jsonFacade;
    }

    /**
     * Returns the YAML facade used by this runtime.
     */
    public YamlFacade<?, ?> yamlFacade() {
        return yamlFacade;
    }

    /**
     * Returns the properties facade used by this runtime.
     */
    public PropertiesFacade propertiesFacade() {
        return propertiesFacade;
    }

    /*
     * --------------------------------------------------------------
     * JSON
     * --------------------------------------------------------------
     */

    /**
     * Reads JSON from a character stream into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON from a character stream into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(type, "type").getType());
    }

    /**
     * Reads JSON from a character stream into the default structural object model.
     */
    public Object fromJson(Reader input) {
        return fromJson(input, Object.class);
    }

    /**
     * Reads JSON text into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON text into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(type, "type").getType());
    }

    /**
     * Reads JSON text into the default structural object model.
     */
    public Object fromJson(String input) {
        return fromJson(input, Object.class);
    }

    /**
     * Reads JSON from a byte stream into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON from a byte stream into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(type, "type").getType());
    }

    /**
     * Reads JSON from a byte stream into the default structural object model.
     */
    public Object fromJson(InputStream input) {
        return fromJson(input, Object.class);
    }

    /**
     * Reads JSON bytes into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON bytes into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, Asserts.notNull(type, "type").getType());
    }

    /**
     * Reads JSON bytes into the default structural object model.
     */
    public Object fromJson(byte[] input) {
        return fromJson(input, Object.class);
    }

    /**
     * Writes a value as JSON to a character stream.
     */
    public void toJson(Writer output, Object node) {
        jsonFacade.writeNode(output, node);
    }

    /**
     * Writes a value as JSON to a byte stream.
     */
    public void toJson(OutputStream output, Object node) {
        jsonFacade.writeNode(output, node);
    }

    /**
     * Serializes a value to a JSON string.
     */
    public String toJsonString(Object node) {
        return jsonFacade.writeNodeAsString(node);
    }

    /**
     * Serializes a value to JSON bytes.
     */
    public byte[] toJsonBytes(Object node) {
        return jsonFacade.writeNodeAsBytes(node);
    }

    /*
     * --------------------------------------------------------------
     * YAML
     * --------------------------------------------------------------
     */

    /**
     * Reads YAML from a character stream into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, Class<T> clazz) {
        return (T) yamlFacade.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads YAML from a character stream into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, TypeReference<T> type) {
        return (T) yamlFacade.readNode(input, Asserts.notNull(type, "type").getType());
    }

    /**
     * Reads YAML from a character stream into the default structural object model.
     */
    public Object fromYaml(Reader input) {
        return fromYaml(input, Object.class);
    }

    /**
     * Reads YAML text into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, Class<T> clazz) {
        return (T) yamlFacade.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads YAML text into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, TypeReference<T> type) {
        return (T) yamlFacade.readNode(input, Asserts.notNull(type, "type").getType());
    }

    /**
     * Reads YAML text into the default structural object model.
     */
    public Object fromYaml(String input) {
        return fromYaml(input, Object.class);
    }

    /**
     * Writes a value as YAML to a character stream.
     */
    public void toYaml(Writer output, Object node) {
        yamlFacade.writeNode(output, node);
    }

    /**
     * Serializes a value to a YAML string.
     */
    public String toYamlString(Object node) {
        return yamlFacade.writeNodeAsString(node);
    }

    /**
     * Serializes a value to YAML bytes.
     */
    public byte[] toYamlBytes(Object node) {
        return yamlFacade.writeNodeAsBytes(node);
    }

    /*
     * --------------------------------------------------------------
     * Node
     * --------------------------------------------------------------
     */

    /**
     * Converts an existing OBNT value into the requested target type.
     * <p>
     * Delegates to the configured {@link NodeFacade} with deep conversion
     * requested. The framework-default facade recursively binds its recognized
     * built-in containers and POJO representations, but converter results can
     * retain references. The configured facade defines compatible-value identity,
     * converter selection, and copy behavior.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromNode(Object node, Class<T> clazz) {
        return (T) nodeFacade.readNode(node, Asserts.notNull(clazz, "clazz"), true);
    }

    /**
     * Converts an existing OBNT value into the requested generic target type.
     * <p>
     * Delegates to the configured {@link NodeFacade} with deep conversion
     * requested. The framework-default facade recursively binds its recognized
     * built-in containers and POJO representations, but converter results can
     * retain references. The configured facade defines compatible-value identity,
     * converter selection, and copy behavior.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromNode(Object node, TypeReference<T> type) {
        return (T) nodeFacade.readNode(node, Asserts.notNull(type, "type").getType(), true);
    }

    /**
     * Binds an existing OBNT value into the requested target type without forcing a deep copy.
     * <p>
     * Delegates to the configured {@link NodeFacade} without requesting deep
     * conversion. The framework-default facade can return a compatible
     * non-parameterized value unchanged; a custom facade defines identity,
     * converter selection, and nested-reference behavior. Use
     * {@link #fromNode(Object, Class)} to request the facade's deep conversion mode.
     */
    @SuppressWarnings("unchecked")
    public <T> T bindNode(Object node, Class<T> clazz) {
        return (T) nodeFacade.readNode(node, Asserts.notNull(clazz, "clazz"), false);
    }

    /**
     * Binds an existing OBNT value into the requested generic target type without forcing a deep copy.
     * <p>
     * Delegates to the configured {@link NodeFacade} without requesting deep
     * conversion. The framework-default facade can return a compatible
     * non-parameterized value unchanged; a custom facade defines identity,
     * converter selection, and nested-reference behavior. Use
     * {@link #fromNode(Object, TypeReference)} to request the facade's deep
     * conversion mode.
     */
    @SuppressWarnings("unchecked")
    public <T> T bindNode(Object node, TypeReference<T> type) {
        return (T) nodeFacade.readNode(node, Asserts.notNull(type, "type").getType(), false);
    }

    /**
     * Creates a deep copy of the supplied OBNT value.
     * <p>
     * Delegates to {@link NodeFacade#deepNode(Object)}. The framework-default
     * facade recursively copies its recognized built-in containers and POJO
     * representations, while unrecognized values and already-instantiated
     * {@code @NodeValue} domain values can be returned by reference. A custom
     * facade defines its own copy boundary.
     */
    @SuppressWarnings("unchecked")
    public <T> T deepNode(T node) {
        return (T) nodeFacade.deepNode(node);
    }

    /**
     * Converts a value into the backend-neutral raw OBNT representation.
     * <p>
     * Supported containers and POJOs are traversed into raw object/array/value
     * representations. {@code @NodeValue} types are encoded by their configured
     * value binding; scalar raw values may be returned unchanged.
     */
    public Object toRaw(Object node) {
        return nodeFacade.writeNode(node);
    }


    /*
     * --------------------------------------------------------------
     * Properties
     * --------------------------------------------------------------
     */

    /**
     * Reads flat {@link Properties} data into the default object node representation.
     */
    public Object fromProperties(Properties props) {
        return propertiesFacade.readNode(props);
    }

    /**
     * Reads flat {@link Properties} data into the requested target type.
     */
    public <T> T fromProperties(Properties props, Class<T> clazz) {
        Asserts.notNull(clazz, "clazz");
        JsonObject jo = propertiesFacade.readNode(props);
        return fromNode(jo, clazz);
    }

    /**
     * Reads flat {@link Properties} data into the requested generic target type.
     */
    public <T> T fromProperties(Properties props, TypeReference<T> type) {
        Asserts.notNull(type, "type");
        JsonObject jo = propertiesFacade.readNode(props);
        return fromNode(jo, type);
    }

    /**
     * Flattens a value into standard Java {@link Properties}.
     */
    public Properties toProperties(Object node) {
        Properties props = new Properties();
        propertiesFacade.writeNode(props, node);
        return props;
    }

    /*
     * --------------------------------------------------------------
     * Builder
     * --------------------------------------------------------------
     */

    public static final class Builder {
        private FacadeProvider<? extends NodeFacade> nodeFacadeProvider;
        private FacadeProvider<? extends JsonFacade<?, ?>> jsonFacadeProvider;
        private FacadeProvider<? extends YamlFacade<?, ?>> yamlFacadeProvider;
        private FacadeProvider<? extends PropertiesFacade> propertiesFacadeProvider;
        private StreamingContext.StreamingMode streamingMode;
        private final Map<Class<?>, String> defaultValueFormats = new LinkedHashMap<>();
        private boolean includeNulls = true;

        /**
         * Creates a builder with framework-default facade providers and serialization behavior.
         */
        public Builder() {}

        /**
         * Creates a builder initialized from an existing runtime instance.
         * <p>
         * This copies facade providers, streaming mode, default value-format mappings,
         * and null-serialization behavior so callers can derive a slightly adjusted runtime.
         */
        public Builder(Sjf4j sjf4j) {
            Asserts.notNull(sjf4j, "sjf4j");
            this.nodeFacadeProvider = sjf4j.nodeFacadeProvider;
            this.jsonFacadeProvider = sjf4j.jsonFacadeProvider;
            this.yamlFacadeProvider = sjf4j.yamlFacadeProvider;
            this.propertiesFacadeProvider = sjf4j.propertiesFacadeProvider;
            this.streamingMode = sjf4j.streamingContext.streamingMode;
            sjf4j.streamingContext.copyDefaultValueFormatsTo(this.defaultValueFormats);
            this.includeNulls = sjf4j.streamingContext.includeNulls;
        }

        /**
         * Overrides the provider used to create the runtime {@link NodeFacade}.
         * <p>
         * Use this when you want a custom node-conversion implementation for this
         * {@link Sjf4j} instance instead of the auto-detected framework default.
         */
        public Builder nodeFacadeProvider(FacadeProvider<? extends NodeFacade> nodeFacadeProvider) {
            this.nodeFacadeProvider = Asserts.notNull(nodeFacadeProvider, "nodeFacadeProvider");
            return this;
        }

        /**
         * Overrides the provider used to create the runtime JSON facade.
         * <p>
         * This controls which JSON backend implementation the instance uses, such as
         * Jackson, Gson, Fastjson2, or a custom facade.
         */
        public Builder jsonFacadeProvider(FacadeProvider<? extends JsonFacade<?, ?>> jsonFacadeProvider) {
            this.jsonFacadeProvider = Asserts.notNull(jsonFacadeProvider, "jsonFacadeProvider");
            return this;
        }

        /**
         * Overrides the provider used to create the runtime YAML facade.
         */
        public Builder yamlFacadeProvider(FacadeProvider<? extends YamlFacade<?, ?>> yamlFacadeProvider) {
            this.yamlFacadeProvider = Asserts.notNull(yamlFacadeProvider, "yamlFacadeProvider");
            return this;
        }

        /**
         * Overrides the provider used to create the runtime properties facade.
         */
        public Builder propertiesFacadeProvider(FacadeProvider<? extends PropertiesFacade> propertiesFacadeProvider) {
            this.propertiesFacadeProvider = Asserts.notNull(propertiesFacadeProvider,
                    "propertiesFacadeProvider");
            return this;
        }

        /**
         * Sets the streaming mode for this runtime.
         * <p>
         * {@link StreamingContext.StreamingMode#AUTO} lets the facade choose the preferred
         * strategy, while other modes can force shared or backend-native streaming paths.
         */
        public Builder streamingMode(StreamingContext.StreamingMode streamingMode) {
            this.streamingMode = Asserts.notNull(streamingMode, "streamingMode");
            return this;
        }

        /**
         * Registers the default named {@code ValueCodec} format for a value type.
         * <p>
         * This runtime-level default is used when a field or creator parameter does not
         * explicitly declare its own {@code valueFormat}.
         */
        public Builder defaultValueFormat(Class<?> valueType, String valueFormat) {
            Class<?> checkedValueType = Asserts.notNull(valueType, "valueType");
            if (checkedValueType.isPrimitive()) {
                throw new IllegalArgumentException("defaultValueFormat does not support primitive type '"
                        + checkedValueType.getName() + "'; use boxed type '"
                        + Types.box(checkedValueType).getName() + "'");
            }
            defaultValueFormats.put(checkedValueType, Asserts.notNull(valueFormat, "valueFormat"));
            return this;
        }

        /**
         * Controls whether JSON object serialization emits properties whose value is {@code null}.
         * <p>
         * The default is {@code true}.
         * <p>
         * This setting is propagated to backend facades that support null filtering so the
         * behavior stays consistent within the constructed runtime instance.
         */
        public Builder includeNulls(boolean includeNulls) {
            this.includeNulls = includeNulls;
            return this;
        }

        /**
         * Builds a new isolated {@link Sjf4j} runtime from the current builder state.
         */
        public Sjf4j build() {
            return new Sjf4j(this);
        }
    }
}
