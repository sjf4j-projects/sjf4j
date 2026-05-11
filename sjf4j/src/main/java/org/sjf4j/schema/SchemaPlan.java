package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.util.List;
import java.util.Map;

/**
 * Compiled schema resource ready for validation.
 * <p>
 * Plans from the same schema resource share fragment lookup maps:
 * anchors, dynamic anchors, and JSON Pointer fragments. A nested schema with
 * its own {@code $id} compiles into a different resource and therefore gets a
 * different set of fragment maps. Instances are immutable after compilation and
 * can be reused across validations.
 */
public final class SchemaPlan {
    final PathSegment keywordPs;
    final Evaluator[] evaluators;
    final boolean booleanSchema;
    final boolean booleanValue;

    final String dynamicAnchor;
    final Map<String, SchemaPlan> byAnchorPlans;
    final Map<String, SchemaPlan> byDynamicAnchorPlans;
    final Map<String, SchemaPlan> byPathPlans;

    SchemaPlan(PathSegment keywordPs, Evaluator[] evaluators,
                boolean booleanSchema, boolean booleanValue, String dynamicAnchor,
                Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                Map<String, SchemaPlan> byPathPlans) {
        this.keywordPs = keywordPs;
        this.evaluators = evaluators;
        this.booleanSchema = booleanSchema;
        this.booleanValue = booleanValue;

        this.dynamicAnchor = dynamicAnchor;
        this.byAnchorPlans = byAnchorPlans;
        this.byDynamicAnchorPlans = byDynamicAnchorPlans;
        this.byPathPlans = byPathPlans;
    }

    static SchemaPlan of(PathSegment keywordPs, BooleanSchema booleanSchema) {
        return new SchemaPlan(keywordPs, new Evaluator[0], true, booleanSchema.booleanValue(),
                null, null, null, null);
    }

    static SchemaPlan of(PathSegment keywordPs, List<Evaluator> evaluators, String dynamicAnchor,
                         Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                         Map<String, SchemaPlan> byPathPlans) {
        return new SchemaPlan(keywordPs, evaluators.toArray(new Evaluator[0]), false, false,
                dynamicAnchor, byAnchorPlans, byDynamicAnchorPlans, byPathPlans);
    }

    /**
     * Resolves one fragment inside this compiled resource.
     * <p>
     * Named fragments are checked before JSON Pointer fragments because both
     * live in the same resource-local fragment namespace.
     */
    SchemaPlan getByFragment(String fragment) {
        // Named anchors and dynamic anchors share one fragment namespace.
        SchemaPlan plan = byAnchorPlans.get(fragment);
        if (plan == null) return byPathPlans.get(fragment);
        return plan;
    }

    /// Validate

    public ValidationResult validate(Object node) {
        return validate(node, ValidationOptions.DEFAULT);
    }

    /**
     * Validates one instance against this compiled schema plan.
     * <p>
     * In fail-fast mode only the latest error is retained; otherwise all
     * collected messages are available in the returned result.
     */
    public ValidationResult validate(Object node, ValidationOptions options) {
        if (booleanSchema) {
            if (booleanValue) {
                return ValidationResult.SUCCESS;
            } else {
                ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                        PathSegment.Root.INSTANCE, keywordPs, "false", "Schema 'false' always fails");
                return new ValidationResult(false, null, msg);
            }
        }

        InstancedNode instance = InstancedNode.infer(node);
        ValidationContext ctx = new ValidationContext(options);
        PathSegment ps = options.isFailFast() ? null : PathSegment.Root.INSTANCE;
        evaluate(instance, ps, ctx);
        return ctx.toResult();
    }

    public boolean isValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAILFAST);
        return result.isValid();
    }

    /**
     * Validates in fail-fast mode and throws on the first error.
     */
    public void requireValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAILFAST);
        if (!result.isValid()) throw new ValidationException(result);
    }


    /// Evaluate
    /**
     * Executes evaluator pipeline for the current instance branch.
     */
    boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
        if (booleanSchema) {
            if (booleanValue) {
                return true;
            } else {
                ctx.addError(instance, ps, keywordPs, "false", "Schema 'false' always fails");
                return false;
            }
        }

        int len = evaluators.length;
        if (len > 0 && evaluators[len - 1] instanceof Evaluator.UnevaluatedEvaluator) {
            instance.createEvaluated();
        }

        boolean result = true;
        ctx.planStack.push(this);
        for (Evaluator evaluator : evaluators) {
            result = result && evaluator.evaluate(instance, ps, ctx);
            if (ctx.shouldAbort()) return result;
        }
        ctx.planStack.pop();
        return result;
    }

}
