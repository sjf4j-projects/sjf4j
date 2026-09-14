package org.sjf4j.binding;


/**
 * JSON facade interface with streaming support and runtime mode dispatch.
 *
 * <p>Implementations expose a concrete {@link org.sjf4j.facade.StreamingContext.StreamingMode} and can override
 * the plugin-module and exclusive-IO hooks when they provide backend-native
 * read/write paths. The default methods in this interface route reads and writes
 * through the appropriate path and normalize common exception handling.</p>
 */
public interface JsonBinding<R extends StreamingReader, W extends StreamingWriter> extends StreamingBinding<R, W> {

}
