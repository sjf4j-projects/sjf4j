package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Mutable validation state shared across evaluator invocations.
 * <p>
 * Holds message aggregation, fail-fast control, temporary ignore-error scopes,
 * and dynamic-anchor resolution stack for nested schema evaluation. Configuration
 * is fixed at construction; diagnostics accumulate across sequential calls to
 * {@link SchemaPlan#evaluate(Object, ValidationContext)}. It is not thread-safe
 * or reentrant. Evaluator reads can invoke getters, value bindings, and user
 * extensions with side effects.
 */
public class ValidationContext {
    private final boolean failFast;
    private final boolean strictFormat;
    private final boolean booleanOnly;
    // Created on the first diagnostic, then retained across evaluations.
    private List<ValidationMessage> messages;
    // Validation-scoped scratch wrapper reused only for non-converted scalar/null
    // child instances. Container nodes and converted value nodes must allocate
    // dedicated wrappers because they can carry branch-local runtime state.
    private final Instance reusedLeaf;

    private int ignoreErrorAdding = 0;
    private Deque<SchemaPlan> planStack;

    /**
     * Creates a reusable context with immutable execution options.
     *
     * @param failFast stop each evaluation after its first failure
     * @param strictFormat enforce format assertions
     * @param booleanOnly omit diagnostic messages and related work
     */
    public ValidationContext(boolean failFast, boolean strictFormat, boolean booleanOnly) {
        this.failFast = failFast;
        this.strictFormat = strictFormat;
        this.booleanOnly = booleanOnly;
        this.reusedLeaf = Instance.infer(null);
    }


    /**
     * Releases execution-scoped references after each evaluation. Diagnostics
     * deliberately survive until the caller clears them.
     */
    void reset() {
        ignoreErrorAdding = 0;
        if (planStack != null) planStack.clear();
        reusedLeaf.clear();
    }

    /**
     * Removes accumulated diagnostics before starting another batch. Previously
     * created ValidationResults backed by this context observe the cleared list.
     */
    public void clearMessages() {
        if (messages != null) messages.clear();
    }

    boolean needsMessage() {
        return !booleanOnly && ignoreErrorAdding == 0;
    }

    /** Returns whether this context skips diagnostic collection entirely. */
    public boolean isBooleanOnly() {
        return booleanOnly;
    }

    /**
     * Returns true when validation stops at first non-ignored error.
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Returns true when format validators should be enforced as assertions.
     */
    public boolean isStrictFormat() {
        return strictFormat;
    }

    /**
     * Builds a result for the entire evaluated batch. Pass the conjunction of
     * each evaluate() return value as {@code valid}. The returned result shares
     * this context's message list; later evaluations and clearMessages() affect
     * its diagnostics. Create a fresh context for an independent result.
     */
    public ValidationResult toResult(boolean valid) {
        if (valid && (messages == null || messages.isEmpty())) {
            return ValidationResult.SUCCESS;
        }
        return new ValidationResult(valid, messages, null);
    }

    // Ignore
    /**
     * Pushes an error-ignore frame.
     * <p>
     * Errors added while ignore-depth is positive are suppressed. Calls must be
     * balanced with {@link #popIgnoreError()}.
     */
    public void pushIgnoreError() {
        ignoreErrorAdding++;
    }
    /**
     * Pops an error-ignore frame started by {@link #pushIgnoreError()}.
     */
    public void popIgnoreError() {
        ignoreErrorAdding--;
    }

    // PlanStack
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

    // message
    /**
     * Adds a validation error message.
     * <p>
     * Fail-fast applies to each evaluation; messages from earlier evaluations
     * remain in the same list until clearMessages() is called.
     */
    void addError(Instance instance, PathSegment instancePs, PathSegment keywordPs, URI schemaUri,
                  String keyword, String message) {
        if (ignoreErrorAdding < 1) {
            if (booleanOnly) return;
            if (instancePs == null && instance != null) {
                instancePs = instance.materializePath();
            }
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                        instancePs, keywordPs, schemaUri, keyword, message);
            if (messages == null) messages = new ArrayList<>();
            messages.add(msg);
        }
    }

    /**
     * Adds a validation warning message.
     * <p>
     * Warnings are collected whenever diagnostics are enabled, regardless of
     * the fail-fast setting.
     */
    void addWarn(PathSegment instancePs, PathSegment keywordPs, URI schemaUri, String keyword, String message) {
        if (!booleanOnly) {
            if (messages == null) messages = new ArrayList<>();
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.WARN,
                    instancePs, keywordPs, schemaUri, keyword, message);
            messages.add(msg);
        }
    }

}
