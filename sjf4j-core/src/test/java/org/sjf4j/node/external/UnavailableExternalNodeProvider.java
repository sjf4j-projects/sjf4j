package org.sjf4j.node.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalProvider;

/** Simulates an optional integration whose native model is absent. */
public final class UnavailableExternalNodeProvider implements ExternalProvider {
    @Override
    public ExternalNode<?> externalNode() {
        return null;
    }
}
