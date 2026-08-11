package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for compile-time generation of direct JDBC {@link java.sql.ResultSet} mappers.
 * This is separate from {@link CompiledMapper}: JDBC mappers read columns from a result set directly
 * rather than mapping a source node or object graph. Each abstract mapper method accepts exactly one
 * {@code ResultSet} and returns a supported declared target, {@code Map<String, Object>}, or a
 * {@code List} of either. {@link MappingCreator} may select a concrete implementation
 * or a default/static mapper factory for interface and abstract declared results.
 *
 * <p>The supplied result set must initially be positioned before its first row. Generated methods
 * advance its cursor but never close it; the caller retains ownership of the result set, statement,
 * and connection. A single-target method returns {@code null} when there is no row. By default it
 * throws {@link org.sjf4j.exception.BindingException} after reading a second row; use
 * {@link MapperOptions}{@code (jdbcResult = JdbcResultPolicy.FIRST)} to return the first row
 * without checking for another. A {@code List<T>} method consumes all remaining rows and returns an
 * empty list when there are none.</p>
 *
 * <p>POJO properties use matching column labels by default; {@link Mapping#source()} supplies a
 * column-label alias. JSONPath/JSON Pointer property-name target paths such as {@code $.profile.name}
 * and {@code /profile/name} are also supported for mutable POJOs when every intermediate parent already
 * exists; they are not allocated.
 * SQL exceptions are wrapped in {@code BindingException}. Version 1 supports
 * direct flat mapping to POJOs, records, single-constructor targets, creator-selected implementations, and
 * {@code Map<String, Object>}; it does not support updates, nested JDBC source paths, nested mapping, or arbitrary
 * collection targets.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompiledJdbcMapper {
}
