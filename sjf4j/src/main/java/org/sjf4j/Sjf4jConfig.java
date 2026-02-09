package org.sjf4j;

import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.supplier.ListSupplier;
import org.sjf4j.supplier.MapSupplier;
import org.sjf4j.supplier.SetSupplier;

/**
 * Configuration class for SJF4J (Simple JSON Facade for Java).
 * <p>
 * This class manages the configuration settings for SJF4J, including:
 * - The facades used for JSON, YAML, Properties, and Object operations
 * - The read and write modes for data processing
 * - The suppliers for creating maps and lists
 * - Global configuration settings
 * <p>
 * It uses the builder pattern to create and configure instances, allowing for flexible
 * customization of SJF4J behavior.
 */
public final class Sjf4jConfig {

    /**
     * The JSON facade implementation to use for JSON operations.
     */
    private JsonFacade<?, ?> jsonFacade;

    /**
     * The YAML facade implementation to use for YAML operations.
     */
    private YamlFacade<?, ?> yamlFacade;

    /**
     * The Properties facade implementation to use for Properties operations.
     */
    private PropertiesFacade propertiesFacade;

    /**
     * The Object facade implementation to use for object conversion operations.
     */
    private NodeFacade nodeFacade;

    public final StreamingFacade.StreamingMode streamingMode;

    /**
     * The supplier used to create map instances.
     */
    public final MapSupplier mapSupplier;

    /**
     * The supplier used to create list instances.
     */
    public final ListSupplier listSupplier;

    /**
     * The supplier used to create list instances.
     */
    public final SetSupplier setSupplier;

    public enum InstantFormat {
        ISO_STRING,
        EPOCH_MILLIS,
    }

    /**
     * The format used when encoding/decoding {@link java.time.Instant}.
     */
    public final InstantFormat instantFormat;

    public final boolean bindingPath;

    /**
     * Private constructor for JsonConfig. Use the Builder to create instances.
     *
     * @param builder The builder containing the configuration settings
     */
    private Sjf4jConfig(Builder builder) {
        this.jsonFacade = builder.jsonFacade;
        this.yamlFacade = builder.yamlFacade;
        this.propertiesFacade = builder.propertiesFacade;
        this.nodeFacade = builder.nodeFacade;
        this.mapSupplier = builder.mapSupplier;
        this.listSupplier = builder.listSupplier;
        this.setSupplier = builder.setSupplier;
        this.streamingMode = builder.streamingMode;
        this.instantFormat = builder.instantFormat;
        this.bindingPath = builder.bindingPath;
    }

    private static volatile Sjf4jConfig GLOBAL = new Sjf4jConfig.Builder().build();

    public static void global(Sjf4jConfig sjf4jConfig) {
        if (sjf4jConfig == null) throw new IllegalArgumentException("JsonConfig must not be null");
        GLOBAL = sjf4jConfig;

        NodeRegistry.refreshInstantValueCodec(sjf4jConfig.instantFormat);
    }
    public static Sjf4jConfig global() {
        return GLOBAL;
    }

    public static void useJacksonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(FacadeFactory.createJacksonFacade()).build());
    }
    public static void useGsonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(FacadeFactory.createGsonFacade()).build());
    }
    public static void useFastjson2AsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(FacadeFactory.createFastjson2Facade()).build());
    }
    public static void useStreamingSharedIOAsGlobal() {
        Sjf4jConfig.global(new Builder(Sjf4jConfig.global())
                .streamingMode(StreamingFacade.StreamingMode.SHARED_IO).build());
    }
    public static void useStreamingExclusiveIOAsGlobal() {
        Sjf4jConfig.global(new Builder(Sjf4jConfig.global())
                .streamingMode(StreamingFacade.StreamingMode.EXCLUSIVE_IO).build());
    }
    public static void useStreamingPluginModuleAsGlobal() {
        Sjf4jConfig.global(new Builder(Sjf4jConfig.global())
                .streamingMode(StreamingFacade.StreamingMode.PLUGIN_MODULE).build());
    }

    public static void useSimpleJsonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(FacadeFactory.createSimpleJsonFacade()).build());
    }

    public static void useInstantEpochMillisAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .instantFormat(InstantFormat.EPOCH_MILLIS).build());
    }

    public static void useInstantIsoAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .instantFormat(InstantFormat.ISO_STRING).build());
    }

    public JsonFacade<?, ?> getJsonFacade() {
        if (jsonFacade == null) {
            jsonFacade = FacadeFactory.getDefaultJsonFacade();
        }
        return jsonFacade;
    }

    public YamlFacade<?, ?> getYamlFacade() {
        if (yamlFacade == null) {
            yamlFacade = FacadeFactory.getDefaultYamlFacade();
        }
        return yamlFacade;
    }

    public PropertiesFacade getPropertiesFacade() {
        if (propertiesFacade == null) {
            propertiesFacade = FacadeFactory.getDefaultPropertiesFacade();
        }
        return propertiesFacade;
    }

    public NodeFacade getNodeFacade() {
        if (nodeFacade == null) {
            nodeFacade = FacadeFactory.getDefaultNodeFacade();
        }
        return nodeFacade;
    }

    public boolean isBindingPath() {
        return bindingPath;
    }


    /// Builder

    public static final class Builder {

        private JsonFacade<?, ?> jsonFacade;
        private YamlFacade<?, ?> yamlFacade;
        private PropertiesFacade propertiesFacade;
        private NodeFacade nodeFacade;

        private MapSupplier mapSupplier = MapSupplier.LinkedHashMapSupplier;
        private ListSupplier listSupplier = ListSupplier.ArrayListSupplier;
        private SetSupplier setSupplier = SetSupplier.LinkedHashSetSupplier;

        private StreamingFacade.StreamingMode streamingMode = null;
        private InstantFormat instantFormat = InstantFormat.ISO_STRING;
        private boolean bindingPath = true;

        /**
         * Creates a new Builder with default settings.
         */
        public Builder() {}

        /**
         * Creates a new Builder with settings copied from the given configuration.
         *
         * @param config The configuration to copy settings from
         */
        public Builder(Sjf4jConfig config) {
            if (config == null) throw new IllegalArgumentException("JsonConfig must not be null");
            this.mapSupplier = config.mapSupplier;
            this.listSupplier = config.listSupplier;
            this.instantFormat = config.instantFormat;
        }

        public Sjf4jConfig build() {
            return new Sjf4jConfig(this);
        }

        public Builder jsonFacade(JsonFacade<?, ?> jsonFacade) {
            if (jsonFacade == null) throw new IllegalArgumentException("jsonFacade must not be null");
            this.jsonFacade = jsonFacade;
            return this;
        }
        public Builder yamlFacade(YamlFacade<?, ?> yamlFacade) {
            if (yamlFacade == null) throw new IllegalArgumentException("yamlFacade must not be null");
            this.yamlFacade = yamlFacade;
            return this;
        }
        public Builder propertiesFacade(PropertiesFacade propertiesFacade) {
            if (propertiesFacade == null) throw new IllegalArgumentException("propertiesFacade must not be null");
            this.propertiesFacade = propertiesFacade;
            return this;
        }
        public Builder nodeFacade(NodeFacade nodeFacade) {
            if (nodeFacade == null) throw new IllegalArgumentException("nodeFacade must not be null");
            this.nodeFacade = nodeFacade;
            return this;
        }
        public Builder mapSupplier(MapSupplier mapSupplier) {
            if (mapSupplier == null) throw new IllegalArgumentException("mapSupplier must not be null");
            this.mapSupplier = mapSupplier;
            return this;
        }
        public Builder listSupplier(ListSupplier listSupplier) {
            if (listSupplier == null) throw new IllegalArgumentException("listSupplier must not be null");
            this.listSupplier = listSupplier;
            return this;
        }
        public Builder setSupplier(SetSupplier setSupplier) {
            if (setSupplier == null) throw new IllegalArgumentException("setSupplier must not be null");
            this.setSupplier = setSupplier;
            return this;
        }
        public Builder streamingMode(StreamingFacade.StreamingMode streamingMode) {
            this.streamingMode = streamingMode;
            return this;
        }
        public Builder instantFormat(InstantFormat instantFormat) {
            if (instantFormat == null) throw new IllegalArgumentException("instantFormat must not be null");
            this.instantFormat = instantFormat;
            return this;
        }
        public Builder bindingPath(boolean bindingPath) {
            this.bindingPath = bindingPath;
            return this;
        }

    }
}
