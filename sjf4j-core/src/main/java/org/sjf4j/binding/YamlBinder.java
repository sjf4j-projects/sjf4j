package org.sjf4j.binding;


import org.sjf4j.RuntimeContext;

/**
 * YAML facade interface with streaming support.
 */
public abstract class YamlBinder<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinder<R, W> {
    protected YamlBinder(RuntimeContext context) {
        super(context);
    }
}
