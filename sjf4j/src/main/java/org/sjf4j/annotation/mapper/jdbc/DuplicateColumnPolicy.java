package org.sjf4j.annotation.mapper.jdbc;

/** Controls handling of duplicate columns when mapping a JDBC row to a map. */
public enum DuplicateColumnPolicy {
    /** Reject duplicate columns rather than silently discarding a value. */
    FAIL,
    /** Keep the value from the last duplicate column. */
    LAST_WINS
}
