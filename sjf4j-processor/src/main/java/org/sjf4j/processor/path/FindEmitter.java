package org.sjf4j.processor.path;

import org.sjf4j.NodeKind;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits direct Java traversal from a fully resolved FindPlan. No path shape,
 * member access, or Java type resolution is performed while writing source.
 */
final class FindEmitter {
    private final TypeSystem types;
    private final NodeReadEmitter reads;

    FindEmitter(ProcessorContext context) {
        this.types = context.types;
        this.reads = new NodeReadEmitter(context.types);
    }

    void emit(FindPlan plan, GeneratedClass generated) {
        final Map<FindPlan.Step, String> filters = new IdentityHashMap<>();
        prepareFilters(plan.traversal, plan.expression, generated, filters, -1, -1);

        final Map<FindPlan.Step, String> descendants = new LinkedHashMap<>();
        collectDescendants(plan.traversal, generated, descendants);
        for (Map.Entry<FindPlan.Step, String> descendant : descendants.entrySet()) {
            generated.addHelper(out -> emitDescendantHelper(
                    out, plan, descendant.getKey(), descendant.getValue(),
                    filters, descendants));
        }

        generated.addMethod(out -> {
            VariableElement root = plan.root;
            String rootName = root.getSimpleName().toString();
            NameAllocator names = generated.names().locals(plan.method);

            out.line("@Override");
            out.beginBlock("public " + plan.method.getReturnType() + " " +
                    plan.method.getSimpleName() + "(" + root.asType() + " " + rootName + ")");

            if (!root.asType().getKind().isPrimitive()) {
                out.line("org.sjf4j.util.Asserts.notNull(" + rootName + ", " +
                        JavaWriter.stringLiteral(rootName) + ");");
            }

            String result = names.newName("result");
            out.line("java.util.ArrayList<" + localType(plan.elementType) + "> " + result +
                    " = new java.util.ArrayList<>();");

            String access = "null";
            if (needsAccess(plan.traversal)) {
                access = names.newName("access");
                out.line("org.sjf4j.node.NodeWalker.Access " + access +
                        " = new org.sjf4j.node.NodeWalker.Access();");
            }
            emitStep(out, names, plan.traversal, rootName, rootName, result,
                    filters, descendants, access);
            out.line("return " + result + ";");
            out.endBlock();
        });
    }

    private void collectDescendants(FindPlan.Step step, GeneratedClass generated,
                                    Map<FindPlan.Step, String> descendants) {
        if (step == null) return;
        if (step.kind == FindPlan.Kind.DESCENDANT) {
            descendants.put(step, generated.names().helperMethod("findDescendant"));
        }
        for (FindPlan.Step alternative : step.alternatives) {
            collectDescendants(alternative, generated, descendants);
        }
        collectDescendants(step.next, generated, descendants);
    }

    // A query-local Access is also reused by filter path predicates.
    private boolean needsAccess(FindPlan.Step step) {
        if (step == null) return false;
        if (step.kind == FindPlan.Kind.FILTER
                || (step.kind == FindPlan.Kind.READ && step.access != null
                && step.access.kind() == NodeAccess.Kind.DYNAMIC)) return true;
        for (FindPlan.Step alternative : step.alternatives) {
            if (needsAccess(alternative)) return true;
        }
        return needsAccess(step.next);
    }

    private void emitDescendantHelper(JavaWriter out, FindPlan plan,
                                      FindPlan.Step descendant, String helper,
                                      Map<FindPlan.Step, String> filters,
                                      Map<FindPlan.Step, String> descendants) {
        out.beginBlock("private static void " + helper + "(" + plan.root.asType() +
                " root, Object current, java.util.List<" + localType(plan.elementType) +
                "> result, org.sjf4j.node.NodeWalker.Access access)");
        out.line("if (current == null) return;");

        NameAllocator names = new NameAllocator()
                .reserve("root").reserve("current").reserve("result").reserve("access");
        // Classify once per visited node and reuse that shape for the selector
        // and the recursive descent. The null check above also guards the selector.
        String shape = names.newName("shape");
        out.line("org.sjf4j.JsonType " + shape + " = org.sjf4j.JsonType.of(current);");
        emitStep(out, names, descendant.next, "current", "root", "result",
                filters, descendants, "access", shape);
        out.beginBlock("if (" + shape + ".isObject())");
        out.beginBlock("org.sjf4j.node.NodeWalker.forEachObject(current, (key, child) ->");
        out.line(helper + "(root, child, result, access);");
        out.endBlock(");");
        out.endBlock();
        out.beginBlock("else if (" + shape + ".isArray())");
        out.beginBlock("org.sjf4j.node.NodeWalker.forEachArray(current, (index, child) ->");
        out.line(helper + "(root, child, result, access);");
        out.endBlock(");");
        out.endBlock();
        out.endBlock();
    }

    private void prepareFilters(FindPlan.Step step, String expression,
                                GeneratedClass generated, Map<FindPlan.Step, String> fields,
                                int unionIndex, int choiceIndex) {
        if (step == null) return;
        if (step.kind == FindPlan.Kind.FILTER) {
            String field = generated.names().field("FIND_FILTER");
            fields.put(step, field);

            String parsed = "org.sjf4j.path.JsonPath.parse(" +
                    JavaWriter.stringLiteral(expression) + ").segments()[" + step.segmentIndex + "]";
            if (unionIndex == step.segmentIndex) {
                parsed = "((org.sjf4j.path.PathSegment.Union) " + parsed +
                        ").union[" + choiceIndex + "]";
            }
            final String filterReference = parsed;
            generated.addField(out -> out.line(
                    "private static final org.sjf4j.path.FilterExpr " + field +
                            " = ((org.sjf4j.path.PathSegment.Filter) " + filterReference +
                            ").filterExpr;"));
        }
        if (step.kind == FindPlan.Kind.UNION) {
            for (int i = 0; i < step.alternatives.size(); i++) {
                prepareFilters(step.alternatives.get(i), expression, generated, fields,
                        step.segmentIndex, i);
            }
        } else {
            prepareFilters(step.next, expression, generated, fields, unionIndex, choiceIndex);
        }
    }

    private void emitStep(JavaWriter out, NameAllocator names, FindPlan.Step step,
                          String owner, String root, String result,
                          Map<FindPlan.Step, String> filters,
                          Map<FindPlan.Step, String> descendants, String accessVar) {
        emitStep(out, names, step, owner, root, result, filters, descendants, accessVar, null);
    }

    private void emitStep(JavaWriter out, NameAllocator names, FindPlan.Step step,
                          String owner, String root, String result,
                          Map<FindPlan.Step, String> filters,
                          Map<FindPlan.Step, String> descendants, String accessVar,
                          String knownShape) {
        switch (step.kind) {
            case COLLECT: {
                String value = owner;
                if (!types.isObject(step.valueType) &&
                        !types.isAssignableBoxedGeneric(step.ownerType, step.valueType)) {
                    value = "(" + localType(step.valueType) + ") " + owner;
                }
                out.line(result + ".add(" + value + ");");
                return;
            }
            case EMPTY:
                return;
            case UNION:
                for (FindPlan.Step alternative : step.alternatives) {
                    emitStep(out, names, alternative, owner, root, result,
                            filters, descendants, accessVar, knownShape);
                }
                return;
            case DESCENDANT:
                out.line(descendants.get(step) + "(" + root + ", " + owner +
                        ", " + result + ", " + accessVar + ");");
                return;
            default:
                break;
        }

        boolean guarded = knownShape == null && !step.ownerType.getKind().isPrimitive();
        if (guarded) out.beginBlock("if (" + owner + " != null)");

        switch (step.kind) {
            case READ:
                emitRead(out, names, step, owner, root, result, filters, descendants,
                        accessVar, knownShape);
                break;
            case WILDCARD:
            case SLICE:
            case FILTER:
                emitSelection(out, names, step, owner, root, result, filters,
                        descendants, accessVar, knownShape);
                break;
            default:
                throw new AssertionError(step.kind);
        }
        if (guarded) out.endBlock();
    }

    private void emitRead(JavaWriter out, NameAllocator names, FindPlan.Step step,
                          String owner, String root, String result,
                          Map<FindPlan.Step, String> filters,
                          Map<FindPlan.Step, String> descendants, String accessVar,
                          String knownShape) {
        NodeAccess nodeAccess = step.access;
        String value = names.newName("value");
        if (nodeAccess.kind() == NodeAccess.Kind.DYNAMIC) {
            boolean name = step.segment instanceof PathSegment.Name;
            String shape = knownShape != null ? knownShape : names.newName("shape");
            if (knownShape == null) {
                out.line("org.sjf4j.JsonType " + shape + " = org.sjf4j.JsonType.of(" + owner + ");");
            }
            out.beginBlock("if (" + shape + (name ? ".isObject()" : ".isArray()") + ")");
            if (name) {
                String key = JavaWriter.stringLiteral(((PathSegment.Name) step.segment).name);
                out.line("org.sjf4j.node.NodeWalker.getAccessInObject(" + owner +
                        ", " + key + ", " + accessVar + ");");
            } else {
                int index = ((PathSegment.Index) step.segment).index;
                out.line("org.sjf4j.node.NodeWalker.getAccessInArray(" + owner + ", " +
                        index + ", " + accessVar + ");");
            }
            out.beginBlock("if (" + accessVar + ".present)");
            out.line("Object " + value + " = " + accessVar + ".node;");
            emitStep(out, names, step.next, value, root, result,
                    filters, descendants, accessVar);
            out.endBlock();
            out.endBlock();
            return;
        }

        if (step.segment instanceof PathSegment.Name) {
            String key = JavaWriter.stringLiteral(((PathSegment.Name) step.segment).name);
            String expression = reads.name(nodeAccess, owner, key);
            out.line(localType(step.valueType) + " " + value + " = " + expression + ";");
            if (nodeAccess.kind() == NodeAccess.Kind.MAP ||
                    nodeAccess.kind() == NodeAccess.Kind.JSON_OBJECT) {
                out.beginBlock("if (" + value + " != null || " + owner +
                        ".containsKey(" + key + "))");
                emitStep(out, names, step.next, value, root, result,
                        filters, descendants, accessVar);
                out.endBlock();
            } else {
                emitStep(out, names, step.next, value, root, result,
                        filters, descendants, accessVar);
            }
            return;
        }

        if (step.segment instanceof PathSegment.Index) {
            String size = step.ownerType.getKind() == TypeKind.ARRAY
                    ? owner + ".length" : owner + ".size()";
            String index = names.newName("index");
            out.line("int " + index + " = " + ((PathSegment.Index) step.segment).index + ";");
            out.line("if (" + index + " < 0) " + index + " += " + size + ";");
            out.beginBlock("if (" + index + " >= 0 && " + index + " < " + size + ")");
            out.line(localType(step.valueType) + " " + value + " = " +
                    reads.index(nodeAccess, owner, index) + ";");
            emitStep(out, names, step.next, value, root, result,
                    filters, descendants, accessVar);
            out.endBlock();
            return;
        }
        throw new AssertionError(step.segment);
    }

    private void emitSelection(JavaWriter out, NameAllocator names, FindPlan.Step step,
                               String owner, String root, String result,
                               Map<FindPlan.Step, String> filters,
                               Map<FindPlan.Step, String> descendants, String accessVar,
                               String knownShape) {
        NodeKind kind = types.nodeKind(step.ownerType);
        boolean slice = step.kind == FindPlan.Kind.SLICE;
        String item = names.newName("item");

        // Direct enhanced-for paths for known collection types.
        if (!slice && (kind == NodeKind.OBJECT_MAP || kind == NodeKind.ARRAY_LIST
                || kind == NodeKind.ARRAY_SET)) {
            String iterable = kind == NodeKind.OBJECT_MAP ? owner + ".values()" : owner;
            out.beginBlock("for (" + localType(step.valueType) + " " + item +
                    " : " + iterable + ")");
            emitSelectionChild(out, names, step, item, root, result,
                    filters, descendants, accessVar);
            out.endBlock();
            return;
        }

        // JsonObject/JOJO can visit declared and dynamic members directly,
        // without allocating entry snapshots for declared JOJO properties.
        if (!slice && (kind == NodeKind.OBJECT_JSON_OBJECT
                || kind == NodeKind.OBJECT_JOJO)) {
            String key = names.newName("key");
            out.beginBlock(owner + ".forEach((" + key + ", " + item + ") ->");
            emitSelectionChild(out, names, step, item, root, result,
                    filters, descendants, accessVar);
            out.endBlock(");");
            return;
        }

        // POJO wildcard/filter are object-property enumeration, not Java array access.
        if (!slice && kind == NodeKind.OBJECT_POJO) {
            emitObjectCallback(out, names, step, owner, root, result,
                    filters, descendants, accessVar, item);
            return;
        }

        // An Object or external tree can represent either an array or an object.
        if (kind == NodeKind.COMPILE_TIME_UNKNOWN) {
            String shape = knownShape != null ? knownShape : names.newName("shape");
            if (knownShape == null) {
                out.line("org.sjf4j.JsonType " + shape + " = org.sjf4j.JsonType.of(" + owner + ");");
            }
            if (!slice) {
                out.beginBlock("if (" + shape + ".isObject())");
                emitObjectCallback(out, names, step, owner, root, result,
                        filters, descendants, accessVar, item);
                out.endBlock();
            }
            out.beginBlock((slice ? "if (" : "else if (") + shape + ".isArray())");
            if (slice) {
                // NodeWalker preserves runtime bounds and Set-index rejection semantics.
                emitIndexedSelection(out, names, step, owner, root, result,
                        filters, descendants, accessVar, item, true);
            } else {
                emitArrayCallback(out, names, step, owner, root, result,
                        filters, descendants, accessVar, item);
            }
            out.endBlock();
            if (step.kind == FindPlan.Kind.FILTER) {
                out.beginBlock("else");
                out.beginBlock("if (" + filters.get(step) +
                        ".evalTruth(" + root + ", " + owner + ", " + accessVar + "))");
                emitStep(out, names, step.next, owner, root, result,
                        filters, descendants, accessVar);
                out.endBlock();
                out.endBlock();
            }
            return;
        }

        if (step.kind == FindPlan.Kind.FILTER && kind.isValue()) {
            out.beginBlock("if (" + filters.get(step) +
                    ".evalTruth(" + root + ", " + owner + ", " + accessVar + "))");
            emitStep(out, names, step.next, owner, root, result,
                    filters, descendants, accessVar);
            out.endBlock();
            return;
        }

        emitIndexedSelection(out, names, step, owner, root, result,
                filters, descendants, accessVar, item, false);
    }

    private void emitObjectCallback(JavaWriter out, NameAllocator names, FindPlan.Step step,
                                    String owner, String root, String result,
                                    Map<FindPlan.Step, String> filters,
                                    Map<FindPlan.Step, String> descendants,
                                    String accessVar, String item) {
        String key = names.newName("key");
        out.beginBlock("org.sjf4j.node.NodeWalker.forEachObject(" + owner +
                ", (" + key + ", " + item + ") ->");
        emitSelectionChild(out, names, step, item, root, result,
                filters, descendants, accessVar);
        out.endBlock(");");
    }

    private void emitArrayCallback(JavaWriter out, NameAllocator names, FindPlan.Step step,
                                   String owner, String root, String result,
                                   Map<FindPlan.Step, String> filters,
                                   Map<FindPlan.Step, String> descendants,
                                   String accessVar, String item) {
        String index = names.newName("index");
        out.beginBlock("org.sjf4j.node.NodeWalker.forEachArray(" + owner +
                ", (" + index + ", " + item + ") ->");
        emitSelectionChild(out, names, step, item, root, result,
                filters, descendants, accessVar);
        out.endBlock(");");
    }

    private void emitSelectionChild(JavaWriter out, NameAllocator names, FindPlan.Step step,
                                    String item, String root, String result,
                                    Map<FindPlan.Step, String> filters,
                                    Map<FindPlan.Step, String> descendants,
                                    String accessVar) {
        if (step.kind == FindPlan.Kind.FILTER) {
            out.beginBlock("if (" + filters.get(step) +
                    ".evalTruth(" + root + ", " + item + ", " + accessVar + "))");
        }
        emitStep(out, names, step.next, item, root, result,
                filters, descendants, accessVar);
        if (step.kind == FindPlan.Kind.FILTER) out.endBlock();
    }

    private void emitIndexedSelection(JavaWriter out, NameAllocator names, FindPlan.Step step,
                                      String owner, String root, String result,
                                      Map<FindPlan.Step, String> filters,
                                      Map<FindPlan.Step, String> descendants, String accessVar,
                                      String item, boolean dynamic) {
        boolean array = step.ownerType.getKind() == TypeKind.ARRAY;
        boolean list = types.nodeKind(step.ownerType) == NodeKind.ARRAY_LIST;
        String size = names.newName("size");
        out.line("int " + size + " = " +
                (dynamic ? "org.sjf4j.node.NodeWalker.sizeInArray(" + owner + ")"
                        : array ? owner + ".length" : owner + ".size()") + ";");

        if (step.kind == FindPlan.Kind.SLICE) {
            PathSegment.Slice slice = (PathSegment.Slice) step.segment;
            if (slice.step != null && slice.step == 0L) return;
            boolean reverse = slice.step != null && slice.step < 0;
            String iterator = null;
            if (list) {
                iterator = names.newName("iterator");
                out.line("java.util.ListIterator<? extends " + localType(step.valueType) +
                        "> " + iterator + " = " + owner +
                        ".listIterator(" + (reverse ? size : "0") + ");");
            }
            String index = emitSliceLoop(out, names, slice, size);
            if (list) {
                out.beginBlock("while (" + iterator + "." +
                        (reverse ? "previousIndex() > " : "nextIndex() < ") + index + ")");
                out.line(iterator + "." + (reverse ? "previous()" : "next()") + ";");
                out.endBlock();
                out.line(localType(step.valueType) + " " + item + " = " +
                        iterator + "." + (reverse ? "previous()": "next()") + ";");
            } else {
                String expression = dynamic
                        ? "org.sjf4j.node.NodeWalker.getInArray(" + owner + ", " + index + ")"
                        : reads.index(step.access, owner, index);
                out.line(localType(step.valueType) + " " + item + " = " + expression + ";");
            }
        } else {
            String index = names.newName("index");
            out.beginBlock("for (int " + index + " = 0; " + index +
                    " < " + size + "; " + index + "++)");
            String expression = dynamic
                    ? "org.sjf4j.node.NodeWalker.getInArray(" + owner + ", " + index + ")"
                    : reads.index(step.access, owner, index);
            out.line(localType(step.valueType) + " " + item + " = " + expression + ";");
        }

        emitSelectionChild(out, names, step, item, root, result,
                filters, descendants, accessVar);
        out.endBlock();
    }

    /** Emit a normalized slice loop, leaving the loop body indented. */
    private String emitSliceLoop(JavaWriter out, NameAllocator names,
                                 PathSegment.Slice slice, String size) {
        long step = slice.step == null ? 1L : slice.step;
        String start = names.newName("sliceStart");
        String end = names.newName("sliceEnd");
        String position = names.newName("position");
        String index = names.newName("index");
        boolean reverse = step < 0;

        out.line("long " + start + " = " +
                (slice.start == null ? (reverse ? size + " - 1L" : "0L") : slice.start + "L") + ";");
        out.line("long " + end + " = " +
                (slice.end == null ? (reverse ? "-1L" : size) : slice.end + "L") + ";");
        if (slice.start != null && slice.start < 0) out.line(start + " += " + size + ";");
        if (slice.end != null && slice.end < 0) out.line(end + " += " + size + ";");

        if (reverse) {
            out.line(start + " = Math.min(Math.max(" + start + ", -1L), " + size + " - 1L);");
            out.line(end + " = Math.min(Math.max(" + end + ", -1L), " + size + " - 1L);");
        } else {
            out.line(start + " = Math.min(Math.max(" + start + ", 0L), " + size + ");");
            out.line(end + " = Math.min(Math.max(" + end + ", 0L), " + size + ");");
        }

        out.beginBlock("for (long " + position + " = " + start + "; " + position +
                (reverse ? " > " : " < ") + end + "; " + position + " += " + step + "L)");
        out.line("int " + index + " = (int) " + position + ";");
        return index;
    }

    private String localType(TypeMirror type) {
        return types.boxed(types.concrete(type)).toString();
    }

    private String convertResult(TypeMirror type, String expression) {
        if (types.isObject(type)) return expression;
        return "(" + localType(type) + ") " + expression;
    }
}
