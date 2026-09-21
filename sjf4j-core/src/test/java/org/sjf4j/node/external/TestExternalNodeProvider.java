package org.sjf4j.node.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeProvider;

public final class TestExternalNodeProvider implements ExternalNodeProvider {
    static final ExternalNode<?> ADAPTER = new ExternalNodeRegistryTest.TestExternalAdapter();

    @Override

    public ExternalNode<?> externalNode() {
        return ADAPTER;
    }
}
