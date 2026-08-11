/**
 * Compile-time mapper annotations.
 *
 * <h2>Object mapping</h2>
 * <p>{@link org.sjf4j.annotation.mapper.CompiledMapper} generates direct,
 * MapStruct-like implementations for declared Java objects and SJF4J
 * structures. It supports typed POJO, record, map, collection, array, and
 * JSON-facing mappings without runtime reflection or facade binding.</p>
 *
 * <h2>JDBC mapping</h2>
 * <p>{@link org.sjf4j.annotation.mapper.CompiledJdbcMapper} generates direct,
 * cursor-consuming {@link java.sql.ResultSet} mappers for single targets,
 * {@code List} results, and {@code Map<String,Object>} rows. JDBC input stays
 * flat: column labels map to POJO or record properties, with aliases and
 * JSONPath/JSON Pointer target paths for existing nested parents.</p>
 *
 * <h2>Customization</h2>
 * <ul>
 *     <li>{@link org.sjf4j.annotation.mapper.Mapping} renames, ignores, computes,
 *     or writes target properties and paths.</li>
 *     <li>{@link org.sjf4j.annotation.mapper.MappingCreator} selects a concrete
 *     implementation or mapper factory for an abstract/interface target.</li>
 *     <li>{@link org.sjf4j.annotation.mapper.MapperOptions} configures conversion,
 *     update, null, and JDBC result-cardinality policies where applicable.</li>
 * </ul>
 *
 * <p>Generated mappers deliberately do not replace full
 * {@code NodeFacade.readNode/writeNode} behavior. In particular, JDBC mappers
 * do not traverse nested source paths or materialize an intermediate JSON node.</p>
 */
package org.sjf4j.annotation.mapper;
