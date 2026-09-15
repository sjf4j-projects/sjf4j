package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a property source from POJO property discovery.
 * <ul>
 *     <li>On a type, any property referencing this type is excluded from discovery
 *         (equivalent to Jackson's {@code @JsonIgnoreType}).</li>
 *     <li>On a field, that field source does not participate.</li>
 *     <li>On a getter, no bean reader is created from that getter.</li>
 *     <li>On a setter, no bean writer is created from that setter.</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeIgnore {
}
