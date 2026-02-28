package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Mutable validation state shared across evaluator invocations.
 * <p>
 * Holds message aggregation, fail-fast control, temporary ignore-error scopes,
 * and dynamic-anchor resolution stack for nested schema evaluation.
 */
public class ValidationContext {
    private final ObjectSchema targetSchema;
    private final ValidationOptions options;

    private boolean valid = true;
    private final List<ValidationMessage> messages;
    private ValidationMessage lastMessage;

    private int ignoreErrorAdding = 0;
    private final Deque<ObjectSchema> idSchemaStack = new ArrayDeque<>();

    ValidationContext(ObjectSchema targetSchema, ValidationOptions options) {
        this.targetSchema = targetSchema;
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
     * Errors added while ignore-depth is positive are suppressed.
     */
    public void pushIgnoreError() {ignoreErrorAdding++;}
    /**
     * Pops an error-ignore frame.
     */
    public void popIgnoreError() {ignoreErrorAdding--;}
//    public void pushIgnoreEvaluated() {ignoreEvaluatedTracking++;}
//    public void popIgnoreEvaluated() {ignoreEvaluatedTracking--;}
//    public boolean ignoreEvaluated() {
//        return ignoreEvaluatedTracking > 0;
//    }

    // anchor
    /**
     * Resolves a schema by anchor or dynamic anchor.
     */
    ObjectSchema getSchemaByAnchor(URI uri, String anchor) {
        ObjectSchema found = targetSchema.getSchemaByAnchor(uri, anchor);
        if (found == null && !anchor.isEmpty()) found = targetSchema.getSchemaByDynamicAnchor(anchor);
        return found;
    }
    /**
     * Resolves a schema by URI and JSON Pointer path.
     */
    JsonSchema getSchemaByPath(URI uri, JsonPointer path) {
        return targetSchema.getSchemaByPath(uri, path);
    }

    // dynamicAnchor
    /**
     * Pushes a schema scope used for dynamicAnchor resolution.
     */
    boolean pushIdSchema(ObjectSchema schema) {
        if (idSchemaStack.peek() != schema) {
            idSchemaStack.push(schema);
            return true;
        }
        return false;
    }
    /**
     * Pops the current schema scope.
     */
    ObjectSchema popIdSchema() {
        return idSchemaStack.pop();
    }
    /**
     * Resolves a schema by dynamic anchor with scope fallback.
     * <p>
     * Resolution starts from referenced resource, then walks current dynamic
     * scope stack from nearest to farthest.
     */
    ObjectSchema getSchemaByDynamicAnchor(URI uri, String dynamicAnchor) {
        ObjectSchema schema = targetSchema.getSchemaByDynamicAnchor(uri, dynamicAnchor);
        if (schema != null) {
            Iterator<ObjectSchema> it = idSchemaStack.descendingIterator();
            while (it.hasNext()) {
                ObjectSchema found = it.next().getSchemaByDynamicAnchor(dynamicAnchor);
                if (found != null) return found;
            }
        }
        return schema;
    }

    // message
    /**
     * Adds a validation error message.
     * <p>
     * In fail-fast mode only the last error is retained.
     */
    void addError(PathSegment ps, String keyword, String message) {
        if (ignoreErrorAdding < 1) {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.ERROR, ps, keyword, message);
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
    void addWarn(PathSegment ps, String keyword, String message) {
        if (messages != null) {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.WARN, ps, keyword, message);
            messages.add(msg);
        }
    }

}
