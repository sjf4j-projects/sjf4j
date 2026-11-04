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
 * Call {@link #validate(Object)} to collect validation errors, {@link #isValid(Object)}
 * for a fail-fast boolean, or {@link #requireValid(Object)} to throw on failure.
 */
public final class SchemaPlan {

    /*
     * --------------------------------------------------------------
     * Validate
     * --------------------------------------------------------------
     */

    /**
     * Validates one instance with aggregated errors and non-strict format checks.
     * <p>
     * The framework does not write the instance graph. {@code @NodeValue}
     * instances use their configured raw encoding for leaf assertions, but
     * remain atomic value nodes: object/array keywords never traverse a raw
     * Map/List produced by the binding. Invoked getters and user extensions
     * can have side effects.
     */
    public ValidationResult validate(Object node) {
        return validate(node, false, false);
    }

    /**
     * Validates one instance with aggregated errors.
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
     * In fail-fast mode only the first error is retained; otherwise all
     * collected errors are available in the returned result. The framework does
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
                ValidationResult.Message error = new ValidationResult.Message(
                        "false", "schema 'false' always fails", null,
                        PathSegment.Root.INSTANCE, keywordPs, resourceUri);
                return new ValidationResult(false, java.util.Collections.singletonList(error));
            }
        }

        ValidationContext ctx = new ValidationContext(failFast, strictFormat, false);
        boolean valid = evaluate(node, ctx);
        return ctx.toResult(valid);
    }

    /** Validates with optional annotation collection. */
    public ValidationResult validate(Object node, ValidationOptions options) {
        Asserts.notNull(options, "options");
        if (!options.isCollectAnnotations()) {
            return validate(node, options.isFailFast(), options.isStrictFormat());
        }
        ValidationContext ctx = new ValidationContext(options, false);
        boolean valid = evaluate(node, ctx);
        return ctx.toResult(valid);
    }

    /** Returns whether the instance passes validation in fail-fast mode. */
    public boolean isValid(Object node) {
        return isValid(node, false);
    }

    /** Returns whether the instance passes fail-fast validation with the selected format behavior. */
    public boolean isValid(Object node, boolean strictFormat) {
        if (booleanSchema) return booleanValue;
        ValidationContext ctx = new ValidationContext(true, strictFormat, true);
        return evaluate(node, ctx);
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
     * Validates one value with a caller-owned context. Returns only this call's
     * validity; diagnostic errors accumulate in the context across calls.
     * Options are fixed when the context is constructed, and it must not be
     * accessed concurrently or recursively.
     */
    public boolean evaluate(Object node, ValidationContext ctx) {
        Asserts.notNull(ctx, "ctx");
        return evaluate(node, ctx.isFailFast() ? null : PathSegment.Root.INSTANCE, ctx);
    }

    /**
     * Evaluates a nested instance using an explicit enclosing instance path.
     * Used by compiled fallback support; resets per-evaluation context state.
     */
    boolean evaluate(Object node, PathSegment instancePs, ValidationContext ctx) {
        Asserts.notNull(ctx, "ctx");
        try {
            if (booleanSchema) {
                if (!booleanValue) {
                    ctx.addError(null, instancePs == null ? PathSegment.Root.INSTANCE : instancePs,
                            keywordPs, resourceUri, "false", "schema 'false' always fails");
                }
                return booleanValue;
            }
            return evaluate(Instance.infer(node), instancePs, ctx);
        } finally {
            ctx.reset();
        }
    }

    /**
     * Executes evaluator pipeline for the current instance branch.
     */
    boolean evaluate(Instance instance, PathSegment ps, ValidationContext ctx) {
        if (ctx.isCollectAnnotations()) {
            int mark = ctx.annotationMark();
            boolean valid = false;
            try {
                valid = evaluateKeywords(instance, ps, ctx);
                if (valid && annotationEvaluator != null) {
                    annotationEvaluator.evaluate(instance, ps, ctx);
                }
                return valid;
            } finally {
                if (!valid) ctx.rollbackAnnotations(mark);
            }
        }
        return evaluateKeywords(instance, ps, ctx);
    }

    /** Unchanged fast validation path; annotation bookkeeping is outside this loop. */
    private boolean evaluateKeywords(Instance instance, PathSegment ps, ValidationContext ctx) {
        if (booleanSchema) {
            if (booleanValue) {
                return true;
            } else {
                ctx.addError(instance, ps, keywordPs, resourceUri, "false", "schema 'false' always fails");
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
        boolean hasDynamicAnchors = !byDynamicAnchorPlans.isEmpty();
        if (hasDynamicAnchors) ctx.pushPlan(this);
        try {
            for (Evaluator evaluator : evaluators) {
                boolean matched = evaluator.evaluate(instance, ps, ctx);
                if (!matched) {
                    result = false;
                    if (ctx.isFailFast() && !tracksEvaluated) {
                        return false;
                    }
                }
            }
        } finally {
            if (hasDynamicAnchors) ctx.popPlan();
        }
        return result;
    }


    /*
     * --------------------------------------------------------------
     * Planning
     * --------------------------------------------------------------
     */

    /** Original parsed BooleanSchema or ObjectSchema, without conversion. */
    public JsonSchema schema() {
        return schema;
    }

    /** Absolute URI identifying this schema node, including its JSON Pointer. */
    public URI schemaUri() {
        if (resourceUri == null) return null;
        String path = keywordPs == null ? "" : keywordPs.rootedPointerExpr();
        if (path.isEmpty()) return resourceUri;
        try {
            if (resourceUri.isOpaque()) {
                return new URI(resourceUri.getScheme(), resourceUri.getSchemeSpecificPart(), path);
            }
            return new URI(resourceUri.getScheme(), resourceUri.getAuthority(),
                    resourceUri.getPath(), resourceUri.getQuery(), path);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("invalid schema URI: " + resourceUri + "#" + path, e);
        }
    }

    final URI resourceUri;
    final PathSegment keywordPs;
    final Evaluator[] evaluators;
    final Evaluator.AnnotationEvaluator annotationEvaluator;
    final boolean booleanSchema;
    final boolean booleanValue;

    final String dynamicAnchor;
    final Map<String, SchemaPlan> byAnchorPlans;
    final Map<String, SchemaPlan> byDynamicAnchorPlans;
    final Map<String, SchemaPlan> byPathPlans;

    final JsonSchema schema;
    final SchemaDialect dialect;
    final Map<String, Boolean> vocabulary;
    SchemaPlan(URI resourceUri, PathSegment keywordPs, Evaluator[] evaluators,
               JsonSchema schema, String dynamicAnchor,
               Map<String, SchemaPlan> byAnchorPlans,
               Map<String, SchemaPlan> byDynamicAnchorPlans,
               Map<String, SchemaPlan> byPathPlans,
               SchemaDialect dialect, Map<String, Boolean> vocabulary,
               Evaluator.AnnotationEvaluator annotationEvaluator) {
        this.resourceUri = resourceUri;
        this.annotationEvaluator = annotationEvaluator;
        this.keywordPs = keywordPs;
        this.evaluators = evaluators;
        this.schema = Asserts.notNull(schema, "schema");
        this.booleanSchema = schema instanceof BooleanSchema;
        this.booleanValue = booleanSchema && ((BooleanSchema) schema).booleanValue();
        this.dynamicAnchor = dynamicAnchor;
        this.byAnchorPlans = byAnchorPlans;
        this.byDynamicAnchorPlans = byDynamicAnchorPlans;
        this.byPathPlans = byPathPlans;
        this.dialect = dialect;
        this.vocabulary = vocabulary;
    }

    static SchemaPlan of(URI resourceUri, PathSegment keywordPs, BooleanSchema schema) {
        return new SchemaPlan(resourceUri, keywordPs, new Evaluator[0], schema,
                null, null, null, null, null, null, null);
    }

    static SchemaPlan of(URI resourceUri, PathSegment keywordPs, List<Evaluator> evaluators, String dynamicAnchor,
                         Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                         Map<String, SchemaPlan> byPathPlans,
                         ObjectSchema schema, SchemaDialect dialect,
                         Map<String, Boolean> vocabulary) {
        return of(resourceUri, keywordPs, evaluators, dynamicAnchor, byAnchorPlans,
                byDynamicAnchorPlans, byPathPlans, schema, dialect, vocabulary, null);
    }

    static SchemaPlan of(URI resourceUri, PathSegment keywordPs, List<Evaluator> evaluators, String dynamicAnchor,
                         Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                         Map<String, SchemaPlan> byPathPlans,
                         ObjectSchema schema, SchemaDialect dialect, Map<String, Boolean> vocabulary,
                         Evaluator.AnnotationEvaluator annotationEvaluator) {
        return new SchemaPlan(resourceUri, keywordPs, evaluators.toArray(new Evaluator[0]), schema,
                dynamicAnchor, byAnchorPlans, byDynamicAnchorPlans, byPathPlans,
                dialect, vocabulary, annotationEvaluator);
    }

}
