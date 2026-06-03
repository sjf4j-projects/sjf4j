package org.sjf4j.annotation.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Configures one {@link CompiledMapper} method. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface MapperOptions {
    /** Null handling for mutable create targets and update targets. */
    NullValuePolicy nulls() default NullValuePolicy.SET;

    Class<? extends List> listType() default ArrayList.class;
    Class<? extends Set> setType() default LinkedHashSet.class;
    Class<? extends Map> mapType() default LinkedHashMap.class;

    /** Array-like update behavior for target properties. */
    ArrayPolicy arrays() default ArrayPolicy.CLEAR_ADD;

    /** Object-like update behavior for target properties. */
    ObjectPolicy objects() default ObjectPolicy.PUT;
}
