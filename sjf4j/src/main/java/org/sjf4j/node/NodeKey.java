package org.sjf4j.node;


/**
 * Key wrapper that uses {@link Nodes#equals(Object, Object)} semantics.
 */
public final class NodeKey {
    private final Object node;

    /**
     * Creates key wrapper for node-semantic map keys.
     */
    public NodeKey(Object node) {
        this.node = node;
    }

    /**
     * Compares wrapped nodes with node semantics.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeKey)) return false;
        return Nodes.equals(this.node, ((NodeKey) o).node);
    }

    /**
     * Computes hash using node semantics.
     */
    @Override
    public int hashCode() {
        return Nodes.hash(node);
    }
}
