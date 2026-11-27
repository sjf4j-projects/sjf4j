package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;
import org.sjf4j.util.Asserts;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * State for a single validation with immutable execution options.
 * A context is not reusable, thread-safe or reentrant.
 */
public class ValidationContext {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean booleanOnly;
    private final boolean collectAnnotations;
    private final long maxEvaluations;
    private final int maxEvaluationDepth;
    private final int maxErrors;
    private long evaluations;
    private int evaluationDepth;
    private ValidationException abortFailure;
    private List<ValidationResult.Detail> errors;
    private List<ValidationResult.Detail> annotations;

    // Reused for non-converted scalar children only.
    private final Instance reusedLeaf;

    private int ignoreErrorAdding;
    private Deque<SchemaPlan> planStack;

    /**
     * @param failFast short-circuit subsequent checks when an assertion fails
     * @param strictFormat enforce format assertions
     * @param booleanOnly skip collecting errors and annotations
     */
    public ValidationContext(boolean failFast, boolean strictFormat, boolean booleanOnly) {
        this.failFast = failFast;
        this.strictFormat = strictFormat;
        this.booleanOnly = booleanOnly;
        this.collectAnnotations = false;
        this.maxEvaluations = 0;
        this.maxEvaluationDepth = 0;
        this.maxErrors = 0;
        this.reusedLeaf = Instance.infer(null);
    }

    /** Creates a context with the requested runtime validation options. */
    public ValidationContext(ValidationOptions options, boolean booleanOnly) {
        Asserts.notNull(options, "options");
        this.failFast = options.isFailFast();
        this.strictFormat = options.isStrictFormat();
        this.booleanOnly = booleanOnly;
        this.collectAnnotations = options.isCollectAnnotations() && !booleanOnly;
        this.maxEvaluations = options.getMaxEvaluations();
        this.maxEvaluationDepth = options.getMaxEvaluationDepth();
        this.maxErrors = options.getMaxErrors();
        this.reusedLeaf = Instance.infer(null);
    }

    public boolean isCollectAnnotations() {
        return collectAnnotations;
    }

    void countEvaluation(SchemaPlan plan, Instance instance, PathSegment instancePs) {
        if (maxEvaluations == 0) return;
        if (evaluations >= maxEvaluations) {
            throw _limitExceeded("maxEvaluations", maxEvaluations, plan, instance, instancePs);
        }
        evaluations++;
    }

    void enterEvaluation(SchemaPlan plan, Instance instance, PathSegment instancePs) {
        if (maxEvaluationDepth == 0) return;
        if (evaluationDepth >= maxEvaluationDepth) {
            throw _limitExceeded("maxEvaluationDepth", maxEvaluationDepth, plan, instance, instancePs);
        }
        evaluationDepth++;
    }

    private ValidationException _limitExceeded(String limit, long max, SchemaPlan plan,
                                               Instance instance, PathSegment instancePs) {
        // Execution limits are terminal even inside suppressed probe branches.
        if (!booleanOnly) {
            if (instancePs == null) instancePs = instance.materializePath();
            ValidationResult.Detail error = new ValidationResult.Detail(limit,
                    "validation aborted: " + limit + "=" + max + " exceeded",
                    null, instancePs, plan.keywordPs, plan.resourceUri);
            if (errors == null) errors = new ArrayList<>();
            if (failFast && !errors.isEmpty()) {
                errors.set(errors.size() - 1, error);
            } else {
                errors.add(error);
            }
        }
        return _abort();
    }

    private ValidationException _abort() {
        abortFailure = new ValidationException(toResult(false));
        return abortFailure;
    }

    /** Identifies this context's terminal failure without swallowing user exceptions. */
    boolean isAbortFailure(ValidationException failure) {
        return failure == abortFailure;
    }

    void leaveEvaluation() {
        if (maxEvaluationDepth != 0) evaluationDepth--;
    }

    /** Accesses the current annotation collection without copying it. */
    public List<ValidationResult.Detail> getAnnotations() {
        return annotations == null ? Collections.emptyList() : annotations;
    }

    /** Appended annotations after this index belong to the current schema evaluation. */
    int annotationMark() {
        return annotations == null ? 0 : annotations.size();
    }

    /** Discard annotations produced by an unsuccessful or ignored evaluation. */
    void rollbackAnnotations(int mark) {
        if (annotations != null && annotations.size() > mark) {
            annotations.subList(mark, annotations.size()).clear();
        }
    }

    /** Collect an annotation only when explicitly requested. */
    void addAnnotation(Instance instance, PathSegment instancePs,
                       PathSegment keywordPs, URI schemaUri, String keyword, String message, Object annotation) {
        if (!collectAnnotations) return;
        if (instancePs == null) instancePs = instance.materializePath();
        if (annotations == null) annotations = new ArrayList<>();
        annotations.add(new ValidationResult.Detail(keyword, message, annotation, instancePs, keywordPs, schemaUri));
    }

    boolean needsErrorDetail() {
        return !booleanOnly && ignoreErrorAdding == 0 && (!failFast || errors == null);
    }

    public boolean isBooleanOnly() {
        return booleanOnly;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public boolean isStrictFormat() {
        return strictFormat;
    }

    /** Returns a result using the diagnostics collected by this validation without copying. */
    public ValidationResult toResult(boolean valid) {
        if (valid && (errors == null || errors.isEmpty())
                && (annotations == null || annotations.isEmpty())) {
            return ValidationResult.SUCCESS;
        }
        return new ValidationResult(valid, errors, annotations);
    }

    /** Suppresses assertion errors while evaluating a conditional/probe branch. */
    public void pushIgnoreError() {
        ignoreErrorAdding++;
    }

    public void popIgnoreError() {
        ignoreErrorAdding--;
    }

    public void pushPlan(SchemaPlan plan) {
        if (planStack == null) planStack = new ArrayDeque<>();
        planStack.push(plan);
    }

    public SchemaPlan popPlan() {
        if (planStack == null) return null;
        return planStack.pop();
    }

    public Deque<SchemaPlan> planStack() {
        return planStack;
    }

    public Instance reusedLeaf() {
        return reusedLeaf;
    }

    /** Accumulates an assertion error, unless errors are being suppressed. */
    void addError(Instance instance, PathSegment instancePs, PathSegment keywordPs,
                  URI schemaUri, String keyword, String message) {
        if (!needsErrorDetail()) return;
        if (instancePs == null && instance != null) instancePs = instance.materializePath();
        if (errors == null) errors = new ArrayList<>();
        errors.add(new ValidationResult.Detail(keyword, message, null, instancePs, keywordPs, schemaUri));
        if (maxErrors != 0 && errors.size() >= maxErrors) throw _abort();
    }
}
