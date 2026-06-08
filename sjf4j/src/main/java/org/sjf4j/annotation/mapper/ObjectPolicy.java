package org.sjf4j.annotation.mapper;

/** Controls update behavior for object-like targets such as maps. */
public enum ObjectPolicy {
    /** Clear the existing target object-like container and put mapped source entries. */
    CLEAR_PUT,
    /** Put mapped source entries into the existing target object-like container. */
    PUT,
    /** Put mapped source entries only when the target key is missing or currently maps to null. */
    PUT_IF_ABSENT
}
