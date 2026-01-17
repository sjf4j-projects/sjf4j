package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ValidationContext {
    private boolean valid = true;
    private final boolean probe;
    private final boolean failFast;
    private final List<ValidationMessage> messages;
    private final JsonSchema rootSchema;
    private final SchemaStore schemaStore;
    private Map<String, Deque<JsonSchema>> dynamicAnchors;

//    public ValidationContext() {
//        this(false);
//    }
//    public ValidationContext(SchemaStore schemaStore) {
//        this(false, schemaStore);
//    }
//    public ValidationContext(boolean failFast) {
//        this(false, failFast, new ArrayList<>(), null, null);
//    }
    public ValidationContext(boolean failFast, JsonSchema rootSchema, SchemaStore schemaStore) {
        this(false, failFast, new ArrayList<>(), rootSchema, schemaStore);
    }
    private ValidationContext(boolean probe, boolean failFast, List<ValidationMessage> messages,
                              JsonSchema rootSchema, SchemaStore schemaStore) {
        this.probe = probe;
        this.failFast = failFast;
        this.messages = messages;
        this.rootSchema = rootSchema;
        this.schemaStore = schemaStore;
    }

    // Probe
    public ValidationContext createProbe() {
        return new ValidationContext(true, true, null, null, null);
    }

    public ValidationResult toResult() {
        return new ValidationResult(valid, messages);
    }
    public boolean isProbe() {
        return probe;
    }
    public boolean isFailFast() {
        return failFast;
    }
    public boolean isValid() {
        return valid;
    }
    public boolean shouldAbort() {
        return failFast && !valid;
    }

    // SchemaStore
    public SchemaStore getSchemaStore() {
        return schemaStore;
    }

    // anchor
    public JsonSchema getSchemaByAnchor(URI uri, String anchor) {
        Objects.requireNonNull(anchor);
        if (uri == null || rootSchema.getUri().equals(uri)) {
            return rootSchema.getSchemaByAnchor(anchor);
        }
        if (schemaStore != null) {
            JsonSchema schema = schemaStore.getSchema(uri);
            if (schema != null) {
                return schema.getSchemaByAnchor(anchor);
            }
        }
        return null;
    }
    public Object getSchemaByPath(URI uri, JsonPointer path) {
        Objects.requireNonNull(path);
        if (rootSchema.getUri().equals(uri)) {
            return rootSchema.getSchemaByPath(path);
        }
        if (schemaStore != null) {
            JsonSchema schema = schemaStore.getSchema(uri);
            if (schema != null) {
                return schema.getSchemaByPath(path);
            }
        }
        return null;
    }

    // dynamicAnchor
    public void enterDynamicAnchor(String dynamicAnchor, JsonSchema schema) {
        if (probe) return;
        if (dynamicAnchors == null) dynamicAnchors = new HashMap<>();
        Deque<JsonSchema> anchorStack = dynamicAnchors.computeIfAbsent(dynamicAnchor, k -> new ArrayDeque<>());
        anchorStack.push(schema);
    }
    public void exitDynamicAnchor(String dynamicAnchor) {
        if (probe) return;
        if (dynamicAnchors == null) return;
        Deque<JsonSchema> anchorStack = dynamicAnchors.get(dynamicAnchor);
        if (anchorStack == null) return;
        anchorStack.pop();
    }
    public JsonSchema getSchemaByDynamicAnchor(String dynamicAnchor) {
        if (dynamicAnchors != null) {
            Deque<JsonSchema> anchorStack = dynamicAnchors.get(dynamicAnchor);
            if (anchorStack != null) {
                return anchorStack.peek();
            }
        }
        return null;
    }

    // Message
    public List<ValidationMessage> getMessages() {
        return messages;
    }
    public void addError(String path, String keyword, String message) {
        valid = false;
        addMessage(ValidationMessage.Severity.ERROR, path, keyword, message);
    }
    public void addWarn(String path, String keyword, String message) {
        addMessage(ValidationMessage.Severity.WARN, path, keyword, message);
    }
    public void addInfo(String path, String keyword, String message) {
        addMessage(ValidationMessage.Severity.INFO, path, keyword, message);
    }
    public void addDebug(String path, String keyword, String message) {
        addMessage(ValidationMessage.Severity.DEBUG, path, keyword, message);
    }
    public void addMessage(ValidationMessage.Severity severity, String path, String keyword, String message) {
        if (messages != null) {
            ValidationMessage error = new ValidationMessage(severity, path, keyword, message);
            messages.add(error);
        }
    }


}
