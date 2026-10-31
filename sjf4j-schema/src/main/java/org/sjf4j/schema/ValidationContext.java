package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Reusable validation state with immutable execution options.
 *
 * <p>Errors and annotations accumulate across sequential evaluations until
 * explicitly cleared. A context is not thread-safe or reentrant.
 */
public class ValidationContext {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean booleanOnly;
    private List<ValidationResult.Message> errors;
    private List<ValidationResult.Message> annotations;

    // Reused for non-converted scalar children only.
    private final Instance reusedLeaf;

    private int ignoreErrorAdding;
    private boolean failFastErrorAdded;
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
        this.reusedLeaf = Instance.infer(null);
    }

    /** Releases per-evaluation state, preserving configuration and output. */
    void reset() {
        ignoreErrorAdding = 0;
        failFastErrorAdded = false;
        if (planStack != null) planStack.clear();
        reusedLeaf.clear();
    }

    /** Clears previously accumulated assertion errors. */
    public void clearErrors() {
        if (errors != null) errors.clear();
    }

    /** Clears previously accumulated annotations. */
    public void clearAnnotations() {
        if (annotations != null) annotations.clear();
    }

    /** Accesses the current annotation collection without copying it. */
    public List<ValidationResult.Message> getAnnotations() {
        return annotations == null ? Collections.emptyList() : annotations;
    }

    /**
     * Reserved for keyword evaluators that produce annotations. The current
     * schema pipeline does not yet emit annotations.
     */
    void addAnnotation(Instance instance, PathSegment instancePs,
                       PathSegment keywordPs, URI schemaUri, String keyword, String message, Object value) {
        if (booleanOnly) return;
        if (instancePs == null) instancePs = instance.materializePath();
        if (annotations == null) annotations = new ArrayList<>();
        annotations.add(new ValidationResult.Message(keyword, message, value, instancePs, keywordPs, schemaUri));
    }

    boolean needsErrorDetail() {
        return !booleanOnly && ignoreErrorAdding == 0 && (!failFast || !failFastErrorAdded);
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

    /**
     * Returns a result over the context's current accumulated errors and annotations.
     * Lists are live: clearing this context also affects previously obtained results.
     */
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
        if (ignoreErrorAdding != 0 || booleanOnly || (failFast && failFastErrorAdded)) return;
        if (instancePs == null && instance != null) instancePs = instance.materializePath();
        if (errors == null) errors = new ArrayList<>();
        errors.add(new ValidationResult.Message(keyword, message, null, instancePs, keywordPs, schemaUri));
        if (failFast) failFastErrorAdded = true;
    }
}
