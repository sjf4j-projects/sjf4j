package org.sjf4j.mapper;

/**
 * Incubating runtime structural mapper between two object-graph node types.
 *
 * <p>A mapper usually starts from default deep conversion and then applies
 * path-based copy/value/compute overrides declared by {@link ObjectMapperBuilder}.
 *
 * @deprecated Use {@link org.sjf4j.annotation.mapping.CompiledMapper} instead.
 */
public interface ObjectMapper<S, T> {

    /**
     * Returns the declared mapper source type.
     */
    Class<S> sourceType();

    /**
     * Returns the declared mapper target type.
     */
    Class<T> targetType();

    /**
     * Converts the source object graph into a target object graph.
     */
    T map(S source);


    /**
     * Creates a new builder for the given source and target types.
     */
    static <S, T> ObjectMapperBuilder<S, T> builder(Class<S> sourceType, Class<T> targetType) {
        return new ObjectMapperBuilder<>(sourceType, targetType);
    }

}
