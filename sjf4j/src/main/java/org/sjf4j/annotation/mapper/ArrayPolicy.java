package org.sjf4j.annotation.mapper;

/** Controls update behavior for OBNT array-like targets such as lists, sets, and collections. */
public enum ArrayPolicy {
    /** Replace the target field value with a newly mapped container. */
    SET,
    /** Clear the existing target container and append mapped source elements. */
    CLEAR_ADD,
    /** Append mapped source elements to the existing target container. */
    ADD
}
