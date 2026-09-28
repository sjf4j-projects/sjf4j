/**
 * Streaming format binding APIs.
 *
 * <p>{@link Binder} owns the format-specific reader and writer lifecycle,
 * while {@link StreamingReader} and {@link StreamingWriter} expose streaming
 * tokens and output operations. {@link StreamingIO} contains the shared OBNT
 * and POJO binding logic used by every format implementation.</p>
 *
 * <p>{@link BinderProvider} implementations are discovered through
 * {@link java.util.ServiceLoader}; {@link BinderFactory} selects the available
 * provider with the highest priority for each {@link Format}. Backend modules
 * implement these APIs to integrate JSON, YAML, or other streaming formats.</p>
 */
package org.sjf4j.binding;
