package org.sjf4j.annotation.node;


import org.sjf4j.node.NamingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the JSON property naming style for a POJO/JOJO type.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeNaming {
    NamingStrategy value();
}
