package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.Types;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * JSON Schema object representation with compiled evaluators and resolution state.
 */
public final class ObjectSchema extends JsonObject implements JsonSchema {

    /**
     * Original retrieval URI used to load the root schema document.
     * <p>
     * This is only populated for externally loaded root schemas. It provides
     * the initial base used to resolve a root relative `$id`.
     */
    private transient URI retrievalUri;

    /**
     * Canonical URI of this schema resource after `$id` resolution.
     * <p>
     * For root schemas without `$id`, compilation promotes the retrieval URI to
     * the canonical URI. For inline schemas without any URI, compilation uses
     * the empty URI.
     */
    private transient URI canonicalUri;
    private transient ObjectSchema idSchema;
    private transient Evaluator[] evaluators;
    private transient Map<URI, ObjectSchema> innerRegistry;
    private transient Map<String, ObjectSchema> anchors;
    private transient Map<String, ObjectSchema> dynamicAnchors;
    private transient Map<String, Boolean> allowedVocabulary;

    private transient SchemaRegistry outerRegistry;

    /**
     * Creates an empty ObjectSchema.
     */
    public ObjectSchema() {
        super();
    }
    /**
     * Creates an ObjectSchema from an object-like node.
     */
    public ObjectSchema(Object node) {
        super(node);
    }

    /**
     * Returns the retrieval URI used to load the root schema document.
     */
    URI getRetrievalUri() {
        return retrievalUri;
    }

    /**
     * Sets the retrieval URI used to load the root schema document.
     */
    void setRetrievalUri(URI retrievalUri) {
        this.retrievalUri = retrievalUri;
    }

    /**
     * Returns the canonical resource URI used for store registration.
     */
    URI getCanonicalUri() {
        if (canonicalUri == null) {
            return CompileUtil.resolveUri(getId(), retrievalUri);
        }
        return canonicalUri;
    }

    /**
     * Sets the canonical resource URI for this schema resource.
     */
    void setCanonicalUri(URI canonicalUri) {
        this.canonicalUri = canonicalUri;
    }

    // Schema keywords
    /**
     * Returns the $id keyword value.
     */
    String getId() {return getString("$id");}
    /**
     * Returns the $dynamicAnchor keyword value.
     */
    String getDynamicAnchor() {return getString("$dynamicAnchor");}
    /**
     * Returns declared vocabulary permissions.
     */
    Map<String, Boolean> getVocabulary() {return getMap("$vocabulary", Boolean.class);}

    // schemaRegistry
    /**
     * Exports compiled absolute resources to a new SchemaRegistry.
     */
    public SchemaRegistry toRegistry() {
        if (innerRegistry == null) throw new SchemaException("Schema has not been compiled yet");
        SchemaRegistry store = new SchemaRegistry();
        for (Map.Entry<URI, ObjectSchema> entry : innerRegistry.entrySet()) {
            if (entry.getKey().isAbsolute()) store.register(entry.getKey(), entry.getValue());
        }
        return store;
    }

    // anchor
    /**
     * Registers a static anchor within current schema resource.
     */
    void putAnchor(String anchor, ObjectSchema schema) {
        if (anchors == null) anchors = new HashMap<>();
        if (anchors.containsKey(anchor)) throw new SchemaException("Duplicate $anchor '" + anchor +
                "' in the same schema resource (implementation restriction)");
        anchors.put(anchor, schema);
    }
    /**
     * Resolves schema by anchor in current id scope.
     */
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
    /**
     * Resolves schema by URI plus anchor.
     */
    ObjectSchema getSchemaByAnchor(URI uri, String anchor) {
        Objects.requireNonNull(anchor);
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (uri == null || uri.toString().isEmpty() || Objects.equals(uri, canonicalUri)) {
                return getSchemaByAnchor(anchor);
            }
            if (innerRegistry != null) {
                ObjectSchema schema = innerRegistry.get(uri);
                if (schema != null) {
                    return schema.getSchemaByAnchor(anchor);
                }
            }
            return null;
        } else {
            return idSchema.getSchemaByAnchor(uri, anchor);
        }
    }
    /**
     * Resolves schema by JSON Pointer in current id scope.
     */
    JsonSchema getSchemaByPath(JsonPointer path) {
        Objects.requireNonNull(path, "path");
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
    /**
     * Resolves schema by URI plus JSON Pointer.
     */
    JsonSchema getSchemaByPath(URI uri, JsonPointer path) {
        Objects.requireNonNull(path);
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (uri == null || uri.toString().isEmpty() || Objects.equals(uri, canonicalUri)) {
                return getSchemaByPath(path);
            }
            if (innerRegistry != null) {
                ObjectSchema schema = innerRegistry.get(uri);
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
    /**
     * Registers a dynamic anchor within current schema resource.
     */
    void putDynamicAnchor(String dynamicAnchor, ObjectSchema schema) {
        if (dynamicAnchors == null) dynamicAnchors = new HashMap<>();
        dynamicAnchors.computeIfAbsent(dynamicAnchor, k -> schema);
    }
    /**
     * Resolves schema by dynamic anchor in current id scope.
     */
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
    /**
     * Resolves schema by URI plus dynamic anchor.
     */
    ObjectSchema getSchemaByDynamicAnchor(URI uri, String dynamicAnchor) {
        Objects.requireNonNull(dynamicAnchor);
        if (idSchema == null) throw new SchemaException("Schema has not been compiled yet");
        if (idSchema == this) {
            if (uri == null || uri.toString().isEmpty() || Objects.equals(uri, canonicalUri)) {
                return getSchemaByDynamicAnchor(dynamicAnchor);
            }
            if (innerRegistry != null) {
                ObjectSchema schema = innerRegistry.get(uri);
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
    /**
     * Returns true when the keyword is allowed by active vocabulary.
     */
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
    /**
     * Returns true when the vocabulary URI is allowed.
     */
    boolean vocabAllowed(String vocab) {
        if (allowedVocabulary != null) {
            return allowedVocabulary.containsKey(vocab);
        }
        return true;
    }

    // compile
    /**
     * Returns true when this schema is already compiled.
     */
    public boolean isCompiled() {
        return evaluators != null;
    }

    /**
     * Compiles this schema with optional outer registry for reference resolution.
     * <p>
     * Compilation initializes inner resource store, applies meta-schema
     * vocabulary constraints, and compiles this schema as root resource.
     */
    public void compile(SchemaRegistry outer) {
        outerRegistry = outer;
        innerRegistry = new HashMap<>();
        compileMeta();
        compile(PathSegment.Root.INSTANCE, this, this);
    }

    /**
     * Compiles this schema resource in provided id/root scopes.
     * <p>
     * Each schema object is compiled at most once. When a new resource URI is
     * resolved, id-scope is switched to this schema resource.
     */
    void compile(PathSegment ps, ObjectSchema idSchema, ObjectSchema rootSchema) {
        if (evaluators == null) {
            if (this == idSchema) {
                URI resolved = CompileUtil.resolveUri(getId(), retrievalUri);
                if (resolved != null) {
                    canonicalUri = resolved;
                } else if (canonicalUri == null) {
                    canonicalUri = retrievalUri != null ? retrievalUri : URI.create("");
                }
            } else if (canonicalUri == null) {
                canonicalUri = CompileUtil.resolveUri(getId(), idSchema.getCanonicalUri());
            }
            if (canonicalUri != null) {
                idSchema = this;
                rootSchema.innerRegistry.put(canonicalUri, this);
            }
            this.idSchema = idSchema;
            evaluators = CompileUtil.compile(ps, this, idSchema, rootSchema);
        }
    }

    /**
     * Loads and applies meta-schema vocabulary constraints.
     */
    void compileMeta() {
        URI metaUri = CompileUtil.resolveUri(getString("$schema"), null);
        if (metaUri != null) {
            ObjectSchema metaSchema = importAndCompile(metaUri);
            if (metaSchema != null) {
                allowedVocabulary = metaSchema.getVocabulary();
            }
        }
    }

    /**
     * Imports and compiles a referenced schema resource.
     * <p>
     * Resolution order: current inner store, outer store, then global store.
     */
    ObjectSchema importAndCompile(URI uri) {
        if (uri == null || Objects.equals(uri, canonicalUri)) return this;
        ObjectSchema schema = innerRegistry.get(uri);
        if (schema != null) return schema;
        if (outerRegistry != null) schema = outerRegistry.resolve(uri);
        if (schema == null) schema = SchemaRegistry.globalResolve(uri);
        if (schema == null) return null;

        if (schema.evaluators != null) {
            if (schema.innerRegistry != null) {
                innerRegistry.putAll(schema.innerRegistry);
            } else {
                innerRegistry.put(uri, schema);
            }
        } else {
            if (schema.getRetrievalUri() == null && schema.getCanonicalUri() == null) {
                schema.setRetrievalUri(uri);
            }
            schema.compile(PathSegment.Root.INSTANCE, schema, this);
        }
        return schema;
    }

    /**
     * Adds an already compiled referenced schema to inner store.
     */
    void importSchema(URI ref, ObjectSchema compiledSchema) {
        if (innerRegistry == null) innerRegistry = new HashMap<>();
        if (compiledSchema != this) innerRegistry.put(ref, compiledSchema);
    }


    // validate
    /**
     * Validates node and returns validation result under given options.
     * <p>
     * Root path is omitted in fail-fast mode to reduce allocation.
     */
    @Override
    public ValidationResult validate(Object node, ValidationOptions options) {
        InstancedNode instance = InstancedNode.infer(node);
        ValidationContext ctx = new ValidationContext(this, options);
        PathSegment ps = options.isFailFast() ? null : PathSegment.Root.INSTANCE;
        evaluate(instance, ps, ctx);
        return ctx.toResult();
    }

    // evaluate
    /**
     * Evaluates compiled keyword evaluators against instance.
     * <p>
     * Dynamic id-scope is pushed for this resource during evaluation and popped
     * afterward. Unevaluated-tracking state is initialized when required.
     */
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
