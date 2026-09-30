package org.sjf4j.binding;

/**
 * Prepared property-name metadata.
 *
 * <p>{@link #match(String)} returns a non-negative property index or
 * {@link #UNKNOWN_FIELD}. This sentinel is deliberately distinct from reader
 * traversal sentinels.</p>
 */
public interface NameMatcher {

    /** The current object field is not represented by this matcher. */
    int UNKNOWN_FIELD = -2;

    /** Returns the canonical property name for an index. */
    String name(int index);

    /** Returns a property index or {@link #UNKNOWN_FIELD}. */
    int match(String name);
}
