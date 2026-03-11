package org.sjf4j;

import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.PropertiesFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.YamlFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.supplier.ListSupplier;
import org.sjf4j.supplier.MapSupplier;
import org.sjf4j.supplier.SetSupplier;

import java.util.Objects;

/**
 * Global configuration for SJF4J.
 *
 * <p>Controls facade implementations, streaming mode, container suppliers,
 * and encoding options (e.g. {@link java.time.Instant} format).
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
        this.instantFormat = builder.instantFormat;
        this.bindingPath = builder.bindingPath;
    }

    private static Sjf4jConfig GLOBAL = new Sjf4jConfig.Builder().build();

    /**
     * Replaces global configuration and refreshes dependent codecs.
     */
    public static void global(Sjf4jConfig sjf4jConfig) {
        Objects.requireNonNull(sjf4jConfig, "sjf4jConfig");
        GLOBAL = sjf4jConfig;
        NodeRegistry.refreshInstantValueCodec(sjf4jConfig.instantFormat);
    }
    /**
     * Returns the current global configuration.
     */
    public static Sjf4jConfig global() {
        return GLOBAL;
    }


    /**
     * Switches global JSON facade to Jackson.
     */
    public static void useJacksonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new JacksonJsonFacade()).build());
    }
    public static void useJacksonAsGlobal(StreamingFacade.StreamingMode streamingMode) {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new JacksonJsonFacade(streamingMode)).build());
    }
    /**
     * Switches global JSON facade to Gson.
     */
    public static void useGsonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new GsonJsonFacade()).build());
    }
    public static void useGsonAsGlobal(StreamingFacade.StreamingMode streamingMode) {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new GsonJsonFacade(streamingMode)).build());
    }
    /**
     * Switches global JSON facade to Fastjson2.
     */
    public static void useFastjson2AsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new Fastjson2JsonFacade()).build());
    }
    public static void useFastjson2AsGlobal(StreamingFacade.StreamingMode streamingMode) {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new Fastjson2JsonFacade(streamingMode)).build());
    }
    /**
     * Switches global JSON facade to JSON-P.
     */
    public static void useJsonpAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new JsonpJsonFacade()).build());
    }
    /**
     * Switches global JSON facade to built-in simple implementation.
     */
    public static void useSimpleJsonAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .jsonFacade(new SimpleJsonFacade()).build());
    }

    /**
     * Enables or disables binding path tracking globally.
     */
    public static void useBindingPath(boolean bindingPath) {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .bindingPath(bindingPath).build());
    }

    /**
     * Uses epoch-millis format for Instant globally.
     */
    public static void useInstantEpochMillisAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .instantFormat(InstantFormat.EPOCH_MILLIS).build());
    }

    /**
     * Uses ISO string format for Instant globally.
     */
    public static void useInstantIsoAsGlobal() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder(Sjf4jConfig.global())
                .instantFormat(InstantFormat.ISO_STRING).build());
    }

    /**
     * Returns JSON facade, lazily resolving default when needed.
     */
    public JsonFacade<?, ?> getJsonFacade() {
        if (jsonFacade == null) {
            jsonFacade = FacadeFactory.getDefaultJsonFacade();
        }
        return jsonFacade;
    }

    /**
     * Returns YAML facade, lazily resolving default when needed.
     */
    public YamlFacade<?, ?> getYamlFacade() {
        if (yamlFacade == null) {
            yamlFacade = FacadeFactory.getDefaultYamlFacade();
        }
        return yamlFacade;
    }

    /**
     * Returns Properties facade, lazily resolving default when needed.
     */
    public PropertiesFacade getPropertiesFacade() {
        if (propertiesFacade == null) {
            propertiesFacade = FacadeFactory.getDefaultPropertiesFacade();
        }
        return propertiesFacade;
    }

    /**
     * Returns node facade, lazily resolving default when needed.
     */
    public NodeFacade getNodeFacade() {
        if (nodeFacade == null) {
            nodeFacade = FacadeFactory.getDefaultNodeFacade();
        }
        return nodeFacade;
    }

    /**
     * Returns whether binding path is enabled.
     */
    public boolean isBindingPath() {
        return bindingPath;
    }

    /**
     * Returns a compact, human-readable snapshot of current config.
     */
    public String inspect() {
        return "Sjf4jConfig{" +
                "jsonFacade=" + _simpleName(getJsonFacade()) +
                ", yamlFacade=" + _simpleName(getYamlFacade()) +
                ", propertiesFacade=" + _simpleName(getPropertiesFacade()) +
                ", nodeFacade=" + _simpleName(getNodeFacade()) +
                ", mapSupplier=" + mapSupplier +
                ", listSupplier=" + listSupplier +
                ", setSupplier=" + setSupplier +
                ", instantFormat=" + instantFormat +
                ", bindingPath=" + bindingPath +
                '}';
    }

    private static String _simpleName(Object object) {
        return object == null ? "null" : object.getClass().getSimpleName();
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

        private InstantFormat instantFormat = InstantFormat.ISO_STRING;
        private boolean bindingPath = true;

        /**
         * Creates a builder with default settings.
         */
        public Builder() {}

        /**
         * Creates a builder initialized from existing config.
         */
        public Builder(Sjf4jConfig config) {
            Objects.requireNonNull(config, "config");
            this.jsonFacade = config.jsonFacade;
            this.yamlFacade = config.yamlFacade;
            this.propertiesFacade = config.propertiesFacade;
            this.nodeFacade = config.nodeFacade;
            this.mapSupplier = config.mapSupplier;
            this.listSupplier = config.listSupplier;
            this.setSupplier = config.setSupplier;
            this.instantFormat = config.instantFormat;
            this.bindingPath = config.bindingPath;
        }

        /**
         * Builds an immutable configuration.
         */
        public Sjf4jConfig build() {
            return new Sjf4jConfig(this);
        }

        /**
         * Sets JSON facade.
         */
        public Builder jsonFacade(JsonFacade<?, ?> jsonFacade) {
            Objects.requireNonNull(jsonFacade, "jsonFacade");
            this.jsonFacade = jsonFacade;
            return this;
        }
        /**
         * Sets YAML facade.
         */
        public Builder yamlFacade(YamlFacade<?, ?> yamlFacade) {
            Objects.requireNonNull(yamlFacade, "yamlFacade");
            this.yamlFacade = yamlFacade;
            return this;
        }
        /**
         * Sets properties facade.
         */
        public Builder propertiesFacade(PropertiesFacade propertiesFacade) {
            Objects.requireNonNull(propertiesFacade, "propertiesFacade");
            this.propertiesFacade = propertiesFacade;
            return this;
        }
        /**
         * Sets node facade.
         */
        public Builder nodeFacade(NodeFacade nodeFacade) {
            Objects.requireNonNull(nodeFacade, "nodeFacade");
            this.nodeFacade = nodeFacade;
            return this;
        }
        /**
         * Sets map supplier.
         */
        public Builder mapSupplier(MapSupplier mapSupplier) {
            Objects.requireNonNull(mapSupplier, "mapSupplier");
            this.mapSupplier = mapSupplier;
            return this;
        }
        /**
         * Sets list supplier.
         */
        public Builder listSupplier(ListSupplier listSupplier) {
            Objects.requireNonNull(listSupplier, "listSupplier");
            this.listSupplier = listSupplier;
            return this;
        }
        /**
         * Sets set supplier.
         */
        public Builder setSupplier(SetSupplier setSupplier) {
            Objects.requireNonNull(setSupplier, "setSupplier");
            this.setSupplier = setSupplier;
            return this;
        }
        /**
         * Sets Instant format.
         */
        public Builder instantFormat(InstantFormat instantFormat) {
            Objects.requireNonNull(instantFormat, "instantFormat");
            this.instantFormat = instantFormat;
            return this;
        }
        /**
         * Sets binding path behavior.
         */
        public Builder bindingPath(boolean bindingPath) {
            this.bindingPath = bindingPath;
            return this;
        }

    }
}
