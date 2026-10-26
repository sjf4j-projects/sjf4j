package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
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
                                    Backend.JACKSON3,
                                    "org.sjf4j.backend.jackson3.binding.Jackson3Binder",
                                    "org.sjf4j.backend.jackson3.binding.Jackson3Parser",
                                    "org.sjf4j.backend.jackson3.binding.Jackson3Generator",
                                    "tools.jackson.core.json.JsonFactory"),
                            spec(
                                    BindingFormat.JSON,
                                    Backend.JACKSON2,
                                    "org.sjf4j.backend.jackson2.binding.Jackson2Binder",
                                    "org.sjf4j.backend.jackson2.binding.Jackson2Parser",
                                    "org.sjf4j.backend.jackson2.binding.Jackson2Generator",
                                    "com.fasterxml.jackson.core.JsonFactory"),
                            spec(
                                    BindingFormat.JSON,
                                    Backend.GSON,
                                    "org.sjf4j.backend.gson.binding.GsonBinder",
                                    "org.sjf4j.backend.gson.binding.GsonParser",
                                    "org.sjf4j.backend.gson.binding.GsonGenerator",
                                    "com.google.gson.Gson"),
                            spec(
                                    BindingFormat.JSON,
                                    Backend.FASTJSON2,
                                    "org.sjf4j.backend.fastjson2.binding.Fastjson2Binder",
                                    "org.sjf4j.backend.fastjson2.binding.Fastjson2Parser",
                                    "org.sjf4j.backend.fastjson2.binding.Fastjson2Generator",
                                    "com.alibaba.fastjson2.JSONReader"),
                            spec(
                                    BindingFormat.JSON,
                                    Backend.JSONP,
                                    "org.sjf4j.backend.jsonp.binding.JsonpBinder",
                                    "org.sjf4j.backend.jsonp.binding.JsonpParser",
                                    "org.sjf4j.backend.jsonp.binding.JsonpGenerator",
                                    "jakarta.json.spi.JsonProvider"),
                            spec(
                                    BindingFormat.JSON,
                                    Backend.SIMPLE,
                                    "org.sjf4j.binding.simple.SimpleJsonBinder",
                                    "org.sjf4j.binding.simple.SimpleJsonParser",
                                    "org.sjf4j.binding.simple.SimpleJsonGenerator",
                                    null)));

    private static final BackendGroup YAML =
            new BackendGroup(
                    BindingFormat.YAML,
                    Collections.singletonList(
                            spec(
                                    BindingFormat.YAML,
                                    Backend.SNAKE,
                                    "org.sjf4j.backend.snake.binding.SnakeBinder",
                                    "org.sjf4j.backend.snake.binding.SnakeParser",
                                    "org.sjf4j.backend.snake.binding.SnakeGenerator",
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
            Backend backend,
            String binderType,
            String parserType,
            String generatorType,
            String libraryMarkerType) {

        return new BackendSpec(
                format,
                backend,
                binderType,
                parserType,
                generatorType,
                libraryMarkerType);
    }
}
