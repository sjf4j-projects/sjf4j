package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares type-level JSON binding rules for a POJO, JOJO, or JAJO model.
 * <p>
 * This annotation is the single place for stable binding semantics that must be
 * cached inside {@code NodeRegistry}. It replaces global naming and plain-field
 * switches for framework-owned POJO analysis.
 * <p>
 * Precedence is explicit {@link NodeProperty} name first, then {@link #naming()},
 * then identity naming when no override exists.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeBinding {
    /**
     * Naming strategy applied when no explicit {@link NodeProperty} name is present.
     */
    NamingStrategy naming() default NamingStrategy.IDENTITY;

    /**
     * Property discovery policy for framework-owned POJO binding.
     * <p>
     * Default is {@link PropertyStrategy#BEAN_FIELD}, which gives Jackson-like
     * bean-first discovery while still allowing field fallback where needed.
     */
    PropertyStrategy propertyStrategy() default PropertyStrategy.BEAN_FIELD;

    /**
     * Whether JOJO reads retain unknown JSON object members as dynamic properties.
     */
    boolean readDynamic() default true;

    /**
     * Whether JOJO writes emit dynamic properties in addition to declared properties.
     */
    boolean writeDynamic() default true;
}
