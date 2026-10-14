package org.sjf4j.annotation.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a compile-time generated read binding operation.
 *
 * <p>Accepts exactly one input parameter: {@link String}, {@code byte[]},
 * {@link java.io.InputStream}, or {@link java.io.Reader} (including assignable
 * stream/reader subtypes). The return type is the bound value type and must not
 * be {@code void}. The method must declare {@link java.io.IOException} or a
 * supertype so generated code can report input failures.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ReadFrom {
}
