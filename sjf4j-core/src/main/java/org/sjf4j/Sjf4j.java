package org.sjf4j;


import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.PropertiesBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimplePropertiesBinder;
import org.sjf4j.mapping.NodeMapper;
import org.sjf4j.node.Types;
import org.sjf4j.util.Asserts;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;


/**
 * Main instance entry point for format IO and node conversion.
 * <p>
 * Typical object targets are regular POJOs, {@link JsonObject}/{@link JsonArray},
 * and their structured subtypes such as JOJO and JAJO models.
 * <p>
 * Regardless of the underlying backend implementation, SJF4J is responsible for
 * keeping JSON binding and structural-processing semantics as consistent as possible
 * across runtimes.
 * <p>
 * Use {@link #global()} for the shared process-wide default instance, or {@link #builder()}
 * to create an isolated instance with custom binders and formatting behavior.
 */
public final class Sjf4j {

    private static final Sjf4j GLOBAL = new Builder().build();

    private final RuntimeContext runtimeContext;
    private final BinderProvider jsonBinderProvider;
    private final BinderProvider yamlBinderProvider;

    private final Binder<?, ?> jsonBinder;
    private final Binder<?, ?> yamlBinder;
    private final PropertiesBinder propertiesBinder;

    /**
     * Creates a runtime instance with the framework-default configuration.
     */
    public Sjf4j() {
        this(new Builder());
    }

    private Sjf4j(Builder builder) {
        this.runtimeContext = new RuntimeContext(builder.defaultValueFormats, builder.includeNulls);

        this.jsonBinderProvider = builder.jsonBinderProvider == null
                ? BinderFactory.jsonBinderProvider() : builder.jsonBinderProvider;
        this.jsonBinder = jsonBinderProvider.create(this.runtimeContext);

        this.yamlBinderProvider = builder.yamlBinderProvider == null
                ? BinderFactory.yamlBinderProvider() : builder.yamlBinderProvider;
        this.yamlBinder = yamlBinderProvider.create(this.runtimeContext);

        this.propertiesBinder = builder.propertiesBinder == null
                ? new SimplePropertiesBinder() : builder.propertiesBinder;
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
     * Returns the immutable runtime settings used by this instance.
     */
    public RuntimeContext runtimeContext() {
        return runtimeContext;
    }

    /**
     * Returns the JSON binder provider used by this runtime.
     */
    public BinderProvider jsonProvider() {
        return jsonBinderProvider;
    }

    /**
     * Returns the JSON binder used by this runtime.
     */
    public Binder<?, ?> jsonBinder() {
        return jsonBinder;
    }

    /**
     * Returns the properties binder used by this runtime.
     */
    public PropertiesBinder propertiesBinder() {
        return propertiesBinder;
    }

    /*
     * --------------------------------------------------------------
     * JSON Binding
     * --------------------------------------------------------------
     */

    /**
     * Reads JSON from a character stream into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, Class<T> clazz) {
        return (T) jsonBinder.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON from a character stream into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(Reader input, TypeReference<T> type) {
        return (T) jsonBinder.readNode(input, Asserts.notNull(type, "type").getType());
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
        return (T) jsonBinder.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON text into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String input, TypeReference<T> type) {
        return (T) jsonBinder.readNode(input, Asserts.notNull(type, "type").getType());
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
        return (T) jsonBinder.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON from a byte stream into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(InputStream input, TypeReference<T> type) {
        return (T) jsonBinder.readNode(input, Asserts.notNull(type, "type").getType());
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
        return (T) jsonBinder.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads JSON bytes into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromJson(byte[] input, TypeReference<T> type) {
        return (T) jsonBinder.readNode(input, Asserts.notNull(type, "type").getType());
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
        jsonBinder.writeNode(output, node);
    }

    /**
     * Writes a value as JSON to a byte stream.
     */
    public void toJson(OutputStream output, Object node) {
        jsonBinder.writeNode(output, node);
    }

    /**
     * Serializes a value to a JSON string.
     */
    public String toJsonString(Object node) {
        return jsonBinder.writeNodeAsString(node);
    }

    /**
     * Serializes a value to JSON bytes.
     */
    public byte[] toJsonBytes(Object node) {
        return jsonBinder.writeNodeAsBytes(node);
    }


    /*
     * --------------------------------------------------------------
     * YAML Binding
     * --------------------------------------------------------------
     */

    /**
     * Reads YAML from a character stream into the requested target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, Class<T> clazz) {
        return (T) yamlBinder.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads YAML from a character stream into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(Reader input, TypeReference<T> type) {
        return (T) yamlBinder.readNode(input, Asserts.notNull(type, "type").getType());
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
        return (T) yamlBinder.readNode(input, Asserts.notNull(clazz, "clazz"));
    }

    /**
     * Reads YAML text into the requested generic target type.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromYaml(String input, TypeReference<T> type) {
        return (T) yamlBinder.readNode(input, Asserts.notNull(type, "type").getType());
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
        yamlBinder.writeNode(output, node);
    }

    /**
     * Serializes a value to a YAML string.
     */
    public String toYamlString(Object node) {
        return yamlBinder.writeNodeAsString(node);
    }

    /**
     * Serializes a value to YAML bytes.
     */
    public byte[] toYamlBytes(Object node) {
        return yamlBinder.writeNodeAsBytes(node);
    }



    /*
     * --------------------------------------------------------------
     * Mapping
     * --------------------------------------------------------------
     */

    /**
     * Converts an existing OBNT value into the requested target type.
     * <p>
     * Uses {@link NodeMapper} for structural conversion. When {@code deepCopy} is
     * true, compatible structures are copied recursively and {@code @NodeValue}
     * types use their configured value-copy behavior.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromNode(Object node, Class<T> clazz, boolean deepCopy) {
        return (T) NodeMapper.convert(node, Asserts.notNull(clazz, "clazz"), deepCopy, runtimeContext);
    }

    /**
     * Converts an existing OBNT value into the requested generic target type.
     * <p>
     * Uses {@link NodeMapper} for structural conversion. When {@code deepCopy} is
     * true, compatible structures are copied recursively and {@code @NodeValue}
     * types use their configured value-copy behavior.
     */
    @SuppressWarnings("unchecked")
    public <T> T fromNode(Object node, TypeReference<T> type, boolean deepCopy) {
        return (T) NodeMapper.convert(node, Asserts.notNull(type, "type").getType(), deepCopy, runtimeContext);
    }

    /**
     * Creates a recursive copy of supported OBNT structures.
     * <p>
     * Delegates to {@link NodeMapper#deepcopy(Object)}. Unsupported values,
     * including backend-native or external node representations, may be returned
     * unchanged.
     */
    @SuppressWarnings("unchecked")
    public <T> T copyNode(T node) {
        if (node == null) return null;
        return (T) NodeMapper.convert(node, node.getClass(), true, runtimeContext);
    }

    /**
     * Converts a value into the backend-neutral raw OBNT representation.
     * <p>
     * Supported containers and POJOs are traversed into raw object/array/value
     * representations. {@code @NodeValue} types are encoded by their configured
     * value binding; scalar raw values may be returned unchanged.
     */
    public Object toRaw(Object node) {
        return NodeMapper.convertToRaw(node, runtimeContext);
    }


    /*
     * --------------------------------------------------------------
     * Properties Binding
     * --------------------------------------------------------------
     */

    /**
     * Reads flat {@link Properties} data into the default object node representation.
     */
    public Object fromProperties(Properties props) {
        return propertiesBinder.readNode(props);
    }

    /**
     * Reads flat {@link Properties} data into the requested target type.
     */
    public <T> T fromProperties(Properties props, Class<T> clazz) {
        Asserts.notNull(clazz, "clazz");
        JsonObject jo = propertiesBinder.readNode(props);
        return fromNode(jo, clazz, false);
    }

    /**
     * Reads flat {@link Properties} data into the requested generic target type.
     */
    public <T> T fromProperties(Properties props, TypeReference<T> type) {
        Asserts.notNull(type, "type");
        JsonObject jo = propertiesBinder.readNode(props);
        return fromNode(jo, type, false);
    }

    /**
     * Flattens a value into standard Java {@link Properties}.
     */
    public Properties toProperties(Object node) {
        Properties props = new Properties();
        propertiesBinder.writeNode(props, node);
        return props;
    }

    /*
     * --------------------------------------------------------------
     * Builder
     * --------------------------------------------------------------
     */

    public static final class Builder {
        private BinderProvider jsonBinderProvider;
        private BinderProvider yamlBinderProvider;
        private PropertiesBinder propertiesBinder;

        private final Map<Class<?>, String> defaultValueFormats = new LinkedHashMap<>();
        private boolean includeNulls = true;

        /**
         * Creates a builder with framework-default binder providers and runtime settings.
         */
        public Builder() {}

        /**
         * Creates a builder initialized from an existing runtime instance.
         * <p>
         * This copies binder providers and the current {@link RuntimeContext}
         * settings so callers can derive a slightly adjusted runtime.
         */
        public Builder(Sjf4j sjf4j) {
            Asserts.notNull(sjf4j, "sjf4j");
            this.jsonBinderProvider = sjf4j.jsonBinderProvider;
            this.yamlBinderProvider = sjf4j.yamlBinderProvider;
            this.propertiesBinder = sjf4j.propertiesBinder;
            sjf4j.runtimeContext.copyDefaultValueFormatsTo(this.defaultValueFormats);
            this.includeNulls = sjf4j.runtimeContext.includeNulls;
        }

        /**
         * Overrides the provider used to create the runtime JSON binder.
         * <p>
         * This controls which JSON backend implementation the instance uses, such as
         * Jackson, Gson, Fastjson2, or a custom binder.
         */
        public Builder jsonBinderProvider(BinderProvider provider) {
            Asserts.notNull(provider, "provider");
            if (!Format.JSON.equals(provider.format())) {
                throw new IllegalArgumentException(
                        "expected a JSON binder provider, but got format: " + provider.format());
            }
            this.jsonBinderProvider = provider;
            return this;
        }

        /**
         * Overrides the provider used to create the runtime YAML binder.
         */
        public Builder yamlBinderProvider(BinderProvider provider) {
            Asserts.notNull(provider, "provider");
            if (!Format.YAML.equals(provider.format())) {
                throw new IllegalArgumentException(
                        "expected a YAML binder provider, but got format: " + provider.format());
            }
            this.yamlBinderProvider = provider;
            return this;
        }

        public Builder propertiesBinder(PropertiesBinder propertiesBinder) {
            Asserts.notNull(propertiesBinder, "propertiesBinder");
            this.propertiesBinder = propertiesBinder;
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
         * This setting is propagated to backend binders that support null filtering so the
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
