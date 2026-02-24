package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

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

    public ValidationOptions getOptions() {return this.options;}
    public ValidationResult toResult() {
        return new ValidationResult(valid, messages, lastMessage);
    }
    public boolean isValid() {
        return valid;
    }
    public boolean shouldAbort() {
        return options.isFailFast() && !valid;
    }

    // Ignore
    public void pushIgnoreError() {ignoreErrorAdding++;}
    public void popIgnoreError() {ignoreErrorAdding--;}
//    public void pushIgnoreEvaluated() {ignoreEvaluatedTracking++;}
//    public void popIgnoreEvaluated() {ignoreEvaluatedTracking--;}
//    public boolean ignoreEvaluated() {
//        return ignoreEvaluatedTracking > 0;
//    }

    // anchor
    ObjectSchema getSchemaByAnchor(URI uri, String anchor) {
        ObjectSchema found = targetSchema.getSchemaByAnchor(uri, anchor);
        if (found == null && !anchor.isEmpty()) found = targetSchema.getSchemaByDynamicAnchor(anchor);
        return found;
    }
    JsonSchema getSchemaByPath(URI uri, JsonPointer path) {
        return targetSchema.getSchemaByPath(uri, path);
    }

    // dynamicAnchor
    boolean pushIdSchema(ObjectSchema schema) {
        if (idSchemaStack.peek() != schema) {
            idSchemaStack.push(schema);
            return true;
        }
        return false;
    }
    ObjectSchema popIdSchema() {
        return idSchemaStack.pop();
    }
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
    void addWarn(PathSegment ps, String keyword, String message) {
        if (messages != null) {
            ValidationMessage msg = new ValidationMessage(ValidationMessage.Severity.WARN, ps, keyword, message);
            messages.add(msg);
        }
    }

}
