package org.sjf4j.annotation.node;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the constructor or static factory method used to create a POJO or JOJO.
 * <p>
 * Use this when object binding should go through a specific creator instead of a
 * default no-args construction path. Creator parameters are matched from input
 * object properties by parameter name or by {@link NodeProperty} when an explicit
 * JSON property name is needed.
 *
 * <p>Only one creator may be selected for a type.
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeCreator {}
