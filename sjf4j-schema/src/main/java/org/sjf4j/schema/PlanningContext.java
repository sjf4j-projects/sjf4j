package org.sjf4j.schema;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

/**
 * State shared by one schema planning operation, including lazy resources
 * and fragment targets. Not thread-safe; never stored in the registry.
 */
final class PlanningContext {
    final SchemaRegistry registry;
    final List<Evaluator.RefEvaluator> refEvaluators = new ArrayList<>();
    final List<Evaluator.DynamicRefEvaluator> dynamicRefEvaluators = new ArrayList<>();

    private final int maxSchemas;
    private final int maxSchemaDepth;
    private int schemas;
    private int depth;

    private final Map<String, SchemaPlan> publishedPlans = new HashMap<>();
    private Map<Map<String, SchemaPlan>, Map<String, SchemaPlan>> fragmentSnapshots;

    PlanningContext(SchemaRegistry registry) {
        this.registry = registry;
        PlanningOptions options = registry.getPlanningOptions();
        this.maxSchemas = options.getMaxSchemas();
        this.maxSchemaDepth = options.getMaxSchemaDepth();
    }

    void putPlan(URI uri, SchemaPlan plan) {
        if (registry.putPlan(uri, plan)) {
            publishedPlans.put(SchemaUtil.normalizeUriKey(uri), plan);
        }
    }

    void beforeLazyBuild(SchemaPlan root) {
        if (fragmentSnapshots == null) fragmentSnapshots = new IdentityHashMap<>();
        snapshot(root.byAnchorPlans);
        snapshot(root.byDynamicAnchorPlans);
        snapshot(root.byPathPlans);
    }

    private void snapshot(Map<String, SchemaPlan> map) {
        if (map != null && !fragmentSnapshots.containsKey(map)) {
            fragmentSnapshots.put(map, new HashMap<>(map));
        }
    }

    void rollback() {
        for (Map.Entry<String, SchemaPlan> entry : publishedPlans.entrySet()) {
            registry.removePlan(entry.getKey(), entry.getValue());
        }
        if (fragmentSnapshots != null) {
            for (Map.Entry<Map<String, SchemaPlan>, Map<String, SchemaPlan>> entry : fragmentSnapshots.entrySet()) {
                entry.getKey().clear();
                entry.getKey().putAll(entry.getValue());
            }
        }
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
