package org.sjf4j.backend.jackson2.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalProvider;

/** Service provider for Jackson 2 nodes when Jackson is available. */
public final class Jackson2ExternalProvider implements ExternalProvider {
    @Override
    public ExternalNode<?> externalNode() {
        try {
            Class.forName("com.fasterxml.jackson.databind.JsonNode", false, Jackson2ExternalProvider.class.getClassLoader());
            return new Jackson2Node();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
