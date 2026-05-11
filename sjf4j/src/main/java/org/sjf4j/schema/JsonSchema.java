package org.sjf4j.schema;


import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.OneOf;


/**
 * Parsed JSON Schema document node.
 * <p>
 * Implementations are lightweight schema models. Runtime validation uses the
 * compiled {@link SchemaPlan} returned by {@link #createPlan(SchemaRegistry)}.
 */
@OneOf({
        @OneOf.Mapping(BooleanSchema.class),
        @OneOf.Mapping(ObjectSchema.class),
})
public interface JsonSchema {

    /**
     * Compiles this schema into runtime evaluators.
     * <p>
     * Compilation resolves references, anchors, and keyword evaluators against
     * the provided registry context. Root schemas may use the registry both for
     * delayed compilation of other indexed schemas and for lookup of referenced
     * absolute schema resources. Passing {@code null} means "compile with an
     * empty external registry context".
     */
    SchemaPlan createPlan(SchemaRegistry registry);


    /// Default
    /**
     * Compiles this schema with no caller-provided external registry.
     * <p>
     * Built-in global meta-schemas are still available through the planner and
     * registry internals. Their schema models are indexed eagerly, while their
     * compiled plans are still created lazily on first resolution.
     */
    default SchemaPlan createPlan() {
        return createPlan(null);
    }

    /// Static

    /**
     * Parses JSON text into a schema model node.
     * <p>
     * The returned value is not compiled yet.
     */
    static JsonSchema fromJson(String json) {
        return Sjf4j.global().fromJson(json, JsonSchema.class);
    }

    /**
     * Creates a schema model node from an already parsed JSON-compatible node.
     * <p>
     * The returned value is not compiled yet.
     */
    static JsonSchema fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonSchema.class);
    }

}
