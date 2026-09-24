package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.BindingFormat;

import java.util.Objects;

/** Compile-time description of one concrete binding backend. */
final class BackendSpec {

    private final BindingFormat format;
    private final BindingBackend backend;
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
            BindingBackend backend,
            String binderType,
            String readerType,
            String writerType,
            String libraryMarkerType) {

        this.format = Objects.requireNonNull(format, "format");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.binderType = Objects.requireNonNull(binderType, "binderType");
        this.readerType = Objects.requireNonNull(readerType, "readerType");
        this.writerType = Objects.requireNonNull(writerType, "writerType");
        this.libraryMarkerType = libraryMarkerType;
    }

    BindingFormat format() {
        return format;
    }

    BindingBackend backend() {
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
}
