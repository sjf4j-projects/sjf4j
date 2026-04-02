package org.sjf4j.mapper;

/**
 * Runtime structural mapper between two object-graph node types.
 *
 * <p>A mapper usually starts from default deep conversion and then applies
 * path-based copy/value/compute overrides declared by {@link NodeMapperBuilder}.
 */
public interface NodeMapper<S, T> {

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
    default NodeMapperBuilder<S, T> builder(Class<S> sourceType, Class<T> targetType) {
        return new NodeMapperBuilder<>(sourceType, targetType);
    }

}
