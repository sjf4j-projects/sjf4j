package org.sjf4j.processor.path;

import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.NameAllocator;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Generates implementations for {@code @FindByPath} methods.
 *
 * <p>Supported multi-target paths are emitted as direct loops. Unsupported
 * paths fail at compile time.</p>
 */
public final class FindGenerator {

    private final ProcessorContext ctx;

    public FindGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Validates and emits a generated implementation for one {@code @FindByPath} method.
     */
    public void genFind(ExecutableElement method, GeneratedClass target, String expr) {
        // 1. Validate root parameter
        if (method.getParameters().isEmpty()) {
            _error(method, target, "@FindByPath method must have a root parameter");
            return;
        }
        if (method.getParameters().size() != 1) {
            _error(method, target, "@FindByPath Phase 2 does not support path parameters");
            return;
        }

        // 2. Validate return type is List
        TypeMirror returnType = method.getReturnType();
        if (!GeneratorUtil.isAssignableErasure(ctx, returnType, ctx.listType)) {
            _error(method, target, "@FindByPath return type must be List<T>, but was " + returnType);
            return;
        }

        // 3. Parse path
        JsonPath path;
        try {
            path = JsonPath.parse(expr);
        } catch (JsonException e) {
            _error(method, target, "Invalid JSON Path value: " + e.getMessage());
            return;
        }
        // Note: root-only "$" is allowed for find (unlike get/put which require length >= 2)

        // 4. Extract element type from List<T>
        TypeMirror elementType = GeneratorUtil.listValueType(ctx, returnType);

        VariableElement root = method.getParameters().get(0);
        if (tryEmitRootFind(method, target, path, returnType, elementType, root)) {
            return;
        }
        if (tryEmitSimpleWildcardFind(method, target, path, returnType, elementType, root)) {
            return;
        }
        if (tryEmitUnionFind(method, target, path, returnType, elementType, root)) {
            return;
        }

        _error(method, target, "@FindByPath unsupported path '" + expr + "': only root, one simple wildcard, or one name/index union with static name/index segments is supported");
    }

    private boolean tryEmitRootFind(ExecutableElement method, GeneratedClass target, JsonPath path,
                                    TypeMirror returnType, TypeMirror elementType, VariableElement root) {
        if (path.segments().length != 1) return false;
        if (!canAddToResult(root.asType(), elementType)) return false;
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public " + GeneratorUtil.typeName(returnType) + " " + method.getSimpleName() +
                    "(" + GeneratorUtil.typeName(root.asType()) + " " + root.getSimpleName() + ") {");
            out.indent();
            if (!root.asType().getKind().isPrimitive()) out.line(root.getSimpleName() + ".getClass();");
            out.line("return java.util.Collections.singletonList(" + root.getSimpleName() + ");");
            out.dedent();
            out.line("}");
        });
        return true;
    }

    private boolean tryEmitSimpleWildcardFind(ExecutableElement method, GeneratedClass target, JsonPath path,
                                             TypeMirror returnType, TypeMirror elementType, VariableElement root) {
        PathSegment[] segments = path.segments();
        int wildcard = -1;
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Wildcard) {
                if (wildcard >= 0) return false;
                wildcard = i;
            } else if (!(segment instanceof PathSegment.Name || segment instanceof PathSegment.Index)) {
                return false;
            }
        }
        if (wildcard < 0) return false;

        TypeMirror current = root.asType();
        for (int i = 1; i < wildcard; i++) {
            current = resolveDirectType(current, segments[i]);
            if (current == null) return false;
        }
        if (!isList(current) && current.getKind() != TypeKind.ARRAY) return false;

        TypeMirror itemType = current.getKind() == TypeKind.ARRAY
                ? ((ArrayType) current).getComponentType()
                : GeneratorUtil.listValueType(ctx, current);
        current = itemType;
        for (int i = wildcard + 1; i < segments.length; i++) {
            current = resolveDirectType(current, segments[i]);
            if (current == null) return false;
        }
        if (!canAddToResult(current, elementType)) return false;

        if (!canEmitAccesses(root.asType(), segments, 1, wildcard)) return false;
        if (!canEmitAccesses(itemType, segments, wildcard + 1, segments.length)) return false;

        TypeMirror wildcardContainerType = resolvePrefixContainerType(root.asType(), segments, wildcard);
        int wildcardIndex = wildcard;
        target.addMethod(out -> {
            NameAllocator names = names(root);
            String outVar = names.local("out");
            String indexVar = names.local("i");
            String sizeVar = names.local("n");
            String itemVar = names.local("item");
            out.line("");
            out.line("@Override");
            out.line("public " + GeneratorUtil.typeName(returnType) + " " + method.getSimpleName() +
                    "(" + GeneratorUtil.typeName(root.asType()) + " " + root.getSimpleName() + ") {");
            out.indent();
            out.line("java.util.ArrayList<" + GeneratorUtil.localTypeName(ctx, elementType) + "> " + outVar + " = new java.util.ArrayList<>();");

            String expr = root.getSimpleName().toString();
            TypeMirror exprType = root.asType();
            for (int i = 1; i < wildcardIndex; i++) {
                Access access = access(exprType, expr, segments[i]);
                if (access.boundsCheck != null) out.line("if (!(" + access.boundsCheck + ")) return " + outVar + ";");
                if (access.presentCheck != null) out.line("if (!(" + access.presentCheck + ")) return " + outVar + ";");
                String name = names.local("v");
                out.line(GeneratorUtil.localTypeName(ctx, access.type) + " " + name + " = " + access.expr + ";");
                out.line("if (" + name + " == null) return " + outVar + ";");
                expr = name;
                exprType = access.type;
            }

            String itemTypeName = GeneratorUtil.localTypeName(ctx, itemType);
            if (wildcardContainerType.getKind() == TypeKind.ARRAY) {
                out.line("for (int " + indexVar + " = 0; " + indexVar + " < " + expr + ".length; " + indexVar + "++) {");
                out.indent();
                out.line(itemTypeName + " " + itemVar + " = " + expr + "[" + indexVar + "];");
            } else {
                out.line("for (int " + indexVar + " = 0, " + sizeVar + " = " + expr + ".size(); " + indexVar + " < " + sizeVar + "; " + indexVar + "++) {");
                out.indent();
                out.line(itemTypeName + " " + itemVar + " = " + expr + ".get(" + indexVar + ");");
            }

            expr = itemVar;
            exprType = itemType;
            for (int i = wildcardIndex + 1; i < segments.length; i++) {
                Access access = access(exprType, expr, segments[i]);
                boolean last = i == segments.length - 1;
                if (access.needsReceiverNonNull) out.line("if (" + expr + " == null) continue;");
                if (access.boundsCheck != null) out.line("if (!(" + access.boundsCheck + ")) continue;");
                if (access.presentCheck != null) out.line("if (!(" + access.presentCheck + ")) continue;");
                String name = names.local("v");
                out.line(GeneratorUtil.localTypeName(ctx, access.type) + " " + name + " = " + access.expr + ";");
                if (!last) out.line("if (" + name + " == null) continue;");
                expr = name;
                exprType = access.type;
            }
            out.line(outVar + ".add(" + expr + ");");
            out.dedent();
            out.line("}");
            out.line("return " + outVar + ";");
            out.dedent();
            out.line("}");
        });
        return true;
    }

    private boolean tryEmitUnionFind(ExecutableElement method, GeneratedClass target, JsonPath path,
                                     TypeMirror returnType, TypeMirror elementType, VariableElement root) {
        PathSegment[] segments = path.segments();
        int unionIndex = -1;
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Union) {
                if (unionIndex >= 0) return false;
                unionIndex = i;
            } else if (!(segment instanceof PathSegment.Name || segment instanceof PathSegment.Index)) {
                return false;
            }
        }
        if (unionIndex < 0) return false;

        PathSegment.Union union = (PathSegment.Union) segments[unionIndex];
        if (union.union.length == 0) return false;
        boolean indexUnion = true;
        boolean nameUnion = true;
        for (PathSegment token : union.union) {
            if (!(token instanceof PathSegment.Index)) indexUnion = false;
            if (!(token instanceof PathSegment.Name)) nameUnion = false;
        }
        if (!indexUnion && !nameUnion) return false;

        TypeMirror prefixType = root.asType();
        for (int i = 1; i < unionIndex; i++) {
            prefixType = resolveDirectType(prefixType, segments[i]);
            if (prefixType == null) return false;
        }

        TypeMirror tokenValueType;
        if (indexUnion) {
            if (!isList(prefixType) && prefixType.getKind() != TypeKind.ARRAY) return false;
            for (PathSegment token : union.union) {
                if (((PathSegment.Index) token).index < 0) return false;
            }
            tokenValueType = prefixType.getKind() == TypeKind.ARRAY
                    ? ((ArrayType) prefixType).getComponentType()
                    : GeneratorUtil.listValueType(ctx, prefixType);
        } else {
            if (!isStringKeyMap(prefixType)) return false;
            tokenValueType = GeneratorUtil.mapValueType(ctx, prefixType);
        }

        TypeMirror current = tokenValueType;
        for (int i = unionIndex + 1; i < segments.length; i++) {
            current = resolveDirectType(current, segments[i]);
            if (current == null) return false;
        }
        if (!canAddToResult(current, elementType)) return false;
        if (!canEmitAccesses(root.asType(), segments, 1, unionIndex)) return false;
        if (!canEmitAccesses(tokenValueType, segments, unionIndex + 1, segments.length)) return false;

        int u = unionIndex;
        TypeMirror containerType = prefixType;
        TypeMirror valueType = tokenValueType;
        boolean byIndex = indexUnion;
        target.addMethod(out -> {
            NameAllocator names = names(root);
            String outVar = names.local("out");
            String indexVar = names.local("i");
            String sizeVar = names.local("n");
            String itemVar = names.local("item");
            String entryVar = names.local("e");
            String keyVar = names.local("k");
            out.line("");
            out.line("@Override");
            out.line("public " + GeneratorUtil.typeName(returnType) + " " + method.getSimpleName() +
                    "(" + GeneratorUtil.typeName(root.asType()) + " " + root.getSimpleName() + ") {");
            out.indent();
            out.line("java.util.ArrayList<" + GeneratorUtil.localTypeName(ctx, elementType) + "> " + outVar + " = new java.util.ArrayList<>();");

            String expr = root.getSimpleName().toString();
            TypeMirror exprType = root.asType();
            for (int i = 1; i < u; i++) {
                Access access = access(exprType, expr, segments[i]);
                if (access.boundsCheck != null) out.line("if (!(" + access.boundsCheck + ")) return " + outVar + ";");
                if (access.presentCheck != null) out.line("if (!(" + access.presentCheck + ")) return " + outVar + ";");
                String name = names.local("v");
                out.line(GeneratorUtil.localTypeName(ctx, access.type) + " " + name + " = " + access.expr + ";");
                out.line("if (" + name + " == null) return " + outVar + ";");
                expr = name;
                exprType = access.type;
            }
            if (u == 1 && !exprType.getKind().isPrimitive()) out.line("if (" + expr + " == null) return " + outVar + ";");

            if (byIndex) {
                int max = maxIndex(union.union);
                if (containerType.getKind() == TypeKind.ARRAY) {
                    out.line("for (int " + indexVar + " = 0, " + sizeVar + " = " + expr + ".length; " + indexVar + " < " + sizeVar + " && " + indexVar + " <= " + max + "; " + indexVar + "++) {");
                } else {
                    out.line("for (int " + indexVar + " = 0, " + sizeVar + " = " + expr + ".size(); " + indexVar + " < " + sizeVar + " && " + indexVar + " <= " + max + "; " + indexVar + "++) {");
                }
                out.indent();
                out.line("if (!(" + indexMatchExpr(indexVar, union.union) + ")) continue;");
                String itemTypeName = GeneratorUtil.localTypeName(ctx, valueType);
                if (containerType.getKind() == TypeKind.ARRAY) out.line(itemTypeName + " " + itemVar + " = " + expr + "[" + indexVar + "];");
                else out.line(itemTypeName + " " + itemVar + " = " + expr + ".get(" + indexVar + ");");
            } else {
                out.line("for (java.util.Map.Entry<String, " + GeneratorUtil.localTypeName(ctx, valueType) + "> " + entryVar + " : " + expr + ".entrySet()) {");
                out.indent();
                out.line("String " + keyVar + " = " + entryVar + ".getKey();");
                out.line("if (!(" + nameMatchExpr(keyVar, union.union) + ")) continue;");
                out.line(GeneratorUtil.localTypeName(ctx, valueType) + " " + itemVar + " = " + entryVar + ".getValue();");
            }

            expr = itemVar;
            exprType = valueType;
            for (int i = u + 1; i < segments.length; i++) {
                Access access = access(exprType, expr, segments[i]);
                boolean last = i == segments.length - 1;
                if (access.needsReceiverNonNull) out.line("if (" + expr + " == null) continue;");
                if (access.boundsCheck != null) out.line("if (!(" + access.boundsCheck + ")) continue;");
                if (access.presentCheck != null) out.line("if (!(" + access.presentCheck + ")) continue;");
                String name = names.local("v");
                out.line(GeneratorUtil.localTypeName(ctx, access.type) + " " + name + " = " + access.expr + ";");
                if (!last) out.line("if (" + name + " == null) continue;");
                expr = name;
                exprType = access.type;
            }
            out.line(outVar + ".add(" + expr + ");");
            out.dedent();
            out.line("}");
            out.line("return " + outVar + ";");
            out.dedent();
            out.line("}");
        });
        return true;
    }

    private TypeMirror resolvePrefixContainerType(TypeMirror root, PathSegment[] segments, int wildcard) {
        TypeMirror current = root;
        for (int i = 1; i < wildcard; i++) current = resolveDirectType(current, segments[i]);
        return current;
    }

    private TypeMirror resolveDirectType(TypeMirror current, PathSegment segment) {
        if (segment instanceof PathSegment.Name) {
            TypeElement type = GeneratorUtil.asTypeElement(current);
            if (type == null) return null;
            return GeneratorUtil.resolveNameType(ctx, current, ((PathSegment.Name) segment).name);
        }
        if (segment instanceof PathSegment.Index) {
            return GeneratorUtil.resolveIndexType(ctx, current, ((PathSegment.Index) segment).index);
        }
        return null;
    }

    private Access access(TypeMirror owner, String receiver, PathSegment segment) {
        if (segment instanceof PathSegment.Name) {
            if (GeneratorUtil.isAssignableErasure(ctx, owner, ctx.mapType)) {
                TypeMirror type = GeneratorUtil.mapValueType(ctx, owner);
                String key = GeneratorUtil.escape(((PathSegment.Name) segment).name);
                return new Access(type, receiver + ".get(\"" + key + "\")", true, null,
                        receiver + ".containsKey(\"" + key + "\")");
            }
            TypeElement type = GeneratorUtil.asTypeElement(owner);
            if (type == null) return null;
            String name = ((PathSegment.Name) segment).name;
            ExecutableElement getter = GeneratorUtil.findReadable(ctx, type, owner, name);
            if (getter != null) {
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, getter);
                return new Access(mt.getReturnType(), receiver + "." + getter.getSimpleName() + "()", true, null, null);
            }
            VariableElement field = GeneratorUtil.findReadableField(ctx, type, name);
            if (field != null) {
                return new Access(ctx.types.asMemberOf((DeclaredType) owner, field), receiver + "." + field.getSimpleName(), true, null, null);
            }
            return null;
        }
        if (segment instanceof PathSegment.Index) {
            int index = ((PathSegment.Index) segment).index;
            if (index < 0) return null;
            if (owner.getKind() == TypeKind.ARRAY) {
                TypeMirror type = ((ArrayType) owner).getComponentType();
                return new Access(type, receiver + "[" + index + "]", true, index + " < " + receiver + ".length", null);
            }
            if (isList(owner)) {
                TypeMirror type = GeneratorUtil.listValueType(ctx, owner);
                return new Access(type, receiver + ".get(" + index + ")", true, index + " < " + receiver + ".size()", null);
            }
        }
        return null;
    }

    private boolean canEmitAccesses(TypeMirror start, PathSegment[] segments, int from, int to) {
        TypeMirror current = start;
        for (int i = from; i < to; i++) {
            Access access = access(current, "x", segments[i]);
            if (access == null) return false;
            current = access.type;
        }
        return true;
    }

    private boolean isStringKeyMap(TypeMirror type) {
        if (!GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) return false;
        if (type.getKind() != TypeKind.DECLARED) return false;
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        if (args.size() != 2) return false;
        TypeMirror stringType = ctx.elements.getTypeElement(String.class.getName()).asType();
        return ctx.types.isSameType(ctx.types.erasure(args.get(0)), ctx.types.erasure(stringType));
    }

    private int maxIndex(PathSegment[] union) {
        int max = 0;
        for (PathSegment token : union) {
            int index = ((PathSegment.Index) token).index;
            if (index > max) max = index;
        }
        return max;
    }

    private String indexMatchExpr(String var, PathSegment[] union) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < union.length; i++) {
            if (i > 0) sb.append(" || ");
            sb.append(var).append(" == ").append(((PathSegment.Index) union[i]).index);
        }
        return sb.toString();
    }

    private String nameMatchExpr(String var, PathSegment[] union) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < union.length; i++) {
            if (i > 0) sb.append(" || ");
            sb.append("\"").append(GeneratorUtil.escape(((PathSegment.Name) union[i]).name)).append("\".equals(").append(var).append(")");
        }
        return sb.toString();
    }

    private boolean isList(TypeMirror type) {
        return GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType);
    }

    private NameAllocator names(VariableElement root) {
        NameAllocator names = new NameAllocator();
        names.reserve(root.getSimpleName().toString());
        return names;
    }

    private boolean canAddToResult(TypeMirror valueType, TypeMirror elementType) {
        if (GeneratorUtil.isObject(ctx, elementType)) return true;
        TypeMirror value = boxed(valueType);
        TypeMirror element = boxed(elementType);
        return ctx.types.isAssignable(value, element);
    }

    private TypeMirror boxed(TypeMirror type) {
        return type.getKind().isPrimitive()
                ? ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).asType()
                : GeneratorUtil.concrete(ctx, type);
    }

    private static final class Access {
        final TypeMirror type;
        final String expr;
        final boolean needsReceiverNonNull;
        final String boundsCheck;
        final String presentCheck;

        Access(TypeMirror type, String expr, boolean needsReceiverNonNull, String boundsCheck, String presentCheck) {
            this.type = type;
            this.expr = expr;
            this.needsReceiverNonNull = needsReceiverNonNull;
            this.boundsCheck = boundsCheck;
            this.presentCheck = presentCheck;
        }
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }
}
