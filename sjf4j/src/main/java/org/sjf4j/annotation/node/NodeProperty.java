package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the JSON-facing name of a field or creator parameter.
 * <p>
 * This annotation is used during POJO and JOJO binding.
 * <ul>
 *     <li>On a field, {@link #value()} sets the primary property name used by object mapping</li>
 *     <li>On a {@link NodeCreator} parameter, it defines which input property supplies that argument</li>
 *     <li>{@link #aliases()} provides additional accepted input names during reads</li>
 * </ul>
 *
 * <p>This is mainly useful when the Java member name should differ from the JSON
 * property name, or when older input names must remain readable.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeProperty {

    /**
     * Primary JSON property name.
     */
    String value() default "";

    /**
     * Additional input names accepted during reads.
     */
    String[] aliases() default {};

    /**
     * Explicit value codec format for this field or creator parameter.
     * <p>
     * Use {@code ""} to force the default value codec slot. The sentinel default
     * means "not configured".
     */
    String valueFormat() default VALUE_FORMAT_UNSET;

    /**
     * Sentinel meaning valueFormat was not specified.
     */
    String VALUE_FORMAT_UNSET = "\u0000";

}
