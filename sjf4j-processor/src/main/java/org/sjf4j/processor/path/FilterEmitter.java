package org.sjf4j.processor.path;

import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Emits a direct predicate without runtime FilterExpr AST evaluation. */
final class FilterEmitter {
    private final Map<FilterPlan, String> paths = new IdentityHashMap<>();
    private final Map<FilterPlan, String> directPaths = new IdentityHashMap<>();
    private final Map<FilterPlan, String> constants = new IdentityHashMap<>();
    private String noArgsField;
    private final TypeSystem types;
    private final NodeReadEmitter reads;

    FilterEmitter(CompilationContext context) {
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
            if (plan.selectors != null) {
                paths.put(plan, addIndexedArray(generated, plan.selectors));
            } else if (prefixLength < plan.members.length) {
                paths.put(plan, addMemberArray(generated, plan.members, prefixLength));
            }
            if (prefixLength > 0) {
                String helper = generated.names().helperMethod("filterPath");
                directPaths.put(plan, helper);
                generated.addHelper(out -> emitDirectPath(out, helper, plan));
            }
        } else if (plan.kind == FilterPlan.Kind.LITERAL && plan.literal instanceof Double) {
            String field = generated.names().field("FILTER_NUMBER");
            constants.put(plan, field);
            String value = JavaWriter.stringLiteral(plan.literal.toString());
            generated.addField(out -> out.line("private static final Double " + field +
                    " = Double.valueOf(" + value + ");"));
        } else if (plan.kind == FilterPlan.Kind.LITERAL && plan.literal instanceof Pattern) {
            Pattern pattern = (Pattern) plan.literal;
            String field = generated.names().field("FILTER_PATTERN");
            constants.put(plan, field);
            generated.addField(out -> out.line("private static final java.util.regex.Pattern " +
                    field + " = java.util.regex.Pattern.compile(" +
                    JavaWriter.stringLiteral(pattern.pattern()) + ", " + pattern.flags() + ");"));
        } else if (plan.kind == FilterPlan.Kind.LITERAL && plan.literal instanceof List) {
            String field = generated.names().field("FILTER_ARRAY");
            constants.put(plan, field);
            List<?> values = (List<?>) plan.literal;
            StringBuilder initializer = new StringBuilder("java.util.Collections.unmodifiableList(" +
                    "java.util.Arrays.asList(new Object[] {");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) initializer.append(", ");
                initializer.append(literal(values.get(i)));
            }
            initializer.append("}))");
            generated.addField(out -> out.line("private static final java.util.List<Object> " +
                    field + " = " + initializer + ";"));
        }
        if (plan.kind == FilterPlan.Kind.FUNCTION) {
            if (plan.arguments.length == 1 && noArgsField == null) {
                noArgsField = generated.names().field("FILTER_NO_ARGS");
                generated.addField(out -> out.line(
                        "private static final Object[] " + noArgsField + " = new Object[0];"));
            }
            for (FilterPlan argument : plan.arguments) prepare(argument, generated);
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

    private String addIndexedArray(GeneratedClass generated, Object[] selectors) {
        String field = generated.names().field("FILTER_SELECTORS");
        StringBuilder init = new StringBuilder("new Object[] {");
        for (int i = 0; i < selectors.length; i++) {
            if (i > 0) init.append(", ");
            Object selector = selectors[i];
            if (selector instanceof String) {
                init.append(JavaWriter.stringLiteral((String) selector));
            } else {
                init.append("Integer.valueOf(").append(selector).append(")");
            }
        }
        init.append("}");
        generated.addField(out -> out.line("private static final Object[] " + field +
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
        out.line("if (node == null) return org.sjf4j.path.PathSupport.missing();");

        NodeAccess[] prefix = plan.propertyPrefix;
        String owner = "((" + javaType(prefix[0].ownerType()) + ") node)";
        for (int i = 0; i < prefix.length; i++) {
            NodeAccess member = prefix[i];
            TypeElement ownerElement = types.typeElement(types.concrete(member.ownerType()));
            if (ownerElement != null && !ownerElement.getModifiers().contains(Modifier.FINAL)) {
                // A subclass may hide a field or change the node-facing property.
                // Specialize only the exact compile-time type; otherwise use OBNT.
                String actual = i == 0 ? "node" : owner;
                String fallback = plan.selectors == null
                        ? "org.sjf4j.path.PathSupport.value(" + actual + ", " +
                            memberArrayLiteral(plan.members, i) + ", access)"
                        : "org.sjf4j.path.PathSupport.indexedValue(" + actual + ", " +
                            paths.get(plan) + ", " + i + ", access)";
                out.line("if (" + actual + ".getClass() != " +
                        ownerElement.getQualifiedName() + ".class) return " + fallback + ";");
            }
            String value = "filterValue" + i;
            String read = reads.name(member, owner, JavaWriter.stringLiteral(plan.members[i]));
            out.line(javaType(member.readType()) + " " + value + " = " + read + ";");
            if (i + 1 < prefix.length) {
                out.line("if (" + value +
                        " == null) return org.sjf4j.path.PathSupport.missing();");
            }
            owner = value;
        }

        if (prefix.length < plan.members.length) {
            String suffix = plan.selectors == null
                    ? "org.sjf4j.path.PathSupport.value(" + owner + ", " +
                        paths.get(plan) + ", access)"
                    : "org.sjf4j.path.PathSupport.indexedValue(" + owner + ", " +
                        paths.get(plan) + ", " + prefix.length + ", access)";
            out.line("return " + suffix + ";");
        } else {
            out.line("return " + owner + ";");
        }
        out.endBlock();
    }

    /** Only allocated when the non-final POJO's runtime type differs. */
    private String memberArrayLiteral(String[] members, int start) {
        StringBuilder literal = new StringBuilder("new String[] {");
        for (int i = start; i < members.length; i++) {
            if (i > start) literal.append(", ");
            literal.append(JavaWriter.stringLiteral(members[i]));
        }
        return literal.append("}").toString();
    }

    private String javaType(TypeMirror type) {
        return types.concrete(type).toString();
    }

    private String literal(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return JavaWriter.stringLiteral((String) value);
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Double) {
            return "Double.valueOf(" + JavaWriter.stringLiteral(value.toString()) + ")";
        }
        throw new AssertionError(value.getClass().getName());
    }

    private String predicate(FilterPlan plan) {
        switch (plan.kind) {
            case LITERAL:
            case FUNCTION:
                return "org.sjf4j.path.FilterExpr.truth(" + value(plan) + ")";
            case PATH:
                if (plan.members.length == 0) return "true";
                if (directPaths.containsKey(plan) || plan.selectors != null) {
                    return "(" + value(plan) +
                            " != org.sjf4j.path.PathSupport.missing())";
                }
                return "org.sjf4j.path.PathSupport.exists(" +
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
                if (plan.literal instanceof Double || plan.literal instanceof Pattern
                        || plan.literal instanceof List) {
                    return constants.get(plan);
                }
                return literal(plan.literal);
            case PATH:
                if (plan.members.length == 0) return plan.rooted ? "root" : "current";
                if (directPaths.containsKey(plan)) {
                    return directPaths.get(plan) + "(" +
                            (plan.rooted ? "root" : "current") + ", access)";
                }
                if (plan.selectors != null) {
                    return "org.sjf4j.path.PathSupport.indexedValue(" +
                            (plan.rooted ? "root" : "current") + ", " +
                            paths.get(plan) + ", 0, access)";
                }
                return "org.sjf4j.path.PathSupport.value(" +
                        (plan.rooted ? "root" : "current") + ", " +
                        paths.get(plan) + ", access)";
            case FUNCTION:
                StringBuilder args = new StringBuilder();
                if (plan.arguments.length == 1) {
                    args.append(noArgsField);
                } else {
                    args.append("new Object[] {");
                    for (int i = 1; i < plan.arguments.length; i++) {
                        if (i > 1) args.append(", ");
                        args.append(value(plan.arguments[i]));
                    }
                    args.append("}");
                }
                return "org.sjf4j.path.FunctionRegistry.invoke(" +
                        JavaWriter.stringLiteral(plan.functionName) + ", " +
                        value(plan.arguments[0]) + ", " + args + ")";
            case NOT: case AND: case OR: case COMPARE:
                return "Boolean.valueOf(" + predicate(plan) + ")";
            default:
                throw new AssertionError(plan.kind);
        }
    }
}
