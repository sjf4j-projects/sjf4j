package org.sjf4j.processor.generate;

import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates direct Java code for {@code @GetByPath} and {@code @PutByPath} methods.
 *
 * <p>The generator resolves path segment types during annotation processing so
 * generated methods can use direct field/getter/setter, map, list, array, and
 * SJF4J container access without runtime path interpretation.</p>
 */
public final class PathGenerator {

    private final ProcessorContext ctx;

    /**
     * Creates a path generator using the shared processor context.
     */
    public PathGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Validates and emits a generated implementation for one {@code @GetByPath} method.
     */
    public void genGet(ExecutableElement method, GeneratedClass target, String expr) {
        if (method.getParameters().isEmpty()) {
            _error(method, target, "@GetByPath method must have a root parameter");
            return;
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            _error(method, target, "@GetByPath method must return the path value");
            return;
        }

        JsonPath path = _resolvePath(method, target, expr);
        if (path == null) return;
        if (!path.isSingleGet()) {
            _error(method, target, "@GetByPath currently supports only Name/Index paths");
            return;
        }

        VariableElement root = method.getParameters().get(0);
        Map<String, VariableElement> pathParams = _resolvePathParams(method, target, path);
        if (pathParams == null) return;
        PathSegment[] segments = path.segments();
        TypeMirror current = root.asType();
        for (int i = 1, len = segments.length; i < len; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Name) {
                current = _resolveNameType(current, ((PathSegment.Name) segment).name, method, target);
            } else if (segment instanceof PathSegment.Index) {
                current = _resolveIndexType(current, ((PathSegment.Index) segment).index, method, target);
            } else if (segment instanceof PathSegment.Param) {
                current = _resolveParamType(current, pathParams.get(((PathSegment.Param) segment).param), method, target);
            } else {
                _error(method, target, "@GetByPath currently supports only Name/Index paths");
                return;
            }
            if (current == null) return;
        }
        TypeMirror finalType = current;

        if (!_validateAssignable(method, target, finalType, method.getReturnType(), "@GetByPath return type")) return;

        target.addMethod(out -> _emitMethodGet(out, method, root, pathParams, path, finalType));
    }

    private Map<String, VariableElement> _resolvePathParams(ExecutableElement method, GeneratedClass target, JsonPath path) {
        List<? extends VariableElement> params = method.getParameters();
        Map<String, VariableElement> methodParams = new HashMap<>();
        for (int i = 1; i < params.size(); i++) {
            VariableElement param = params.get(i);
            methodParams.put(param.getSimpleName().toString(), param);
        }
        Set<String> used = new HashSet<>();
        for (PathSegment segment : path.segments()) {
            if (segment instanceof PathSegment.Param) {
                String name = ((PathSegment.Param) segment).param;
                if (!methodParams.containsKey(name)) {
                    _error(method, target, "@GetByPath path parameter '{" + name + "}' has no matching method parameter");
                    return null;
                }
                used.add(name);
            }
        }
        for (String name : methodParams.keySet()) {
            if (!used.contains(name)) {
                _error(method, target, "@GetByPath method parameter '" + name + "' is not used by the path");
                return null;
            }
        }
        return methodParams;
    }

    /**
     * Validates and emits a generated implementation for one {@code @PutByPath} method.
     */
    public void genPut(ExecutableElement method, GeneratedClass target, String expr) {
        if (method.getParameters().size() != 2) {
            _error(method, target, "@PutByPath method must have exactly root and value parameters");
            return;
        }

        JsonPath path = _resolvePath(method, target, expr);
        if (path == null) return;
        if (!path.isSinglePut()) {
            _error(method, target, "@PutByPath currently supports only Name/Index/Append paths");
            return;
        }

        VariableElement root = method.getParameters().get(0);
        PathSegment[] segments = path.segments();
        TypeMirror current = root.asType();
        for (int i = 1, len = segments.length - 1; i < len; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Name) {
                current = _resolveNameType(current, ((PathSegment.Name) segment).name, method, target);
            } else if (segment instanceof PathSegment.Index) {
                current = _resolveIndexType(current, ((PathSegment.Index) segment).index, method, target);
            } else {
                _error(method, target, "@PutByPath currently supports only Name/Index intermediate path segments");
                return;
            }
            if (current == null) return;
        }
        TypeMirror finalParentType = current;

        TypeMirror valueType = _resolvePutValueType(finalParentType, segments[segments.length - 1], method, target);
        if (valueType == null) return;

        if (!_validateAssignable(method, target, method.getParameters().get(1).asType(), valueType, "@PutByPath value type")) return;
        TypeMirror oldType = _resolvePutOldType(finalParentType, segments[segments.length - 1]);
        if (oldType == null && method.getReturnType().getKind().isPrimitive()) {
            _error(method, target, "@PutByPath return type mismatch: append returns null");
            return;
        }
        if (oldType != null && method.getReturnType().getKind() != TypeKind.VOID &&
                !_validateAssignable(method, target, oldType, method.getReturnType(), "@PutByPath return type")) {
            return;
        }

        target.addMethod(out ->
                _emitMethodPut(out, method, root, method.getParameters().get(1), path, finalParentType, valueType));
    }

    private boolean _validateAssignable(Element element, GeneratedClass target, TypeMirror from, TypeMirror to, String label) {
        if (_isAssignableBoxed(from, to)) return true;
        _error(element, target, label + " mismatch: cannot assign " + from + " to " + to);
        return false;
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }

    private JsonPath _resolvePath(ExecutableElement method, GeneratedClass target, String expr) {
        JsonPath path;
        try {
            path = JsonPath.parse(expr);
        } catch (JsonException e) {
            _error(method, target, "Invalid JSON Path value: " + e.getMessage());
            return null;
        }
        if (path.length() < 2) {
            _error(method, target, "Invalid JSON Path value: requires a non-root path");
            return null;
        }
        return path;
    }

    private TypeMirror _resolveNameType(TypeMirror current, String name, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, current) || GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonObjectType)) {
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.mapType)) {
            return GeneratorUtil.mapValueType(ctx, current);
        }

        TypeElement type = GeneratorUtil.asTypeElement(current);
        if (type == null) {
            _error(context, target, "Cannot resolve property '" + name + "' on " + current);
            return null;
        }

        ExecutableElement getter = _findReadable(type, current, name);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            return mt.getReturnType();
        }

        VariableElement field = GeneratorUtil.findField(ctx, type, name);
        if (field != null) {
            return ctx.types.asMemberOf((DeclaredType) current, field);
        }

        _error(context, target, "Cannot resolve readable property '" + name + "' on " + current);
        return null;
    }

    private TypeMirror _resolveIndexType(TypeMirror current, int index, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, current) || GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (current.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) current).getComponentType();
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.listType)) {
            return GeneratorUtil.listValueType(ctx, current);
        }
        _error(context, target, "Cannot resolve index [" + index + "] on " + current);
        return null;
    }

    private TypeMirror _resolveParamType(TypeMirror current, VariableElement param, Element context, GeneratedClass target) {
        TypeMirror paramType = param.asType();
        if (_isString(paramType)) return _resolveParamNameType(current, param.getSimpleName().toString(), context, target);
        if (_isInt(paramType)) return _resolveParamIndexType(current, param.getSimpleName().toString(), context, target);
        _error(context, target, "@GetByPath path parameter '" + param.getSimpleName() + "' must be String, int, or Integer");
        return null;
    }

    private TypeMirror _resolveParamNameType(TypeMirror current, String name, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, current) || GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonObjectType)) {
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.mapType)) {
            return GeneratorUtil.mapValueType(ctx, current);
        }
        _error(context, target, "Cannot resolve dynamic key parameter '{" + name + "}' on " + current);
        return null;
    }

    private TypeMirror _resolveParamIndexType(TypeMirror current, String name, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, current) || GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (current.getKind() == TypeKind.ARRAY) return ((ArrayType) current).getComponentType();
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.listType)) return GeneratorUtil.listValueType(ctx, current);
        _error(context, target, "Cannot resolve dynamic index parameter '{" + name + "}' on " + current);
        return null;
    }

    private boolean _isString(TypeMirror type) {
        return ctx.types.isSameType(ctx.types.erasure(type),
                ctx.types.erasure(ctx.elements.getTypeElement(String.class.getName()).asType()));
    }

    private boolean _isInt(TypeMirror type) {
        return type.getKind() == TypeKind.INT ||
                ctx.types.isSameType(ctx.types.erasure(type),
                        ctx.types.erasure(ctx.elements.getTypeElement(Integer.class.getName()).asType()));
    }

    private ExecutableElement _findReadable(TypeElement type, TypeMirror owner, String name) {
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement getter = GeneratorUtil.findGetter(ctx, type, owner, "get" + suffix, false);
        if (getter == null) {
            getter = GeneratorUtil.findGetter(ctx, type, owner, "is" + suffix, true);
        }
        if (getter == null) {
            getter = GeneratorUtil.findGetter(ctx, type, owner, name, false);
        }
        return getter;
    }

    private TypeMirror _resolvePutValueType(TypeMirror parent, PathSegment segment, Element context, GeneratedClass target) {
        if (segment instanceof PathSegment.Name) {
            return _resolvePutNameValueType(parent, ((PathSegment.Name) segment).name, context, target);
        }
        if (segment instanceof PathSegment.Index) {
            return _resolvePutIndexValueType(parent, ((PathSegment.Index) segment).index, context, target);
        }
        if (segment instanceof PathSegment.Append) {
            return _resolvePutAppendValueType(parent, context, target);
        }
        _error(context, target, "@PutByPath currently supports only Name/Index/Append paths");
        return null;
    }

    private TypeMirror _resolvePutNameValueType(TypeMirror parent, String name, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) {
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            return GeneratorUtil.mapValueType(ctx, parent);
        }
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) {
            _error(context, target, "Cannot resolve writable property '" + name + "' on " + parent);
            return null;
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement setter = GeneratorUtil.findSetter(ctx, type, parent, "set" + suffix);
        if (setter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, setter);
            return mt.getParameterTypes().get(0);
        }
        VariableElement field = GeneratorUtil.findField(ctx, type, name);
        if (field != null && !field.getModifiers().contains(Modifier.FINAL)) {
            return ctx.types.asMemberOf((DeclaredType) parent, field);
        }
        _error(context, target, "Cannot resolve writable property '" + name + "' on " + parent);
        return null;
    }

    private TypeMirror _resolvePutIndexValueType(TypeMirror parent, int index, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) parent).getComponentType();
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.listType)) {
            return GeneratorUtil.listValueType(ctx, parent);
        }
        _error(context, target, "Cannot resolve index [" + index + "] on " + parent);
        return null;
    }

    private TypeMirror _resolvePutAppendValueType(TypeMirror parent, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) parent).getComponentType();
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.listType)) {
            return GeneratorUtil.listValueType(ctx, parent);
        }
        _error(context, target, "Cannot append on " + parent);
        return null;
    }

    private TypeMirror _resolvePutOldType(TypeMirror parent, PathSegment segment) {
        if (segment instanceof PathSegment.Name) return _resolvePutNameOldType(parent, ((PathSegment.Name) segment).name);
        if (segment instanceof PathSegment.Index) return _resolvePutIndexOldType(parent);
        if (segment instanceof PathSegment.Append) return null;
        return null;
    }

    private TypeMirror _resolvePutNameOldType(TypeMirror parent, String name) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) {
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            return GeneratorUtil.mapValueType(ctx, parent);
        }
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) return null;
        ExecutableElement getter = _findReadable(type, parent, name);
        if (getter != null) {
            return ((ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, getter)).getReturnType();
        }
        VariableElement field = GeneratorUtil.findField(ctx, type, name);
        if (field != null) return ctx.types.asMemberOf((DeclaredType) parent, field);
        return ctx.objectType;
    }

    private TypeMirror _resolvePutIndexOldType(TypeMirror parent) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) return ((ArrayType) parent).getComponentType();
        return GeneratorUtil.listValueType(ctx, parent);
    }

    /// emit

    private void _emitMethodGet(SourceWriter out, ExecutableElement method, VariableElement root,
                                Map<String, VariableElement> pathParams,
                                JsonPath path, TypeMirror finalType) {
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                _methodParams(method) + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        String nullReturn = _nullReturn(method, path);
        out.line("if (" + root.getSimpleName() + " == null) " + nullReturn);

        String currentVar = root.getSimpleName().toString();
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            String nextVar = "v" + (i - 1);
            boolean checkValueNull = i != segments.length - 1;
            if (segment instanceof PathSegment.Name) {
                currentType = _emitName(out, currentType, ((PathSegment.Name) segment).name, currentVar, nextVar, nullReturn, checkValueNull);
            } else if (segment instanceof PathSegment.Index) {
                currentType = _emitIndex(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar, nullReturn, checkValueNull);
            } else {
                VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
                currentType = _emitParam(out, currentType, param, currentVar, nextVar, nullReturn, checkValueNull);
            }
            currentVar = nextVar;
        }

        if (_isAssignableBoxed(finalType, method.getReturnType())) {
            out.line("return " + currentVar + ";");
        }
        out.dedent();
        out.line("}");
    }

    private String _methodParams(ExecutableElement method) {
        StringBuilder sb = new StringBuilder();
        List<? extends VariableElement> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            VariableElement param = params.get(i);
            sb.append(GeneratorUtil.typeName(param.asType())).append(' ').append(param.getSimpleName());
        }
        return sb.toString();
    }

    private TypeMirror _emitName(SourceWriter out, TypeMirror current, String name, String currentVar, String nextVar,
                                 String nullReturn, boolean checkValueNull) {
        if (current == null) {
            throw new AssertionError("CompiledPath emitName called with null current type");
        }
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line("Object " + nextVar + " = org.sjf4j.node.Nodes.getInObject(" + currentVar + ", \"" +
                    GeneratorUtil.escape(name) + "\");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonObjectType)) {
            out.line("Object " + nextVar + " = " + currentVar + ".getNode(\"" + GeneratorUtil.escape(name) + "\");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.mapType)) {
            TypeMirror outputType = GeneratorUtil.mapValueType(ctx, current);
            String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
            out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".get(\"" +
                    GeneratorUtil.escape(name) + "\");");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }

        TypeElement type = GeneratorUtil.asTypeElement(current);
        if (type == null) {
            throw new AssertionError("CompiledPath emitName cannot resolve type element for " + current);
        }
        ExecutableElement getter = _findReadable(type, current, name);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            TypeMirror outputType = mt.getReturnType();
            out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "." +
                    getter.getSimpleName() + "();");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }

        VariableElement field = GeneratorUtil.findField(ctx, type, name);
        if (field == null) {
            throw new AssertionError("CompiledPath emitName cannot resolve validated field '" +
                    name + "' on " + current);
        }
        TypeMirror outputType = ctx.types.asMemberOf((DeclaredType) current, field);
        out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "." + name + ";");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    private TypeMirror _emitIndex(SourceWriter out, TypeMirror current, int index, String currentVar, String nextVar,
                                  String nullReturn, boolean checkValueNull) {
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line("Object " + nextVar + " = org.sjf4j.node.Nodes.getInArray(" + currentVar + ", " + index + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            out.line("Object " + nextVar + " = " + currentVar + ".getNode(" + index + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (current.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) current).getComponentType();
            out.line("int " + nextVar + "i = " + _indexExpr(index, currentVar + ".length") + ";");
            out.line("if (" + nextVar + "i < 0 || " + nextVar + "i >= " + currentVar + ".length) " + nullReturn);
            out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "[" + nextVar + "i];");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line("int " + nextVar + "i = " + _indexExpr(index, currentVar + ".size()") + ";");
        out.line("if (" + nextVar + "i < 0 || " + nextVar + "i >= " + currentVar + ".size()) " + nullReturn);
        out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".get(" + nextVar + "i);");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    private TypeMirror _emitParam(SourceWriter out, TypeMirror current, VariableElement param, String currentVar, String nextVar,
                                  String nullReturn, boolean checkValueNull) {
        if (_isString(param.asType())) {
            return _emitParamName(out, current, param.getSimpleName().toString(), currentVar, nextVar, nullReturn, checkValueNull);
        }
        return _emitParamIndex(out, current, param, currentVar, nextVar, nullReturn, checkValueNull);
    }

    private TypeMirror _emitParamName(SourceWriter out, TypeMirror current, String paramName, String currentVar, String nextVar,
                                      String nullReturn, boolean checkValueNull) {
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line("Object " + nextVar + " = org.sjf4j.node.Nodes.getInObject(" + currentVar + ", " + paramName + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonObjectType)) {
            out.line("Object " + nextVar + " = " + currentVar + ".getNode(" + paramName + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        TypeMirror outputType = GeneratorUtil.mapValueType(ctx, current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".get(" + paramName + ");");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    private TypeMirror _emitParamIndex(SourceWriter out, TypeMirror current, VariableElement param, String currentVar, String nextVar,
                                       String nullReturn, boolean checkValueNull) {
        String paramName = param.getSimpleName().toString();
        String indexVar = paramName;
        if (param.asType().getKind() != TypeKind.INT) {
            out.line("if (" + paramName + " == null) " + nullReturn);
            indexVar = nextVar + "p";
            out.line("int " + indexVar + " = " + paramName + ";");
        }
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line("Object " + nextVar + " = org.sjf4j.node.Nodes.getInArray(" + currentVar + ", " + indexVar + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            out.line("Object " + nextVar + " = " + currentVar + ".getNode(" + indexVar + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (current.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) current).getComponentType();
            out.line("int " + nextVar + "i = " + indexVar + " >= 0 ? " + indexVar + " : " + currentVar + ".length + " + indexVar + ";");
            out.line("if (" + nextVar + "i < 0 || " + nextVar + "i >= " + currentVar + ".length) " + nullReturn);
            out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "[" + nextVar + "i];");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line("int " + nextVar + "i = " + indexVar + " >= 0 ? " + indexVar + " : " + currentVar + ".size() + " + indexVar + ";");
        out.line("if (" + nextVar + "i < 0 || " + nextVar + "i >= " + currentVar + ".size()) " + nullReturn);
        out.line(declaredType + " " + nextVar + " = (" + declaredType + ") " + currentVar + ".get(" + nextVar + "i);");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    private String _readValueTypeName(TypeMirror type) {
        if (type.getKind().isPrimitive()) return GeneratorUtil.typeName(type);
        return GeneratorUtil.localTypeName(ctx, type);
    }

    private static void _emitNullCheck(SourceWriter out, String var, TypeMirror type, String nullReturn) {
        if (nullReturn != null && !type.getKind().isPrimitive()) out.line("if (" + var + " == null) " + nullReturn);
    }

    private void _emitMethodPut(SourceWriter out, ExecutableElement method, VariableElement root, VariableElement value,
                                JsonPath path, TypeMirror parentType, TypeMirror valueType) {
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                GeneratorUtil.typeName(root.asType()) + " " + root.getSimpleName() + ", " +
                GeneratorUtil.typeName(value.asType()) + " " + value.getSimpleName() + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        String missing = _putMissingThrow(method, path);
        out.line("if (" + root.getSimpleName() + " == null) " + missing);

        String currentVar = root.getSimpleName().toString();
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length - 1; i++) {
            String nextVar = "v" + (i - 1);
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Name) {
                currentType = _emitName(out, currentType, ((PathSegment.Name) segment).name, currentVar, nextVar, missing, true);
            } else {
                currentType = _emitIndex(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar, missing, true);
            }
            currentVar = nextVar;
        }
        if (!ctx.types.isSameType(ctx.types.erasure(currentType), ctx.types.erasure(parentType))) {
            // validation already resolved the parent type; this is only a generator guard.
            throw new AssertionError("CompiledPath PUT parent type mismatch");
        }

        String valueExpr = value.getSimpleName().toString();
        String oldVar = "old";
        TypeMirror oldType;
        PathSegment last = segments[segments.length - 1];
        if (last instanceof PathSegment.Name) {
            oldType = _emitPutName(out, parentType, ((PathSegment.Name) last).name, currentVar, valueExpr, oldVar);
        } else if (last instanceof PathSegment.Index) {
            oldType = _emitPutIndex(out, parentType, ((PathSegment.Index) last).index, currentVar, valueExpr, oldVar);
        } else if (last instanceof PathSegment.Append) {
            oldType = _emitPutAppend(out, parentType, currentVar, valueExpr);
        } else {
            throw new AssertionError("CompiledPath PUT unsupported segment");
        }
        _emitPutReturn(out, method, oldVar, oldType);
        out.dedent();
        out.line("}");
    }

    private TypeMirror _emitPutName(SourceWriter out, TypeMirror parent, String name, String parentVar, String valueExpr, String oldVar) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInObject(" + parentVar + ", \"" +
                    GeneratorUtil.escape(name) + "\", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) {
            out.line("Object " + oldVar + " = " + parentVar + ".put(\"" + GeneratorUtil.escape(name) + "\", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            TypeMirror outputType = GeneratorUtil.mapValueType(ctx, parent);
            out.line(GeneratorUtil.localTypeName(ctx, outputType) + " " + oldVar + " = " + parentVar + ".put(\"" +
                    GeneratorUtil.escape(name) + "\", " + valueExpr + ");");
            return outputType;
        }
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) {
            throw new AssertionError("CompiledPath PUT cannot resolve type element for " + parent);
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement setter = GeneratorUtil.findSetter(ctx, type, parent, "set" + suffix);
        ExecutableElement getter = _findReadable(type, parent, name);
        VariableElement field = GeneratorUtil.findField(ctx, type, name);
        TypeMirror oldType = null;
        if (getter != null) {
            oldType = ((ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, getter)).getReturnType();
        } else if (field != null) {
            oldType = ctx.types.asMemberOf((DeclaredType) parent, field);
        }
        out.line((oldType == null ? "Object" : GeneratorUtil.localTypeName(ctx, oldType)) + " " + oldVar + " = " +
                (getter != null ? parentVar + "." + getter.getSimpleName() + "()"
                        : field != null ? parentVar + "." + name : "null") + ";");
        if (setter != null) {
            out.line(parentVar + "." + setter.getSimpleName() + "(" + valueExpr + ");");
        } else {
            if (field == null) throw new AssertionError("CompiledPath PUT cannot resolve writable field '" + name + "'");
            out.line(parentVar + "." + name + " = " + valueExpr + ";");
        }
        return oldType == null ? ctx.objectType : oldType;
    }

    private TypeMirror _emitPutIndex(SourceWriter out, TypeMirror parent, int index, String parentVar, String valueExpr, String oldVar) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInArray(" +
                    parentVar + ", " + index + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInArray(" +
                    parentVar + ", " + index + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) parent).getComponentType();
            out.line(_readValueTypeName(outputType) + " " + oldVar + " = (" + _readValueTypeName(outputType) +
                    ") org.sjf4j.node.Nodes.putInArray(" + parentVar + ", " + index + ", " + valueExpr + ");");
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, parent);
        out.line(GeneratorUtil.localTypeName(ctx, outputType) + " " + oldVar + " = (" +
                GeneratorUtil.localTypeName(ctx, outputType) + ") org.sjf4j.node.Nodes.putInArray(" +
                parentVar + ", " + index + ", " + valueExpr + ");");
        return outputType;
    }

    private TypeMirror _emitPutAppend(SourceWriter out, TypeMirror parent, String parentVar, String valueExpr) {
        if (GeneratorUtil.isObject(ctx, parent) || parent.getKind() == TypeKind.ARRAY) {
            out.line("org.sjf4j.node.Nodes.addInArray(" + parentVar + ", " + valueExpr + ");");
            return null;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType) ||
                GeneratorUtil.isAssignableErasure(ctx, parent, ctx.listType)) {
            out.line(parentVar + ".add(" + valueExpr + ");");
            return null;
        }
        throw new AssertionError("CompiledPath PUT cannot append on " + parent);
    }

    private static String _indexExpr(int index, String sizeExpr) {
        if (index >= 0) return Integer.toString(index);
        if (index == Integer.MIN_VALUE) return sizeExpr + " + " + index;
        return sizeExpr + " - " + (-index);
    }

    private void _emitPutReturn(SourceWriter out, ExecutableElement method, String oldVar, TypeMirror oldType) {
        TypeMirror returnType = method.getReturnType();
        if (returnType.getKind() == TypeKind.VOID) return;
        if (oldType == null) {
            out.line("return null;");
            return;
        }
        if (_isAssignableBoxed(oldType, returnType)) {
            out.line("return " + oldVar + ";");
        }
    }

    private String _nullReturn(ExecutableElement method, JsonPath path) {
        TypeMirror returnType = method.getReturnType();
        if (!returnType.getKind().isPrimitive()) {
            return "return null;";
        }
        return "throw new org.sjf4j.exception.JsonException(\"@GetByPath primitive result is missing: method " +
                GeneratorUtil.escape(method.getSimpleName().toString()) + " returns " +
                GeneratorUtil.escape(GeneratorUtil.typeName(returnType)) + " for path " +
                GeneratorUtil.escape(path.toExpr()) + "\");";
    }

    private boolean _isAssignableBoxed(TypeMirror from, TypeMirror to) {
        return ctx.types.isAssignable(
                ctx.types.erasure(GeneratorUtil.boxed(ctx, from)),
                ctx.types.erasure(GeneratorUtil.boxed(ctx, to)));
    }

    private String _putMissingThrow(ExecutableElement method, JsonPath path) {
        return "throw new org.sjf4j.exception.JsonException(\"@PutByPath missing parent: method " +
                GeneratorUtil.escape(method.getSimpleName().toString()) + " for path " +
                GeneratorUtil.escape(path.toExpr()) + "\");";
    }

}
