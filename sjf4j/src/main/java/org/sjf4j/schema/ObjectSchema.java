package org.sjf4j.schema;

import org.sjf4j.JsonObject;

import java.net.URI;
import java.util.Map;


public final class ObjectSchema extends JsonObject implements JsonSchema {

    String getId() {return getString("$id");}
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
     */
    public void setRetrievalUri(URI retrievalUri) {
        this.retrievalUri = retrievalUri;
    }

    /**
     * Returns the canonical resource URI used for store registration.
     */
    public URI getCanonicalUri() {
        String id = getId();
        if (id == null) return getRetrievalUri();
        return URI.create(id);
    }

    /// Plan

    @Override
    public SchemaPlan createPlan(SchemaRegistry registry) {
        return SchemaPlanner.createPlan(this, new SchemaRegistry().copyFrom(registry));
    }


}
