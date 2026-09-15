package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the JSON-facing name of a field, bean method, or creator parameter.
 * <p>
 * This annotation is used during POJO and JOJO binding.
 * <ul>
 *     <li>On a field, {@link #value()} sets the primary property name used by object mapping</li>
 *     <li>On a getter or setter method, {@link #value()} renames that property family</li>
 *     <li>On a {@link NodeCreator} parameter, it defines which input property supplies that argument</li>
 *     <li>{@link #aliases()} provides additional accepted input names during reads</li>
 * </ul>
 *
 * <p>This is mainly useful when the Java member name should differ from the JSON
 * property name, or when older input names must remain readable.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
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
     * Named codec variant for this property or creator parameter.
     * <p>
     * Selects among registered codec variants for the target type
     * (e.g. {@code "epochMillis"} for {@link java.time.Instant}).
     * The sentinel default means "not configured".
     */
    String codecName() default CODEC_NAME_UNSET;

    /**
     * Sentinel meaning {@link #codecName()} was not specified.
     */
    String CODEC_NAME_UNSET = "\u0000";

    /**
     * Date/time format pattern for this property or creator parameter.
     * <p>
     * Only applies to types whose value codec implements
     * {@link org.sjf4j.node.PatternedValueCodec}.
     * When specified, takes precedence over {@link #codecName()}.
     */
    String codecPattern() default "";

}
