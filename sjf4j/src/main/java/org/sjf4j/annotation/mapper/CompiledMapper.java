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
 * the target and copies assignable properties directly. Void methods update the
 * first argument in place from the remaining source arguments.</p>
 *
 * <p>By default, source and target properties with the same name are mapped
 * automatically. Use repeatable {@link Mapping} annotations to rename a source
 * property, ignore a target property, compute a value from one or more source
 * properties, or write to an SJF4J target path. Use {@link MapperOptions} for
 * null handling and collection/map update policies.</p>
 *
 * <p>Collection and map mappings support recursive parameterized element/value
 * conversion. When a unique compatible mapper method is available, the
 * processor can apply it through nested {@code List}, {@code Set}, and
 * parameterized {@code Map} value shapes such as {@code List<List<User>> ->
 * List<List<UserDto>>} or {@code Map<String, List<User>> -> Map<String,
 * List<UserDto>>}. For update methods, method-level collection/map policies
 * default to {@link ArrayPolicy#CLEAR_ADD} for array-like containers and
 * {@link ObjectPolicy#PUT} for map-like containers, and apply to mapped
 * container properties and nested value containers. {@link NullValuePolicy} is
 * applied before container policy update: {@code IGNORE} keeps the existing
 * target property, while {@code SET} writes {@code null} to the target property
 * when the source property is {@code null}. Root container update methods such
 * as {@code void update(List<T> target, List<S> source)} still return
 * immediately when the source root is {@code null}, because the target
 * parameter itself cannot be reassigned.</p>
 *
 * <p>Current limits: raw or non-parameterized collection/map types are not
 * supported for recursive conversion; map key conversion is not supported and
 * key types must already match; mapper interfaces and mapper methods must not
 * themselves declare type parameters.</p>
 *
 * <p>Supported source reads are public fields, JavaBean getters, boolean
 * {@code isXxx} getters, and record accessors. Supported target writes are
 * public setters and writable public fields. Targets may be no-args classes,
 * records, or classes with a unique public constructor.</p>
 *
 * <pre>{@code
 * @CompiledMapper
 * interface Users {
 *     @Mapping(target = "surname", source = "lastName")
 *     UserDto toDto(User user);
 * }
 * }</pre>
 *
 * <p>This annotation is retained only in source because it is consumed by the
 * annotation processor.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface CompiledMapper {
}
