package org.sjf4j.facade;

/**
 * Factory contract for creating configured facade instances.
 */
@FunctionalInterface
public interface FacadeProvider<T> {
    T create(StreamingContext context);
}
