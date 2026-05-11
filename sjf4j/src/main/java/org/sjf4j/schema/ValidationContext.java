package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

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
    private final ValidationOptions options;
    private final List<ValidationMessage> messages;
    private boolean valid = true;
    private ValidationMessage lastMessage;
    private int ignoreErrorAdding = 0;

    final Deque<SchemaPlan> planStack = new ArrayDeque<>();

    ValidationContext(ValidationOptions options) {
        this.options = options;
        this.messages = options.isFailFast() ? null : new ArrayList<>();
    }

    /**
     * Returns validation options.
     */
    public ValidationOptions getOptions() {return this.options;}
    /**
     * Builds a result snapshot from current context state.
     */
    public ValidationResult toResult() {
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
        return options.isFailFast() && !valid;
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


    // message
    /**
     * Adds a validation error message.
     * <p>
     * In fail-fast mode only the last error is retained.
     */
    void addError(InstancedNode instance, PathSegment instancePs, PathSegment keywordPs,
                  String keyword, String message) {
        if (ignoreErrorAdding < 1) {
            if (instancePs == null && instance != null) {
                instancePs = instance.materializePath();
            }
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR,
                    instancePs, keywordPs, keyword, message);
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
    void addWarn(PathSegment instancePs, PathSegment keywordPs, String keyword, String message) {
        if (messages != null) {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.WARN,
                    instancePs, keywordPs, keyword, message);
            messages.add(msg);
        }
    }

}
