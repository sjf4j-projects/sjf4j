package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

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

    /**
     * Validates one instance with aggregated messages and non-strict format checks.
     * <p>
     * The framework does not write the instance graph. {@code @NodeValue}
     * instances are validated as raw representations encoded by their configured
     * value binding; invoked getters and user extensions can have side effects.
     */
    public ValidationResult validate(Object node) {
        return validate(node, false, false);
    }

    /**
     * Validates one instance with aggregated messages.
     * <p>
     * {@code strictFormat} controls format assertions; it does not change value
     * binding or framework input-write behavior.
     */
    public ValidationResult validate(Object node, boolean strictFormat) {
        return validate(node, false, strictFormat);
    }

    /**
     * Validates one instance against this compiled schema plan.
     * <p>
     * In fail-fast mode only the latest error is retained; otherwise all
     * collected messages are available in the returned result. The framework does
     * not write the input graph, but invoked getters, value bindings, and user
     * extensions can have side effects. Null is an explicit null instance, not a
     * missing property; missing-property handling belongs to object keywords such
     * as {@code required}.
     */
    public ValidationResult validate(Object node, boolean failFast, boolean strictFormat) {
        if (booleanSchema) {
            if (booleanValue) {
                return ValidationResult.SUCCESS;
            } else {
                ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                        PathSegment.Root.INSTANCE, keywordPs,
                        schemaUri, "false", "schema 'false' always fails");
                return new ValidationResult(false, null, msg);
            }
        }

        Instance instance = Instance.infer(node);
        ValidationContext ctx = new ValidationContext(failFast, strictFormat, false);
        PathSegment ps = failFast ? null : PathSegment.Root.INSTANCE;
        boolean valid = evaluate(instance, ps, ctx);
        return ctx.toResult(valid);
    }

    /**
     * Validates with a caller-owned reusable context. Each call resets the
     * context's execution state; previously returned results remain independent.
     * Do not use the same context concurrently or recursively.
     */
    public ValidationResult validate(Object node, ValidationContext context) {
        java.util.Objects.requireNonNull(context, "context");
        context.begin(true);
        try {
            if (booleanSchema) {
                if (booleanValue) return ValidationResult.SUCCESS;
                ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                        PathSegment.Root.INSTANCE, keywordPs,
                        schemaUri, "false", "schema 'false' always fails");
                return new ValidationResult(false, null, msg);
            }
            Instance instance = Instance.infer(node);
            PathSegment ps = context.isFailFast() ? null : PathSegment.Root.INSTANCE;
            boolean valid = evaluate(instance, ps, context);
            return context.toResult(valid);
        } finally {
            context.end();
        }
    }

    /**
     * Fast fail-first validation using a caller-owned reusable context.
     * Prefer ValidationContext.forIsValid(strictFormat) to avoid diagnostics.
     */
    public boolean isValid(Object node, ValidationContext context) {
        java.util.Objects.requireNonNull(context, "context");
        context.begin(false);
        try {
            if (booleanSchema) return booleanValue;
            return evaluate(Instance.infer(node), null, context);
        } finally {
            context.end();
        }
    }

    /** Returns whether the instance passes validation in fail-fast mode. */
    public boolean isValid(Object node) {
        return isValid(node, false);
    }

    /** Returns whether the instance passes fail-fast validation with the selected format behavior. */
    public boolean isValid(Object node, boolean strictFormat) {
        if (booleanSchema) return booleanValue;
        Instance instance = Instance.infer(node);
        ValidationContext ctx = new ValidationContext(true, strictFormat, true);
        return evaluate(instance, null, ctx);
    }

    /**
     * Validates in fail-fast mode and throws on the first error.
     */
    public void requireValid(Object node) {
        requireValid(node, false);
    }

    /**
     * Validates in fail-fast mode with the selected format behavior and throws on
     * the first error.
     */
    public void requireValid(Object node, boolean strictFormat) {
        ValidationResult result = validate(node, true, strictFormat);
        if (!result.isValid()) throw new ValidationException(result);
    }


    /** Throws on invalid input, reusing the caller's diagnostic context. */
    public void requireValid(Object node, ValidationContext context) {
        ValidationResult result = validate(node, context);
        if (!result.isValid()) throw new ValidationException(result);
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
