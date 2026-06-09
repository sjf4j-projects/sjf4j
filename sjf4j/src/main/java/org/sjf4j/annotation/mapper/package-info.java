/**
 * Compile-time structural mapper annotations.
 *
 * <p>Annotate an interface with {@link org.sjf4j.annotation.mapper.CompiledMapper}
 * to generate direct MapStruct-like mapper implementations. The generated code
 * maps known Java and SJF4J node-facing structures without runtime facade
 * binding or reflection.</p>
 *
 * <p>{@link org.sjf4j.annotation.mapper.Mapping} customizes normal target
 * property writes and strict target-path writes. {@link
 * org.sjf4j.annotation.mapper.MappingIfParentPresent} and {@link
 * org.sjf4j.annotation.mapper.EnsureMapping} provide target-path APIs for
 * skip-if-parent-missing and ensure-parent writes.</p>
 *
 * <p>{@link org.sjf4j.annotation.mapper.CompiledMapper} also supports strict
 * value conversion, recursive parameterized collection/map element and value
 * mapping, declared List/Set plus raw List/Set treated as
 * List&lt;Object&gt;/Set&lt;Object&gt;, JsonArray/JAJO/Java-array, and declared-Object
 * runtime-List sources to collection targets, object-like
 * Map/JsonObject sources to POJO or record targets, root `Map<String,V>`
 * projections from POJO/JsonObject/JOJO sources with typed value conversion,
 * shallow plain JsonObject root projections, shallow plain JsonArray and JAJO
 * root create targets, JOJO root create targets with typed declared
 * properties plus shallow dynamic extras, and typed Java array root create
 * targets. Declared Object root projection to Map/JsonObject/JOJO accepts only
 * runtime Map values; declared Object array-like projection accepts only
 * runtime List values; raw List/Set source is treated as
 * List&lt;Object&gt;/Set&lt;Object&gt;; other declared Collection source types are
 * rejected for array-like mapping; raw List/Set targets and other raw
 * collection/map targets are rejected. It deliberately does not provide full
 * NodeFacade.readNode/writeNode semantics: runtime converters, private
 * binding, deep OBNT materialization, JOJO update targets, JAJO and Java array
 * update targets, generic mapper interfaces or methods, and map key conversion
 * are not supported. Limited type-level {@code @OneOf} dispatch is supported
 * only for create mappings with {@code scope=CURRENT} and empty {@code path}:
 * discriminator-key dispatch uses {@code key}, while shape-based dispatch uses
 * distinct subtype raw JSON types. Field/parameter-local {@code @OneOf},
 * discriminator paths, non-current scopes, and root {@code @OneOf} update
 * targets are unsupported.</p>
 *
 * <p>Automatic converter selection first keeps directly assignable values as-is,
 * then may use preferred method references declared by {@link
 * org.sjf4j.annotation.mapper.MapperOptions#using()}, local mapper methods,
 * imported {@code @CompiledMapper} interfaces declared through {@link
 * org.sjf4j.annotation.mapper.CompiledMapper#importing()}, generated structural
 * object helpers, or built-in strict conversions. {@link
 * org.sjf4j.annotation.mapper.MappingCreator} customizes target instantiation;
 * interface-level creators are inherited from parent mapper interfaces across
 * compilation units, and method-level creators override matching interface-level
 * creators for the declaring mapper method.</p>
 */
package org.sjf4j.annotation.mapper;
