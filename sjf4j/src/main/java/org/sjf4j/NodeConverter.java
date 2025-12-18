package org.sjf4j;


/**
 * Converter for a custom NodeConvertible type.
 * Provides projection to/from Node-Tree and underlying JSON representation.
 */
public interface NodeConverter<T> {

    Object convert(T node);

    T unconvert(Object raw);

    default T copy(T node) {
        return node;
    }

}
