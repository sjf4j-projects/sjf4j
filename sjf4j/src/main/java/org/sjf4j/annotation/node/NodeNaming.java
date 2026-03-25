package org.sjf4j.annotation.node;


import org.sjf4j.node.NamingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the default JSON property naming style for a POJO or JOJO type.
 * <p>
 * This annotation applies a type-level naming convention when SJF4J derives JSON
 * property names from Java member names. It overrides the global naming strategy
 * from {@code Sjf4jConfig} for the annotated type.
 *
 * <p>Precedence is:
 * <ul>
 *     <li>Explicit {@link NodeProperty} name on a field or creator parameter</li>
 *     <li>{@link NodeNaming} strategy on the declaring type</li>
 *     <li>Global naming strategy from configuration</li>
 * </ul>
 *
 * <p>{@code IDENTITY} is not supported here because leaving the annotation absent
 * already means "use the default naming behavior".
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeNaming {
    /**
     * Naming strategy applied when no explicit {@link NodeProperty} name is present.
     */
    NamingStrategy value();
}
