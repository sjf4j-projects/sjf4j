package org.sjf4j.binding;


import org.sjf4j.RuntimeContext;

/**
 * YAML streaming binder.
 */
public abstract class YamlBinder<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinder<R, W> {
    protected YamlBinder(RuntimeContext context) {
        super(context);
    }
}
