package org.sjf4j.annotation.mapping.jdbc;

import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingCreator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for compile-time generation of direct JDBC {@link java.sql.ResultSet} mappers.
 * This is separate from {@link org.sjf4j.annotation.mapping.CompiledMapper}: JDBC mappers read
 * columns from a result set directly rather than mapping a Java object graph.
 * Abstract methods accept either a {@code ResultSet} for cursor-consuming mappings,
 * or a {@link java.sql.ResultSetMetaData} to prepare a reusable
 * {@link org.sjf4j.mapping.JdbcRowMapper JdbcRowMapper&lt;T&gt;}.
 * {@link MappingCreator} can select the concrete target implementation; mapping
 * rules may be placed on the interface or an individual method, with method rules taking precedence.
 *
 * <p>Cursor-consuming methods expect a ResultSet positioned before the first row. They
 * advance but never close it. A single-target method returns {@code null} for no rows;
 * by default it throws {@link org.sjf4j.exception.BindingException} on multiple rows.
 * {@link JdbcMappingOptions}{@code (singleResult = SingleResultPolicy.FIRST)}
 * returns only the first row. List methods consume the remaining rows.</p>
 *
 * <p>Prepared methods have signature
 * {@code JdbcRowMapper<T> prepare(ResultSetMetaData metadata)}. They resolve
 * column names once and return a stateless row mapper that captures only the
 * resulting column layout (indexes and labels). The caller manages ResultSet
 * cursor movement and invokes {@code map(ResultSet)} for each current row.
 * The prepare method name is arbitrary; only its signature determines its role.
 * Prepared mappers never hold the ResultSet or advance/close it, and are
 * reusable for results with the same column layout. The legacy
 * {@code (ResultSet, int)} signature is not supported.</p>
 *
 * <p>{@link JdbcMappingOptions#columnProjection()} defaults to requiring every POJO or JOJO declared property;
 * {@code PRESENT_ONLY} leaves absent mutable properties at their initialized value. For map results, duplicate result
 * columns overwrite the earlier value with the last value.</p>
 *
 * <p>Column names are matched using {@link JdbcMappingOptions#columnNaming()}, which defaults to
 * case-insensitive matching with camelCase-to-snake_case support. Explicit mappings have precedence.</p>
 *
 * <p>POJO properties use matching result columns by default; {@link Mapping#source()} supplies a flat
 * result column alias and target paths must declare one explicitly. JSONPath/JSON Pointer property-name
 * target paths such as {@code $.profile.name} and {@code /profile/name} are also supported for mutable
 * POJOs when every intermediate parent already exists; they are not allocated. JDBC mapper SQL exceptions
 * are wrapped in {@code BindingException}.
 * Generated JDBC mapping supports
 * direct flat mapping to POJOs, JOJOs, records, single-constructor targets, creator-selected implementations, and
 * {@code Map<String, Object>}. JOJO declared properties map as POJO properties; unconsumed result columns are retained
 * as raw JDBC values in its dynamic map (including last-column-wins duplicate labels), while columns consumed by aliases
 * are not copied dynamically. It does not support updates, nested JDBC source paths, nested mapping, or arbitrary
 * collection targets.</p>
 *
 * <p>The annotated interface cannot declare type parameters. Generic methods
 * inherited from a parent interface must resolve to concrete signatures.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompiledJdbcMapper {
}
