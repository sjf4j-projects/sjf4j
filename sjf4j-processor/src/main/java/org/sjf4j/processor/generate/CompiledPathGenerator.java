package org.sjf4j.processor.generate;

import org.sjf4j.annotation.compiled.CompiledPath;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CompiledPathGenerator {

    private final ProcessorContext ctx;

    public CompiledPathGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    public void generate(ExecutableElement method, GeneratedClass target) {
        CompiledPath anno = method.getAnnotation(CompiledPath.class);
        if (anno == null) {
            error(method, target, "@CompiledPath annotation is required");
            return;
        }
        NodeMethod nodeMethod = resolveMethod(method, anno, target);
        if (nodeMethod != null) target.addMethod(out -> emitMethod(out, nodeMethod));
    }

    private void error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }


    private NodeMethod resolveMethod(ExecutableElement method, CompiledPath annotation, GeneratedClass target) {
        if (annotation.method() != CompiledPath.MethodKind.GET) {
            error(method, target, "Only CompiledPath.MethodKind.GET is supported by the processor for now");
            return null;
        }
        if (method.getModifiers().contains(Modifier.STATIC)) {
            error(method, target, "@CompiledPath GET method must not be static");
            return null;
        }
        if (method.getParameters().size() != 1) {
            error(method, target, "@CompiledPath GET method must have exactly one root parameter");
            return null;
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            error(method, target, "@CompiledPath GET method must return the path value");
            return null;
        }
        if (method.getReturnType().getKind().isPrimitive()) {
            error(method, target, "@CompiledPath GET primitive return types are not supported yet; use the boxed type");
            return null;
        }

        JsonPath path;
        try {
            path = JsonPath.parse(annotation.expr());
        } catch (JsonException e) {
            error(method, target, "Invalid @CompiledPath expr: " + e.getMessage());
            return null;
        }
        if (path.length() < 2) {
            error(method, target, "@CompiledPath GET requires a non-root path");
            return null;
        }
        if (!path.isSinglePut() || path.hasAppend()) {
            error(method, target, "@CompiledPath GET currently supports only single Name/Index paths");
            return null;
        }

        VariableElement root = method.getParameters().get(0);
        List<Step> steps = resolveSteps(root.asType(), path.segments(), method, target);
        if (steps == null) return null;

        NodeMethod nodeMethod = new NodeMethod();
        nodeMethod.name = method.getSimpleName().toString();
        nodeMethod.rootName = root.getSimpleName().toString();
        nodeMethod.rootType = root.asType();
        nodeMethod.returnType = method.getReturnType();
        nodeMethod.steps = steps;
        return nodeMethod;
    }

    private List<Step> resolveSteps(TypeMirror rootType, PathSegment[] segments, Element context, GeneratedClass target) {
        List<Step> steps = new ArrayList<>();
        TypeMirror current = rootType;
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            Step step;
            if (segment instanceof PathSegment.Name) {
                step = resolveName(current, ((PathSegment.Name) segment).name, context, target);
            } else if (segment instanceof PathSegment.Index) {
                step = resolveIndex(current, ((PathSegment.Index) segment).index, context, target);
            } else {
                error(context, target, "@CompiledPath GET currently supports only Name and Index path segments");
                return null;
            }
            if (step == null) return null;
            steps.add(step);
            current = step.outputType;
        }
        return steps;
    }

    private Step resolveName(TypeMirror current, String name, Element context, GeneratedClass target) {
        if (isAssignableErasure(current, ctx.jsonObjectType)) return Step.jsonObject(name, ctx.objectType);
        if (isAssignableErasure(current, ctx.mapType)) return Step.map(name, mapValueType(current));

        TypeElement type = asTypeElement(current);
        if (type == null) {
            error(context, target, "Cannot resolve property '" + name + "' on " + current);
            return null;
        }

        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement getter = findGetter(type, current, "get" + suffix, false);
        if (getter == null) getter = findGetter(type, current, "is" + suffix, true);
        if (getter == null) getter = findGetter(type, current, name, false);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            return Step.getter(getter.getSimpleName().toString(), mt.getReturnType());
        }

        VariableElement field = findField(type, name);
        if (field != null) {
            TypeMirror ft = ctx.types.asMemberOf((DeclaredType) current, field);
            return Step.field(name, ft);
        }

        error(context, target, "Cannot resolve readable property '" + name + "' on " + current);
        return null;
    }

    private Step resolveIndex(TypeMirror current, int index, Element context, GeneratedClass target) {
        if (current.getKind() == TypeKind.ARRAY) return Step.array(index, ((ArrayType) current).getComponentType());
        if (isAssignableErasure(current, ctx.jsonArrayType)) return Step.jsonArray(index, ctx.objectType);
        if (isAssignableErasure(current, ctx.listType)) return Step.list(index, listValueType(current));
        error(context, target, "Cannot resolve index [" + index + "] on " + current);
        return null;
    }

    private ExecutableElement findGetter(TypeElement type, TypeMirror owner, String name, boolean booleanOnly) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (!member.getSimpleName().contentEquals(name)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (!method.getParameters().isEmpty()) continue;
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
            TypeMirror returnType = mt.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) continue;
            if (booleanOnly && returnType.getKind() != TypeKind.BOOLEAN &&
                    !ctx.types.isSameType(ctx.types.erasure(returnType), ctx.types.erasure(ctx.elements.getTypeElement("java.lang.Boolean").asType()))) {
                continue;
            }
            return method;
        }
        return null;
    }

    private VariableElement findField(TypeElement type, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.FIELD) continue;
            if (!member.getSimpleName().contentEquals(name)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC)) return (VariableElement) member;
        }
        return null;
    }

    private void emitMethod(SourceWriter out, NodeMethod method) {
        out.line("");
        out.line("@Override");
        out.line("public " + typeName(method.returnType) + " " + method.name + "(" +
                typeName(method.rootType) + " " + method.rootName + ") {");
        out.indent();
        out.line("if (" + method.rootName + " == null) return null;");

        String currentVar = method.rootName;
        for (int i = 0; i < method.steps.size(); i++) {
            Step step = method.steps.get(i);
            String nextVar = "v" + i;
            emitStep(out, step, currentVar, nextVar, localTypeName(step.outputType));
            currentVar = nextVar;
        }

        TypeMirror outputType = method.steps.get(method.steps.size() - 1).outputType;
        if (ctx.types.isAssignable(ctx.types.erasure(outputType), ctx.types.erasure(method.returnType))) {
            out.line("return " + currentVar + ";");
        } else {
            out.line("return (" + typeName(method.returnType) + ") " + currentVar + ";");
        }
        out.dedent();
        out.line("}");
    }

    private void emitStep(SourceWriter out, Step step, String currentVar, String nextVar, String declaredType) {
        switch (step.kind) {
            case GETTER:
                out.line(declaredType + " " + nextVar + " = " + currentVar + "." + step.name + "();");
                out.line("if (" + nextVar + " == null) return null;");
                break;
            case FIELD:
                out.line(declaredType + " " + nextVar + " = " + currentVar + "." + step.name + ";");
                out.line("if (" + nextVar + " == null) return null;");
                break;
            case MAP:
                out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".get(\"" + escape(step.name) + "\");");
                out.line("if (" + nextVar + " == null) return null;");
                break;
            case JSON_OBJECT:
                out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".getNode(\"" + escape(step.name) + "\");");
                out.line("if (" + nextVar + " == null) return null;");
                break;
            case LIST:
                out.line("int " + nextVar + "i = " + step.index + " < 0 ? " + currentVar + ".size() + " + step.index + " : " + step.index + ";");
                out.line("if (" + nextVar + "i < 0 || " + nextVar + "i >= " + currentVar + ".size()) return null;");
                out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".get(" + nextVar + "i);");
                out.line("if (" + nextVar + " == null) return null;");
                break;
            case ARRAY:
                out.line("int " + nextVar + "i = " + step.index + " < 0 ? " + currentVar + ".length + " + step.index + " : " + step.index + ";");
                out.line("if (" + nextVar + "i < 0 || " + nextVar + "i >= " + currentVar + ".length) return null;");
                out.line(declaredType + " " + nextVar + " = " + currentVar + "[" + nextVar + "i];");
                out.line("if (" + nextVar + " == null) return null;");
                break;
            case JSON_ARRAY:
                out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".getNode(" + step.index + ");");
                out.line("if (" + nextVar + " == null) return null;");
                break;
        }
    }

    private boolean isAssignableErasure(TypeMirror type, TypeMirror target) {
        return type != null && target != null && ctx.types.isAssignable(ctx.types.erasure(type), ctx.types.erasure(target));
    }

    private TypeElement asTypeElement(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return null;
        Element element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private TypeMirror mapValueType(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() == 2) return concrete(args.get(1));
        }
        return ctx.objectType;
    }

    private TypeMirror listValueType(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() == 1) return concrete(args.get(0));
        }
        return ctx.objectType;
    }

    private TypeMirror concrete(TypeMirror type) {
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            return wildcard.getExtendsBound() == null ? ctx.objectType : wildcard.getExtendsBound();
        }
        return type;
    }

    private String typeName(TypeMirror type) { return type.toString(); }

    private String localTypeName(TypeMirror type) {
        if (type.getKind().isPrimitive()) return ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).getQualifiedName().toString();
        return typeName(concrete(type));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum StepKind { GETTER, FIELD, MAP, LIST, ARRAY, JSON_OBJECT, JSON_ARRAY }

    private static final class Step {
        final StepKind kind;
        final String name;
        final int index;
        final TypeMirror outputType;

        private Step(StepKind kind, String name, int index, TypeMirror outputType) {
            this.kind = kind;
            this.name = name;
            this.index = index;
            this.outputType = outputType;
        }

        static Step getter(String name, TypeMirror outputType) { return new Step(StepKind.GETTER, name, 0, outputType); }
        static Step field(String name, TypeMirror outputType) { return new Step(StepKind.FIELD, name, 0, outputType); }
        static Step map(String name, TypeMirror outputType) { return new Step(StepKind.MAP, name, 0, outputType); }
        static Step jsonObject(String name, TypeMirror outputType) { return new Step(StepKind.JSON_OBJECT, name, 0, outputType); }
        static Step list(int index, TypeMirror outputType) { return new Step(StepKind.LIST, null, index, outputType); }
        static Step array(int index, TypeMirror outputType) { return new Step(StepKind.ARRAY, null, index, outputType); }
        static Step jsonArray(int index, TypeMirror outputType) { return new Step(StepKind.JSON_ARRAY, null, index, outputType); }
    }

    private static final class NodeMethod {
        String name;
        String rootName;
        TypeMirror rootType;
        TypeMirror returnType;
        List<Step> steps;
    }

}
