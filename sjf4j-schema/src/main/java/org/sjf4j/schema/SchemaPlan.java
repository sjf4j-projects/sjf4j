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

        ValidationContext ctx = new ValidationContext(failFast, strictFormat);
        NodeFrame instance = ctx.root(node);
        PathSegment ps = failFast ? null : PathSegment.Root.INSTANCE;
        evaluate(instance, ps, ctx);
        return ctx.toResult();
    }

    /** Returns whether the instance passes validation in fail-fast mode. */
    public boolean isValid(Object node) {
        return isValid(node, false);
    }

    /** Returns whether the instance passes fail-fast validation with the selected format behavior. */
    public boolean isValid(Object node, boolean strictFormat) {
        if (booleanSchema) return booleanValue;
        ValidationContext ctx = ValidationContext.booleanOnly(strictFormat);
        return evaluate(ctx.root(node), null, ctx);
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


    /*
     * --------------------------------------------------------------
     * Evaluate
     * --------------------------------------------------------------
     */

    /**
     * Executes evaluator pipeline for the current instance branch.
     */
    boolean evaluate(NodeFrame instance, PathSegment ps, ValidationContext ctx) {
        if (booleanSchema) {
            if (booleanValue) return true;
            if (ctx.needsDiagnostic()) {
                ctx.addError(instance, ps, keywordPs, schemaUri, "false",
                        "schema 'false' always fails");
            }
            return false;
        }

        // Dynamic anchor maps may be populated after a plan is constructed;
        // unlike evaluated tracking, they must be checked at execution time.
        boolean dynamicScope = !byDynamicAnchorPlans.isEmpty();
        // Most schemas (including nested Creek schemas) need no execution scope.
        // Keep one evaluator loop for scoped and unscoped plans alike.
        if (!tracksEvaluated && !dynamicScope) {
            return evaluateKeywords(instance, ps, ctx);
        }

        int savedDepth = tracksEvaluated ? ctx.evaluationDepth() : 0;
        if (tracksEvaluated) ctx.createEvaluated();
        if (dynamicScope) ctx.pushPlan(this);
        try {
            return evaluateKeywords(instance, ps, ctx);
        } finally {
            if (dynamicScope) ctx.popPlan();
            if (tracksEvaluated) ctx.restoreEvaluationDepth(savedDepth);
        }
    }

    private boolean evaluateKeywords(NodeFrame instance, PathSegment ps, ValidationContext ctx) {
        boolean valid = true;
        for (Evaluator evaluator : evaluators) {
            if (!evaluator.evaluate(instance, ps, ctx)) {
                valid = false;
                if (ctx.shouldAbort()) return false;
            }
        }
        return valid;
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
    final boolean tracksEvaluated;

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
        this.tracksEvaluated = evaluators.length != 0
                && evaluators[evaluators.length - 1] instanceof Evaluator.UnevaluatedEvaluator;

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
