package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for compile-time mapper implementation generation.
 *
 * <p>Each non-void abstract method describes a mapping from one or more declared
 * source types to one declared target type. The generated method returns
 * {@code null} when all source arguments are {@code null}; otherwise it creates
 * the target and maps properties directly. Void methods update the first
 * argument in place from the remaining source arguments.</p>
 *
 * <p>By default, source and target properties with the same name are mapped
 * automatically. Use repeatable {@link Mapping} annotations to rename a source
 * property, ignore a target property, compute a value from one or more source
 * properties, or write to an SJF4J target path. Use {@link MapperOptions} for
 * null handling, method-level converter preferences, and collection/map update
 * policies. Use repeatable {@link MappingCreator} annotations to customize
 * target instantiation, especially for interface or abstract targets.</p>
 *
 * <p>{@code CompiledMapper} is a MapStruct-like compiled structural mapper. It
 * is not a compile-time replacement for {@code NodeFacade.readNode}; runtime
 * converters, private binding, and facade context are outside its scope.
 * Limited type-level {@code @OneOf} dispatch is supported for create mappings:
 * discriminator-key dispatch when the target type declares {@code key}, and
 * shape-based dispatch when it does not. Both modes require
 * {@code scope=CURRENT} and an empty {@code path}.</p>
 *
 * <p>Generated mappers include SJF4J strict value conversion for mapper leaves:
 * numeric narrowing/widening through SJF4J number checks, string/character/enum
 * conversion, boolean node checks, and {@code @NodeValue} codecs. Dynamic
 * {@code Object} leaves read from maps, JsonObject, or paths use strict
 * {@code Nodes.toXxx(...)} conversion. Lenient coercions such as arbitrary
 * string-to-number or boolean-to-string are intentionally not generated unless
 * supplied by an explicit mapper or compute expression.</p>
 *
 * <p>Collection and map mappings support recursive parameterized element/value
 * conversion. When a unique compatible mapper method or generated converter is
 * available, the processor can apply it through nested {@code List},
 * {@code Set}, and parameterized {@code Map} value shapes such as
 * {@code List<List<User>> -> List<List<UserDto>>} or {@code Map<String,
 * List<User>> -> Map<String, List<UserDto>>}. Declared parameterized
 * {@code List} and {@code Set}, raw {@code List} treated as
 * {@code List<Object>}, raw {@code Set} treated as {@code Set<Object>},
 * JsonArray/JAJO, Java array, and declared-{@code Object} runtime-{@code List}
 * sources may feed collection targets. Declared-{@code Object} still accepts
 * only runtime {@code List}, not runtime {@code Set}. Other declared
 * {@code Collection} source types are rejected unless the declaration itself
 * is {@code List} or {@code Set}. Raw or non-parameterized collection/map
 * target shapes are rejected.
 * Plain {@code JsonArray}, JAJO, and Java array root targets are described
 * below.</p>
 *
 * <p>Object-like sources ({@code Map} and {@code JsonObject}) can bind to
 * declared POJO, record, or constructor targets using public target properties
 * and constructor parameters. Root {@code Map<String,V>} create targets also
 * accept POJO/record readable properties and {@code JsonObject}/JOJO entries,
 * allocating a fresh target map and applying normal typed value conversion for
 * each first-level value. Plain {@code JsonObject} root create targets are
 * shallow projections: POJO/record readable values, {@code Map<String, ?>}
 * entries, or {@code JsonObject}/JOJO entries are copied only at the first
 * level, and child object/array/POJO values are shared as-is without deep
 * materialization or scalar conversion. When the declared root source type is
 * {@code Object}, projection to root {@code Map}, plain {@code JsonObject}, or
 * JOJO accepts only runtime {@code java.util.Map}; runtime POJO/
 * {@code JsonObject} dispatch is not generated. Plain {@code JsonArray} and
 * JAJO root create targets are likewise shallow one-level copies from Java
 * arrays, declared {@code List}/{@code Set}, raw {@code List}/{@code Set}
 * treated as {@code List<Object>}/{@code Set<Object>},
 * {@code JsonArray}/JAJO, or declared-{@code Object} runtime-{@code List}
 * sources. Elements are copied as-is with no scalar conversion, preferred
 * mapper use, or deep materialization.</p>

 * <p>JOJO root create targets are supported as typed declared properties plus
 * shallow dynamic extras. Declared JOJO properties are initialized using the
 * same no-args/record/unique-constructor creation rules as POJO targets. For
 * {@code Map<String, ?>}, {@code JsonObject}/JOJO, and runtime-{@code Map}
 * {@code Object} sources, first-level source entries that do not match a
 * declared JOJO property are copied into the target via dynamic
 * {@code JsonObject.put(key, value)}. JOJO update methods are still not
 * generated.</p>
 *
 * <p>Java array and collection root create targets allocate fresh typed
 * containers and apply the same typed element conversion rules used by
 * collection mappings, including strict scalar conversion, enum conversion,
 * {@code @NodeValue} codecs, method-level preferred or unique mapper methods,
 * and object-like to POJO conversion. This differs from plain
 * {@code JsonArray} and JAJO targets, which keep copied elements as-is. When
 * the declared root
 * source type is {@code Object}, array-like projections accept only runtime
 * {@code java.util.List} values. Raw root {@code List} and {@code Set}
 * sources are treated as {@code List<Object>} and {@code Set<Object>}.
 * Other declared {@code Collection} source types are not accepted for
 * array-like create/projection methods, and raw {@code List}/{@code Set}
 * targets are rejected because no element type is available.</p>
 *
 * <p>For update methods, method-level collection/map policies default to
 * {@link ArrayPolicy#CLEAR_ADD} for array-like containers and
 * {@link ObjectPolicy#PUT} for map-like containers, and apply to mapped
 * container properties and nested value containers. {@link NullValuePolicy} is
 * applied before container policy update: {@code IGNORE} keeps the existing
 * target property, while {@code SET} writes {@code null} to the target property
 * when the source property is {@code null}. Root container update methods such
 * as {@code void update(List<T> target, List<S> source)} still return
 * immediately when the source root is {@code null}, because the target
 * parameter itself cannot be reassigned.</p>
 *
 * <p>Current limits: raw or non-parameterized collection/map target types are
 * not supported for recursive conversion, except raw source {@code List} and
 * raw source {@code Set} which are treated as {@code List<Object>} and
 * {@code Set<Object>}; map key conversion is not supported and key types must
 * already match; POJO/{@code JsonObject}/{@code Object}
 * projection to root {@code Map} requires target key type
 * {@code java.lang.String}; plain {@code JsonObject} and JOJO root projection
 * from {@code Map} likewise requires source key type {@code String}; Java
 * array and JAJO targets are create-only and update methods are not generated;
 * mapper interfaces and mapper methods must not themselves declare type
 * parameters.</p>
 *
 * <p>Supported source reads are public fields, JavaBean getters, boolean
 * {@code isXxx} getters, and record accessors. Supported target writes are
 * public setters and writable public fields. Targets may be no-args classes,
 * records, classes with a unique public constructor, or types resolved through
 * {@link MappingCreator}.</p>
 *
 * <pre>{@code
 * @CompiledMapper
 * interface Users {
 *     @Mapping(target = "surname", source = "lastName")
 *     UserDto toDto(User user);
 * }
 * }</pre>
 *
 * <p>Imported mapper interfaces may be declared through {@link #importing()} so
 * generated implementations can call other compiled mappers directly without
 * runtime lookup or reflection.</p>
 *
 * <p>This annotation is retained in class files so annotation processors can
 * recognize imported compiled mappers across compilation units and modules. It
 * is still primarily a processor-consumed annotation.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompiledMapper {
    /**
     * Other {@code @CompiledMapper} interfaces that this mapper may use.
     *
     * <p>Only compiled mapper interfaces are supported here. Generated
     * implementations instantiate imported mapper implementations directly and
     * call compatible mapper methods without runtime lookup. Ordinary utility or
     * converter classes are intentionally not supported.</p>
     */
    Class<?>[] importing() default {};
}
