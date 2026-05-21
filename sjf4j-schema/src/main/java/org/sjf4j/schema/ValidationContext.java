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
 * and dynamic-anchor resolution stack for nested schema evaluation. One context
 * instance is created per validation call and is not thread-safe.
 */
public class ValidationContext {
    private final boolean failFast;
    private final boolean strictFormat;
    private final List<ValidationMessage> messages;
    // Validation-scoped scratch wrapper reused only for non-converted scalar/null
    // child instances. Container nodes and converted value nodes must allocate
    // dedicated wrappers because they can carry branch-local runtime state.
    private final InstancedNode reusedLeaf;

    private boolean valid = true;
    private ValidationMessage lastMessage;
    private int ignoreErrorAdding = 0;
    private Deque<SchemaPlan> planStack;

    ValidationContext(boolean failFast, boolean strictFormat) {
        this.failFast = failFast;
        this.strictFormat = strictFormat;
        this.messages = failFast ? null : new ArrayList<>();
        this.reusedLeaf = InstancedNode.infer(null);
    }

    /**
     * Returns true when validation stops at first non-ignored error.
     */
    public boolean isFailFast() {return failFast;}

    /**
     * Returns true when format validators should be enforced as assertions.
     */
    public boolean isStrictFormat() {return strictFormat;}

    /**
     * Builds a result snapshot from current context state.
     */
    public ValidationResult toResult() {
        if (valid && (messages == null || messages.isEmpty()) && lastMessage == null) {
            return ValidationResult.SUCCESS;
        }
        return new ValidationResult(valid, messages, lastMessage);
    }

    /**
     * Returns true when no validation error was added.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns true when validation should abort early.
     * <p>
     * This is true only in fail-fast mode after the first non-ignored error.
     */
    public boolean shouldAbort() {
        return failFast && !valid;
    }

    // Ignore
    /**
     * Pushes an error-ignore frame.
     * <p>
     * Errors added while ignore-depth is positive are suppressed. Calls must be
     * balanced with {@link #popIgnoreError()}.
     */
    public void pushIgnoreError() {ignoreErrorAdding++;}
    /**
     * Pops an error-ignore frame started by {@link #pushIgnoreError()}.
     */
    public void popIgnoreError() {ignoreErrorAdding--;}

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

    public InstancedNode reusedLeaf() {
        return reusedLeaf;
    }

    // message
    /**
     * Adds a validation error message.
     * <p>
     * In fail-fast mode only the last error is retained.
     */
    void addError(InstancedNode instance, PathSegment instancePs, PathSegment keywordPs, URI schemaUri,
                  String keyword, String message) {
        if (ignoreErrorAdding < 1) {
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
            valid = false;
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
