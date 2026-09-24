package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.BindingFormat;

import java.util.Arrays;
import java.util.Collections;

/** Built-in compile-time backend catalog. */
final class BackendCatalog {

    private static final BackendGroup JSON =
            new BackendGroup(
                    BindingFormat.JSON,
                    Arrays.asList(
                            spec(
                                    BindingFormat.JSON,
                                    BindingBackend.JACKSON3,
                                    "org.sjf4j.backend.jackson3.binding.Jackson3Binder",
                                    "org.sjf4j.backend.jackson3.binding.Jackson3Reader",
                                    "org.sjf4j.backend.jackson3.binding.Jackson3Writer",
                                    "tools.jackson.core.json.JsonFactory"),
                            spec(
                                    BindingFormat.JSON,
                                    BindingBackend.JACKSON2,
                                    "org.sjf4j.backend.jackson2.binding.Jackson2Binder",
                                    "org.sjf4j.backend.jackson2.binding.Jackson2Reader",
                                    "org.sjf4j.backend.jackson2.binding.Jackson2Writer",
                                    "com.fasterxml.jackson.core.JsonFactory"),
                            spec(
                                    BindingFormat.JSON,
                                    BindingBackend.GSON,
                                    "org.sjf4j.backend.gson.binding.GsonBinder",
                                    "org.sjf4j.backend.gson.binding.GsonReader",
                                    "org.sjf4j.backend.gson.binding.GsonWriter",
                                    "com.google.gson.Gson"),
                            spec(
                                    BindingFormat.JSON,
                                    BindingBackend.FASTJSON2,
                                    "org.sjf4j.backend.fastjson2.binding.Fastjson2Binder",
                                    "org.sjf4j.backend.fastjson2.binding.Fastjson2Reader",
                                    "org.sjf4j.backend.fastjson2.binding.Fastjson2Writer",
                                    "com.alibaba.fastjson2.JSONReader"),
                            spec(
                                    BindingFormat.JSON,
                                    BindingBackend.JSONP,
                                    "org.sjf4j.backend.jsonp.binding.JsonpBinder",
                                    "org.sjf4j.backend.jsonp.binding.JsonpReader",
                                    "org.sjf4j.backend.jsonp.binding.JsonpWriter",
                                    "jakarta.json.spi.JsonProvider"),
                            spec(
                                    BindingFormat.JSON,
                                    BindingBackend.SIMPLE,
                                    "org.sjf4j.binding.simple.SimpleJsonBinder",
                                    "org.sjf4j.binding.simple.SimpleJsonReader",
                                    "org.sjf4j.binding.simple.SimpleJsonWriter",
                                    null)));

    private static final BackendGroup YAML =
            new BackendGroup(
                    BindingFormat.YAML,
                    Collections.singletonList(
                            spec(
                                    BindingFormat.YAML,
                                    BindingBackend.SNAKE,
                                    "org.sjf4j.backend.snake.binding.SnakeBinder",
                                    "org.sjf4j.backend.snake.binding.SnakeReader",
                                    "org.sjf4j.backend.snake.binding.SnakeWriter",
                                    "org.yaml.snakeyaml.Yaml")));

    private BackendCatalog() {
    }

    static BackendGroup group(BindingFormat format) {
        switch (format) {
            case JSON:
                return JSON;
            case YAML:
                return YAML;
            default:
                throw new AssertionError(format);
        }
    }

    private static BackendSpec spec(
            BindingFormat format,
            BindingBackend backend,
            String binderType,
            String readerType,
            String writerType,
            String libraryMarkerType) {

        return new BackendSpec(
                format,
                backend,
                binderType,
                readerType,
                writerType,
                libraryMarkerType);
    }
}
