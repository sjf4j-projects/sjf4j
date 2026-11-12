package org.sjf4j.annotation.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates a Java type with a JSON Schema for {@code SchemaValidator}.
 * Inline {@link #value()} text takes precedence over {@link #ref()}; when both
 * are empty, the validator looks for a schema file named after the type.
 * Annotated superclasses are also validated in parent-to-child order.
 * On {@code @CompiledValidator} methods, a method-level annotation overrides a
 * parameter annotation, which overrides annotations on the parameter type.
 * The runtime {@code SchemaValidator} resolves type annotations only.
 * External schemas used by {@code @CompiledValidator} may change without
 * triggering incremental Java recompilation; build tooling must track them.
 */
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidJsonSchema {
    /** Inline JSON Schema document, if supplied. */
    String value() default "";
    /**
     * Schema URI resolved against the validator's configured base directory.
     * When used by {@code @CompiledValidator}, changes to this external resource
     * may not trigger incremental Java recompilation.
     */
    String ref() default "";

    /**
     * JSON object mapping absolute retrieval URIs to JSON Schema documents
     * (object or boolean), as in the official JSON Schema Test Suite.
     * <p>
     * For example, a JSON Schema referring to an externally mocked resource:
     * <pre>{@code
     * @ValidJsonSchema(
     *     value = "{\"$ref\":\"https://example.test/common\"}",
     *     externalSchemas = "{\"https://example.test/common\":{\"type\":\"string\"}}"
     * )
     * }</pre>
     * This mapping is scoped to the annotated schema, not shared globally.
     * Inline mappings take precedence over {@link #externalSchemasRef()}.
     */
    String externalSchemas() default "";

    /**
     * A local {@code file:} or {@code classpath:} URI containing the same
     * JSON object format as {@link #externalSchemas()}, for example:
     * <pre>{@code
     * @ValidJsonSchema(
     *     ref = "classpath:/schemas/user.json",
     *     externalSchemasRef = "classpath:/schemas/external.json"
     * )
     * }</pre>
     * The external.json file contains a JSON object whose keys are absolute
     * retrieval URIs and whose values are JSON Schema objects or booleans.
     * Relative resource references use the default schema directory.
     */
    String externalSchemasRef() default "";
}
