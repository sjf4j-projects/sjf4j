package org.sjf4j.node;


import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures generic type information at runtime (type token).
 */
public abstract class TypeReference<T> {
    /**
     * The captured generic type.
     */
    private final Type type;

    /**
     * Constructs a TypeReference and captures the generic type information.
     *
     * @throws IllegalArgumentException if the type parameter is missing
     */
    protected TypeReference() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("Missing type parameter.");
        }
    }

    /**
     * Returns the captured generic type.
     *
     * @return the captured generic type
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns a string representation of the captured type.
     *
     * @return a string representation of the captured type
     */
    @Override
    public String toString() {
        return type.toString();
    }

}
