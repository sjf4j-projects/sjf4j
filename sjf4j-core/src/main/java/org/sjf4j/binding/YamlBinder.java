package org.sjf4j.binding;


/**
 * YAML facade interface with streaming support.
 */
public interface YamlBinder<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinder<R, W> {
}
