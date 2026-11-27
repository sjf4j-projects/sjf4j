package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

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

    private final Map<String, SchemaPlan> pendingPlans = new HashMap<>();
    private Map<Map<String, SchemaPlan>, Map<String, SchemaPlan>> pendingFragments;

    PlanningContext(SchemaRegistry registry) {
        this.registry = registry;
        PlanningOptions options = registry.getPlanningOptions();
        this.maxSchemas = options.getMaxSchemas();
        this.maxSchemaDepth = options.getMaxSchemaDepth();
    }

    SchemaPlan findPlan(String id) {
        SchemaPlan plan = pendingPlans.get(id);
        return plan != null ? plan : registry.findLocalPlan(id);
    }

    void putPlan(URI uri, SchemaPlan plan) {
        String id = registry.checkPlanUri(uri);
        SchemaPlan old = findPlan(id);
        if (old != null && old != plan) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, uri));
        }
        if (old == null) pendingPlans.put(id, plan);
    }

    /** Existing resource maps are updated only after all references are bound. */
    void stageFragments(SchemaPlan root) {
        if (pendingFragments == null) pendingFragments = new IdentityHashMap<>();
        pendingFragments.computeIfAbsent(root.byAnchorPlans, key -> new HashMap<>());
        pendingFragments.computeIfAbsent(root.byDynamicAnchorPlans, key -> new HashMap<>());
        pendingFragments.computeIfAbsent(root.byPathPlans, key -> new HashMap<>());
    }

    SchemaPlan findFragment(Map<String, SchemaPlan> fragments, String key) {
        if (pendingFragments != null) {
            Map<String, SchemaPlan> pending = pendingFragments.get(fragments);
            if (pending != null) {
                SchemaPlan plan = pending.get(key);
                if (plan != null) return plan;
            }
        }
        return fragments.get(key);
    }

    void putFragment(Map<String, SchemaPlan> fragments, String key, SchemaPlan plan) {
        Map<String, SchemaPlan> pending = pendingFragments == null ? null : pendingFragments.get(fragments);
        (pending == null ? fragments : pending).put(key, plan);
    }

    /** Publishes only fully built plans. Planning holds the shared registry lock. */
    void commit() {
        if (pendingFragments != null) pendingFragments.forEach(Map::putAll);
        registry.putPlans(pendingPlans);
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
