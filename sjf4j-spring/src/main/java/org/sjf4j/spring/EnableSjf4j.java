package org.sjf4j.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables automatic registration of annotation-processor-generated SJF4J interfaces
 * in a plain Spring application context.
 *
 * <p>Spring Boot applications do not need this annotation when using
 * {@code sjf4j-spring-boot-starter}.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(Sjf4jSpringConfiguration.class)
public @interface EnableSjf4j {
}
