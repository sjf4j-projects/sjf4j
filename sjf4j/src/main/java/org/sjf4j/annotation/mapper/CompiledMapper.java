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
 * update null handling and collection/map update policies.</p>
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
