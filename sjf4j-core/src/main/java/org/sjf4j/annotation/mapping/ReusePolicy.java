package org.sjf4j.annotation.mapping;

/**
 * Controls when a mapper may reuse an existing source value.
 */
public enum ReusePolicy {

    /**
     * Reuse the source value whenever its declared type is assignable to the
     * target type and no runtime container-element conversion is required.
     * The original object reference is preserved, without copying or conversion.
     */
    ASSIGNABLE
}
