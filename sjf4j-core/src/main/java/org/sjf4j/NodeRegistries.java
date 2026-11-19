package org.sjf4j;

import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.external.ExternalRegistry;
import org.sjf4j.value.ValueRegistry;

/** Explicit runtime registration for an annotated node registry interface. */
public final class NodeRegistries {
    private NodeRegistries() {}

    /**
     * Registers both ValueCodec and ExternalNode declarations from a
     * {@link NodeRegistry} interface. Invoke during application initialization
     * before any affected type metadata is analyzed.
     *
     * <p>This operation is idempotent for the same registry interface.
     */
    public static synchronized void register(Class<?> registry) {
        ValueRegistry.registerAnnotated(registry);
        ExternalRegistry.registerAnnotated(registry);
    }
}
