package org.sjf4j.node;


import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Abstract base class for capturing generic type information at runtime. This
 * class allows for preserving type parameters that would otherwise be erased
 * during Java's type erasure process.
 *
 * <p>Usage example:
 * <pre>{@code
 * Type listType = new TypeReference<List<String>>() {}.getType();
 * }</pre>
 *
 * @param <T> the generic type to capture
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