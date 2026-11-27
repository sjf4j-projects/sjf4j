package org.sjf4j.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * State shared by one schema planning operation, including lazy resources
 * and fragment targets. Not thread-safe; never stored in the registry.
 */
final class PlanningContext {
    final SchemaRegistry registry;
    final SchemaRegistry source;
    final PlanningOptions options;
    final List<Evaluator.RefEvaluator> refEvaluators = new ArrayList<>();
    final List<Evaluator.DynamicRefEvaluator> dynamicRefEvaluators = new ArrayList<>();

    private int schemas;
    private int depth;

    PlanningContext(SchemaRegistry registry) {
        this(registry, null, PlanningOptions.DEFAULT);
    }

    PlanningContext(SchemaRegistry registry, SchemaRegistry source, PlanningOptions options) {
        this.registry = registry;
        this.source = source;
        this.options = options;
    }

    void enter() {
        int maxSchemas = options.getMaxSchemas();
        int maxDepth = options.getMaxSchemaDepth();
        if (maxSchemas != 0 && schemas >= maxSchemas) {
            throw new SchemaException("Planning limit exceeded: maxSchemas=" + maxSchemas);
        }
        if (maxDepth != 0 && depth >= maxDepth) {
            throw new SchemaException("Planning limit exceeded: maxSchemaDepth=" + maxDepth);
        }
        if (maxSchemas != 0) schemas++;
        if (maxDepth != 0) depth++;
    }

    void leave() {
        if (options.getMaxSchemaDepth() != 0) depth--;
    }

    /** Removes the pending references belonging to a completed resource or fragment. */
    void clearRefs(int refStart, int dynamicRefStart) {
        refEvaluators.subList(refStart, refEvaluators.size()).clear();
        dynamicRefEvaluators.subList(dynamicRefStart, dynamicRefEvaluators.size()).clear();
    }
}
