package org.sjf4j.processor.path;

import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.NameAllocator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;

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
 * <p>Supported multi-target paths are emitted as direct loops that append typed
 * values to the returned {@code List}.  Root, wildcard, slice, filter, and one
 * static union shape are recognized explicitly.  Descendant and filter-heavy
 * cases can fall back to runtime {@link JsonPath#find(Object)} only when the
 * annotation opts into that cost.</p>
 */
public final class FindGenerator {

    private final ProcessorContext ctx;
    private int filterSeq;
    private int fallbackSeq;

    public FindGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Validates and emits a generated implementation for one {@code @FindByPath} method.
     */
    public void genFind(ExecutableElement method, GeneratedClass target, String expr, boolean allowFallback) {
        // 1. Validate root parameter
        if (method.getParameters().isEmpty()) {
            _error(method, target, "@FindByPath method must have a root parameter");
            return;
        }
        if (method.getParameters().size() != 1) {
            _error(method, target, "@FindByPath does not support path parameters");
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

        PathSegment[] segments = path.segments();
        boolean hasFilter = false;
        boolean hasDescendant = false;
        for (int i = 1; i < segments.length; i++) {
            if (segments[i] instanceof PathSegment.Filter) hasFilter = true;
            else if (segments[i] instanceof PathSegment.Descendant) hasDescendant = true;
        }
        if (hasFilter && !allowFallback) {
            _error(method, target, "@FindByPath path '" + expr + "' requires allowFallback=true because filter expressions use runtime evaluation");
            return;
        }
        if (hasDescendant && !allowFallback) {
            _error(method, target, "@FindByPath path '" + expr + "' requires allowFallback=true because descendant is not fully compiled");
            return;
        }

        // 4. Extract element type from List<T>
        TypeMirror elementType = GeneratorUtil.listValueType(ctx, returnType);

        VariableElement root = method.getParameters().get(0);
        if (tryEmitRootFind(method, target, path, returnType, elementType, root)) {
            return;
        }
        if (hasDescendant && tryEmitDescendantFind(method, target, path, returnType, elementType, root)) {
            return;
        }
        if (tryEmitWildcardFind(method, target, path, returnType, elementType, root)) {
            return;
        }
        if (tryEmitUnionFind(method, target, path, returnType, elementType, root)) {
            return;
        }

        _error(method, target, "@FindByPath unsupported path '" + expr + "': only root, wildcard/slice/filter, or one name/index union with static name/index segments is supported");
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

    private boolean tryEmitWildcardFind(ExecutableElement method, GeneratedClass target, JsonPath path,
                                        TypeMirror returnType, TypeMirror elementType, VariableElement root) {
        PathSegment[] segments = path.segments();
        boolean hasMulti = false;
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Wildcard || segment instanceof PathSegment.Slice || segment instanceof PathSegment.Filter) {
                hasMulti = true;
            } else if (!(segment instanceof PathSegment.Name || segment instanceof PathSegment.Index)) {
                return false;
            }
        }
        if (!hasMulti) return false;

        TypeMirror current = root.asType();
        for (int i = 1; i < segments.length; i++) {
            if (segments[i] instanceof PathSegment.Wildcard || segments[i] instanceof PathSegment.Slice) {
                if (!isList(current) && current.getKind() != TypeKind.ARRAY) return false;
                current = current.getKind() == TypeKind.ARRAY
                        ? ((ArrayType) current).getComponentType()
                        : GeneratorUtil.listValueType(ctx, current);
            } else if (segments[i] instanceof PathSegment.Filter) {
                if (!isList(current) && current.getKind() != TypeKind.ARRAY && !isMap(current)) return false;
                current = current.getKind() == TypeKind.ARRAY
                        ? ((ArrayType) current).getComponentType()
                        : isList(current) ? GeneratorUtil.listValueType(ctx, current) : GeneratorUtil.mapValueType(ctx, current);
            } else {
                current = resolveDirectType(current, segments[i]);
                if (current == null) return false;
            }
        }
        if (!canAddToResult(current, elementType)) return false;

        String[] filterFields = addFilterFields(target, path.toString(), segments);

        target.addMethod(out -> {
            NameAllocator names = names(root);
            String outVar = names.local("out");
            String rootVar = root.getSimpleName().toString();
            out.line("");
            out.line("@Override");
            out.line("public " + GeneratorUtil.typeName(returnType) + " " + method.getSimpleName() +
                    "(" + GeneratorUtil.typeName(root.asType()) + " " + rootVar + ") {");
            out.indent();
            out.line("java.util.ArrayList<" + GeneratorUtil.localTypeName(ctx, elementType) + "> " + outVar + " = new java.util.ArrayList<>();");
            if (!root.asType().getKind().isPrimitive()) out.line(rootVar + ".getClass();");
            emitWildcardSegments(out, names, outVar, filterFields, segments, 1, rootVar, rootVar, root.asType(), true, true);
            out.line("return " + outVar + ";");
            out.dedent();
            out.line("}");
        });
        return true;
    }

    private boolean tryEmitDescendantFind(ExecutableElement method, GeneratedClass target, JsonPath path,
                                          TypeMirror returnType, TypeMirror elementType, VariableElement root) {
        PathSegment[] segments = path.segments();
        for (int i = 1; i < segments.length; i++) {
            if (segments[i] instanceof PathSegment.Descendant) {
                String fallbackField = "_sjf4j_find_fallback_" + fallbackSeq++;
                String fallbackExpr = path.toString();
                target.addField(out -> out.line("private static final org.sjf4j.path.JsonPath " + fallbackField +
                        " = org.sjf4j.path.JsonPath.parse(\"" + GeneratorUtil.escape(fallbackExpr) + "\");"));

                target.addMethod(out -> {
                    String rootVar = root.getSimpleName().toString();
                    out.line("");
                    out.line("@SuppressWarnings({\"unchecked\", \"rawtypes\"})");
                    out.line("@Override");
                    out.line("public " + GeneratorUtil.typeName(returnType) + " " + method.getSimpleName() +
                            "(" + GeneratorUtil.typeName(root.asType()) + " " + rootVar + ") {");
                    out.indent();
                    if (!root.asType().getKind().isPrimitive()) out.line(rootVar + ".getClass();");
                    out.line("return (" + GeneratorUtil.typeName(returnType) + ") (java.util.List) " + fallbackField + ".find(" + rootVar + ");");
                    out.dedent();
                    out.line("}");
                });
                return true;
            }
        }
        return false;
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
            String itemVar = names.local("item");
            out.line("");
            out.line("@Override");
            out.line("public " + GeneratorUtil.typeName(returnType) + " " + method.getSimpleName() +
                    "(" + GeneratorUtil.typeName(root.asType()) + " " + root.getSimpleName() + ") {");
            out.indent();
            out.line("java.util.ArrayList<" + GeneratorUtil.localTypeName(ctx, elementType) + "> " + outVar + " = new java.util.ArrayList<>();");
            if (!root.asType().getKind().isPrimitive()) out.line(root.getSimpleName() + ".getClass();");

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
            if (byIndex) {
                String itemTypeName = GeneratorUtil.localTypeName(ctx, valueType);
                for (PathSegment token : union.union) {
                    int index = ((PathSegment.Index) token).index;
                    String limit = containerType.getKind() == TypeKind.ARRAY ? expr + ".length" : expr + ".size()";
                    out.line("if (" + index + " < " + limit + ") {");
                    out.indent();
                    out.line("do {");
                    out.indent();
                    if (containerType.getKind() == TypeKind.ARRAY) out.line(itemTypeName + " " + itemVar + " = " + expr + "[" + index + "];");
                    else out.line(itemTypeName + " " + itemVar + " = " + expr + ".get(" + index + ");");
                    emitStaticSuffix(out, names, outVar, segments, u + 1, itemVar, valueType, "break", false);
                    out.dedent();
                    out.line("} while (false);");
                    out.dedent();
                    out.line("}");
                }
            } else {
                String itemTypeName = GeneratorUtil.localTypeName(ctx, valueType);
                for (PathSegment token : union.union) {
                    String key = GeneratorUtil.escape(((PathSegment.Name) token).name);
                    out.line("if (" + expr + ".containsKey(\"" + key + "\")) {");
                    out.indent();
                    out.line("do {");
                    out.indent();
                    out.line(itemTypeName + " " + itemVar + " = " + expr + ".get(\"" + key + "\");");
                    emitStaticSuffix(out, names, outVar, segments, u + 1, itemVar, valueType, "break", false);
                    out.dedent();
                    out.line("} while (false);");
                    out.dedent();
                    out.line("}");
                }
            }
            out.line("return " + outVar + ";");
            out.dedent();
            out.line("}");
        });
        return true;
    }

    private void emitWildcardSegments(SourceWriter out, NameAllocator names, String outVar,
                                      String[] filterFields, PathSegment[] segments, int index,
                                      String rootExpr, String expr,
                                      TypeMirror exprType, boolean topLevel, boolean exprKnownNonNull) {
        if (index == segments.length) {
            out.line(outVar + ".add(" + expr + ");");
            return;
        }

        PathSegment segment = segments[index];
        if (segment instanceof PathSegment.Wildcard || segment instanceof PathSegment.Slice || segment instanceof PathSegment.Filter) {
            if (!exprKnownNonNull && !exprType.getKind().isPrimitive()) {
                out.line("if (" + expr + " == null) " + (topLevel ? "return " + outVar : "continue") + ";");
            }
            boolean filterMap = segment instanceof PathSegment.Filter && isMap(exprType);
            TypeMirror itemType = exprType.getKind() == TypeKind.ARRAY
                    ? ((ArrayType) exprType).getComponentType()
                    : filterMap ? GeneratorUtil.mapValueType(ctx, exprType) : GeneratorUtil.listValueType(ctx, exprType);
            String itemVar = names.local("item");
            String itemTypeName = GeneratorUtil.localTypeName(ctx, itemType);
            if (exprType.getKind() == TypeKind.ARRAY) {
                String indexVar = names.local("i");
                String sizeExpr = expr + ".length";
                out.line("for (int " + indexVar + " = 0; " + indexVar + " < " + sizeExpr + "; " + indexVar + "++) {");
                out.indent();
                emitSliceCheck(out, segment, indexVar, sizeExpr);
                out.line(itemTypeName + " " + itemVar + " = " + expr + "[" + indexVar + "];");
            } else if (filterMap) {
                out.line("for (" + itemTypeName + " " + itemVar + " : " + expr + ".values()) {");
                out.indent();
            } else {
                String indexVar = names.local("i");
                String sizeVar = names.local("n");
                out.line("for (int " + indexVar + " = 0, " + sizeVar + " = " + expr + ".size(); " + indexVar + " < " + sizeVar + "; " + indexVar + "++) {");
                out.indent();
                emitSliceCheck(out, segment, indexVar, sizeVar);
                out.line(itemTypeName + " " + itemVar + " = " + expr + ".get(" + indexVar + ");");
            }
            emitFilterCheck(out, filterFields, index, rootExpr, itemVar);
            emitWildcardSegments(out, names, outVar, filterFields, segments, index + 1, rootExpr, itemVar, itemType, false, false);
            out.dedent();
            out.line("}");
            return;
        }

        Access access = access(exprType, expr, segment);
        boolean last = index == segments.length - 1;
        String miss = topLevel ? "return " + outVar : "continue";
        if (!exprKnownNonNull && access.needsReceiverNonNull) out.line("if (" + expr + " == null) " + miss + ";");
        if (access.boundsCheck != null) out.line("if (!(" + access.boundsCheck + ")) " + miss + ";");
        if (access.presentCheck != null) out.line("if (!(" + access.presentCheck + ")) " + miss + ";");
        String name = names.local("v");
        out.line(GeneratorUtil.localTypeName(ctx, access.type) + " " + name + " = " + access.expr + ";");
        if (!last) out.line("if (" + name + " == null) " + miss + ";");
        emitWildcardSegments(out, names, outVar, filterFields, segments, index + 1, rootExpr, name, access.type, topLevel, !last);
    }

    private String[] addFilterFields(GeneratedClass target, String rawExpr, PathSegment[] segments) {
        String[] filterFields = new String[segments.length];
        for (int i = 1; i < segments.length; i++) {
            if (segments[i] instanceof PathSegment.Filter) {
                String field = "_sjf4j_find_filter_" + filterSeq++;
                int filterIndex = i;
                filterFields[i] = field;
                target.addField(out -> out.line("private static final org.sjf4j.path.FilterExpr " + field +
                        " = ((org.sjf4j.path.PathSegment.Filter) org.sjf4j.path.JsonPath.parse(\"" +
                        GeneratorUtil.escape(rawExpr) + "\").segments()[" + filterIndex + "]).filterExpr;"));
            }
        }
        return filterFields;
    }

    private void emitFilterCheck(SourceWriter out, String[] filterFields, int index, String rootExpr, String itemVar) {
        String field = filterFields[index];
        if (field != null) out.line("if (!" + field + ".evalTruth(" + rootExpr + ", " + itemVar + ")) continue;");
    }

    private void emitSliceCheck(SourceWriter out, PathSegment segment, String indexVar, String sizeExpr) {
        if (!(segment instanceof PathSegment.Slice)) return;
        PathSegment.Slice slice = (PathSegment.Slice) segment;
        if (slice.start != null) {
            String pstart = slice.start < 0 ? "(" + sizeExpr + " + " + slice.start + ")" : String.valueOf(slice.start);
            out.line("if (" + indexVar + " < " + pstart + ") continue;");
        }
        if (slice.end != null) {
            String pend = slice.end < 0 ? "(" + sizeExpr + " + " + slice.end + ")" : String.valueOf(slice.end);
            out.line("if (" + indexVar + " >= " + pend + ") continue;");
        }
        if (slice.step != null) {
            int mod = slice.start == null ? 0 : slice.start;
            out.line("if (((" + indexVar + " - " + mod + ") % " + slice.step + ") != 0) continue;");
        }
    }

    private void emitStaticSuffix(SourceWriter out, NameAllocator names, String outVar,
                                  PathSegment[] segments, int index, String expr, TypeMirror exprType,
                                  String miss, boolean exprKnownNonNull) {
        for (int i = index; i < segments.length; i++) {
            Access access = access(exprType, expr, segments[i]);
            boolean last = i == segments.length - 1;
            if (!exprKnownNonNull && access.needsReceiverNonNull) out.line("if (" + expr + " == null) " + miss + ";");
            if (access.boundsCheck != null) out.line("if (!(" + access.boundsCheck + ")) " + miss + ";");
            if (access.presentCheck != null) out.line("if (!(" + access.presentCheck + ")) " + miss + ";");
            String name = names.local("v");
            out.line(GeneratorUtil.localTypeName(ctx, access.type) + " " + name + " = " + access.expr + ";");
            if (!last) out.line("if (" + name + " == null) " + miss + ";");
            expr = name;
            exprType = access.type;
            exprKnownNonNull = !last;
        }
        out.line(outVar + ".add(" + expr + ");");
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

    private boolean isList(TypeMirror type) {
        return GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType);
    }

    private boolean isMap(TypeMirror type) {
        return GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType);
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
