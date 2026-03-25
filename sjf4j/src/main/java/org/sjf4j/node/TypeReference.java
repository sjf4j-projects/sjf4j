package org.sjf4j.node;

import org.sjf4j.exception.JsonException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures generic target type information for SJF4J reads and conversions.
 * <p>
 * Use this when the target type contains generics that cannot be expressed with
 * a plain {@link Class}, for example {@code List<User>}, {@code Map<String,
 * Integer>}, or nested JOJO/POJO container types.
 *
 * <p>Typical usage:
 * <pre>{@code
 * List<User> users = Sjf4j.fromJson(json, new TypeReference<List<User>>() {});
 * }</pre>
 */
public abstract class TypeReference<T> {
    /**
     * The captured generic type.
     */
    private final Type type;

    /**
     * Captures the generic type argument from the anonymous subclass declaration.
     */
    protected TypeReference() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
        } else {
            throw new JsonException("Invalid TypeReference declaration: missing type parameter");
        }
    }

    /**
     * Returns the captured target type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the captured type as string.
     */
    @Override
    public String toString() {
        return type.toString();
    }

}
