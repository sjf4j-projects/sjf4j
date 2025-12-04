package org.sjf4j;

import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.ObjectFacade;
import org.sjf4j.facades.PropertiesFacade;
import org.sjf4j.facades.YamlFacade;
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
public final class JsonConfig {

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
    private ObjectFacade objectFacade;

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
    private JsonConfig(Builder builder) {
        this.jsonFacade = builder.jsonFacade;
        this.yamlFacade = builder.yamlFacade;
        this.propertiesFacade = builder.propertiesFacade;
        this.objectFacade = builder.objectFacade;
        this.mapSupplier = builder.mapSupplier;
        this.listSupplier = builder.listSupplier;
        this.readMode = builder.readMode;
        this.writeMode = builder.writeMode;
    }

    /**
     * The global configuration instance.
     */
    private static volatile JsonConfig GLOBAL = new JsonConfig.Builder().build();

    /**
     * Sets the global configuration instance.
     *
     * @param jsonConfig The configuration instance to set as global
     */
    public static void global(JsonConfig jsonConfig) {
        if (jsonConfig == null) throw new IllegalArgumentException("JsonConfig must not be null");
        GLOBAL = jsonConfig;
    }

    /**
     * Gets the global configuration instance.
     *
     * @return The global configuration instance
     */
    public static JsonConfig global() {
        return GLOBAL;
    }

    /**
     * Sets Jackson as the global JSON facade.
     */
    public static void useJacksonAsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createJacksonFacade()).build());
    }

    /**
     * Sets Gson as the global JSON facade.
     */
    public static void useGsonAsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createGsonFacade()).build());
    }

    /**
     * Sets Fastjson2 as the global JSON facade.
     */
    public static void useFastjson2AsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createFastjson2Facade()).build());
    }

    /**
     * Gets the JSON facade, creating a default one if not set.
     *
     * @return The JSON facade implementation
     */
    public JsonFacade<?, ?> getJsonFacade() {
        if (jsonFacade == null) {
            jsonFacade = FacadeFactory.getDefaultJsonFacade();
        }
        return jsonFacade;
    }

    /**
     * Gets the YAML facade, creating a default one if not set.
     *
     * @return The YAML facade implementation
     */
    public YamlFacade<?, ?> getYamlFacade() {
        if (yamlFacade == null) {
            yamlFacade = FacadeFactory.getDefaultYamlFacade();
        }
        return yamlFacade;
    }

    /**
     * Gets the Properties facade, creating a default one if not set.
     *
     * @return The Properties facade implementation
     */
    public PropertiesFacade getPropertiesFacade() {
        if (propertiesFacade == null) {
            propertiesFacade = FacadeFactory.getDefaultPropertiesFacade();
        }
        return propertiesFacade;
    }

    /**
     * Gets the Object facade, creating a default one if not set.
     *
     * @return The Object facade implementation
     */
    public ObjectFacade getObjectFacade() {
        if (objectFacade == null) {
            objectFacade = FacadeFactory.getDefaultObjectFacade();
        }
        return objectFacade;
    }

    /// Mode

    /**
     * Enumeration of read modes for data processing.
     */
    public enum ReadMode {
        /** General streaming mode for reading data. */
        STREAMING_GENERAL,
        /** Specific streaming mode optimized for the underlying facade. */
        STREAMING_SPECIFIC,
        /** Uses modules for data reading. */
        USE_MODULE,
        /** Fast but potentially unsafe mode for reading data. */
        FAST_UNSAFE,
    }

    /**
     * Enumeration of write modes for data processing.
     */
    public enum WriteMode {
        /** General streaming mode for writing data. */
        STREAMING_GENERAL,
        /** Specific streaming mode optimized for the underlying facade. */
        STREAMING_SPECIFIC,
        /** Uses modules for data writing. */
        USE_MODULE,
    }


    /// Builder

    /**
     * Builder class for creating JsonConfig instances.
     * <p>
     * This class follows the builder pattern, allowing for fluent configuration
     * of JsonConfig settings before creation.
     */
    public static final class Builder {

        private JsonFacade<?, ?> jsonFacade;
        private YamlFacade<?, ?> yamlFacade;
        private PropertiesFacade propertiesFacade;
        private ObjectFacade objectFacade;

        private MapSupplier mapSupplier = MapSupplier.LinkedHashMapSupplier;
        private ListSupplier listSupplier = ListSupplier.ArrayListSupplier;

        private ReadMode readMode = ReadMode.USE_MODULE;
        private WriteMode writeMode = WriteMode.USE_MODULE;

        /**
         * Creates a new Builder with default settings.
         */
        public Builder() {}


        public Builder(JsonConfig jsonConfig) {
//            this.jsonFacade = config.jsonFacade;
//            this.yamlFacade = config.yamlFacade;
//            this.propertiesFacade = config.propertiesFacade;
//            this.objectFacade = config.objectFacade;
            if (jsonConfig == null) throw new IllegalArgumentException("jsonConfig must not be null");
            this.mapSupplier = jsonConfig.mapSupplier;
            this.listSupplier = jsonConfig.listSupplier;
            this.readMode = jsonConfig.readMode;
            this.writeMode = jsonConfig.writeMode;
        }

        /**
         * Builds and returns the configured JsonConfig instance.
         *
         * @return The configured JsonConfig instance
         */
        public JsonConfig build() {
            return new JsonConfig(this);
        }

        /**
         * Sets the JSON facade implementation.
         *
         * @param jsonFacade The JSON facade to set
         * @return This builder instance for method chaining
         */
        public Builder jsonFacade(JsonFacade<?, ?> jsonFacade) {
            if (jsonFacade == null) throw new IllegalArgumentException("JsonFacade must not be null");
            this.jsonFacade = jsonFacade;
            return this;
        }

        /**
         * Sets the YAML facade implementation.
         *
         * @param yamlFacade The YAML facade to set
         * @return This builder instance for method chaining
         */
        public Builder yamlFacade(YamlFacade<?, ?> yamlFacade) {
            if (yamlFacade == null) throw new IllegalArgumentException("YamlFacade must not be null");
            this.yamlFacade = yamlFacade;
            return this;
        }

        /**
         * Sets the Properties facade implementation.
         *
         * @param propertiesFacade The Properties facade to set
         * @return This builder instance for method chaining
         */
        public Builder propertiesFacade(PropertiesFacade propertiesFacade) {
            if (propertiesFacade == null) throw new IllegalArgumentException("PropertiesFacade must not be null");
            this.propertiesFacade = propertiesFacade;
            return this;
        }

        /**
         * Sets the Object facade implementation.
         *
         * @param objectFacade The Object facade to set
         * @return This builder instance for method chaining
         */
        public Builder objectFacade(ObjectFacade objectFacade) {
            if (objectFacade == null) throw new IllegalArgumentException("ObjectFacade must not be null");
            this.objectFacade = objectFacade;
            return this;
        }

        /**
         * Sets the map supplier for creating map instances.
         *
         * @param mapSupplier The map supplier to set
         * @return This builder instance for method chaining
         */
        public Builder mapSupplier(MapSupplier mapSupplier) {
            if (mapSupplier == null) throw new IllegalArgumentException("MapSupplier must not be null");
            this.mapSupplier = mapSupplier;
            return this;
        }

        /**
         * Sets the list supplier for creating list instances.
         *
         * @param listSupplier The list supplier to set
         * @return This builder instance for method chaining
         */
        public Builder listSupplier(ListSupplier listSupplier) {
            if (listSupplier == null) throw new IllegalArgumentException("ListSupplier must not be null");
            this.listSupplier = listSupplier;
            return this;
        }

        /**
         * Sets the read mode for data processing.
         *
         * @param readMode The read mode to set
         * @return This builder instance for method chaining
         */
        public Builder readMode(JsonConfig.ReadMode readMode) {
            if (readMode == null) throw new IllegalArgumentException("ReadMode must not be null");
            this.readMode = readMode;
            return this;
        }

        /**
         * Sets the write mode for data processing.
         *
         * @param writeMode The write mode to set
         * @return This builder instance for method chaining
         */
        public Builder writeMode(JsonConfig.WriteMode writeMode) {
            if (writeMode == null) throw new IllegalArgumentException("WriteMode must not be null");
            this.writeMode = writeMode;
            return this;
        }

    }
}