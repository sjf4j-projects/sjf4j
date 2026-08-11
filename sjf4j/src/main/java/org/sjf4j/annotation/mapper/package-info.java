/**
 * Compile-time mapper annotations.
 *
 * <h2>Object mapping</h2>
 * <p>{@link org.sjf4j.annotation.mapper.CompiledMapper} generates direct,
 * MapStruct-like implementations for declared Java objects and SJF4J
 * structures. It supports typed POJO, record, map, collection, array, and
 * JSON-facing mappings without runtime reflection or facade binding.</p>
 *
 * <h2>Customization</h2>
 * <ul>
 *     <li>{@link org.sjf4j.annotation.mapper.Mapping} renames, ignores, computes,
 *     or writes target properties and paths.</li>
 *     <li>{@link org.sjf4j.annotation.mapper.MappingCreator} selects a concrete
 *     implementation or mapper factory for an abstract/interface target.</li>
 *     <li>{@link org.sjf4j.annotation.mapper.MapperOptions} configures conversion,
 *     update, and null policies.</li>
 * </ul>
 */
package org.sjf4j.annotation.mapper;
