package org.sjf4j.node.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalProvider;

public final class TestExternalNodeProvider implements ExternalProvider {
    static final ExternalNode<?> ADAPTER = new ExternalRegistryTest.TestExternalAdapter();

    @Override
    public ExternalNode<?> externalNode() {
        return ADAPTER;
    }
}
