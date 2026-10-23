package org.sjf4j.annotation.binding;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Selects the default ValueCodec format of one value type for a compiled interface. */
@Retention(RetentionPolicy.CLASS)
public @interface CodecMapping {
    Class<?> type();
    String format();
}
