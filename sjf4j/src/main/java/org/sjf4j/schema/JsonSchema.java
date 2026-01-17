package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.path.JsonPointer;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class JsonSchema extends JsonObject {

//    private String $id;
//    private String $dynamicAnchor;
//    private Map<String, Boolean> $vocabulary;

    private transient URI uri;
    private transient Evaluator[] evaluators;
    private transient SchemaStore schemaStore;
    private transient Map<String, JsonSchema> anchors;

    public JsonSchema() {
        super();
    }

    public JsonSchema(Object node) {
        super(node);
    }

    public static JsonSchema fromJson(String json) {
        return Sjf4j.fromJson(json, JsonSchema.class);
    }

    // uri
    public URI getUri() {
        if (uri == null) {
            compileUri();
        }
        return uri;
    }
    public void compileUri() {
        if (uri == null) {
            String id = getId();
            uri = URI.create(id);
            if (uri.getFragment() != null)
                throw new SchemaException("Invalid schema $id '" + id + "': should not have a fragment '#'");
        }
    }

    // Getter / Setter
    public String getId() {return getString("$id", "");}
    public String getDynamicAnchor() {return getString("$dynamicAnchor");}
    public Map<String, Boolean> getVocabulary() {return asMap("$vocabulary", Boolean.class);}

    // schemaStore
    public void setSchemaStore(SchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }
    public SchemaStore getSchemaStore() {
        return schemaStore;
    }

    // anchor
    public void putAnchor(String anchor, JsonSchema schema) {
        Objects.requireNonNull(anchor, "anchor is null");
        Objects.requireNonNull(schema, "schema is null");
        if (anchors == null) anchors = new HashMap<String, JsonSchema>();
        anchors.put(anchor, schema);
    }
    public Map<String, JsonSchema> getAnchors() {
        return anchors;
    }
    public JsonSchema getSchemaByAnchor(String anchor) {
        Objects.requireNonNull(anchor, "anchor is null");
        if (anchor.isEmpty()) return this;
        if (anchors != null) return anchors.get(anchor);
        return null;
    }
    public Object getSchemaByPath(JsonPointer path) {
        Objects.requireNonNull(path, "path is null");
        return path.getNode(this);
    }

    // compile
    public void compileOrThrow() {
        JsonPointer path = new JsonPointer();
        compile(path, this);
    }

    void compile(JsonPointer path, JsonSchema rootSchema) {
        if (evaluators == null) {
            compileUri();
            evaluators = SchemaUtil.compile(this, path, rootSchema);
        }
    }

    public boolean isCompiled() {
        return evaluators != null;
    }

    // validate
    public ValidationResult validate(Object node) {
        InstancedNode instance = InstancedNode.infer(node);
        ValidationContext ctx = new ValidationContext(false, this, schemaStore);
        validate(instance, new JsonPointer(), ctx);
        return ctx.toResult();
    }

    public ValidationResult validateFailFast(Object node) {
        InstancedNode instance = InstancedNode.infer(node);
        ValidationContext ctx = new ValidationContext(true, this, schemaStore);
        validate(instance, new JsonPointer(), ctx);
        return ctx.toResult();
    }

    public boolean isValid(Object node) {
        InstancedNode instance = InstancedNode.infer(node);
        ValidationContext ctx = new ValidationContext(true, this, schemaStore);
        return validate(instance, new JsonPointer(), ctx);
    }

    public void validateOrThrow(Object node) {
        ValidationResult result = validateFailFast(node);
        if (!result.isValid())
            throw new ValidationException(result);
    }

    boolean validate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
        if (evaluators == null)
            throw new SchemaException("JsonSchema has not been compiled.");
        boolean result = true;
        String dynamicAnchor = getDynamicAnchor();
        if (dynamicAnchor != null) ctx.enterDynamicAnchor(dynamicAnchor, this);
        for (Evaluator evaluator : evaluators) {
            result = result && evaluator.evaluate(instance, path, ctx);
            if (ctx.shouldAbort()) return result;
        }
        if (dynamicAnchor != null) ctx.exitDynamicAnchor(dynamicAnchor);
        return result;
    }

}
