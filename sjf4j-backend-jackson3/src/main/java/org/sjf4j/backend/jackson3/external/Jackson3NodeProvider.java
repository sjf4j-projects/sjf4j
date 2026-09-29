package org.sjf4j.backend.jackson3.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeProvider;

/** Service provider for Jackson 3 nodes when Jackson is available. */
public final class Jackson3NodeProvider implements ExternalNodeProvider {
    @Override
    public ExternalNode<?> externalNode() {
        try {
            Class.forName("tools.jackson.databind.JsonNode", false, Jackson3NodeProvider.class.getClassLoader());
            return new Jackson3Node();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
