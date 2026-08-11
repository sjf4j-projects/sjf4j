package org.sjf4j.annotation.mapper;

/** Policy for a single-target {@link CompiledJdbcMapper} method. */
public enum JdbcResultPolicy {
    /**
     * Map and return the first row. The cursor advances once when a row is present and no second-row
     * check is made.
     */
    FIRST,
    /**
     * Map the first row, then advance once more to verify there is no second row. If one exists,
     * throw {@link org.sjf4j.exception.BindingException}; this is the default policy.
     */
    FAIL_ON_MULTIPLE
}
