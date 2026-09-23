package org.sjf4j.annotation.mapping;

/** Controls update behavior for object-like targets such as maps. */
public enum ObjectPolicy {
    /** Put mapped source entries into the existing target object-like container. */
    PUT,
    /** Put mapped source entries only when the target key is missing or currently maps to null. */
    PUT_IF_ABSENT,
    /** Put mapped source entries only when the target key exists and currently maps to a non-null value. */
    PUT_IF_PRESENT
}
