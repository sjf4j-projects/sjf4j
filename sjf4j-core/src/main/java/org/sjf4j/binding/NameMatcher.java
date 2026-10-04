package org.sjf4j.binding;


/**
 * Prepared member-name matcher.
 *
 * <p>A successful match returns a non-negative index. The canonical
 * member name for that index can be obtained through
 * {@link #name(int)}.</p>
 *
 * <p>Backend implementations may attach native matching metadata to an
 * implementation of this interface. This allows
 * {@link StreamingReaderV4#nextNameMatch(NameMatcher)} to match directly against the
 * underlying input without first materializing the member name as a
 * {@link String}.</p>
 */
public interface NameMatcher {

    /**
     * Indicates that a member name did not match any known name.
     */
    int UNKNOWN = -1;

    /**
     * Indicates that object traversal reached the object end.
     */
    int OBJECT_END = -2;

    /**
     * Returns the canonical member name associated with a matched
     * index.
     */
    String name(int index);

    /**
     * Matches an already materialized member name.
     *
     * <p>This method is also the generic fallback used by backends that
     * do not provide a native member-name matching fast path.</p>
     *
     * @param name member name
     * @return a non-negative member index, or {@link #UNKNOWN}
     */
    int match(String name);
}
