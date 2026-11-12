package org.sjf4j.mapping;

import java.sql.ResultSet;

/**
 * Maps an already-positioned JDBC row using a prepared column layout.
 *
 * <p>Obtain an implementation from an {@code @CompiledJdbcMapper} method
 * accepting {@link java.sql.ResultSetMetaData}. The mapper captures column
 * indexes and labels but never owns or advances the {@link ResultSet}.
 * It can be reused for results with the same column layout.</p>
 *
 * @param <T> target row type
 */
@FunctionalInterface
public interface JdbcRowMapper<T> {

    /**
     * Maps the current row without advancing or closing the ResultSet.
     * SQLExceptions are wrapped in {@link org.sjf4j.exception.BindingException}.
     */
    T map(ResultSet rs);
}
