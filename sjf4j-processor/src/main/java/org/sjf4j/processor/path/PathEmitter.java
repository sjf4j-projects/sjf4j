package org.sjf4j.processor.path;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.access.NodeWriteEmitter;
import org.sjf4j.processor.type.TypeSystem;
import org.sjf4j.processor.path.PathPlan.Step;
import org.sjf4j.processor.path.PathPlan.Creation;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Emits direct Java source for fully compiled Get/Put path plans. */
final class PathEmitter {

    private final TypeSystem types;
    private final NodeReadEmitter reads;

    PathEmitter(CompilationContext context) {
        this.types = context.types;
        this.reads = new NodeReadEmitter(context.types);

    }

    void emit(PathPlan plan, GeneratedClass generated) {
        if (plan instanceof PathPlan.Get) {
            generated.addMethod(out -> emitGet(out, (PathPlan.Get) plan));
        } else if (plan instanceof PathPlan.Put) {
            generated.addMethod(out -> emitPut(out, (PathPlan.Put) plan));
        } else {
            throw new AssertionError("Unsupported compiled path plan");
        }
    }

    /*
     * --------------------------------------------------------------
     * Emit
     * --------------------------------------------------------------
     */

    private void emitGet(
            JavaWriter out,
            PathPlan.Get resolved) {

        ExecutableElement method = resolved.plan.method;
        NameAllocator names = names(method);

        out.line("@Override");
        out.beginBlock(methodHeader(method));

        String root = resolved.plan.root.getSimpleName().toString();
        String missing = getMissing(method, resolved.plan);

        if (!resolved.plan.root
                .asType()
                .getKind()
                .isPrimitive()) {

            out.line(
                    "if (" +
                            root +
                            " == null) " +
                            missing);
        }

        String current = root;

        for (Step step : resolved.steps) {
            String next = names.newName(stepHint(step));
            emitRead(out, step, current, next, names, ReadMode.STRICT, missing);
            current = next;
        }

        out.line(
                "return " +
                        current +
                        ";");

        out.endBlock();
    }

    /*
     * --------------------------------------------------------------
     * Emit Put
     * --------------------------------------------------------------
     */

    private void emitPut(
            JavaWriter out,
            PathPlan.Put resolved) {

        if (resolved.plan.ensure) {
            emitEnsurePut(
                    out,
                    resolved);
        } else {
            emitPlainPut(
                    out,
                    resolved);
        }
    }

    private void emitPlainPut(
            JavaWriter out,
            PathPlan.Put resolved) {

        NavigatorPlan.Put plan = resolved.plan;
        ExecutableElement method = plan.method;
        NameAllocator names = names(method);

        out.line("@Override");
        out.beginBlock(methodHeader(method));

        String root = plan.root.getSimpleName().toString();
        String value = plan.value.getSimpleName().toString();

        String missing =
                plan.ifParentPresent
                        ? nullReturn(method)
                        : putMissingThrow(plan);

        if (!plan.root
                .asType()
                .getKind()
                .isPrimitive()) {

            out.line(
                    "if (" +
                            root +
                            " == null) " +
                            missing);
        }

        String current = root;

        for (Step step : resolved.steps) {
            String next = names.newName(stepHint(step));
            emitRead(out, step, current, next, names, ReadMode.STRICT, missing);
            current = next;
        }

        boolean needOld =
                method.getReturnType().getKind() != TypeKind.VOID &&
                        resolved.target.oldType != null;

        String old = needOld ? names.newName("oldValue") : null;
        emitWrite(out, resolved.target, current, value, old, names);
        emitOldReturn(out, method, old, resolved.target.oldType);

        out.endBlock();
    }

    private void emitEnsurePut(
            JavaWriter out,
            PathPlan.Put resolved) {

        NavigatorPlan.Put plan = resolved.plan;
        ExecutableElement method = plan.method;
        NameAllocator names = names(method);

        out.line("@Override");
        out.beginBlock(methodHeader(method));

        String root = plan.root.getSimpleName().toString();
        String value = plan.value.getSimpleName().toString();

        out.line(
                "org.sjf4j.util.Asserts.notNull(" +
                        root +
                        ", " +
                        JavaWriter.stringLiteral(root) +
                        ");");

        String current = root;

        for (Step step : resolved.steps) {
            String next = names.newName(stepHint(step));

            /*
             * Intermediate append creates a fresh child immediately.
             */
            if (step.resolved.segment() instanceof PathSegment.Append) {
                out.line(
                        localType(step.resultType) +
                                " " +
                                next +
                                " = " +
                                creationExpression(step.creation) +
                                ";");

                emitWrite(out, step, current, next, null, names);

                current = next;
                continue;
            }

            emitRead(out, step, current, next, names, ReadMode.ENSURE, null);

            if (!step.resultType
                    .getKind()
                    .isPrimitive()) {

                out.beginBlock("if (" + next + " == null)");

                if (step.resolved.access().writable()) {
                    if (step.creation == null) {
                        throw new AssertionError(
                                "Missing ensure creation expression");
                    }

                    out.line(
                            next +
                                    " = " +
                                    creationExpression(step.creation) +
                                    ";");

                    emitWrite(out, step, current, next, null, names);

                } else {
                    emitReadonlyEnsureFailure(out, step);
                }

                out.endBlock();
            }

            current = next;
        }

        if (plan.ifAbsent) {
            emitIfAbsent(out, resolved, current, value, names);

        } else {
            boolean needOld =
                    method.getReturnType().getKind() != TypeKind.VOID &&
                            resolved.target.oldType != null;

            String old = needOld ? names.newName("oldValue") : null;
            emitWrite(out, resolved.target, current, value, old, names);
            emitOldReturn(out, method, old, resolved.target.oldType);
        }

        out.endBlock();
    }

    /*
     * --------------------------------------------------------------
     * Path Read
     * --------------------------------------------------------------
     */

    private void emitRead(
            JavaWriter out,
            Step step,
            String owner,
            String target,
            NameAllocator names,
            ReadMode mode,
            String missing) {

        TypeMirror type = step.resolved.access().readType();

        switch (step.resolved.access().kind()) {
            case PROPERTY:
            case MAP:
            case JSON_OBJECT:
                out.line(localType(type) + " " + target + " = " +
                        reads.name(step.resolved.access(), owner, nameExpression(step)) + ";");
                break;

            case LIST:
                emitIndexedRead(
                        out,
                        step,
                        owner,
                        target,
                        names,
                        type,
                        mode,
                        missing,
                        false);
                break;

            case ARRAY:
                emitIndexedRead(
                        out,
                        step,
                        owner,
                        target,
                        names,
                        type,
                        mode,
                        missing,
                        true);
                break;

            case JSON_ARRAY:
                emitIndexedRead(
                        out,
                        step,
                        owner,
                        target,
                        names,
                        type,
                        mode,
                        missing,
                        false);
                break;

            case DYNAMIC:
                if (isIndexStep(step)) {
                    emitObjectResult(
                            out,
                            type,
                            target,
                            "org.sjf4j.node.NodeWalker.getInArray(" +
                                    owner +
                                    ", " +
                                    rawIndexExpression(step) +
                                    ")");
                } else {
                    emitObjectResult(
                            out,
                            type,
                            target,
                            "org.sjf4j.node.NodeWalker.getInObject(" +
                                    owner +
                                    ", " +
                                    nameExpression(step) +
                                    ")");
                }
                break;

            default:
                throw new AssertionError(
                        "Unsupported NodeAccess kind: " +
                                step.resolved.access().kind());
        }

        if (mode == ReadMode.STRICT &&
                !type.getKind().isPrimitive()) {

            out.line(
                    "if (" +
                            target +
                            " == null) " +
                            missing);
        }
    }

    private void emitIndexedRead(
            JavaWriter out,
            Step step,
            String owner,
            String target,
            NameAllocator names,
            TypeMirror type,
            ReadMode mode,
            String missing,
            boolean array) {

        String size = array ? owner + ".length" : owner + ".size()";
        String index = emitIndex(out, step, size, names);

        String expression = reads.index(step.resolved.access(), owner, index);

        if (mode == ReadMode.ENSURE) {
            out.line(
                    localType(type) +
                            " " +
                            target +
                            " = (" +
                            index +
                            " < 0 || " +
                            index +
                            " >= " +
                            size +
                            ") ? null : " +
                            expression +
                            ";");

            return;
        }

        out.line(
                "if (" +
                        index +
                        " < 0 || " +
                        index +
                        " >= " +
                        size +
                        ") " +
                        missing);

        out.line(
                localType(type) +
                        " " +
                        target +
                        " = " +
                        expression +
                        ";");
    }

    private void emitObjectResult(
            JavaWriter out,
            TypeMirror type,
            String target,
            String expression) {

        out.line(
                localType(type) +
                        " " +
                        target +
                        " = " +
                        objectExpression(
                                type,
                                expression) +
                        ";");
    }

    private String objectExpression(
            TypeMirror type,
            String expression) {

        if (types.isObject(type)) return expression;

        return "(" + localType(type) + ") " + expression;
    }

    /*
     * --------------------------------------------------------------
     * Path Write
     * --------------------------------------------------------------
     */

    /**
     * Emits one write. When {@code old} is null the previous value is not read
     * or captured.
     */
    private void emitWrite(
            JavaWriter out,
            Step step,
            String owner,
            String value,
            String old,
            NameAllocator names) {

        if (step.resolved.segment() instanceof PathSegment.Append) {
            emitAppend(
                    out,
                    step,
                    owner,
                    value);
            return;
        }

        switch (step.resolved.access().kind()) {
            case PROPERTY:
                if (old != null) {
                    emitPropertyOld(
                            out,
                            step,
                            owner,
                            old);
                }

                out.line(NodeWriteEmitter.name(step.resolved.access(), owner, null, value) + ";");
                return;

            case MAP: {
                String expression = NodeWriteEmitter.name(step.resolved.access(), owner, nameExpression(step), value);

                emitOptionalOld(out, step, old, expression);

                return;
            }

            case JSON_OBJECT: {
                String expression = NodeWriteEmitter.name(step.resolved.access(), owner, nameExpression(step), value);

                emitOptionalObjectOld(out, step, old, expression);

                return;
            }

            case LIST:
            case ARRAY:
            case JSON_ARRAY:
                emitIndexWrite(out, step, owner, value, old, names);
                return;

            case DYNAMIC: {
                String expression = isIndexStep(step)
                        ? NodeWriteEmitter.index(step.resolved.access(), owner, rawIndexExpression(step), value)
                        : NodeWriteEmitter.name(step.resolved.access(), owner, nameExpression(step), value);

                emitOptionalObjectOld(out, step, old, expression);

                return;
            }

            default:
                throw new AssertionError(
                        "Unsupported NodeAccess kind: " +
                                step.resolved.access().kind());
        }
    }

    private void emitPropertyOld(
            JavaWriter out,
            Step step,
            String owner,
            String old) {

        if (!step.resolved.access().readable()) {
            out.line(
                    "Object " +
                            old +
                            " = null;");
            return;
        }

        out.line(localType(step.resolved.access().readType()) + " " + old + " = " +
                reads.name(step.resolved.access(), owner, nameExpression(step)) + ";");
    }

    private void emitOptionalOld(
            JavaWriter out,
            Step step,
            String old,
            String expression) {

        if (old == null) {
            out.line(
                    expression +
                            ";");
            return;
        }

        out.line(
                localType(step.oldType) +
                        " " +
                        old +
                        " = " +
                        expression +
                        ";");
    }

    private void emitOptionalObjectOld(
            JavaWriter out,
            Step step,
            String old,
            String expression) {

        if (old == null) {
            out.line(
                    expression +
                            ";");
            return;
        }

        TypeMirror type = step.oldType;

        out.line(
                localType(type) +
                        " " +
                        old +
                        " = " +
                        objectExpression(
                                type,
                                expression) +
                        ";");
    }

    /*
     * --------------------------------------------------------------
     * Indexed Write
     * --------------------------------------------------------------
     */

    private void emitIndexWrite(
            JavaWriter out,
            Step step,
            String owner,
            String value,
            String old,
            NameAllocator names) {

        if (step.resolved.segment() instanceof PathSegment.Append) {
            throw new AssertionError(
                    "Append must use emitAppend");
        }

        boolean array = step.resolved.access().kind() == NodeAccess.Kind.ARRAY;
        String size = array ? owner + ".length" : owner + ".size()";
        String index = emitIndex(out, step, size, names);

        if (array) {
            out.line(
                    "if (" +
                            index +
                            " == " +
                            owner +
                            ".length) " +
                            "throw new org.sjf4j.exception.NodeException(" +
                            JavaWriter.stringLiteral(
                                    "cannot append to a Java array") +
                            ");");

            out.line(
                    "if (" +
                            index +
                            " < 0 || " +
                            index +
                            " >= " +
                            owner +
                            ".length) " +
                            "throw new org.sjf4j.exception.NodeException(" +
                            JavaWriter.stringLiteral(
                                    "invalid Java array index") +
                            ");");

            if (old != null) {
                out.line(
                        localType(step.oldType) +
                                " " +
                                old +
                                " = " +
                                owner +
                                "[" +
                                index +
                                "];");
            }

            out.line(NodeWriteEmitter.index(step.resolved.access(), owner, index, value) + ";");

            return;
        }

        if (old != null) {
            out.line(
                    localType(step.oldType) +
                            " " +
                            old +
                            ";");
        }

        out.beginBlock(
                "if (" +
                        index +
                        " >= 0 && " +
                        index +
                        " < " +
                        owner +
                        ".size())");

        if (old != null) {
            out.line(
                    old +
                            " = " +
                            (step.resolved.access().kind() == NodeAccess.Kind.LIST
                                    || types.isObject(step.oldType)
                                    ? "" : "(" + localType(step.oldType) + ") ") +
                            NodeWriteEmitter.index(step.resolved.access(), owner, index, value) +
                            ";");
        } else {
            out.line(NodeWriteEmitter.index(step.resolved.access(), owner, index, value) + ";");
        }

        out.endBlock(
                " else if (" +
                        index +
                        " == " +
                        owner +
                        ".size()) {");

        out.indent();

        out.line(NodeWriteEmitter.append(step.resolved.access(), owner, value) + ";");

        if (old != null) {
            out.line(
                    old +
                            " = null;");
        }

        out.endBlock(" else {");
        out.indent();

        String container = step.resolved.access().kind() == NodeAccess.Kind.JSON_ARRAY ? "JsonArray" : "List";

        out.line(
                "throw new org.sjf4j.exception.NodeException(" +
                        JavaWriter.stringLiteral(
                                "cannot set at index ") +
                        " + " +
                        index +
                        " + " +
                        JavaWriter.stringLiteral(
                                " in " +
                                        container +
                                        " of size ") +
                        " + " +
                        owner +
                        ".size());");

        out.endBlock();
    }

    private void emitAppend(JavaWriter out, Step step, String owner, String value) {
        out.line(NodeWriteEmitter.append(step.resolved.access(), owner, value) + ";");
    }

    /*
     * --------------------------------------------------------------
     * Put If Absent
     * --------------------------------------------------------------
     */

    private void emitIfAbsent(
            JavaWriter out,
            PathPlan.Put resolved,
            String owner,
            String value,
            NameAllocator names) {

        Step target = resolved.target;

        if (target.resolved.segment() instanceof PathSegment.Append) {
            emitWrite(out, target, owner, value, null, names);
            emitNullReturn(out, resolved.plan.method);

            return;
        }

        String old = names.newName("oldValue");
        emitRead(out, target, owner, old, names, ReadMode.ENSURE, null);

        out.beginBlock(
                "if (" +
                        old +
                        " != null)");

        if (resolved.plan.method
                .getReturnType()
                .getKind() == TypeKind.VOID) {

            out.line("return;");
        } else {
            out.line(
                    "return " +
                            old +
                            ";");
        }

        out.endBlock();

        emitWrite(out, target, owner, value, null, names);
        emitNullReturn(out, resolved.plan.method);
    }

    private String creationExpression(Creation creation) {
        return "new " + creation.typeName + (creation.diamond ? "<>()" : "()");
    }

    /*
     * --------------------------------------------------------------
     * Path Arguments
     * --------------------------------------------------------------
     */

    private String emitIndex(
            JavaWriter out,
            Step step,
            String size,
            NameAllocator names) {

        String index = names.newName("index");

        if (step.resolved.segment() instanceof PathSegment.Index) {
            int value = ((PathSegment.Index) step.resolved.segment()).index;

            out.line(
                    "int " +
                            index +
                            " = " +
                            staticIndex(
                                    value,
                                    size) +
                            ";");

        } else {
            String parameter = step.parameter.getSimpleName().toString();

            out.line(
                    "int " +
                            index +
                            " = " +
                            parameter +
                            " >= 0 ? " +
                            parameter +
                            " : " +
                            size +
                            " + " +
                            parameter +
                            ";");
        }

        return index;
    }

    private static String staticIndex(int index, String size) {
        if (index >= 0) return Integer.toString(index);

        if (index == Integer.MIN_VALUE) return size + " + " + index;

        return size + " - " + (-index);
    }

    private String nameExpression(
            Step step) {

        if (step.resolved.segment() instanceof PathSegment.Name) {
            return JavaWriter.stringLiteral(
                    ((PathSegment.Name)
                            step.resolved.segment()).name);
        }

        return step.parameter.getSimpleName().toString();
    }

    private String rawIndexExpression(
            Step step) {

        if (step.resolved.segment() instanceof PathSegment.Index) {
            return Integer.toString(
                    ((PathSegment.Index)
                            step.resolved.segment()).index);
        }

        return step.parameter.getSimpleName().toString();
    }

    private boolean isIndexStep(
            Step step) {

        if (step.resolved.segment() instanceof PathSegment.Index ||
                step.resolved.segment() instanceof PathSegment.Append) {

            return true;
        }

        return step.resolved.segment() instanceof PathSegment.Param && step.parameter != null
                && step.parameter.asType().getKind() == TypeKind.INT;
    }

    private String methodHeader(
            ExecutableElement method) {

        StringBuilder out = new StringBuilder();

        out.append("public ")
                .append(method.getReturnType())
                .append(' ')
                .append(method.getSimpleName())
                .append('(');

        List<? extends VariableElement> parameters = method.getParameters();

        for (int i = 0; i < parameters.size(); i++) {
            if (i != 0) {
                out.append(", ");
            }

            VariableElement parameter = parameters.get(i);

            out.append(parameter.asType())
                    .append(' ')
                    .append(parameter.getSimpleName());
        }

        out.append(')');

        return out.toString();
    }

    private NameAllocator names(
            ExecutableElement method) {

        NameAllocator names = new NameAllocator();

        for (VariableElement parameter : method.getParameters()) {
            names.reserve(parameter.getSimpleName().toString());
        }

        return names;
    }

    /*
     * --------------------------------------------------------------
     * Return Values
     * --------------------------------------------------------------
     */

    private String getMissing(
            ExecutableElement method,
            NavigatorPlan.Get plan) {

        if (!method.getReturnType()
                .getKind()
                .isPrimitive()) {

            return "return null;";
        }

        return "throw new org.sjf4j.exception.NodeException(" +
                JavaWriter.stringLiteral("@GetByPath primitive result is missing: method " +
                        method.getSimpleName() + " returns " + method.getReturnType() +
                        " for path " + plan.path.toExpr()) + ");";
    }

    private String putMissingThrow(
            NavigatorPlan.Put plan) {

        return "throw new org.sjf4j.exception.NodeException(" +
                JavaWriter.stringLiteral(plan.annotation + " missing parent: method " +
                        plan.method.getSimpleName() + " for path " + plan.path.toExpr()) + ");";
    }

    private String nullReturn(ExecutableElement method) {
        return method.getReturnType().getKind() == TypeKind.VOID ? "return;" : "return null;";
    }

    private void emitOldReturn(
            JavaWriter out,
            ExecutableElement method,
            String old,
            TypeMirror oldType) {

        if (method.getReturnType()
                .getKind() == TypeKind.VOID) {

            return;
        }

        if (oldType == null) {
            out.line("return null;");
            return;
        }

        if (old == null) {
            throw new AssertionError(
                    "Old value variable was not emitted");
        }

        out.line(
                "return " +
                        old +
                        ";");
    }

    private void emitNullReturn(
            JavaWriter out,
            ExecutableElement method) {

        if (method.getReturnType()
                .getKind() == TypeKind.VOID) {

            out.line("return;");
        } else {
            out.line("return null;");
        }
    }

    /*
     * --------------------------------------------------------------
     * Helpers
     * --------------------------------------------------------------
     */

    private String localType(TypeMirror type) {
        return types.concrete(type).toString();
    }

    private String stepHint(Step step) {
        if (step.resolved.segment() instanceof PathSegment.Name) return ((PathSegment.Name) step.resolved.segment()).name;

        if (step.resolved.segment() instanceof PathSegment.Param) return ((PathSegment.Param) step.resolved.segment()).param;

        if (step.resolved.segment() instanceof PathSegment.Append) return "value";

        return "item";
    }

    private void emitReadonlyEnsureFailure(
            JavaWriter out,
            Step step) {

        out.line(
                "throw new org.sjf4j.exception.NodeException(" +
                        JavaWriter.stringLiteral(
                                "Cannot create missing ensure intermediate '" +
                                        stepHint(step) +
                                        "' on " +
                                        step.resolved.ownerType() +
                                        ": access is read-only") +
                        ");");
    }

    private enum ReadMode {
        STRICT,
        ENSURE
    }

}
