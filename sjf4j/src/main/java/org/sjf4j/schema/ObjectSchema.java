package org.sjf4j.schema;

import org.sjf4j.JsonObject;

import java.net.URI;
import java.util.Map;


/**
 * Object-shaped schema model.
 * <p>
 * This type keeps parsed schema content plus a root retrieval URI when the
 * schema document was loaded from an external location. Compilation state is
 * kept in {@link SchemaPlan}, not in this model.
 */
public final class ObjectSchema extends JsonObject implements JsonSchema {

    /**
     * Returns the raw {@code $id} keyword value, or {@code null} when absent.
     */
    String getId() {return getString("$id");}
    /**
     * Returns declared {@code $vocabulary} entries from this schema object.
     */
    Map<String, Boolean> getVocabulary() {return getMap("$vocabulary", Boolean.class);}

    /**
     * Original retrieval URI used to load the root schema document.
     * <p>
     * This is only populated for externally loaded root schemas. It provides
     * the initial base used to resolve a root relative `$id`.
     */
    private transient URI retrievalUri;

    /**
     * Returns the retrieval URI used to load the root schema document.
     */
    public URI getRetrievalUri() {
        return retrievalUri;
    }

    /**
     * Sets the retrieval URI used to load the root schema document.
     * <p>
     * This is root-resource metadata. Nested subschemas do not carry their own
     * retrieval URIs.
     */
    public void setRetrievalUri(URI retrievalUri) {
        this.retrievalUri = retrievalUri;
    }

    /**
     * Returns the schema resource URI declared by this object.
     * <p>
     * When {@code $id} is relative, callers still need a retrieval/base URI to
     * obtain the absolute resource URI used by registries and compiled plans.
     */
    public URI getCanonicalUri() {
        String id = getId();
        if (id == null) return getRetrievalUri();
        return URI.create(id);
    }

    /// Plan

    /**
     * Compiles this root schema with an isolated copy of the provided registry.
     * <p>
     * The copy preserves caller-visible registry state while still allowing the
     * planner to lazily cache referenced plans during compilation.
     */
    @Override
    public SchemaPlan createPlan(SchemaRegistry registry) {
        return SchemaPlanner.createPlan(this, SchemaRegistry.copyOf(registry));
    }


}
