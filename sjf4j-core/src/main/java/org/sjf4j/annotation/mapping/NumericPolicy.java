package org.sjf4j.annotation.mapping;

/**
 * Controls whether generated numeric conversions may lose precision.
 * Both policies retain SJF4J's existing range checks.
 */
public enum NumericPolicy {

    /**
     * Permit truncating fractional parts or losing representable precision,
     * but reject conversions outside the supported target range.
     */
    ALLOW_PRECISION_LOSS,

    /**
     * Reject conversions that change the exact numeric value, in addition
     * to rejecting out-of-range values.
     */
    EXACT
}
