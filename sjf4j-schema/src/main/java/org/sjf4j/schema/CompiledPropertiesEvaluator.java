package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.path.PathSegment;

import java.util.regex.Pattern;

/**
 * Compiled POJO traversal for a single properties evaluator. The original
 * evaluator and the generic OBNT path remain unchanged for non-POJO inputs.
 */
final class CompiledPropertiesEvaluator implements Evaluator {
    private final Evaluator.PropertiesEvaluator fallback;
    private final CompiledPropertyAccess access;
    private final int[] order;
    private final String[] names;

    CompiledPropertiesEvaluator(Evaluator.PropertiesEvaluator fallback,
                                CompiledPropertyAccess access, int[] order, PropertyInfo[] actual) {
        this.fallback = fallback;
        this.access = access;
        this.order = order;
        this.names = new String[actual.length];
        for (int i = 0; i < actual.length; i++) names[i] = actual[i].name;
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
            if (fallback.properties != null) {
                SchemaPlan plan = fallback.properties.get(key);
                if (plan != null) {
                    Instance child = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                    boolean accepted = plan.evaluate(child, cps, ctx);
                    if (accepted) instance.markEvaluated(propIdx);
                    result = result && accepted;
                    if (!accepted && ctx.isFailFast()) return false;
                    matched = true;
                }
            }

            if (fallback.patterns != null) {
                for (int i = 0; i < fallback.patterns.length; i++) {
                    if (fallback.patterns[i].matcher(key).find()) {
                        SchemaPlan plan = fallback.patternPlans[i];
                        if (plan != null) {
                            Instance child = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                            PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                            boolean accepted = plan.evaluate(child, cps, ctx);
                            if (accepted) instance.markEvaluated(propIdx);
                            result = result && accepted;
                            if (!accepted && ctx.isFailFast()) return false;
                            matched = true;
                        }
                    }
                }
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
