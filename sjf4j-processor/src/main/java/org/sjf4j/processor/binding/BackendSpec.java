package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.BindingFormat;
import org.sjf4j.util.Asserts;


/** Compile-time description of one concrete binding backend. */
final class BackendSpec {

    private final BindingFormat format;
    private final Backend backend;
    private final String binderType;
    private final String readerType;
    private final String writerType;

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
            String readerType,
            String writerType,
            String libraryMarkerType) {

        this.format = Asserts.notNull(format, "format");
        this.backend = Asserts.notNull(backend, "backend");
        this.binderType = Asserts.notNull(binderType, "binderType");
        this.readerType = Asserts.notNull(readerType, "readerType");
        this.writerType = Asserts.notNull(writerType, "writerType");
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

    String readerType() {
        return readerType;
    }

    String writerType() {
        return writerType;
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

    boolean usesExpectedNameMatch() {
        return backend == Backend.JACKSON2;
    }

}
