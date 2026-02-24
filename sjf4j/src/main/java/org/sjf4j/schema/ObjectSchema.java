package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.Types;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public final class ObjectSchema extends JsonObject implements JsonSchema {

    private transient URI uri;
    private transient ObjectSchema idSchema;
    private transient Evaluator[] evaluators;
    private transient Map<URI, ObjectSchema> innerStore;
    private transient Map<String, ObjectSchema> anchors;
    private transient Map<String, ObjectSchema> dynamicAnchors;
    private transient Map<String, Boolean> allowedVocabulary;

    private transient SchemaStore outerStore;

    public ObjectSchema() {
        super();
    }
    public ObjectSchema(Object node) {
        super(node);
    }


    // uri
    public URI getUri() {
        return uri;
    }
    void setUri(URI uri) {this.uri = uri;}
    URI getResolvedUri() {
        if (uri == null) {
            return CompileUtil.resolveUri(getId(), null);
        }
        return uri;
    }

    // Getter / Setter
    String getId() {return getString("$id");}
    String getDynamicAnchor() {return getString("$dynamicAnchor");}
    Map<String, Boolean> getVocabulary() {return getMap("$vocabulary", Boolean.class);}

    // schemaStore
    public SchemaStore toStore() {
        if (innerStore == null) throw new SchemaException("Schema has not been compiled yet");
        SchemaStore store = new SchemaStore();
        for (Map.Entry<URI, ObjectSchema> entry : innerStore.entrySet()) {
            if (entry.getKey().isAbsolute()) store.register(entry.getKey(), entry.getValue());
        }
        return store;
    }

    // anchor
    void putAnchor(String anchor, ObjectSchema schema) {
        if (anchors == null) anchors = new HashMap<>();
        if (anchors.containsKey(anchor)) throw new SchemaException("Duplicate $anchor '" + anchor +
                "' in the same schema resource (implementation restriction)");
        anchors.put(anchor, schema);
    }
    ObjectSchema getSchemaByAnchor(String anchor) {
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (anchor.isEmpty()) return this;
            if (anchors != null) return anchors.get(anchor);
            return null;
        } else {
            return idSchema.getSchemaByAnchor(anchor);
        }
    }
    ObjectSchema getSchemaByAnchor(URI uri, String anchor) {
        Objects.requireNonNull(anchor);
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (uri == null || uri.toString().isEmpty()) {
                return getSchemaByAnchor(anchor);
            }
            if (innerStore != null) {
                ObjectSchema schema = innerStore.get(uri);
                if (schema != null) {
                    return schema.getSchemaByAnchor(anchor);
                }
            }
            return null;
        } else {
            return idSchema.getSchemaByAnchor(uri, anchor);
        }
    }
    JsonSchema getSchemaByPath(JsonPointer path) {
        Objects.requireNonNull(path, "path is null");
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            Object node = path.getNode(this);
            if (node instanceof JsonSchema) {
                return (JsonSchema) node;
            } else {
                throw new SchemaException("Invalid schema at '" + path + "': node type is " + Types.name(node));
            }
        } else {
            return idSchema.getSchemaByPath(path);
        }
    }
    JsonSchema getSchemaByPath(URI uri, JsonPointer path) {
        Objects.requireNonNull(path);
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (uri == null || uri.toString().isEmpty()) {
                return getSchemaByPath(path);
            }
            if (innerStore != null) {
                ObjectSchema schema = innerStore.get(uri);
                if (schema != null) {
                    return schema.getSchemaByPath(path);
                }
            }
            return null;
        } else {
            return idSchema.getSchemaByPath(uri, path);
        }
    }

    // dynamicAnchor
    void putDynamicAnchor(String dynamicAnchor, ObjectSchema schema) {
        if (dynamicAnchors == null) dynamicAnchors = new HashMap<>();
        dynamicAnchors.computeIfAbsent(dynamicAnchor, k -> schema);
    }
    ObjectSchema getSchemaByDynamicAnchor(String dynamicAnchor) {
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (dynamicAnchor.isEmpty()) return this;
            if (dynamicAnchors != null) return dynamicAnchors.get(dynamicAnchor);
            return null;
        } else {
            return idSchema.getSchemaByDynamicAnchor(dynamicAnchor);
        }
    }
    ObjectSchema getSchemaByDynamicAnchor(URI uri, String dynamicAnchor) {
        Objects.requireNonNull(dynamicAnchor);
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (uri == null || uri.toString().isEmpty()) {
                return getSchemaByDynamicAnchor(dynamicAnchor);
            }
            if (innerStore != null) {
                ObjectSchema schema = innerStore.get(uri);
                if (schema != null) {
                    return schema.getSchemaByDynamicAnchor(dynamicAnchor);
                }
            }
            return null;
        } else {
            return idSchema.getSchemaByDynamicAnchor(uri, dynamicAnchor);
        }
    }

    // allowedVocabulary
    boolean keywordAllowed(String keyword) {
        if (allowedVocabulary != null) {
            String vocab = VocabularyRegistry.getVocabUri(keyword);
            if (vocab != null) {
                return allowedVocabulary.containsKey(vocab);
            }
        }
        return true;
    }
    // allowedVocabulary
    boolean vocabAllowed(String vocab) {
        if (allowedVocabulary != null) {
            return allowedVocabulary.containsKey(vocab);
        }
        return true;
    }

    // compile
    public boolean isCompiled() {
        return evaluators != null;
    }

    public void compile(SchemaStore outer) {
        outerStore = outer;
        innerStore = new HashMap<>();
        compileMeta();
        compile(PathSegment.Root.INSTANCE, this, this);
    }

    void compile(PathSegment ps, ObjectSchema idSchema, ObjectSchema rootSchema) {
        if (evaluators == null) {
            if (uri == null) uri = CompileUtil.resolveUri(getId(), idSchema.getUri());
            if (uri == null && this == idSchema) uri = URI.create("");
            if (uri != null) {
                idSchema = this;
                rootSchema.innerStore.put(uri, this);
            }
            this.idSchema = idSchema;
            evaluators = CompileUtil.compile(ps, this, idSchema, rootSchema);
        }
    }

    void compileMeta() {
        URI metaUri = CompileUtil.resolveUri(getString("$schema"), null);
        if (metaUri != null) {
            ObjectSchema metaSchema = importAndCompile(metaUri);
            if (metaSchema != null) {
                allowedVocabulary = metaSchema.getVocabulary();
            }
        }
    }

    ObjectSchema importAndCompile(URI uri) {
        if (uri == null || uri.equals(this.uri)) return this;
        ObjectSchema schema = innerStore.get(uri);
        if (schema != null) return schema;
        if (outerStore != null) schema = outerStore.resolve(uri);
        if (schema == null) schema = SchemaStore.globalResolve(uri);
        if (schema == null) return null;

        schema.setUri(uri);
        if (schema.evaluators != null) {
            if (schema.innerStore != null) {
                innerStore.putAll(schema.innerStore);
            } else {
                innerStore.put(uri, schema);
            }
        } else {
            schema.compile(PathSegment.Root.INSTANCE, schema, this);
        }
        return schema;
    }

    void importSchema(URI ref, ObjectSchema compiledSchema) {
        if (innerStore == null) innerStore = new HashMap<>();
        if (compiledSchema != this) innerStore.put(ref, compiledSchema);
    }


    // validate
    @Override
    public ValidationResult validate(Object node, ValidationOptions options) {
        InstancedNode instance = InstancedNode.infer(node);
        ValidationContext ctx = new ValidationContext(this, options);
        PathSegment ps = options.isFailFast() ? null : new PathSegment.Root(null, instance.getObjectType());
        evaluate(instance, ps, ctx);
        return ctx.toResult();
    }

    // evaluate
    @Override
    public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
        if (evaluators == null)
            throw new SchemaException("Schema has not been compiled.");
        int len = evaluators.length;
        if (len > 0 && evaluators[len - 1] instanceof Evaluator.UnevaluatedEvaluator) {
            instance.createEvaluated();
        }
        boolean result = true;
        boolean pushed = ctx.pushIdSchema(this.idSchema);
        for (Evaluator evaluator : evaluators) {
            result = result && evaluator.evaluate(instance, ps, ctx);
            if (ctx.shouldAbort()) return result;
        }
        if (pushed) ctx.popIdSchema();
        return result;
    }

}
