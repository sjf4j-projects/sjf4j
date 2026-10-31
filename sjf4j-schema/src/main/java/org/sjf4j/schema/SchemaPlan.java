package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Compiled schema resource ready for validation of OBNT representations.
 * <p>
 * Plans from the same schema resource share fragment lookup maps:
 * anchors, dynamic anchors, and JSON Pointer fragments. A nested schema with
 * its own {@code $id} compiles into a different resource and therefore gets a
 * different set of fragment maps. Additional JSON Pointer fragment targets may
 * be compiled lazily on first resolution and cached back into the same resource
 * lookup maps for reuse.
 * Call {@link #validate(Object)} to collect messages, {@link #isValid(Object)}
 * for a fail-fast boolean, or {@link #requireValid(Object)} to throw on failure.
 */
public final class SchemaPlan {

    /*
     * --------------------------------------------------------------
     * Validate
     * --------------------------------------------------------------
     */

    /** Validates with aggregated messages and non-strict format assertions. */
    public ValidationResult validate(Object node) {
        return validate(node, false, false);
    }

    /** Validates with aggregated messages and the selected format behavior. */
    public ValidationResult validate(Object node, boolean strictFormat) {
        return validate(node, false, strictFormat);
    }

    /**
     * Validates an OBNT instance. Null is an explicit value, never a missing
     * object member. Input nodes are not modified, but custom getters or value
     * bindings may have side effects.
     */
    public ValidationResult validate(Object node, boolean failFast, boolean strictFormat) {
        if (booleanSchema) return booleanResult();
        return validate(node, failFast, strictFormat, new ValidationContext());
    }

    /** Validates using a caller-owned context with default aggregate settings. */
    public ValidationResult validate(Object node, ValidationContext context) {
        return validate(node, false, false, context);
    }

    /**
     * Validates using a caller-owned context, reusable across sequential calls.
     * Execution options belong to this call, not to the context. Returned
     * results retain their own messages; concurrent context use is unsupported.
     */
    public ValidationResult validate(Object node, boolean failFast, boolean strictFormat,
                                     ValidationContext context) {
        Asserts.notNull(context, "context");
        if (booleanSchema) return booleanResult();
        context.reset(failFast, strictFormat, false);
        try {
            Instance instance = Instance.infer(node);
            PathSegment ps = failFast ? null : PathSegment.Root.INSTANCE;
            boolean valid = evaluate(instance, ps, context);
            return context.toResult(valid);
        } finally {
            context.clear();
        }
    }

    /** Returns whether an instance passes fail-fast validation. */
    public boolean isValid(Object node) {
        return isValid(node, false);
    }

    /** Returns whether an instance passes fail-fast validation in the selected format mode. */
    public boolean isValid(Object node, boolean strictFormat) {
        if (booleanSchema) return booleanValue;
        return isValid(node, strictFormat, new ValidationContext());
    }

    /** Fail-fast boolean validation using a caller-owned context. */
    public boolean isValid(Object node, ValidationContext context) {
        return isValid(node, false, context);
    }

    /** Fast fail-first validation, reusing a caller-owned context without diagnostics. */
    public boolean isValid(Object node, boolean strictFormat, ValidationContext context) {
        Asserts.notNull(context, "context");
        if (booleanSchema) return booleanValue;
        context.reset(true, strictFormat, true);
        try {
            return evaluate(Instance.infer(node), null, context);
        } finally {
            context.clear();
        }
    }

    /** Validates in fail-fast mode and throws on the first error. */
    public void requireValid(Object node) {
        requireValid(node, false);
    }

    /** Validates in fail-fast mode with the selected format behavior. */
    public void requireValid(Object node, boolean strictFormat) {
        ValidationResult result = validate(node, true, strictFormat);
        if (!result.isValid()) throw new ValidationException(result);
    }

    /** Validates in fail-fast mode, reusing a caller-owned context. */
    public void requireValid(Object node, ValidationContext context) {
        requireValid(node, false, context);
    }

    /** Validates in fail-fast mode, reusing a caller-owned context. */
    public void requireValid(Object node, boolean strictFormat, ValidationContext context) {
        ValidationResult result = validate(node, true, strictFormat, context);
        if (!result.isValid()) throw new ValidationException(result);
    }

    private ValidationResult booleanResult() {
        if (booleanValue) return ValidationResult.SUCCESS;
        ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                PathSegment.Root.INSTANCE, keywordPs,
                schemaUri, "false", "schema 'false' always fails");
        return new ValidationResult(false, null, msg);
    }


    /*
     * --------------------------------------------------------------
     * Evaluate
     * --------------------------------------------------------------
     */

    /**
     * Executes evaluator pipeline for the current instance branch.
     */
    boolean evaluate(Instance instance, PathSegment ps, ValidationContext ctx) {
        if (booleanSchema) {
            if (booleanValue) {
                return true;
            } else {
                ctx.addError(instance, ps, keywordPs, schemaUri, "false", "schema 'false' always fails");
                return false;
            }
        }

        int len = evaluators.length;
        boolean tracksEvaluated = len > 0 && evaluators[len - 1] instanceof Evaluator.UnevaluatedEvaluator;
        if (tracksEvaluated) {
            instance.createEvaluated();
        }

        boolean result = true;
        // Only resources that declare dynamic anchors participate in runtime
        // dynamicRef rebinding. Other resources do not need to live on the
        // validation scope stack.
        if (!byDynamicAnchorPlans.isEmpty()) ctx.pushPlan(this);
        for (Evaluator evaluator : evaluators) {
            boolean matched = evaluator.evaluate(instance, ps, ctx);
            if (!matched) {
                result = false;
                if (ctx.isFailFast() && !tracksEvaluated) {
                    if (!byDynamicAnchorPlans.isEmpty()) ctx.popPlan();
                    return false;
                }
            }
        }
        if (!byDynamicAnchorPlans.isEmpty()) ctx.popPlan();
        return result;
    }


    /*
     * --------------------------------------------------------------
     * Planning
     * --------------------------------------------------------------
     */

    final URI schemaUri;
    final PathSegment keywordPs;
    final Evaluator[] evaluators;
    final boolean booleanSchema;
    final boolean booleanValue;

    final String dynamicAnchor;
    final Map<String, SchemaPlan> byAnchorPlans;
    final Map<String, SchemaPlan> byDynamicAnchorPlans;
    final Map<String, SchemaPlan> byPathPlans;

    final ObjectSchema schema;
    final SchemaDialect dialect;
    final Map<String, Boolean> vocabulary;

    SchemaPlan(URI schemaUri, PathSegment keywordPs, Evaluator[] evaluators,
               boolean booleanSchema, boolean booleanValue, String dynamicAnchor,
               Map<String, SchemaPlan> byAnchorPlans,
               Map<String, SchemaPlan> byDynamicAnchorPlans,
               Map<String, SchemaPlan> byPathPlans,
               ObjectSchema schema, SchemaDialect dialect, Map<String, Boolean> vocabulary) {
        this.schemaUri = schemaUri;
        this.keywordPs = keywordPs;
        this.evaluators = evaluators;
        this.booleanSchema = booleanSchema;
        this.booleanValue = booleanValue;

        this.dynamicAnchor = dynamicAnchor;
        this.byAnchorPlans = byAnchorPlans;
        this.byDynamicAnchorPlans = byDynamicAnchorPlans;
        this.byPathPlans = byPathPlans;

        this.schema = schema;
        this.dialect = dialect;
        this.vocabulary = vocabulary;
    }

    static SchemaPlan of(URI schemaUri, PathSegment keywordPs, BooleanSchema booleanSchema) {
        return new SchemaPlan(schemaUri, keywordPs, new Evaluator[0], true, booleanSchema.booleanValue(),
                null, null, null, null, null, null, null);
    }

    static SchemaPlan of(URI schemaUri, PathSegment keywordPs, List<Evaluator> evaluators, String dynamicAnchor,
                         Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                         Map<String, SchemaPlan> byPathPlans,
                         ObjectSchema resourceRootSchema, SchemaDialect resourceDialect,
                         Map<String, Boolean> resourceVocabulary) {
        return new SchemaPlan(schemaUri, keywordPs, evaluators.toArray(new Evaluator[0]), false, false,
                dynamicAnchor, byAnchorPlans, byDynamicAnchorPlans, byPathPlans,
                resourceRootSchema, resourceDialect, resourceVocabulary);
    }

}
