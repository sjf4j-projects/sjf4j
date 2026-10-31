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
 * and dynamic-anchor resolution stack for nested schema evaluation. A context
 * can be reused sequentially across SchemaPlan calls; it is not thread-safe
 * and cannot be used by concurrent or nested calls. It records
 * validation failures; it does not itself write the supplied instance. Evaluator
 * reads can invoke getters, value bindings, and user extensions with side effects.
 */
public class ValidationContext {
    private boolean failFast;
    private boolean strictFormat;
    private boolean booleanOnly;
    private List<ValidationMessage> messages;
    // Validation-scoped scratch wrapper reused only for non-converted scalar/null
    // child instances. Container nodes and converted value nodes must allocate
    // dedicated wrappers because they can carry branch-local runtime state.
    private final Instance reusedLeaf;

    private ValidationMessage lastMessage;
    private int ignoreErrorAdding = 0;
    private Deque<SchemaPlan> planStack;

    /** Creates an execution context that can be reused sequentially. */
    public ValidationContext() {
        this.reusedLeaf = Instance.infer(null);
    }

    /** Prepares one call, including its fail-fast and format behavior. */
    void reset(boolean failFast, boolean strictFormat, boolean booleanOnly) {
        this.failFast = failFast;
        this.strictFormat = strictFormat;
        this.booleanOnly = booleanOnly;
        // Give each result its own list; never copy or mutate an earlier result.
        this.messages = failFast ? null : new ArrayList<>();
        this.lastMessage = null;
        this.ignoreErrorAdding = 0;
        if (planStack != null) planStack.clear();
    }

    /** Releases references after evaluation, including on exceptional exits. */
    void clear() {
        reusedLeaf.clear();
        if (planStack != null) planStack.clear();
        messages = null;
        lastMessage = null;
        ignoreErrorAdding = 0;
    }

    boolean needsMessage() {
        return !booleanOnly && ignoreErrorAdding == 0;
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
     * Builds a result snapshot from current context state.
     */
    public ValidationResult toResult(boolean valid) {
        if (valid && (messages == null || messages.isEmpty()) && lastMessage == null) {
            return ValidationResult.SUCCESS;
        }
        return new ValidationResult(valid, messages, lastMessage);
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
     * In fail-fast mode only the last error is retained.
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
            if (messages != null) {
                messages.add(msg);
            } else {
                lastMessage = msg;
            }
        }
    }

    /**
     * Adds a validation warning message.
     * <p>
     * Warnings are collected only when message list is enabled (non fail-fast).
     */
    void addWarn(PathSegment instancePs, PathSegment keywordPs, URI schemaUri, String keyword, String message) {
        if (messages != null) {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.WARN,
                    instancePs, keywordPs, schemaUri, keyword, message);
            messages.add(msg);
        }
    }

}
