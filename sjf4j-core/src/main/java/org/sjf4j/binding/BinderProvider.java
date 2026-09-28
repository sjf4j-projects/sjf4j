package org.sjf4j.binding;

import org.sjf4j.RuntimeContext;

import java.util.Objects;
import java.util.function.Function;

/**
 * Service provider for binders configured for one SJF4J runtime.
 *
 * <p>Providers are discovered through {@link java.util.ServiceLoader}. A
 * provider returns {@code false} from {@link #isAvailable()} only when its
 * optional backend is absent; configuration and implementation failures must
 * otherwise fail fast.</p>
 */
public interface BinderProvider {

    /**
     * Returns the format produced by this provider.
     */
    Format format();

    /**
     * Creates a binder using the supplied immutable runtime configuration.
     */
    Binder<?, ?> create(RuntimeContext context);

    /**
     * Returns whether this optional provider can create its backend binder.
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Selection priority used by {@link BinderFactory}; higher values win.
     */
    default int priority() {
        return 100;
    }

    /**
     * Returns whether {@code className} is visible to {@code loader}.
     *
     * <p>Only a missing class is treated as unavailable. Linkage and
     * configuration failures deliberately propagate.</p>
     */
    static boolean isClassAvailable(String className, ClassLoader loader) {
        Objects.requireNonNull(className, "className");
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Creates a provider with the supplied format, priority, and binder factory.
     */
    static BinderProvider of(Format format, int priority,
                             Function<RuntimeContext, ? extends Binder<?, ?>> factory) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(factory, "factory");
        return new BinderProvider() {
            @Override
            public Format format() {
                return format;
            }

            @Override
            public Binder<?, ?> create(RuntimeContext context) {
                return factory.apply(context);
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }

}
