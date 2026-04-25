package org.sjf4j;


import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.mapper.NodeMapperBuilder;
import org.sjf4j.node.Types;
import org.sjf4j.node.TypeReference;
import org.sjf4j.node.ValueFormatMapping;

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

    public Sjf4j() {
        this(new Builder());
    }

    private Sjf4j(Builder builder) {
        ValueFormatMapping valueFormatMapping = ValueFormatMapping.of(builder.defaultValueFormats);
        StreamingContext.StreamingMode streamingMode = builder.streamingMode == null ?
                StreamingContext.StreamingMode.AUTO : builder.streamingMode;
        this.streamingContext = new StreamingContext(valueFormatMapping, streamingMode, builder.includeNulls);

        this.nodeFacadeProvider = builder.nodeFacadeProvider == null
                ? FacadeFactory.nodeFacadeProvider() : builder.nodeFacadeProvider;
        this.jsonFacadeProvider = builder.jsonFacadeProvider == null
                ? FacadeFactory.jsonFacadeProvider() : builder.jsonFacadeProvider;
        this.yamlFacadeProvider = builder.yamlFacadeProvider == null
                ? FacadeFactory.yamlFacadeProvider() : builder.yamlFacadeProvider;
        this.propertiesFacadeProvider = builder.propertiesFacadeProvider == null
                ? FacadeFactory.propertiesFacadeProvider() : builder.propertiesFacadeProvider;

        this.nodeFacade = Objects.requireNonNull(nodeFacadeProvider.create(streamingContext), "nodeFacade");
        this.jsonFacade = Objects.requireNonNull(jsonFacadeProvider.create(streamingContext), "jsonFacade");
        this.yamlFacade = Objects.requireNonNull(yamlFacadeProvider.create(streamingContext), "yamlFacade");
        this.propertiesFacade = Objects.requireNonNull(propertiesFacadeProvider.create(streamingContext), "propertiesFacade");
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

    /// Getter

    public StreamingContext streamingContext() {
        return streamingContext;
    }

    public NodeFacade nodeFacade() {
        return nodeFacade;
    }

    public JsonFacade<?, ?> jsonFacade() {
        return jsonFacade;
    }

    public YamlFacade<?, ?> yamlFacade() {
        return yamlFacade;
    }

    public PropertiesFacade propertiesFacade() {
        return propertiesFacade;
    }

    /// JSON

    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(Reader input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(String input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(InputStream input) {
        return fromJson(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, Class<T> clazz) {
        return (T) jsonFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, TypeReference<T> type) {
        return (T) jsonFacade.readNode(input, type.getType());
    }

    public Object fromJson(byte[] input) {
        return fromJson(input, Object.class);
    }

    public void toJson(Writer output, Object node) {
        jsonFacade.writeNode(output, node);
    }

    public void toJson(OutputStream output, Object node) {
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
        return (T) yamlFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, TypeReference<T> type) {
        return (T) yamlFacade.readNode(input, type.getType());
    }

    public Object fromYaml(Reader input) {
        return fromYaml(input, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, Class<T> clazz) {
        return (T) yamlFacade.readNode(input, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, TypeReference<T> type) {
        return (T) yamlFacade.readNode(input, type.getType());
    }

    public Object fromYaml(String input) {
        return fromYaml(input, Object.class);
    }

    public void toYaml(Writer output, Object node) {
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
        return propertiesFacade.readNode(props);
    }

    public <T> T fromProperties(Properties props, Class<T> clazz) {
        JsonObject jo = propertiesFacade.readNode(props);
        return fromNode(jo, clazz);
    }

    public <T> T fromProperties(Properties props, TypeReference<T> type) {
        JsonObject jo = propertiesFacade.readNode(props);
        return fromNode(jo, type);
    }

    public Properties toProperties(Object node) {
        Properties props = new Properties();
        propertiesFacade.writeNode(props, node);
        return props;
    }

    /// NodeMapper

    public <S, T> NodeMapperBuilder<S, T> nodeMapperBuilder(Class<S> sourceClass, Class<T> targetClass) {
        return new NodeMapperBuilder<>(sourceClass, targetClass, streamingContext);
    }

    /// Builder

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
            Objects.requireNonNull(sjf4j, "sjf4j");
            this.nodeFacadeProvider = sjf4j.nodeFacadeProvider;
            this.jsonFacadeProvider = sjf4j.jsonFacadeProvider;
            this.yamlFacadeProvider = sjf4j.yamlFacadeProvider;
            this.propertiesFacadeProvider = sjf4j.propertiesFacadeProvider;
            this.streamingMode = sjf4j.streamingContext.streamingMode;
            this.defaultValueFormats.putAll(sjf4j.streamingContext.valueFormatMapping.asMap());
            this.includeNulls = sjf4j.streamingContext.includeNulls;
        }

        /**
         * Overrides the provider used to create the runtime {@link NodeFacade}.
         * <p>
         * Use this when you want a custom node-conversion implementation for this
         * {@link Sjf4j} instance instead of the auto-detected framework default.
         */
        public Builder nodeFacadeProvider(FacadeProvider<? extends NodeFacade> nodeFacadeProvider) {
            this.nodeFacadeProvider = Objects.requireNonNull(nodeFacadeProvider, "nodeFacadeProvider");
            return this;
        }

        /**
         * Overrides the provider used to create the runtime JSON facade.
         * <p>
         * This controls which JSON backend implementation the instance uses, such as
         * Jackson, Gson, Fastjson2, or a custom facade.
         */
        public Builder jsonFacadeProvider(FacadeProvider<? extends JsonFacade<?, ?>> jsonFacadeProvider) {
            this.jsonFacadeProvider = Objects.requireNonNull(jsonFacadeProvider, "jsonFacadeProvider");
            return this;
        }

        /**
         * Overrides the provider used to create the runtime YAML facade.
         */
        public Builder yamlFacadeProvider(FacadeProvider<? extends YamlFacade<?, ?>> yamlFacadeProvider) {
            this.yamlFacadeProvider = Objects.requireNonNull(yamlFacadeProvider, "yamlFacadeProvider");
            return this;
        }

        /**
         * Overrides the provider used to create the runtime properties facade.
         */
        public Builder propertiesFacadeProvider(FacadeProvider<? extends PropertiesFacade> propertiesFacadeProvider) {
            this.propertiesFacadeProvider = Objects.requireNonNull(propertiesFacadeProvider,
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
            this.streamingMode = Objects.requireNonNull(streamingMode, "streamingMode");
            return this;
        }

        /**
         * Registers the default named {@code ValueCodec} format for a value type.
         * <p>
         * This runtime-level default is used when a field or creator parameter does not
         * explicitly declare its own {@code valueFormat}.
         */
        public Builder defaultValueFormat(Class<?> valueType, String valueFormat) {
            Class<?> checkedValueType = Objects.requireNonNull(valueType, "valueType");
            if (checkedValueType.isPrimitive()) {
                throw new IllegalArgumentException("defaultValueFormat does not support primitive type '"
                        + checkedValueType.getName() + "'; use boxed type '"
                        + Types.box(checkedValueType).getName() + "'");
            }
            defaultValueFormats.put(checkedValueType, Objects.requireNonNull(valueFormat, "valueFormat"));
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
