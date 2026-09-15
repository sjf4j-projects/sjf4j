package org.sjf4j.facade;

/**
 * Exact source/target converter used during node read conversion.
 *
 * <p>Converters are matched by declared runtime source type and target type.
 * When a match is found, the converter takes precedence over the default POJO,
 * map, list, and array binding flow.
 */
public interface NodeConverter<S, T> {

    /**
     * Returns the exact runtime source type this converter accepts.
     */
    Class<S> sourceType();

    /**
     * Returns the exact target type this converter produces.
     */
    Class<T> targetType();

    /**
     * Converts the source value directly to the declared target type.
     */
    T convert(S source);
}
