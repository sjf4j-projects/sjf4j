package org.sjf4j.node.external;

import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalProvider;

public final class TestExternalChildNodeProvider implements ExternalProvider {
    static final ExternalNode<?> ADAPTER = new ExternalRegistryTest.TestExternalChildNodeAdapter();

    @Override
    public ExternalNode<?> externalNode() {
        return ADAPTER;
    }
}
