package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.util.ArrayList;
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

        ValidationContext ctx = new ValidationContext(failFast, strictFormat, false);
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
     * validity; diagnostic messages accumulate in the context across calls.
     * Options are fixed when the context is constructed, and it must not be
     * accessed concurrently or recursively.
     */
    public boolean evaluate(Object node, ValidationContext ctx) {
        Asserts.notNull(ctx, "ctx");
        int annotationMark = ctx.annotationMark();
        try {
            if (booleanSchema) {
                if (!booleanValue) {
                    ctx.addError(null, PathSegment.Root.INSTANCE, keywordPs,
                            schemaUri, "false", "schema 'false' always fails");
                }
                return booleanValue;
            }
            Instance instance = Instance.infer(node);
            PathSegment ps = ctx.isFailFast() ? null : PathSegment.Root.INSTANCE;
            return evaluate(instance, ps, ctx);
        } catch (RuntimeException | Error ex) {
            ctx.rollbackAnnotations(annotationMark);
            throw ex;
        } finally {
            ctx.reset();
        }
    }

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

        int annotationMark = ctx.annotationMark();
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
                        ctx.rollbackAnnotations(annotationMark);
                        return false;
                    }
                }
            }
        } finally {
            if (hasDynamicAnchors) ctx.popPlan();
        }
        if (!result) {
            ctx.rollbackAnnotations(annotationMark);
        } else if (annotationSpecs != null && !ctx.isBooleanOnly()) {
            for (AnnotationSpec spec : annotationSpecs) {
                ctx.addAnnotation(instance, ps, spec.keywordPs, schemaUri, spec.keyword, spec.value);
            }
        }
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
    private final AnnotationSpec[] annotationSpecs;

    // Metadata keywords producing original-value annotations. Additional
    // keyword families (such as unevaluated and content vocabularies) may have
    // computed annotation values and are not represented by this fixed list.
    private static final String[] ANNOTATION_KEYWORDS = {
            "title", "description", "default", "examples",
            "deprecated", "readOnly", "writeOnly", "format",
            "contentEncoding", "contentMediaType", "contentSchema"
    };

    private static final class AnnotationSpec {
        final String keyword;
        final PathSegment keywordPs;
        final Object value;

        AnnotationSpec(String keyword, PathSegment keywordPs, Object value) {
            this.keyword = keyword;
            this.keywordPs = keywordPs;
            this.value = value;
        }
    }

    private static AnnotationSpec[] annotationSpecs(ObjectSchema schema, PathSegment ps,
                                                    SchemaDialect dialect, Map<String, Boolean> vocabulary) {
        if (schema == null || (dialect == SchemaDialect.DRAFT_07 && schema.getString("$ref") != null))
            return null;
        List<AnnotationSpec> specs = null;
        for (String key : ANNOTATION_KEYWORDS) {
            if (!schema.containsKey(key) || !dialect.supportsKeyword(key)) continue;
            if (vocabulary != null) {
                String[] vocabUris = VocabularyRegistry.getVocabUris(key);
                boolean allowed = vocabUris == null;
                if (vocabUris != null) {
                    for (String uri : vocabUris) {
                        if (vocabulary.containsKey(uri)) {
                            allowed = true;
                            break;
                        }
                    }
                }
                if ("format".equals(key) &&
                        vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT_ASSERTION)) {
                    allowed = true;
                }
                if (!allowed) continue;
            }
            if (specs == null) specs = new ArrayList<>();
            specs.add(new AnnotationSpec(key, new PathSegment.Name(ps, key), schema.getNode(key)));
        }
        return specs == null ? null : specs.toArray(new AnnotationSpec[0]);
    }

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
        this.annotationSpecs = annotationSpecs(schema, keywordPs, dialect, vocabulary);
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
