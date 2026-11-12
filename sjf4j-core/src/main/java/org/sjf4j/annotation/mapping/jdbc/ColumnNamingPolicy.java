package org.sjf4j.annotation.mapping.jdbc;

/** Controls matching between JDBC column labels and Java property names. */
public enum ColumnNamingPolicy {
    /** Match column labels exactly, including case. */
    EXACT,
    /** Match exact labels first, then ignore case. */
    INSENSITIVE,
    /** Match exact, then case-insensitive, then camelCase-to-snake_case labels. */
    INSENSITIVE_SNAKE
}
