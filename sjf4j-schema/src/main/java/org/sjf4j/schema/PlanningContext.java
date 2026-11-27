package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * State shared by one schema planning operation, including referenced resources.
 * Not thread-safe; never stored in the registry.
 */
final class PlanningContext {
    final SchemaRegistry registry;
    final List<Evaluator.RefEvaluator> refEvaluators = new ArrayList<>();
    final List<Evaluator.DynamicRefEvaluator> dynamicRefEvaluators = new ArrayList<>();

    private final int maxSchemas;
    private final int maxSchemaDepth;
    private int schemas;
    private int depth;

    private final Map<String, SchemaPlan> pendingPlans = new HashMap<>();

    PlanningContext(SchemaRegistry registry) {
        this.registry = registry;
        PlanningOptions options = registry.getPlanningOptions();
        this.maxSchemas = options.getMaxSchemas();
        this.maxSchemaDepth = options.getMaxSchemaDepth();
    }

    SchemaPlan findPlan(String id) {
        return pendingPlans.get(id);
    }

    void putPlan(URI uri, SchemaPlan plan) {
        String id = registry.checkPlanUri(uri);
        SchemaPlan old = pendingPlans.putIfAbsent(id, plan);
        if (old != null && old != plan) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
    }

    /** Registers completed plan graphs; standalone compilation never calls this method. */
    void register() {
        registry.registerPlans(pendingPlans);
    }

    /** Rejects oversized subschema arrays before allocating their plan array. */
    void checkRemainingSchemas(int count) {
        if (maxSchemas != 0 && count > maxSchemas - schemas) {
            throw new SchemaException("Planning limit exceeded: maxSchemas=" + maxSchemas);
        }
    }

    void enter() {
        if (maxSchemas != 0 && schemas >= maxSchemas) {
            throw new SchemaException("Planning limit exceeded: maxSchemas=" + maxSchemas);
        }
        if (maxSchemaDepth != 0 && depth >= maxSchemaDepth) {
            throw new SchemaException("Planning limit exceeded: maxSchemaDepth=" + maxSchemaDepth);
        }
        if (maxSchemas != 0) schemas++;
        if (maxSchemaDepth != 0) depth++;
    }

    void leave() {
        if (maxSchemaDepth != 0) depth--;
    }

    /** Removes the pending references belonging to a completed resource or fragment. */
    void clearRefs(int refStart, int dynamicRefStart) {
        refEvaluators.subList(refStart, refEvaluators.size()).clear();
        dynamicRefEvaluators.subList(dynamicRefStart, dynamicRefEvaluators.size()).clear();
    }
}
