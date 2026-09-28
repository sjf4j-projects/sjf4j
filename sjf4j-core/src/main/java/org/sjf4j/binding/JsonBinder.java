package org.sjf4j.binding;


import org.sjf4j.RuntimeContext;

/**
 * JSON streaming binder.
 *
 * <p>Implementations provide JSON readers and writers and use the supplied
 * {@link RuntimeContext} for runtime binding settings.</p>
 */
public abstract class JsonBinder<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinder<R, W> {

    protected JsonBinder(RuntimeContext context) {
        super(context);
    }

}
