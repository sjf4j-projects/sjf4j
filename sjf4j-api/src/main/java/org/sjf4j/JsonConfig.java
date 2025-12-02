package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.facades.JsonFacade;
import org.sjf4j.facades.ObjectFacade;
import org.sjf4j.facades.PropertiesFacade;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.supplier.ListSupplier;
import org.sjf4j.supplier.MapSupplier;

@Getter
public final class JsonConfig {

    private JsonFacade<?, ?> jsonFacade;
    private YamlFacade<?, ?> yamlFacade;
    private PropertiesFacade propertiesFacade;
    private ObjectFacade objectFacade;

    public final ReadMode readMode;
    public final WriteMode writeMode;

    public final MapSupplier mapSupplier;

    public final ListSupplier listSupplier;

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

    public static void global(@NonNull JsonConfig jsonConfig) {
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


        public Builder() {}

        public Builder(@NonNull JsonConfig config) {
//            this.jsonFacade = config.jsonFacade;
//            this.yamlFacade = config.yamlFacade;
//            this.propertiesFacade = config.propertiesFacade;
//            this.objectFacade = config.objectFacade;
            this.mapSupplier = config.mapSupplier;
            this.listSupplier = config.listSupplier;
            this.readMode = config.readMode;
            this.writeMode = config.writeMode;
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
        public Builder readMode(@NonNull JsonConfig.ReadMode readMode) {
            this.readMode = readMode;
            return this;
        }
        public Builder writeMode(@NonNull JsonConfig.WriteMode writeMode) {
            this.writeMode = writeMode;
            return this;
        }

    }
}
