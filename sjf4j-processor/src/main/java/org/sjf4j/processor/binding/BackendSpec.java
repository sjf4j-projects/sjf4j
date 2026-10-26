package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.BindingFormat;
import org.sjf4j.util.Asserts;


/** Compile-time description of one concrete binding backend. */
final class BackendSpec {

    private final BindingFormat format;
    private final Backend backend;
    private final String binderType;
    private final String parserType;
    private final String generatorType;

    /*
     * Marker from the native backend library itself. This is deliberately
     * separate from the SJF4J adapter types: an aggregate SJF4J artifact may
     * expose every adapter while leaving third-party JSON/YAML libraries
     * optional. Null means no external library is required.
     */
    private final String libraryMarkerType;

    BackendSpec(
            BindingFormat format,
            Backend backend,
            String binderType,
            String parserType,
            String generatorType,
            String libraryMarkerType) {

        this.format = Asserts.notNull(format, "format");
        this.backend = Asserts.notNull(backend, "backend");
        this.binderType = Asserts.notNull(binderType, "binderType");
        this.parserType = Asserts.notNull(parserType, "parserType");
        this.generatorType = Asserts.notNull(generatorType, "generatorType");
        this.libraryMarkerType = libraryMarkerType;
    }

    BindingFormat format() {
        return format;
    }

    Backend backend() {
        return backend;
    }

    String binderType() {
        return binderType;
    }

    String parserType() {
        return parserType;
    }

    String generatorType() {
        return generatorType;
    }

    String libraryMarkerType() {
        return libraryMarkerType;
    }

    boolean usesNameMatcher() {
        switch (backend) {
            case JACKSON3:
            case JACKSON2:
            case FASTJSON2:
                return true;
            default:
                return false;
        }
    }

    /**
     * Native matcher type instantiated directly by generated binders.
     */
    String matcherType() {
        switch (backend) {
            case JACKSON2:
                return "org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher";
            case JACKSON3:
                return "org.sjf4j.backend.jackson3.binding.Jackson3NameMatcher";
            case FASTJSON2:
                return "org.sjf4j.backend.fastjson2.binding.Fastjson2NameMatcher";
            default:
                throw new IllegalStateException(
                        "No compiled name matcher for backend " + backend);
        }
    }

    boolean usesCompiledNames() {
        return backend == Backend.JACKSON2
                || backend == Backend.JACKSON3
                || backend == Backend.FASTJSON2
                || backend == Backend.SIMPLE
                || backend == Backend.SNAKE;
    }

    String compiledNameType() {
        switch (backend) {
            case JACKSON2:
                return "org.sjf4j.backend.jackson2.binding.Jackson2CompiledName";
            case JACKSON3:
                return "org.sjf4j.backend.jackson3.binding.Jackson3CompiledName";
            case FASTJSON2:
                return "org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName";
            case SIMPLE:
            case SNAKE:
                return "org.sjf4j.binding.CompiledName";
            default:
                throw new IllegalStateException(
                        "No compiled property names for backend " + backend);
        }
    }

    boolean usesExpectedNameMatch() {
        return backend == Backend.JACKSON2
                || backend == Backend.FASTJSON2;
    }

}
