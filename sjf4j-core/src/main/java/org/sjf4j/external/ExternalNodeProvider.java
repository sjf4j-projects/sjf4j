package org.sjf4j.external;

/**
 * Service provider for an {@link ExternalNode} classifier.
 *
 * <p>Providers are discovered once by {@link ExternalNodeRegistry} through
 * {@link java.util.ServiceLoader}. A provider may return {@code null} when its
 * optional native node model is not present at runtime.</p>
 *
 * <p>To support compile-only native models, the provider class itself must be
 * loadable and instantiable without the native jar: its constructor, static
 * initialization, fields, and public method signatures must not link native
 * model types. {@link #externalNode()} must first detect availability without
 * linking a native adapter, then create the adapter only when the model is
 * available; otherwise it returns {@code null}. Provider loading,
 * configuration, and implementation failures are not treated as absence and
 * fail fast.</p>
 */
public interface ExternalNodeProvider {
    /**
     * Returns this provider's classifier, or {@code null} when its optional
     * native node model is unavailable.
     */
    ExternalNode<?> externalNode();
}
