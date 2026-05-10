package org.sjf4j.schema;


import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.OneOf;


@OneOf({
        @OneOf.Mapping(BooleanSchema.class),
        @OneOf.Mapping(ObjectSchema.class),
})
public interface JsonSchema {

    /**
     * Compiles this schema into runtime evaluators.
     * <p>
     * Compilation resolves references, anchors, and keyword evaluators against
     * the provided registry context.
     */
    SchemaPlan createPlan(SchemaRegistry registry);


    /// Default
    /**
     * Compiles this schema using default resolution context.
     */
    default SchemaPlan createPlan() {
        return createPlan(null);
    }

    /// Static

    /**
     * Parses JSON text and creates a schema instance.
     */
    static JsonSchema fromJson(String json) {
        return Sjf4j.global().fromJson(json, JsonSchema.class);
    }

    /**
     * Creates a schema instance from a parsed JSON node.
     */
    static JsonSchema fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonSchema.class);
    }

}
