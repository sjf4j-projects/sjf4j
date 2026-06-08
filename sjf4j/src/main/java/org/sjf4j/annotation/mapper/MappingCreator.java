package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares how a {@link CompiledMapper} creates a target type.
 *
 * <p>Use this annotation when the declared target type cannot or should not be
 * instantiated by the default rules. The common case is an interface or
 * abstract target:</p>
 *
 * <pre>{@code
 * @CompiledMapper
 * @MappingCreator(targetType = UserView.class, implementation = UserViewImpl.class)
 * interface Users {
 *     UserView toView(User user);
 * }
 * }</pre>
 *
 * <p>{@link #implementation()} and {@link #creator()} are mutually exclusive.
 * An implementation creator maps the requested target as the implementation
 * type and returns it as the declared type. A method creator calls a no-args
 * factory method on the mapper and then writes mapped properties to the returned
 * mutable object.</p>
 *
 * <p>Creators are selected by assignability: a creator whose
 * {@link #targetType()} is a supertype of the requested target may match, and
 * the most specific matching target type wins. Equal or unrelated matches are
 * rejected as ambiguous. Creators declared on parent mapper interfaces are
 * inherited.</p>
 *
 * <p>This annotation is retained in class files so a child mapper compiled in a
 * later module can inherit creators from an already compiled parent interface.
 * It is still processor-consumed metadata and is not retained for runtime
 * reflection.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Repeatable(MappingCreators.class)
public @interface MappingCreator {
    /**
     * Declared target type this creator applies to.
     *
     * <p>The type must be a declared class or interface. It may be concrete,
     * abstract, or an interface.</p>
     */
    Class<?> targetType();

    /**
     * Concrete implementation to instantiate and structurally map.
     *
     * <p>The implementation must be assignable to {@link #targetType()} and must
     * itself satisfy normal target construction rules: public no-args
     * constructor, record canonical constructor, or one public constructor.
     * Leave as {@link Void} when using {@link #creator()}.</p>
     */
    Class<?> implementation() default Void.class;

    /**
     * Mapper factory method used to create a mutable target instance.
     *
     * <p>The current supported form is {@code "this::method"}. The method must
     * be default or static, declare no parameters, and return a concrete mutable
     * type assignable to {@link #targetType()}. Leave empty when using
     * {@link #implementation()}.</p>
     */
    String creator() default "";
}
