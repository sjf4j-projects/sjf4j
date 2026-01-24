package org.sjf4j.node;


public final class NodeKey {
    private final Object node;

    public NodeKey(Object node) {
        this.node = node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeKey)) return false;
        return Nodes.equals(this.node, ((NodeKey) o).node);
    }

    @Override
    public int hashCode() {
        return Nodes.hash(node);
    }
}
