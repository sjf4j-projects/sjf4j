package org.sjf4j.annotation.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a validator for statically bound JSON Schemas.
 *
 * <p>Abstract methods accept one value parameter and an optional
 * {@code boolean strictFormat} parameter. They return {@code boolean},
 * {@code void} (throwing on failure), or {@code ValidationResult}.
 * Schema binding is resolved in this order: method-level
 * {@link ValidJsonSchema}, parameter-level annotation, then type annotations
 * (including annotated superclasses in parent-to-child order).
 * Schema resources are analyzed during annotation processing; generated
 * fallback plans load the original annotations or resources at runtime.
 * Schema JSON is not duplicated in the generated implementation.</p>
 *
 * <p>When {@link #fallback()} is false, compilation fails unless all checks
 * can be generated without a runtime {@code SchemaPlan}.</p>
 *
 * <p><b>Incremental compilation:</b> external schema resources referenced via
 * {@link ValidJsonSchema#ref()} or {@link ValidJsonSchema#externalSchemasRef()}
 * (or implicit filename lookup) can change without triggering the Java
 * annotation processor. JSR 269 cannot declare resource
 * files as incremental compilation dependencies. Build tooling must track
 * these inputs and rerun compilation, or a clean rebuild may be needed.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CompiledValidator {
    /** Whether unsupported checks and ValidationResult methods may use SchemaPlan at runtime. */
    boolean fallback() default false;

    /** Additional precompiled {@link org.sjf4j.annotation.node.NodeRegistry} interfaces. */
    Class<?>[] registries() default {};

}
