package org.sjf4j.binding;


/**
 * YAML facade interface with streaming support.
 */
public interface YamlBinding<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinding<R, W> {
}
