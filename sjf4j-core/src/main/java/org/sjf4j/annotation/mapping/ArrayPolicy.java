package org.sjf4j.annotation.mapping;

/** Controls update behavior for array-like targets such as lists, sets, and collections. */
public enum ArrayPolicy {
    /** Overwrite existing indexed elements and append remaining mapped source elements. */
    SET,
    /** Append mapped source elements to the existing target container. */
    ADD
}
