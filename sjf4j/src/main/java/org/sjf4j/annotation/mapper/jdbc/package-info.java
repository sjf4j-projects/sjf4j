/**
 * Compile-time JDBC {@link java.sql.ResultSet} mapper annotations.
 *
 * <p>{@link org.sjf4j.annotation.mapper.jdbc.CompiledJdbcMapper} generates direct,
 * cursor-consuming mappers for single targets, {@code List} results, and
 * {@code Map<String, Object>} rows. POJO properties map from result columns;
 * {@link org.sjf4j.annotation.mapper.Mapping} supplies column aliases.</p>
 *
 * <p>{@link org.sjf4j.annotation.mapper.jdbc.JdbcMapperOptions} configures
 * single-result handling and column projection. Map rows preserve column-label
 * order and use last-column-wins behavior for duplicate labels. JDBC mappers
 * read columns directly without an intermediate JSON node; they do not support
 * nested JDBC source paths or arbitrary collection targets.</p>
 */
package org.sjf4j.annotation.mapper.jdbc;
