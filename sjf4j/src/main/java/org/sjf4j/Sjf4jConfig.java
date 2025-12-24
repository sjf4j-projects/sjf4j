package org.sjf4j;

import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.supplier.ListSupplier;
import org.sjf4j.supplier.MapSupplier;

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

    /**
     * The read mode for data processing.
     */
    public final ReadMode readMode;

    /**
     * The write mode for data processing.
     */
    public final WriteMode writeMode;

    /**
     * The supplier used to create map instances.
     */
    public final MapSupplier mapSupplier;

    /**
     * The supplier used to create list instances.
     */
    public final ListSupplier listSupplier;

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
        this.readMode = builder.readMode;
        this.writeMode = builder.writeMode;
    }

    private static volatile Sjf4jConfig GLOBAL = new Sjf4jConfig.Builder().build();

    public static void global(Sjf4jConfig sjf4jConfig) {
        if (sjf4jConfig == null) throw new IllegalArgumentException("JsonConfig must not be null");
        GLOBAL = sjf4jConfig;
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
    public static void useSimpleJsonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(FacadeFactory.createSimpleJsonFacade()).build());
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

    /// Mode

    public enum ReadMode {
        STREAMING_GENERAL,
        STREAMING_SPECIFIC,
        USE_MODULE,
        FAST_UNSAFE,
    }

    public enum WriteMode {
        STREAMING_GENERAL,
        STREAMING_SPECIFIC,
        USE_MODULE,
    }


    /// Builder

    public static final class Builder {

        private JsonFacade<?, ?> jsonFacade;
        private YamlFacade<?, ?> yamlFacade;
        private PropertiesFacade propertiesFacade;
        private NodeFacade nodeFacade;

        private MapSupplier mapSupplier = MapSupplier.LinkedHashMapSupplier;
        private ListSupplier listSupplier = ListSupplier.ArrayListSupplier;

        private ReadMode readMode = ReadMode.USE_MODULE;
        private WriteMode writeMode = WriteMode.USE_MODULE;

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
            this.readMode = config.readMode;
            this.writeMode = config.writeMode;
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
        public Builder readMode(Sjf4jConfig.ReadMode readMode) {
            if (readMode == null) throw new IllegalArgumentException("readMode must not be null");
            this.readMode = readMode;
            return this;
        }
        public Builder writeMode(Sjf4jConfig.WriteMode writeMode) {
            if (writeMode == null) throw new IllegalArgumentException("writeMode must not be null");
            this.writeMode = writeMode;
            return this;
        }

    }
}
