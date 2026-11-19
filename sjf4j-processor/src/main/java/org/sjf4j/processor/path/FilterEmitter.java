package org.sjf4j.processor.path;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.type.TypeMirror;
import java.util.IdentityHashMap;
import java.util.Map;

/** Emits a direct predicate without runtime FilterExpr AST evaluation. */
final class FilterEmitter {
    private final Map<FilterPlan, String> paths = new IdentityHashMap<>();
    private final Map<FilterPlan, String> directPaths = new IdentityHashMap<>();
    private final Map<FilterPlan, String> numbers = new IdentityHashMap<>();
    private final TypeSystem types;
    private final NodeReadEmitter reads;

    FilterEmitter(ProcessorContext context) {
        this.types = context.types;
        this.reads = new NodeReadEmitter(types);
    }

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
            int prefixLength = plan.propertyPrefix.length;
            if (prefixLength > 0) {
                String helper = generated.names().helperMethod("filterPath");
                directPaths.put(plan, helper);
                if (prefixLength < plan.members.length) {
                    paths.put(plan, addMemberArray(generated, plan.members, prefixLength));
                }
                generated.addHelper(out -> emitDirectPath(out, helper, plan));
            } else {
                paths.put(plan, addMemberArray(generated, plan.members, 0));
            }
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

    private String addMemberArray(GeneratedClass generated, String[] members, int start) {
        String field = generated.names().field("FILTER_PATH");
        StringBuilder init = new StringBuilder("new String[] {");
        for (int i = start; i < members.length; i++) {
            if (i > start) init.append(", ");
            init.append(JavaWriter.stringLiteral(members[i]));
        }
        init.append("}");
        generated.addField(out -> out.line("private static final String[] " + field +
                " = " + init + ";"));
        return field;
    }

    /**
     * Directly read the statically resolved POJO prefix. The generated helper
     * reads each member once and checks null between accesses. An absent
     * intermediate node returns Nothing, not Java null.
     */
    private void emitDirectPath(JavaWriter out, String helper, FilterPlan plan) {
        out.beginBlock("private static Object " + helper +
                "(Object node, org.sjf4j.node.NodeWalker.Access access)");
        out.line("if (node == null) return org.sjf4j.path.CompiledFilterSupport.missing();");

        NodeAccess[] prefix = plan.propertyPrefix;
        String owner = "((" + javaType(prefix[0].ownerType()) + ") node)";
        for (int i = 0; i < prefix.length; i++) {
            NodeAccess member = prefix[i];
            String value = "filterValue" + i;
            String read = reads.name(member, owner, JavaWriter.stringLiteral(plan.members[i]));
            out.line(javaType(member.readType()) + " " + value + " = " + read + ";");
            if (i + 1 < prefix.length) {
                out.line("if (" + value +
                        " == null) return org.sjf4j.path.CompiledFilterSupport.missing();");
            }
            owner = value;
        }

        if (prefix.length < plan.members.length) {
            out.line("return org.sjf4j.path.CompiledFilterSupport.value(" +
                    owner + ", " + paths.get(plan) + ", access);");
        } else {
            out.line("return " + owner + ";");
        }
        out.endBlock();
    }

    private String javaType(TypeMirror type) {
        return types.concrete(type).toString();
    }

    private String predicate(FilterPlan plan) {
        switch (plan.kind) {
            case LITERAL:
                return "org.sjf4j.path.FilterExpr.truth(" + value(plan) + ")";
            case PATH:
                if (plan.members.length == 0) return "true";
                if (directPaths.containsKey(plan)) {
                    return "(" + value(plan) +
                            " != org.sjf4j.path.CompiledFilterSupport.missing())";
                }
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
                if (directPaths.containsKey(plan)) {
                    return directPaths.get(plan) + "(" +
                            (plan.rooted ? "root" : "current") + ", access)";
                }
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
