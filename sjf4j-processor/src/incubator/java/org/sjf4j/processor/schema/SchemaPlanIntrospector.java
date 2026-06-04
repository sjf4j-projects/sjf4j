package org.sjf4j.processor.schema;

import org.sjf4j.schema.Evaluator;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.path.PathSegment;

import java.lang.reflect.Field;

/** Reflection-only access to schema runtime internals for processor fast-path planning. */
final class SchemaPlanIntrospector {
    private SchemaPlanIntrospector() {}

    static Evaluator[] evaluators(SchemaPlan plan) {
        return (Evaluator[]) field(plan, "evaluators");
    }

    static boolean booleanSchema(SchemaPlan plan) {
        return ((Boolean) field(plan, "booleanSchema")).booleanValue();
    }

    static boolean booleanValue(SchemaPlan plan) {
        return ((Boolean) field(plan, "booleanValue")).booleanValue();
    }

    static String pointer(SchemaPlan plan) {
        PathSegment ps = (PathSegment) field(plan, "keywordPs");
        return ps == null ? "" : ps.rootedPointerExpr();
    }

    static Object field(Object owner, String name) {
        try {
            Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(owner);
        } catch (Exception e) {
            throw new IllegalStateException("cannot inspect schema field '" + name + "' on " + owner.getClass().getName(), e);
        }
    }
}
