package org.sjf4j.node;

import org.sjf4j.util.NodeUtil;


public final class NodeKey {
    private final Object node;

    public NodeKey(Object node) {
        this.node = node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeKey)) return false;
        return NodeUtil.equals(this.node, ((NodeKey) o).node);
    }

    @Override
    public int hashCode() {
        return NodeUtil.hashCode(node);
    }
}
