package org.sjf4j.processor.path;

import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;

import java.util.IdentityHashMap;
import java.util.Map;

/** Emits a direct predicate without runtime FilterExpr AST evaluation. */
final class FilterEmitter {
    private final Map<FilterPlan, String> paths = new IdentityHashMap<>();
    private final Map<FilterPlan, String> numbers = new IdentityHashMap<>();

    String emit(FilterPlan plan, GeneratedClass generated) {
        prepare(plan, generated);
        String method = generated.names().helperMethod("findFilter");
        generated.addHelper(out -> {
            out.beginBlock("private static boolean " + method +
                    "(Object root, Object current, org.sjf4j.node.NodeWalker.Access access)");
            out.line("return " + predicate(plan) + ";");
            out.endBlock();
        });
        return method;
    }

    private void prepare(FilterPlan plan, GeneratedClass generated) {
        if (plan.kind == FilterPlan.Kind.PATH && plan.members.length > 0) {
            String field = generated.names().field("FILTER_PATH");
            paths.put(plan, field);
            StringBuilder init = new StringBuilder("new String[] {");
            for (int i = 0; i < plan.members.length; i++) {
                if (i > 0) init.append(", ");
                init.append(JavaWriter.stringLiteral(plan.members[i]));
            }
            init.append("}");
            generated.addField(out -> out.line("private static final String[] " + field +
                    " = " + init + ";"));
        } else if (plan.kind == FilterPlan.Kind.LITERAL && plan.literal instanceof Double) {
            String field = generated.names().field("FILTER_NUMBER");
            numbers.put(plan, field);
            String value = JavaWriter.stringLiteral(plan.literal.toString());
            generated.addField(out -> out.line("private static final Double " + field +
                    " = Double.valueOf(" + value + ");"));
        }
        if (plan.left != null) prepare(plan.left, generated);
        if (plan.right != null) prepare(plan.right, generated);
    }

    private String predicate(FilterPlan plan) {
        switch (plan.kind) {
            case LITERAL:
                return "org.sjf4j.path.FilterExpr.truth(" + value(plan) + ")";
            case PATH:
                if (plan.members.length == 0) return "true";
                return "org.sjf4j.path.CompiledFilterSupport.exists(" +
                        (plan.rooted ? "root" : "current") + ", " +
                        paths.get(plan) + ", access)";
            case NOT:
                return "(!" + predicate(plan.left) + ")";
            case AND:
                return "(" + predicate(plan.left) + " && " + predicate(plan.right) + ")";
            case OR:
                return "(" + predicate(plan.left) + " || " + predicate(plan.right) + ")";
            case COMPARE:
                return "org.sjf4j.path.FilterExpr.compare(org.sjf4j.path.FilterExpr.Op." +
                        plan.operator.name() + ", " + value(plan.left) + ", " +
                        value(plan.right) + ")";
            default:
                throw new AssertionError(plan.kind);
        }
    }

    private String value(FilterPlan plan) {
        switch (plan.kind) {
            case LITERAL:
                if (plan.literal == null) return "null";
                if (plan.literal instanceof String) {
                    return JavaWriter.stringLiteral((String) plan.literal);
                }
                if (plan.literal instanceof Boolean) return plan.literal.toString();
                if (plan.literal instanceof Double) return numbers.get(plan);
                throw new AssertionError(plan.literal);
            case PATH:
                if (plan.members.length == 0) return plan.rooted ? "root" : "current";
                return "org.sjf4j.path.CompiledFilterSupport.value(" +
                        (plan.rooted ? "root" : "current") + ", " +
                        paths.get(plan) + ", access)";
            case NOT: case AND: case OR: case COMPARE:
                return "Boolean.valueOf(" + predicate(plan) + ")";
            default:
                throw new AssertionError(plan.kind);
        }
    }
}
