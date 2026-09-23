package org.sjf4j.facade;

import java.lang.reflect.Type;

/**
 * Facade for binding OBNT values and producing raw OBNT representations.
 */
public interface NodeFacade {

    /**
     * Binds an OBNT value to {@code type}.
     * <p>
     * When {@code deepCopy} is true, this requests the facade's deep conversion
     * mode; it does not require a recursive copy of every representation. The
     * facade defines supported representations, conversion order, and its copy
     * boundary. Results, including converter results, can retain references.
     */
    Object readNode(Object node, Type type, boolean deepCopy);

    /**
     * Binds an OBNT value without requesting recursive copying.
     */
    default Object readNode(Object node, Type type) {
        return readNode(node, type, false);
    }

    /**
     * Requests this facade's deep conversion mode through its conversion pipeline.
     * The framework-default facade recursively processes recognized built-in
     * containers and POJO representations, but converter results, unrecognized
     * values, and already-instantiated {@code @NodeValue} domain values can be
     * returned by reference. Custom facades define their own copy behavior.
     */
    default Object deepNode(Object node) {
        return readNode(node, Object.class, true);
    }

    /**
     * Encodes a runtime value as this facade's raw OBNT representation.
     */
    Object writeNode(Object node);

}
