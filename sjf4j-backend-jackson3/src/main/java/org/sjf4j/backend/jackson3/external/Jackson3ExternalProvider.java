package org.sjf4j.backend.jackson3.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalProvider;

/** Service provider for Jackson 3 nodes when Jackson is available. */
public final class Jackson3ExternalProvider implements ExternalProvider {
    @Override
    public ExternalNode<?> externalNode() {
        try {
            Class.forName("tools.jackson.databind.JsonNode", false, Jackson3ExternalProvider.class.getClassLoader());
            return new Jackson3Node();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
