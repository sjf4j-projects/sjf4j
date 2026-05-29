package org.sjf4j.annotation.compiled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface CompiledPath {
    String expr();
    Method method();

    enum Method {
        GET,
        PUT,
        PUT_IF_PARENT_PRESENT,
        ENSURE_PUT
    }
}
