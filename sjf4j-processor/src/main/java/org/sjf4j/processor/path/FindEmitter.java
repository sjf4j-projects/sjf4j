package org.sjf4j.processor.path;

import org.sjf4j.NodeKind;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Emits direct Java traversal from a fully resolved FindPlan. No path shape,
 * member access, or Java type resolution is performed while writing source.
 */
final class FindEmitter {
    private final TypeSystem types;

    FindEmitter(ProcessorContext context) {
        this.types = context.types;
    }

    void emit(FindPlan plan, GeneratedClass generated) {
        final Map<FindPlan.Step, String> filters = new IdentityHashMap<>();
        final String fallback;
        if (plan.runtimeFallback) {
            fallback = generated.names().field("FIND_PATH");
            generated.addField(out -> out.line(
                    "private static final org.sjf4j.path.JsonPath " + fallback +
                    " = org.sjf4j.path.JsonPath.parse(" +
                    JavaWriter.stringLiteral(plan.expression) + ");"));
        } else {
            fallback = null;
            prepareFilters(plan.traversal, plan.expression, generated, filters, -1, -1);
        }

        generated.addMethod(out -> {
            VariableElement root = plan.root;
            String rootName = root.getSimpleName().toString();
            NameAllocator names = new NameAllocator();
            names.reserve(rootName);

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

            if (plan.runtimeFallback) {
                // Runtime path is cached once per generated interface, not parsed per invocation.
                out.beginBlock("for (Object value : " + fallback + ".find(" + rootName + "))");
                out.line(result + ".add(" + convertResult(plan.elementType, "value") + ");");
                out.endBlock();
            } else {
                emitStep(out, names, plan.traversal, rootName, rootName, result, filters);
            }

            out.line("return " + result + ";");
            out.endBlock();
        });

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
                          Map<FindPlan.Step, String> filters) {
        switch (step.kind) {
            case COLLECT:
                out.line(result + ".add(" + owner + ");");
                return;
            case EMPTY:
                return;
            case UNION:
                for (FindPlan.Step alternative : step.alternatives) {
                    emitStep(out, names, alternative, owner, root, result, filters);
                }
                return;
            default:
                break;
        }

        boolean guarded = !step.ownerType.getKind().isPrimitive();
        if (guarded) out.beginBlock("if (" + owner + " != null)");

        switch (step.kind) {
            case READ:
                emitRead(out, names, step, owner, root, result, filters);
                break;
            case WILDCARD:
            case SLICE:
            case FILTER:
                emitSelection(out, names, step, owner, root, result, filters);
                break;
            default:
                throw new AssertionError(step.kind);
        }

        if (guarded) out.endBlock();
    }

    private void emitRead(JavaWriter out, NameAllocator names, FindPlan.Step step,
                          String owner, String root, String result,
                          Map<FindPlan.Step, String> filters) {
        NodeAccess access = step.access;
        String value = names.newName("value");
        String expression;

        if (step.segment instanceof PathSegment.Name) {
            String key = JavaWriter.stringLiteral(((PathSegment.Name) step.segment).name);
            switch (access.kind()) {
                case PROPERTY: {
                    PropertyAccess read = access.property().read();
                    expression = owner + "." + read.memberName() + (read.isMethod() ? "()" : "");
                    break;
                }
                case MAP:
                    expression = owner + ".get(" + key + ")";
                    break;
                case JSON_OBJECT:
                    expression = owner + ".getNode(" + key + ")";
                    break;
                default:
                    throw new AssertionError(access.kind());
            }
            out.line(localType(step.valueType) + " " + value + " = " +
                    convertResult(step.valueType, expression) + ";");

            if (access.kind() == NodeAccess.Kind.MAP ||
                    access.kind() == NodeAccess.Kind.JSON_OBJECT) {
                out.beginBlock("if (" + value + " != null || " + owner +
                        ".containsKey(" + key + "))");
                emitStep(out, names, step.next, value, root, result, filters);
                out.endBlock();
            } else {
                emitStep(out, names, step.next, value, root, result, filters);
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
            expression = elementExpression(step.ownerType, owner, index);
            out.line(localType(step.valueType) + " " + value + " = " +
                    convertResult(step.valueType, expression) + ";");
            emitStep(out, names, step.next, value, root, result, filters);
            out.endBlock();
            return;
        }

        throw new AssertionError(step.segment);
    }

    private void emitSelection(JavaWriter out, NameAllocator names, FindPlan.Step step,
                               String owner, String root, String result,
                               Map<FindPlan.Step, String> filters) {
        NodeKind kind = types.nodeKind(step.ownerType);
        boolean map = kind == NodeKind.OBJECT_MAP;
        boolean array = step.ownerType.getKind() == TypeKind.ARRAY;
        boolean list = kind == NodeKind.ARRAY_LIST;
        boolean slice = step.kind == FindPlan.Kind.SLICE;

        String item = names.newName("item");
        if (map || (!slice && list)) {
            String iterable = map ? owner + ".values()" : owner;
            out.beginBlock("for (" + localType(step.valueType) + " " + item + " : " + iterable + ")");
        } else {
            String size = names.newName("size");
            out.line("int " + size + " = " + (array ? owner + ".length" : owner + ".size()") + ";");

            if (slice) {
                PathSegment.Slice sliceValue = (PathSegment.Slice) step.segment;
                if (sliceValue.step != null && sliceValue.step == 0L) return;
                boolean reverse = sliceValue.step != null && sliceValue.step < 0L;
                String iterator = null;
                if (list) {
                    iterator = names.newName("iterator");
                    out.line("java.util.ListIterator<? extends " + localType(step.valueType) + "> " +
                            iterator + " = " + owner + ".listIterator(" + (reverse ? size : "0") + ");");
                }
                String index = emitSliceLoop(out, names, sliceValue, size);
                if (list) {
                    out.beginBlock("while (" + iterator + "." +
                            (reverse ? "previousIndex() > " : "nextIndex() < ") + index + ")");
                    out.line(iterator + "." + (reverse ? "previous()" : "next()") + ";");
                    out.endBlock();
                    out.line(localType(step.valueType) + " " + item + " = " +
                            iterator + "." + (reverse ? "previous()" : "next()") + ";");
                } else {
                    out.line(localType(step.valueType) + " " + item + " = " +
                            convertResult(step.valueType, elementExpression(step.ownerType, owner, index)) + ";");
                }
            } else {
                String index = names.newName("index");
                out.beginBlock("for (int " + index + " = 0; " + index +
                        " < " + size + "; " + index + "++)");
                out.line(localType(step.valueType) + " " + item + " = " +
                        convertResult(step.valueType, elementExpression(step.ownerType, owner, index)) + ";");
            }
        }

        if (step.kind == FindPlan.Kind.FILTER) {
            out.beginBlock("if (" + filters.get(step) + ".evalTruth(" + root + ", " + item + "))");
        }
        emitStep(out, names, step.next, item, root, result, filters);
        if (step.kind == FindPlan.Kind.FILTER) out.endBlock();
        out.endBlock();
    }

    private String elementExpression(TypeMirror ownerType, String receiver, String index) {
        if (ownerType.getKind() == TypeKind.ARRAY) return receiver + "[" + index + "]";
        NodeKind kind = types.nodeKind(ownerType);
        if (kind == NodeKind.ARRAY_JSON_ARRAY || kind == NodeKind.ARRAY_JAJO) {
            return receiver + ".getNode(" + index + ")";
        }
        return receiver + ".get(" + index + ")";
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
