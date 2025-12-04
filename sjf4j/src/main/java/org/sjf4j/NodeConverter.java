package org.sjf4j;

/**
 * Interface for converting between wrapped Java objects and pure JSON nodes.
 * <p>
 * Implementations of this interface handle the conversion between custom Java types (wrapped) and
 * standard JSON types (pure), allowing for seamless integration between Java objects and JSON structures.
 * <p>
 * <b>Note:</b> This interface has been deprecated according to the original comment.
 *
 * @param <W> the type of the wrapped Java object
 * @param <P> the type of the pure JSON node
 * @deprecated This interface has been marked as deprecated.
 */
@Deprecated
public interface NodeConverter<W, P> {

    /**
     * Gets the wrapped Java object type that this converter handles.
     *
     * @return the wrapped Java object type
     */
    Class<W> getWrapType();

    /**
     * Gets the pure JSON node type that this converter handles.
     *
     * @return the pure JSON node type
     */
    Class<P> getPureType();

    /**
     * Converts a wrapped Java object to a pure JSON node.
     *
     * @param wrap the wrapped Java object to convert
     * @return the converted pure JSON node
     */
    P wrap2Pure(W wrap);

    /**
     * Converts a pure JSON node to a wrapped Java object.
     *
     * @param pure the pure JSON node to convert
     * @return the converted wrapped Java object
     */
    W pure2Wrap(P pure);

}