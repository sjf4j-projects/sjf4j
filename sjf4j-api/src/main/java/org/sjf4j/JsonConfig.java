package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.supplier.ListSupplier;
import org.sjf4j.supplier.MapSupplier;

public final class JsonConfig {

    public final JsonFacade<?, ?> jsonFacade;

    public final YamlFacade<?, ?> yamlFacade;

    public final FacadeMode facadeMode;

    public final MapSupplier mapSupplier;

    public final ListSupplier listSupplier;

    private JsonConfig(Builder builder) {
        this.jsonFacade = builder.jsonFacade;
        this.yamlFacade = builder.yamlFacade;
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
        STREAMING_DESIGNED,
        STREAMING_ALL_IN_ONE
    }

    /// Builder

    public static final class Builder {

        private JsonFacade<?, ?> jsonFacade = FacadeFactory.getDefaultJsonFacade();

        private YamlFacade<?, ?> yamlFacade = FacadeFactory.getDefaultYamlFacade();

        private FacadeMode facadeMode = FacadeMode.STREAMING_DESIGNED;

        private MapSupplier mapSupplier = MapSupplier.LinkedHashMapSupplier;

        private ListSupplier listSupplier = ListSupplier.ArrayListSupplier;

        public Builder() {}

        public Builder(@NonNull JsonConfig config) {
            this.jsonFacade = config.jsonFacade;
            this.yamlFacade = config.yamlFacade;
            this.mapSupplier = config.mapSupplier;
            this.listSupplier = config.listSupplier;
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
        public Builder facadeMode(@NonNull FacadeMode facadeMode) {
            this.facadeMode = facadeMode;
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

    }
}
