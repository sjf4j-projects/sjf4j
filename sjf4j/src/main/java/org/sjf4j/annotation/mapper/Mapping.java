package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customizes one target property or target path in a {@link CompiledMapper} method.
 *
 * <p>Without this annotation, mapper methods use same-name auto mapping for
 * writable target properties and constructor/record parameters. Add one or more
 * {@code @Mapping} annotations to override that default for selected targets.</p>
 *
 * <p>Supported forms:</p>
 * <ul>
 *     <li>{@code @Mapping(target = "to", source = "from")} maps a differently
 *     named source property.</li>
 *     <li>{@code @Mapping(target = "to", ignore = true)} skips a writable target
 *     property. Constructor and record targets cannot ignore required constructor
 *     arguments.</li>
 *     <li>{@code @Mapping(target = "to", sources = {...}, compute = "...")}
 *     computes a target value from one or more source properties or paths.</li>
 *     <li>For mapper methods with multiple source parameters, source names may
 *     be qualified with the parameter name and a colon: {@code "customer:name"}
 *     reads the {@code name} property from the {@code customer} parameter, and
 *     {@code "customer:$.profile.name"} or {@code "customer:/profile/name"}
 *     applies an SJF4J path to that parameter root.</li>
 * </ul>
 *
 * <p>This annotation does not choose mapper methods. Automatic converter
 * preferences are declared at method scope with {@link MapperOptions#using()}.
 * Forced value computation or normalization should be expressed with
 * {@link #compute()}.</p>
 *
 * <p>Compute expressions are intentionally simple source-code snippets consumed
 * by the annotation processor. They may be expression-bodied lambda-like
 * strings, for example {@code "(a, b) -> a + b"}, or {@code "this::helper"}
 * references to default or static methods declared on the mapper interface.
 * Blocks, statements, and {@code return} are not supported.</p>
 *
 * <p>With a single source parameter, unqualified source names retain existing
 * semantics, including dotted map-key compatibility. With multiple source
 * parameters, unqualified names must resolve to a unique readable source
 * property, otherwise the parameter-qualified form is required.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@Repeatable(Mappings.class)
public @interface Mapping {
    /**
     * Target property name or path to customize.
     *
     * <p>Required for normal, ignored, and computed mappings. An all-default
     * annotation is treated as a no-op marker. Target paths use strict
     * PutByPath-like semantics: the root target exists, but create mapping does
     * not create missing nested parents except the root. Intermediate parents
     * must already exist. {@link MappingIfParentPresent} and
     * {@link EnsureMapping} expose skip-if-missing-parent and
     * ensure-parent variants.</p>
     *
     * <p>Target values support the same three forms as source paths: plain
     * property/key name, JSONPath beginning with {@code $}, or JSON Pointer
     * beginning with {@code /}. Plain dotted names are literal property/key
     * names, not nested target paths.</p>
     */
    String target() default "";

    /**
     * Source property name or source path for a simple property copy.
     *
     * <p>When omitted, the target name is used as the source name. For computed
     * mappings use {@link #sources()} instead; {@code source} is for direct
     * source reads. Cannot be combined with {@link #ignore()}.</p>
     */
    String source() default "";

    /**
     * Source property names or paths passed to a computed mapping.
     *
     * <p>When {@link #compute()} is an inline lambda-like expression, these names
     * are matched to the lambda parameters. When omitted, the lambda parameter
     * names are used as source property names. For {@code this::helper}, omitted
     * sources default to the helper method parameter names.</p>
     */
    String[] sources() default {};

    /**
     * Computed value expression for the target property.
     *
     * <p>Use an expression-bodied lambda-like string such as
     * {@code "(first, last) -> first + \" \" + last"}, or a helper reference such
     * as {@code "this::join"}. The generated mapper emits direct Java code; no
     * runtime lambda object is created.</p>
     */
    String compute() default "";

    /** Array-like update behavior for this target property on update methods. */
    ArrayPolicy array() default ArrayPolicy.SET;

    /** Object-like update behavior for this target property on update methods. */
    ObjectPolicy object() default ObjectPolicy.PUT;

    /**
     * Whether to skip assignment of the target property.
     *
     * <p>Cannot be combined with {@link #source()}, {@link #sources()},
     * {@link #compute()}, or per-property update policies.</p>
     */
    boolean ignore() default false;
}
