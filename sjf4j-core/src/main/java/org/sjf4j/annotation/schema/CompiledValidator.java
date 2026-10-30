package org.sjf4j.annotation.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a validator for statically bound JSON Schemas.
 *
 * <p>Abstract methods accept exactly one value parameter and return boolean,
 * void (throwing on failure), or org.sjf4j.schema.ValidationResult.
 * Schema binding is resolved in this order: method-level
 * {@link ValidJsonSchema}, parameter-level annotation, then type annotations
 * (including annotated superclasses in parent-to-child order).
 * Schema resources are analyzed during annotation processing; generated
 * fallback plans load the original annotations or resources at runtime.
 * Schema JSON is not duplicated in the generated implementation.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CompiledValidator {
}
