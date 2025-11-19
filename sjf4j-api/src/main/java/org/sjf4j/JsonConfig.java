package org.sjf4j;

import lombok.NonNull;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.ObjectFacade;
import org.sjf4j.facades.PropertiesFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.supplier.ListSupplier;
import org.sjf4j.supplier.MapSupplier;

public final class JsonConfig {

    public final JsonFacade<?, ?> jsonFacade;
    public final YamlFacade<?, ?> yamlFacade;
    public final PropertiesFacade propertiesFacade;
    public final ObjectFacade objectFacade;

    public final FacadeMode facadeMode;

    public final MapSupplier mapSupplier;

    public final ListSupplier listSupplier;

    private JsonConfig(Builder builder) {
        this.jsonFacade = builder.jsonFacade;
        this.yamlFacade = builder.yamlFacade;
        this.propertiesFacade = builder.propertiesFacade;
        this.objectFacade = builder.objectFacade;
        this.mapSupplier = builder.mapSupplier;
        this.listSupplier = builder.listSupplier;
        this.facadeMode = builder.facadeMode;
    }

    private static volatile JsonConfig GLOBAL = new JsonConfig.Builder().build();

    public static void setGlobal(@NonNull JsonConfig config) {
        GLOBAL = config;
    }
    public static JsonConfig global() {
        return GLOBAL;
    }

    public static void useJacksonAsGlobal() {
        JsonConfig.setGlobal(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createJacksonFacade()).build());
    }
    public static void useGsonAsGlobal() {
        JsonConfig.setGlobal(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createGsonFacade()).build());
    }
    public static void useFastjson2AsGlobal() {
        JsonConfig.setGlobal(new JsonConfig.Builder(JsonConfig.global())
                .jsonFacade(FacadeFactory.createFastjson2Facade()).build());
    }

    /// FacadeMode

    public static enum FacadeMode {
        STREAMING_GENERAL,
        STREAMING_SPECIFIC,
        FAST_UNSAFE
    }

    /// Builder

    public static final class Builder {

        private JsonFacade<?, ?> jsonFacade = FacadeFactory.getDefaultJsonFacade();
        private YamlFacade<?, ?> yamlFacade = FacadeFactory.getDefaultYamlFacade();
        private PropertiesFacade propertiesFacade = FacadeFactory.getDefaultPropertiesFacade();
        private ObjectFacade objectFacade = FacadeFactory.getDefaultObjectFacade();

        private MapSupplier mapSupplier = MapSupplier.LinkedHashMapSupplier;
        private ListSupplier listSupplier = ListSupplier.ArrayListSupplier;

        private FacadeMode facadeMode = FacadeMode.STREAMING_GENERAL;


        public Builder() {}

        public Builder(@NonNull JsonConfig config) {
            this.jsonFacade = config.jsonFacade;
            this.yamlFacade = config.yamlFacade;
            this.propertiesFacade = config.propertiesFacade;
            this.objectFacade = config.objectFacade;
            this.mapSupplier = config.mapSupplier;
            this.listSupplier = config.listSupplier;
            this.facadeMode = config.facadeMode;
        }

        public JsonConfig build() {
            return new JsonConfig(this);
        }

        public Builder jsonFacade(@NonNull JsonFacade<?, ?> jsonFacade) {
            this.jsonFacade = jsonFacade;
            return this;
        }
        public Builder yamlFacade(@NonNull YamlFacade<?, ?> yamlFacade) {
            this.yamlFacade = yamlFacade;
            return this;
        }
        public Builder propertiesFacade(@NonNull PropertiesFacade propertiesFacade) {
            this.propertiesFacade = propertiesFacade;
            return this;
        }
        public Builder objectFacade(@NonNull ObjectFacade objectFacade) {
            this.objectFacade = objectFacade;
            return this;
        }
        public Builder mapSupplier(@NonNull MapSupplier mapSupplier) {
            this.mapSupplier = mapSupplier;
            return this;
        }
        public Builder listSupplier(@NonNull ListSupplier listSupplier) {
            this.listSupplier = listSupplier;
            return this;
        }
        public Builder facadeMode(@NonNull FacadeMode facadeMode) {
            this.facadeMode = facadeMode;
            return this;
        }

    }
}
