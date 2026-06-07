package org.sjf4j.annotation.mapper;

/** Null assignment behavior for create and update mapper methods. */
public enum NullValuePolicy {
    /** Assign null source values to the target. */
    SET_TO_NULL,

    /** Skip null source values, preserving target defaults or existing values. */
    IGNORE
}
