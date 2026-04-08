package org.sjf4j.node;

/**
 * Type-level POJO field access policy used by framework-owned binding paths.
 */
public enum AccessStrategy {
    /**
     * Public fields bind directly. Non-public fields bind through public bean accessors
     * unless explicit node metadata forces direct field binding.
     */
    BEAN_BASED,

    /**
     * Allows direct field binding for non-public fields in addition to bean-style access.
     * Native backend modules do not currently mirror this per-type policy, so SJF4J keeps
     * annotated types on the framework-owned read/write path.
     */
    FIELD_BASED,
}
