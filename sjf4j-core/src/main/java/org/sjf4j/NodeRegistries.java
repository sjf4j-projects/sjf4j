package org.sjf4j;

import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.external.ExternalRegistry;
import org.sjf4j.value.ValueRegistry;

/** Explicit runtime registration for a {@link NodeRegistry} interface. */
public final class NodeRegistries {
    private NodeRegistries() {}

    /**
     * Registers both ValueCodec and ExternalNode declarations. Call during
     * initialization before affected types are analyzed.
     *
     * <p>Repeated registrations retain the existing duplicate registration
     * error behavior; this method does not maintain a holder cache.
     */
    public static void register(Class<?> registry) {
        ValueRegistry.registerAnnotated(registry);
        ExternalRegistry.registerAnnotated(registry);
    }
}
