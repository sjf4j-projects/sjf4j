package org.sjf4j.annotation.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a compile-time generated read binding operation.
 *
 * <p>V2 accepts exactly one input parameter: {@link String}, {@code byte[]},
 * {@link java.io.InputStream}, or {@link java.io.Reader}. The return type is
 * the bound value type.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ReadFrom {
}
