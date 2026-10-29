package org.sjf4j.schema;

import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;

import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Installs optional compiled property access without modifying runtime plans.
 * A failed metadata match leaves the original evaluator chain unchanged.
 */
public final class CompiledSchemaSupport {
    private CompiledSchemaSupport() {}

    public static SchemaPlan specialize(SchemaPlan plan, CompiledPropertyAccess access) {
        if (plan == null || access == null) throw new IllegalArgumentException("null compiled schema input");
        Class<?> type = access.type();
        if (type == null || !Modifier.isPublic(type.getModifiers())) return plan;
        TypeInfo info = TypeRegistry.registerTypeInfo(type);
        PojoInfo pojo = info.pojoInfo;
        if (pojo == null || pojo.isJojo) return plan;

        PropertyInfo[] actual = pojo.gettableProperties;
        String[] names = access.names();
        String[] getters = access.getterNames();
        if (names.length != actual.length || names.length != getters.length) return plan;

        int[] order = new int[actual.length];
        Arrays.fill(order, -1);
        for (int i = 0; i < actual.length; i++) {
            PropertyInfo property = actual[i];
            if (property.publicGetter == null ||
                    !Modifier.isPublic(property.publicGetter.getModifiers())) return plan;
            for (int j = 0; j < names.length; j++) {
                if (property.name.equals(names[j]) &&
                        property.publicGetter.getName().equals(getters[j])) {
                    order[i] = j;
                    break;
                }
            }
            if (order[i] < 0) return plan;
        }

        Evaluator[] original = plan.evaluators;
        Evaluator[] compiled = null;
        for (int i = 0; i < original.length; i++) {
            if (original[i] instanceof Evaluator.PropertiesEvaluator) {
                if (compiled == null) compiled = original.clone();
                compiled[i] = ((Evaluator.PropertiesEvaluator) original[i])
                        .withCompiledAccess(access, order, actual);
            }
        }
        if (compiled == null) return plan;
        return new SchemaPlan(plan.schemaUri, plan.keywordPs, compiled,
                plan.booleanSchema, plan.booleanValue, plan.dynamicAnchor,
                plan.byAnchorPlans, plan.byDynamicAnchorPlans, plan.byPathPlans,
                plan.schema, plan.dialect, plan.vocabulary);
    }
}
