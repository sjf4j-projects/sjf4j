package org.sjf4j.facade;

import java.lang.reflect.Type;

/**
 * Facade for converting between arbitrary nodes and JSON-compatible trees.
 */
public interface NodeFacade {

    Object readNode(Object node, Type type);

    Object deepNode(Object node);

    Object writeNode(Object node);

}
