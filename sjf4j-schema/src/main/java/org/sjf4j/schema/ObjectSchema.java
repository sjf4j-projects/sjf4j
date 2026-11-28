package org.sjf4j.schema;

import org.sjf4j.JsonObject;

import java.util.Map;


/**
 * Object-shaped schema model.
 * <p>
 * Resource metadata is maintained separately; compilation state lives in
 * {@link SchemaPlan}.
 */
public final class ObjectSchema extends JsonObject implements JsonSchema {

    /**
     * Returns the raw {@code $id} keyword value, or {@code null} when absent.
     */
    String getId() {
        return getString("$id");
    }

    /**
     * Returns declared {@code $vocabulary} entries from this schema object.
     */
    Map<String, Boolean> getVocabulary() {
        return getMap("$vocabulary", Boolean.class);
    }

    /*
     * --------------------------------------------------------------
     * Plan
     * --------------------------------------------------------------
     */

    /**
     * Compiles this root schema without modifying the provided registry.
     */
    @Override
    public SchemaPlan createPlan(SchemaRegistry registry) {
        return SchemaPlanner.buildPlan(new SchemaResource(null, this), registry);
    }


}
