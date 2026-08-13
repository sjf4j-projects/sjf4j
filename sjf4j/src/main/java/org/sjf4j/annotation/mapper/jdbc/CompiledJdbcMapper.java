package org.sjf4j.annotation.mapper.jdbc;

import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingCreator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for compile-time generation of direct JDBC {@link java.sql.ResultSet} mappers.
 * This is separate from {@link org.sjf4j.annotation.mapper.CompiledMapper}: JDBC mappers read columns from a result set directly
 * rather than mapping a source node or object graph. Abstract methods accept either {@code ResultSet}
 * or {@code ResultSet, int}. They return a supported declared target, {@code Map<String, Object>}, or,
 * for the one-argument form only, a {@code List} of either. {@link MappingCreator} may select a concrete implementation
 * or a default/static mapper factory for interface and abstract declared results.
 *
 * <p>One-argument methods require a result set positioned before its first row. Generated methods
 * advance its cursor but never close it; the caller retains ownership of the result set, statement,
 * and connection. A single-target method returns {@code null} when there is no row. By default it
 * throws {@link org.sjf4j.exception.BindingException} after reading a second row; use
 * {@link JdbcMapperOptions}{@code (singleResult = SingleResultPolicy.FIRST)} to return the first row
 * without checking for another. A {@code List<T>} method consumes all remaining rows and returns an
 * empty list when there are none. Two-argument methods map the current row: the caller must already
 * position the result set on a row, and generated code neither advances the cursor nor checks another row.
 * The {@code int} row number is ignored during mapping and current-row methods do not support {@code List} results.</p>
 *
 * <p>{@link JdbcMapperOptions#columnProjection()} defaults to requiring every POJO column; {@code PRESENT_ONLY}
 * leaves absent mutable properties at their initialized value. For map results, duplicate result
 * columns overwrite the earlier value with the last value.</p>
 *
 * <p>POJO properties use matching result columns by default; {@link Mapping#source()} supplies a
 * result column alias. JSONPath/JSON Pointer property-name target paths such as {@code $.profile.name}
 * and {@code /profile/name} are also supported for mutable POJOs when every intermediate parent already
 * exists; they are not allocated. JDBC mapper SQL exceptions are wrapped in {@code BindingException}.
 * Version 1 supports
 * direct flat mapping to POJOs, records, single-constructor targets, creator-selected implementations, and
 * {@code Map<String, Object>}; it does not support updates, nested JDBC source paths, nested mapping, or arbitrary
 * collection targets.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompiledJdbcMapper {
}
