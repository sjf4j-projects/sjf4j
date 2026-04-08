package org.sjf4j.annotation.node;


import org.sjf4j.node.AccessStrategy;
import org.sjf4j.node.NamingStrategy;

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
     * Access policy for non-public POJO fields during framework-owned binding.
     */
    AccessStrategy access() default AccessStrategy.BEAN_BASED;
}
