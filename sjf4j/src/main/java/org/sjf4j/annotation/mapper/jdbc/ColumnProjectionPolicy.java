package org.sjf4j.annotation.mapper.jdbc;

/** Controls whether JDBC POJO columns must all be present. */
public enum ColumnProjectionPolicy {
    /** Every mapped property column is required. */
    REQUIRE_ALL,
    /** For mutable targets, assign only properties whose result column is present. */
    PRESENT_ONLY
}
