package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

/**
 * Runtime support for generated compiled JSON Schema validators.
 * Only operations that cannot be emitted as direct Java access belong here.
 */
public final class CompiledValidatorSupport {
    private CompiledValidatorSupport() {}

    /**
     * Executes a compiled validator's diagnostic fallback at its original
     * instance location, without exposing path-aware SchemaPlan APIs.
     */
    public static void requireValid(SchemaPlan plan, Object node,
                                    PathSegment instancePs, boolean strictFormat) {
        ValidationContext ctx = new ValidationContext(true, strictFormat, false);
        if (!plan.evaluate(node, instancePs, ctx)) {
            throw new ValidationException(ctx.toResult(false));
        }
    }

}
