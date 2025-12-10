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

    private static volatile JsonConfig GLOBAL = new JsonConfig.Builder().build();

    public static void global(JsonConfig jsonConfig) {
        if (jsonConfig == null) throw new IllegalArgumentException("JsonConfig must not be null");
        GLOBAL = jsonConfig;
    }
    public static JsonConfig global() {
        return GLOBAL;
    }

    public static void useJacksonAsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createJacksonFacade()).build());
    }
    public static void useGsonAsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createGsonFacade()).build());
    }
    public static void useFastjson2AsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createFastjson2Facade()).build());
    }
    public static void useSimpleJsonAsGlobal() {
        JsonConfig.global(new JsonConfig.Builder(JsonConfig.global())
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

    public ObjectFacade getObjectFacade() {
        if (objectFacade == null) {
            objectFacade = FacadeFactory.getDefaultObjectFacade();
        }
        return objectFacade;
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
        private ObjectFacade objectFacade;

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
        public Builder(JsonConfig config) {
            if (config == null) throw new IllegalArgumentException("JsonConfig must not be null");
            this.mapSupplier = config.mapSupplier;
            this.listSupplier = config.listSupplier;
            this.readMode = config.readMode;
            this.writeMode = config.writeMode;
        }

        public JsonConfig build() {
            return new JsonConfig(this);
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
        public Builder objectFacade(ObjectFacade objectFacade) {
            if (objectFacade == null) throw new IllegalArgumentException("objectFacade must not be null");
            this.objectFacade = objectFacade;
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
        public Builder readMode(JsonConfig.ReadMode readMode) {
            if (readMode == null) throw new IllegalArgumentException("readMode must not be null");
            this.readMode = readMode;
            return this;
        }
        public Builder writeMode(JsonConfig.WriteMode writeMode) {
            if (writeMode == null) throw new IllegalArgumentException("writeMode must not be null");
            this.writeMode = writeMode;
            return this;
        }

    }
}
