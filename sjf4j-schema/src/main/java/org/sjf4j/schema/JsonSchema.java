package org.sjf4j.schema;


import org.sjf4j.NodeConfig;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfCase;
import org.sjf4j.node.NodeMapper;


/**
 * Parsed JSON Schema document represented as an OBNT object node or value node.
 * <p>
 * Implementations are lightweight schema models. Runtime validation uses the
 * compiled {@link SchemaPlan} returned by {@link #createPlan(SchemaRegistry)}.
 * A schema may be boolean ({@link BooleanSchema}) or object-shaped
 * ({@link ObjectSchema}); invalid schema content fails during parsing or
 * compilation rather than yielding a validation result for an instance.
 */
@OneOf(cases = {
        @OneOfCase(type = BooleanSchema.class),
        @OneOfCase(type = ObjectSchema.class),
})
public interface JsonSchema {

    /**
     * Compiles this schema into runtime evaluators.
     * <p>
     * Compilation resolves references, anchors, and keyword evaluators against
     * the provided registry's immutable planning policy and resource context. Root schemas may use the registry both for
     * delayed compilation of other indexed schemas and for lookup of referenced
     * absolute schema resources. Passing {@code null} means "compile with an
     * empty external registry context".
     */
    SchemaPlan createPlan(SchemaRegistry registry);

    /**
     * Compiles with resource limits. Bounded planning uses an isolated registry
     * and rebuilds indexed object resources instead of sharing cached plans.
     * The caller's registry is not modified on failure.
     */
    SchemaPlan createPlan(SchemaRegistry registry, PlanningOptions options);


    /*
     * --------------------------------------------------------------
     * Default
     * --------------------------------------------------------------
     */
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

    /*
     * --------------------------------------------------------------
     * Static
     * --------------------------------------------------------------
     */

    /**
     * Parses JSON text into a schema model node using exact generic numeric reads.
     * <p>
     * The returned value is not compiled yet.
     */
    static JsonSchema fromJson(String json) {
        return SchemaSupport.readSchema(json);
    }

    /**
     * Creates a schema model from an already parsed OBNT representation.
     * Numeric precision already lost by the caller cannot be recovered.
     * <p>
     * The returned value is not compiled yet.
     */
    static JsonSchema fromNode(Object node) {
        return (JsonSchema) NodeMapper.convert(node, JsonSchema.class, false, NodeConfig.DEFAULT);
    }

}
