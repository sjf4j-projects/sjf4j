package org.sjf4j.facade;

import java.lang.reflect.Type;

/**
 * Facade for converting between arbitrary nodes and JSON-compatible trees.
 */
public interface NodeFacade {

    /**
     * Converts a node to target type, with optional deep copy behavior.
     */
    Object readNode(Object node, Type type, boolean deepCopy);

    /**
     * Converts a node to target type without forcing deep copy.
     */
    default Object readNode(Object node, Type type) {
        return readNode(node, type, false);
    }

    /**
     * Deep-copies a node through the conversion pipeline.
     */
    default Object deepNode(Object node) {
        return readNode(node, Object.class, true);
    }

    Object writeNode(Object node);

}
