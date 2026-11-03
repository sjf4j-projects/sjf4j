package org.sjf4j.annotation.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a compile-time generated write binding operation.
 *
 * <p>A one-parameter method binds the value to a returned {@link String} or
 * {@code byte[]}. A two-parameter method uses the first parameter as the value
 * and requires the second parameter to be {@link java.io.OutputStream} or
 * {@link java.io.Writer} (or a subtype); that form returns {@code void}.
 * Write failures are reported as unchecked
 * {@link org.sjf4j.exception.BindingException}.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Write {
}
