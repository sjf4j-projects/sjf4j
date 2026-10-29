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
 * Unlike SchemaValidator's runtime lookup, schema resources are resolved and
 * validated during annotation processing and embedded in generated code.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CompiledValidator {
}
