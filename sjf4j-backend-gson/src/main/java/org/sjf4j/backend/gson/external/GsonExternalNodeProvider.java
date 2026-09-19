package org.sjf4j.backend.gson.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeProvider;

/** Service provider for Gson nodes when Gson is available. */
public final class GsonExternalNodeProvider implements ExternalNodeProvider {
    @Override
    public ExternalNode<?> externalNode() {
        try {
            Class.forName("com.google.gson.JsonElement", false, GsonExternalNodeProvider.class.getClassLoader());
            return new GsonExternalNode();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
