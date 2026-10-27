/**
 * Streaming format binding APIs.
 *
 * <p>{@link Binder} owns the format-specific reader and writer lifecycle,
 * while {@link StreamingParser} and {@link StreamingGenerator} expose streaming
 * tokens and output operations. {@link StreamingIO} contains the shared OBNT
 * and POJO binding logic used by streaming format implementations.
 * {@link PropertiesBinder} provides separate binding for flat Java properties.</p>
 *
 * <p>{@link BinderProvider} implementations are discovered through
 * {@link java.util.ServiceLoader}; {@link BinderFactory} selects the available
 * provider with the highest priority for each {@link Format}. Backend modules
 * implement these APIs to integrate JSON, YAML, or other streaming formats.</p>
 */
package org.sjf4j.binding;
