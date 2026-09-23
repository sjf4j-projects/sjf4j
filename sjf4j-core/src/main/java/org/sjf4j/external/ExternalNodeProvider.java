package org.sjf4j.external;

/**
 * Service provider for an {@link ExternalNode} adapter.
 *
 * <p>Providers are discovered once by {@link ExternalNodeRegistry} through
 * {@link java.util.ServiceLoader}. A provider may return {@code null} when its
 * optional external Java model is not present at runtime.</p>
 *
 * <p>To support compile-only external models, the provider class itself must be
 * loadable and instantiable without the external library: its constructor, static
 * initialization, fields, and public method signatures must not link external
 * model types. {@link #externalNode()} must first detect availability without
 * linking an adapter, then create the adapter only when the model is
 * available; otherwise it returns {@code null}. Provider loading,
 * configuration, and implementation failures are not treated as absence and
 * fail fast.</p>
 */
public interface ExternalNodeProvider {
    /**
     * Returns this provider's adapter, or {@code null} when its optional
     * external Java model is unavailable.
     */
    ExternalNode<?> externalNode();
}
