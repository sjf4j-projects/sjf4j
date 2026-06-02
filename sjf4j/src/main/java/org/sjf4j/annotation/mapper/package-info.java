/**
 * Compile-time object mapper annotations.
 *
 * <p>Annotate an interface with {@link org.sjf4j.annotation.mapper.CompiledMapper}
 * to generate direct one-source-to-one-target mapper implementations.</p>
 *
 * <p>{@link org.sjf4j.annotation.mapper.Mapping} is the strict target write
 * form. {@link org.sjf4j.annotation.mapper.MappingIfParentPresent} and
 * {@link org.sjf4j.annotation.mapper.EnsureMapping} provide target-path APIs
 * for skip-if-parent-missing and ensure-parent writes.</p>
 */
package org.sjf4j.annotation.mapper;
