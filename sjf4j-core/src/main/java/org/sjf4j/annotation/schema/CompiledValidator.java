package org.sjf4j.annotation.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a validator for statically bound JSON Schemas.
 *
 * <p>Abstract methods accept one value parameter and an optional boolean strictFormat,
 * and return boolean,
 * void (throwing on failure), or org.sjf4j.schema.ValidationResult.
 * Schema binding is resolved in this order: method-level
 * {@link ValidJsonSchema}, parameter-level annotation, then type annotations
 * (including annotated superclasses in parent-to-child order).
 * Schema resources are analyzed during annotation processing; generated
 * fallback plans load the original annotations or resources at runtime.
 * Schema JSON is not duplicated in the generated implementation.
 *
 * <p><b>Incremental compilation:</b> external schema resources referenced via
 * {@link ValidJsonSchema#ref()} (or implicit filename lookup) can change without
 * triggering the Java annotation processor. JSR 269 cannot declare resource
 * files as incremental compilation dependencies. Build tooling must track
 * these inputs and rerun compilation, or a clean rebuild may be needed.</p>
 * When {@link #fallback()} is false, compilation fails unless the validator can
 * generate all checks without relying on a runtime SchemaPlan.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CompiledValidator {
    /** Whether unsupported checks and ValidationResult methods may use SchemaPlan at runtime. */
    boolean fallback() default false;

    /**
     * Optional file-system directory containing pre-registered JSON Schemas.
     * Every .json file is indexed at compile time before reference resolution.
     * The same directory must be accessible when runtime fallback is used.
     * Accepts a file: directory URI or a working-directory-relative path ending in '/'.
     * Classpath directory enumeration and HTTP fetching are not supported.
     */
    String registryDir() default "";

    /**
     * Optional absolute URI base for resources in registryDir, ending in '/'.
     * Each local file is indexed at this base plus its path relative to registryDir.
     * Without it, files use their local file: retrieval URIs. This supports
     * preloaded remote schemas that have no $id, without fetching over HTTP.
     */
    String registryBaseUri() default "";

}
