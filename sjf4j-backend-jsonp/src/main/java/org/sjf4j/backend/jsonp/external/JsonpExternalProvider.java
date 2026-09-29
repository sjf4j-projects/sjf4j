package org.sjf4j.backend.jsonp.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalProvider;

/** Service provider for JSON-P nodes when JSON-P is available. */
public final class JsonpExternalProvider implements ExternalProvider {
    @Override
    public ExternalNode<?> externalNode() {
        try {
            Class.forName("jakarta.json.JsonValue", false, JsonpExternalProvider.class.getClassLoader());
            return new JsonpNode();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
