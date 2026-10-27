package org.sjf4j.annotation.binding;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Selects a ValueCodec format within {@link BindingOptions#valueCodecs()}. */
@Target({})
@Retention(RetentionPolicy.CLASS)
public @interface CodecMapping {
    Class<?> type();
    String format();
}
