package org.sjf4j.binding;


/**
 * YAML facade interface with streaming support.
 */
public abstract class YamlBinder<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinder<R, W> {
    protected YamlBinder(StreamingContext context) {
        super(context);
    }
}
