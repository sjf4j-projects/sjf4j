package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class ValidationContext {
    private boolean valid = true;
    private final ValidationOptions options;
    private final List<ValidationMessage> messages;
    private final ObjectSchema rootSchema;

    private int ignoreErrorAdding = 0;
    private final Deque<ObjectSchema> idSchemaStack = new ArrayDeque<>();

    ValidationContext(ObjectSchema rootSchema, ValidationOptions options) {
        this.rootSchema = rootSchema;
        this.options = options;
        this.messages = new ArrayList<>();
    }

    public ValidationOptions getOptions() {return this.options;}
    public ValidationResult toResult() {
        return new ValidationResult(valid, messages);
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
        ObjectSchema found = rootSchema.getSchemaByAnchor(uri, anchor);
        if (found == null && !anchor.isEmpty()) found = rootSchema.getSchemaByDynamicAnchor(anchor);
        return found;
    }
    Object getSchemaByPath(URI uri, JsonPointer path) {
        return rootSchema.getSchemaByPath(uri, path);
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
        ObjectSchema schema = rootSchema.getSchemaByDynamicAnchor(uri, dynamicAnchor);
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
    void addError(String path, String keyword, String message) {
        if (ignoreErrorAdding < 1) {
            valid = false;
            addMessage(ValidationMessage.Severity.ERROR, path, keyword, message);
        } else {
            addMessage(ValidationMessage.Severity.WARN, path, keyword, message);
        }
    }
    void addWarn(String path, String keyword, String message) {
        addMessage(ValidationMessage.Severity.WARN, path, keyword, message);
    }
    void addInfo(String path, String keyword, String message) {
        addMessage(ValidationMessage.Severity.INFO, path, keyword, message);
    }
    void addDebug(String path, String keyword, String message) {
        addMessage(ValidationMessage.Severity.DEBUG, path, keyword, message);
    }
    private void addMessage(ValidationMessage.Severity severity, String path, String keyword, String message) {
        ValidationMessage error = new ValidationMessage(severity, path, keyword, message);
        messages.add(error);
    }


}
