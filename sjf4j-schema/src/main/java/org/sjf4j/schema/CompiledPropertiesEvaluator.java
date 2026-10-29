package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.path.PathSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiled POJO traversal for a single properties evaluator. The original
 * evaluator and the generic OBNT path remain unchanged for non-POJO inputs.
 */
final class CompiledPropertiesEvaluator implements Evaluator {
    private final Evaluator.PropertiesEvaluator fallback;
    private final CompiledPropertyAccess access;
    private final int[] order;
    private final String[] names;
    private final SchemaPlan[] propertyPlans;
    private final SchemaPlan[][] patternPlans;

    CompiledPropertiesEvaluator(Evaluator.PropertiesEvaluator fallback,
                                CompiledPropertyAccess access, int[] order, PropertyInfo[] actual) {
        this.fallback = fallback;
        this.access = access;
        this.order = order;
        this.names = new String[actual.length];
        this.propertyPlans = new SchemaPlan[actual.length];
        this.patternPlans = new SchemaPlan[actual.length][];
        for (int i = 0; i < actual.length; i++) {
            String name = actual[i].name;
            names[i] = name;
            propertyPlans[i] = fallback.properties == null ? null : fallback.properties.get(name);
            List<SchemaPlan> matched = new ArrayList<SchemaPlan>();
            if (fallback.patterns != null) {
                for (int j = 0; j < fallback.patterns.length; j++) {
                    if (fallback.patterns[j].matcher(name).find() &&
                            fallback.patternPlans[j] != null) {
                        matched.add(fallback.patternPlans[j]);
                    }
                }
            }
            patternPlans[i] = matched.toArray(new SchemaPlan[0]);
        }
    }

    @Override
    public boolean evaluate(Instance instance, PathSegment ps, ValidationContext ctx) {
        if (instance.jsonType() != JsonType.OBJECT) return true;
        Object node = instance.node();
        if (node.getClass() != access.type()) return fallback.evaluate(instance, ps, ctx);

        boolean result = true;
        for (int propIdx = 0; propIdx < names.length; propIdx++) {
            String key = names[propIdx];
            Object value = access.read(node, order[propIdx]);
            boolean matched = false;
            SchemaPlan propertyPlan = propertyPlans[propIdx];
            if (propertyPlan != null) {
                    Instance child = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                    boolean accepted = propertyPlan.evaluate(child, cps, ctx);
                    if (accepted) instance.markEvaluated(propIdx);
                    result = result && accepted;
                    if (!accepted && ctx.isFailFast()) return false;
                    matched = true;
            }

            for (SchemaPlan plan : patternPlans[propIdx]) {
                Instance child = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                boolean accepted = plan.evaluate(child, cps, ctx);
                if (accepted) instance.markEvaluated(propIdx);
                result = result && accepted;
                if (!accepted && ctx.isFailFast()) return false;
                matched = true;
            }

            if (fallback.additionalPropertiesPlan != null && !matched) {
                Instance child = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                boolean accepted = fallback.additionalPropertiesPlan.evaluate(child, cps, ctx);
                if (accepted) instance.markEvaluated(propIdx);
                result = result && accepted;
                if (!accepted && ctx.isFailFast()) return false;
            }
        }
        return result;
    }
}
