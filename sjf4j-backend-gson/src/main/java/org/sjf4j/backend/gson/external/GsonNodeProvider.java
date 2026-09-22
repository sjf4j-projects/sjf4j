package org.sjf4j.backend.gson.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeProvider;

/** Service provider for Gson nodes when Gson is available. */
public final class GsonNodeProvider implements ExternalNodeProvider {
    @Override
    public ExternalNode<?> externalNode() {
        try {
            Class.forName("com.google.gson.JsonElement", false, GsonNodeProvider.class.getClassLoader());
            return new GsonNode();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
