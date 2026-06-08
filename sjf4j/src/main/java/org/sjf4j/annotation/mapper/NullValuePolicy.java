package org.sjf4j.annotation.mapper;

/** Null assignment behavior for generated mapper assignments. */
public enum NullValuePolicy {
    /** Assign null source values to the target property or constructor argument. */
    SET_TO_NULL,

    /**
     * Skip null source values, preserving target defaults or existing values.
     *
     * <p>This policy applies to mutable create targets and update targets. It is
     * not supported for constructor or record create targets.</p>
     */
    IGNORE
}
