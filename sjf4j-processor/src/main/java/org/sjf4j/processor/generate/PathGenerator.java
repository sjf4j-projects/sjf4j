package org.sjf4j.processor.generate;

import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
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
        Map<String, VariableElement> pathParams = _resolvePathParams(method, target, path, 1, method.getParameters().size(), "@GetByPath");
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

    /**
     * Resolves dynamic path parameters ({@code {name}}) to method parameters and
     * rejects extra method parameters that are not consumed by the path.
     */
    private Map<String, VariableElement> _resolvePathParams(ExecutableElement method, GeneratedClass target, JsonPath path,
                                                             int firstParam, int endParam, String annotation) {
        List<? extends VariableElement> params = method.getParameters();
        Map<String, VariableElement> methodParams = new HashMap<>();
        for (int i = firstParam; i < endParam; i++) {
            VariableElement param = params.get(i);
            methodParams.put(param.getSimpleName().toString(), param);
        }
        Set<String> used = new HashSet<>();
        for (PathSegment segment : path.segments()) {
            if (segment instanceof PathSegment.Param) {
                String name = ((PathSegment.Param) segment).param;
                if (!methodParams.containsKey(name)) {
                    _error(method, target, annotation + " path parameter '{" + name + "}' has no matching method parameter");
                    return null;
                }
                used.add(name);
            }
        }
        for (String name : methodParams.keySet()) {
            if (!used.contains(name)) {
                _error(method, target, annotation + " method parameter '" + name + "' is not used by the path");
                return null;
            }
        }
        return methodParams;
    }

    /**
     * Validates and emits a generated implementation for one {@code @PutByPath} method.
     */
    public void genPut(ExecutableElement method, GeneratedClass target, String expr) {
        _genPut(method, target, expr, false);
    }

    /**
     * Validates and emits a generated implementation for one {@code @PutIfParentPresentByPath} method.
     */
    public void genPutIfParentPresent(ExecutableElement method, GeneratedClass target, String expr) {
        _genPut(method, target, expr, true);
    }

    /**
     * Validates and emits a generated implementation for one {@code @EnsurePutByPath} method.
     */
    public void genEnsurePut(ExecutableElement method, GeneratedClass target, String expr) {
        _genEnsurePut(method, target, expr, false);
    }

    /**
     * Validates and emits a generated implementation for one {@code @EnsurePutIfAbsentByPath} method.
     */
    public void genEnsurePutIfAbsent(ExecutableElement method, GeneratedClass target, String expr) {
        _genEnsurePut(method, target, expr, true);
    }

    /**
     * Shared validation for ensure-style put methods. The emitted code path stays
     * separate from normal put because intermediate traversal creates missing
     * containers instead of failing or returning early.
     */
    private void _genEnsurePut(ExecutableElement method, GeneratedClass target, String expr, boolean ifAbsent) {
        String annotation = ifAbsent ? "@EnsurePutIfAbsentByPath" : "@EnsurePutByPath";
        if (method.getParameters().size() < 2) {
            _error(method, target, annotation + " method must have root and value parameters");
            return;
        }

        JsonPath path = _resolvePath(method, target, expr);
        if (path == null) return;
        if (!path.isSinglePut()) {
            _error(method, target, annotation + " currently supports only Name/Index/Param/Append paths");
            return;
        }

        VariableElement root = method.getParameters().get(0);
        VariableElement value = method.getParameters().get(method.getParameters().size() - 1);
        Map<String, VariableElement> pathParams = _resolvePathParams(method, target, path, 1,
                method.getParameters().size() - 1, annotation);
        if (pathParams == null) return;
        PathSegment[] segments = path.segments();
        TypeMirror current = root.asType();
        for (int i = 1, len = segments.length - 1; i < len; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Name) {
                String name = ((PathSegment.Name) segment).name;
                if (!_validateWritableNameIfPojo(current, name, method, target)) return;
                current = _resolveNameType(current, name, method, target);
                if (current == null) return;
            } else if (segment instanceof PathSegment.Index) {
                current = _resolveIndexType(current, ((PathSegment.Index) segment).index, method, target);
            } else if (segment instanceof PathSegment.Append) {
                current = _resolvePutAppendValueType(current, method, target);
            } else if (segment instanceof PathSegment.Param) {
                current = _resolvePutParamType(current, pathParams.get(((PathSegment.Param) segment).param), method, target, annotation);
            } else {
                _error(method, target, annotation + " currently supports only Name/Index/Param/Append intermediate path segments");
                return;
            }
            if (current == null) return;
            if (!current.getKind().isPrimitive() &&
                    _createContainerExpr(current, segments[i + 1], pathParams, method, target) == null) {
                return;
            }
        }
        TypeMirror finalParentType = current;

        TypeMirror valueType = _resolvePutValueType(finalParentType, segments[segments.length - 1], pathParams, method, target, annotation);
        if (valueType == null) return;
        if (ifAbsent && segments[segments.length - 1] instanceof PathSegment.Name &&
                !_validateReadableNameIfPojo(finalParentType, ((PathSegment.Name) segments[segments.length - 1]).name, method, target)) {
            return;
        }
        if (!_validateAssignable(method, target, value.asType(), valueType, annotation + " value type")) return;
        TypeMirror oldType = _resolvePutOldType(finalParentType, segments[segments.length - 1], pathParams);
        if (method.getReturnType().getKind().isPrimitive() && (ifAbsent || oldType == null)) {
            _error(method, target, annotation + " return type mismatch: " +
                    (ifAbsent ? "absent write returns null" : "append returns null"));
            return;
        }
        if (oldType != null && method.getReturnType().getKind() != TypeKind.VOID &&
                !_validateAssignable(method, target, oldType, method.getReturnType(), annotation + " return type")) {
            return;
        }

        target.addMethod(out -> _emitMethodEnsurePut(out, method, root, value, pathParams, path,
                finalParentType, ifAbsent));
    }

    /**
     * Shared validation and emission for plain put operations. Intermediate path
     * segments are read-only traversal; missing parents either throw or return
     * {@code null}, depending on {@code ifParentPresent}.
     */
    private void _genPut(ExecutableElement method, GeneratedClass target, String expr, boolean ifParentPresent) {
        String annotation = ifParentPresent ? "@PutIfParentPresentByPath" : "@PutByPath";
        if (method.getParameters().size() < 2) {
            _error(method, target, annotation + " method must have root and value parameters");
            return;
        }

        JsonPath path = _resolvePath(method, target, expr);
        if (path == null) return;
        if (!path.isSinglePut()) {
            _error(method, target, annotation + " currently supports only Name/Index/Param/Append paths");
            return;
        }
        if (path.appendCount() > 1 || (path.appendCount() > 0 && !(path.tail() instanceof PathSegment.Append))) {
            _error(method, target, annotation + " append segment must be the final path segment");
            return;
        }

        VariableElement root = method.getParameters().get(0);
        VariableElement value = method.getParameters().get(method.getParameters().size() - 1);
        Map<String, VariableElement> pathParams = _resolvePathParams(method, target, path, 1,
                method.getParameters().size() - 1, annotation);
        if (pathParams == null) return;
        PathSegment[] segments = path.segments();
        TypeMirror current = root.asType();
        for (int i = 1, len = segments.length - 1; i < len; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Name) {
                current = _resolveNameType(current, ((PathSegment.Name) segment).name, method, target);
            } else if (segment instanceof PathSegment.Index) {
                current = _resolveIndexType(current, ((PathSegment.Index) segment).index, method, target);
            } else if (segment instanceof PathSegment.Param) {
                current = _resolvePutParamType(current, pathParams.get(((PathSegment.Param) segment).param), method, target, annotation);
            } else {
                _error(method, target, annotation + " currently supports only Name/Index/Param intermediate path segments");
                return;
            }
            if (current == null) return;
        }
        TypeMirror finalParentType = current;

        TypeMirror valueType = _resolvePutValueType(finalParentType, segments[segments.length - 1], pathParams, method, target, annotation);
        if (valueType == null) return;

        if (!_validateAssignable(method, target, value.asType(), valueType, annotation + " value type")) return;
        TypeMirror oldType = _resolvePutOldType(finalParentType, segments[segments.length - 1], pathParams);
        if (method.getReturnType().getKind().isPrimitive() && (ifParentPresent || oldType == null)) {
            _error(method, target, annotation + " return type mismatch: " +
                    (ifParentPresent ? "missing parent returns null" : "append returns null"));
            return;
        }
        if (oldType != null && method.getReturnType().getKind() != TypeKind.VOID &&
                !_validateAssignable(method, target, oldType, method.getReturnType(), annotation + " return type")) {
            return;
        }

        target.addMethod(out ->
                _emitMethodPut(out, method, root, value, pathParams, path, finalParentType, valueType, ifParentPresent));
    }

    /**
     * Validates assignability using boxed/erased types so primitive signatures and
     * generic container element types can share the same checks.
     */
    private boolean _validateAssignable(Element element, GeneratedClass target, TypeMirror from, TypeMirror to, String label) {
        if (_isAssignableBoxed(from, to)) return true;
        _error(element, target, label + " mismatch: cannot assign " + from + " to " + to);
        return false;
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }

    /**
     * Parses an annotation path and rejects root-only paths, which cannot name a
     * child location for compiled get/put methods.
     */
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

    /**
     * Resolves the static type reached by reading an object-name segment from the
     * current type.
     */
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

    /**
     * Resolves the static element type reached by reading an array-index segment.
     */
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
        _error(context, target, "@GetByPath path parameter '" + param.getSimpleName() + "' must be String or int");
        return null;
    }

    /**
     * Resolves a put path parameter: {@code String} means object key, {@code int}
     * means array index. Other parameter types are rejected at compile time.
     */
    private TypeMirror _resolvePutParamType(TypeMirror current, VariableElement param, Element context, GeneratedClass target, String annotation) {
        TypeMirror paramType = param.asType();
        if (_isString(paramType)) return _resolveParamNameType(current, param.getSimpleName().toString(), context, target);
        if (_isInt(paramType)) return _resolveParamIndexType(current, param.getSimpleName().toString(), context, target);
        _error(context, target, annotation + " path parameter '" + param.getSimpleName() + "' must be String or int");
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
        return type.getKind() == TypeKind.INT;
    }

    /**
     * Finds the generated-code readable accessor for a POJO property, accepting
     * JavaBean getters and fluent getters.
     */
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

    /**
     * Resolves the value type accepted by the final put segment.
     */
    private TypeMirror _resolvePutValueType(TypeMirror parent, PathSegment segment, Map<String, VariableElement> pathParams,
                                            Element context, GeneratedClass target, String annotation) {
        if (segment instanceof PathSegment.Name) {
            return _resolvePutNameValueType(parent, ((PathSegment.Name) segment).name, context, target);
        }
        if (segment instanceof PathSegment.Index) {
            return _resolvePutIndexValueType(parent, ((PathSegment.Index) segment).index, context, target);
        }
        if (segment instanceof PathSegment.Append) {
            return _resolvePutAppendValueType(parent, context, target);
        }
        if (segment instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
            return _resolvePutParamType(parent, param, context, target, annotation);
        }
        _error(context, target, annotation + " currently supports only Name/Index/Param/Append paths");
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

    /**
     * Ensures an intermediate POJO property can be written back when ensure
     * traversal needs to create a missing child container.
     */
    private boolean _validateWritableNameIfPojo(TypeMirror parent, String name, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType) ||
                GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            return true;
        }
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) return true;
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement setter = GeneratorUtil.findSetter(ctx, type, parent, "set" + suffix);
        if (setter != null) return true;
        VariableElement field = GeneratorUtil.findField(ctx, type, name);
        if (field != null && !field.getModifiers().contains(Modifier.FINAL)) return true;
        _error(context, target, "Cannot resolve writable property '" + name + "' on " + parent);
        return false;
    }

    /**
     * Ensures a POJO final name target can expose its current value for
     * put-if-absent checks.
     */
    private boolean _validateReadableNameIfPojo(TypeMirror parent, String name, Element context, GeneratedClass target) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType) ||
                GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            return true;
        }
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) return true;
        if (_findReadable(type, parent, name) != null || GeneratorUtil.findField(ctx, type, name) != null) return true;
        _error(context, target, "Cannot resolve readable property '" + name + "' on " + parent);
        return false;
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

    /**
     * Resolves the type of the previous value returned by a final put segment;
     * append returns no previous value.
     */
    private TypeMirror _resolvePutOldType(TypeMirror parent, PathSegment segment, Map<String, VariableElement> pathParams) {
        if (segment instanceof PathSegment.Name) return _resolvePutNameOldType(parent, ((PathSegment.Name) segment).name);
        if (segment instanceof PathSegment.Index) return _resolvePutIndexOldType(parent);
        if (segment instanceof PathSegment.Append) return null;
        if (segment instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
            if (_isString(param.asType())) return _resolvePutNameOldType(parent, param.getSimpleName().toString());
            return _resolvePutIndexOldType(parent);
        }
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

    /**
     * Emits a direct get method by walking each segment with statically selected
     * object/array/POJO access code.
     */
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

    /**
     * Emits a read of an object-name segment and returns the read value type.
     */
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

    /**
     * Emits a read of an array-index segment, including static bounds handling
     * for Java arrays and lists.
     */
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

    private void _emitMethodPut(SourceWriter out, ExecutableElement method, VariableElement root,
                                 VariableElement value, Map<String, VariableElement> pathParams,
                                 JsonPath path, TypeMirror parentType, TypeMirror valueType, boolean ifParentPresent) {
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                _methodParams(method) + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        String missing = ifParentPresent ? _putMissingReturn(method) : _putMissingThrow(method, path);
        out.line("if (" + root.getSimpleName() + " == null) " + missing);

        String currentVar = root.getSimpleName().toString();
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length - 1; i++) {
            String nextVar = "v" + (i - 1);
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Name) {
                currentType = _emitName(out, currentType, ((PathSegment.Name) segment).name, currentVar, nextVar, missing, true);
            } else if (segment instanceof PathSegment.Index) {
                currentType = _emitIndex(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar, missing, true);
            } else {
                VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
                currentType = _emitParam(out, currentType, param, currentVar, nextVar, missing, true);
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
        } else if (last instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            oldType = _emitPutParam(out, parentType, param, currentVar, valueExpr, oldVar, missing);
        } else {
            throw new AssertionError("CompiledPath PUT unsupported segment");
        }
        _emitPutReturn(out, method, oldVar, oldType);
        out.dedent();
        out.line("}");
    }

    /**
     * Emits ensure-style put methods. Intermediate missing/null containers are
     * allocated directly in generated code and written back before continuing.
     */
    private void _emitMethodEnsurePut(SourceWriter out, ExecutableElement method, VariableElement root,
                                      VariableElement value, Map<String, VariableElement> pathParams,
                                      JsonPath path, TypeMirror parentType, boolean ifAbsent) {
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                _methodParams(method) + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        out.line("java.util.Objects.requireNonNull(" + root.getSimpleName() + ", \"" + root.getSimpleName() + "\");");

        String currentVar = root.getSimpleName().toString();
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length - 1; i++) {
            String nextVar = "v" + (i - 1);
            PathSegment segment = segments[i];
            TypeMirror nextType;
            if (segment instanceof PathSegment.Append) {
                nextType = _appendValueType(currentType);
                out.line(GeneratorUtil.localTypeName(ctx, nextType) + " " + nextVar + ";");
                _emitCreateContainer(out, nextVar, nextType, segments[i + 1], pathParams);
                _emitPutAppend(out, currentType, currentVar, nextVar);
            } else {
                if (segment instanceof PathSegment.Name) {
                    nextType = _emitName(out, currentType, ((PathSegment.Name) segment).name, currentVar, nextVar, "return;", false);
                } else if (segment instanceof PathSegment.Index) {
                    nextType = _emitEnsureIndexRead(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar);
                } else {
                    VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
                    nextType = _isString(param.asType())
                            ? _emitParamName(out, currentType, param.getSimpleName().toString(), currentVar, nextVar, "return;", false)
                            : _emitEnsureParamIndexRead(out, currentType, param, currentVar, nextVar);
                }
                if (!nextType.getKind().isPrimitive()) {
                    out.line("if (" + nextVar + " == null) {");
                    out.indent();
                    _emitCreateContainer(out, nextVar, nextType, segments[i + 1], pathParams);
                    _emitEnsurePutBack(out, currentType, segment, pathParams, currentVar, nextVar, i);
                    out.dedent();
                    out.line("}");
                }
            }
            currentVar = nextVar;
            currentType = nextType;
        }
        if (!ctx.types.isSameType(ctx.types.erasure(currentType), ctx.types.erasure(parentType))) {
            throw new AssertionError("CompiledPath ENSURE parent type mismatch");
        }

        String valueExpr = value.getSimpleName().toString();
        String oldVar = "old";
        PathSegment last = segments[segments.length - 1];
        TypeMirror oldType = ifAbsent
                ? _emitPutIfAbsentLast(out, method, parentType, last, pathParams, currentVar, valueExpr, oldVar)
                : _emitPutLast(out, parentType, last, pathParams, currentVar, valueExpr, oldVar, "return;");
        _emitPutReturn(out, method, oldVar, oldType);
        out.dedent();
        out.line("}");
    }

    /**
     * Emits the final write for Name/Index/Append/Param and returns the old-value
     * type, or {@code null} when no previous value is available.
     */
    private TypeMirror _emitPutLast(SourceWriter out, TypeMirror parentType, PathSegment last,
                                    Map<String, VariableElement> pathParams, String parentVar,
                                    String valueExpr, String oldVar, String missing) {
        if (last instanceof PathSegment.Name) return _emitPutName(out, parentType, ((PathSegment.Name) last).name, parentVar, valueExpr, oldVar);
        if (last instanceof PathSegment.Index) return _emitPutIndex(out, parentType, ((PathSegment.Index) last).index, parentVar, valueExpr, oldVar);
        if (last instanceof PathSegment.Append) return _emitPutAppend(out, parentType, parentVar, valueExpr);
        if (last instanceof PathSegment.Param) return _emitPutParam(out, parentType, pathParams.get(((PathSegment.Param) last).param), parentVar, valueExpr, oldVar, missing);
        throw new AssertionError("CompiledPath PUT unsupported segment");
    }

    /**
     * Writes a newly-created intermediate container back into its parent path
     * segment before traversal continues.
     */
    private void _emitEnsurePutBack(SourceWriter out, TypeMirror parentType, PathSegment segment,
                                    Map<String, VariableElement> pathParams, String parentVar,
                                    String valueVar, int i) {
        _emitPutLast(out, parentType, segment, pathParams, parentVar, valueVar, "ignored" + i, "return;");
    }

    /**
     * Emits assignment of a direct container allocation expression selected from
     * the value type and the next path segment.
     */
    private void _emitCreateContainer(SourceWriter out, String var, TypeMirror type, PathSegment next,
                                      Map<String, VariableElement> pathParams) {
        String expr = _createContainerExpr(type, next, pathParams, null, null);
        if (expr == null) throw new AssertionError("CompiledPath ENSURE cannot create container for " + type);
        out.line(var + " = " + expr + ";");
    }

    /**
     * Selects a direct allocation expression for an ensured intermediate node.
     * Object-shaped children default to {@code LinkedHashMap}; array-shaped
     * children default to {@code ArrayList}. Unsupported or non-instantiable
     * concrete targets are rejected during validation.
     */
    private String _createContainerExpr(TypeMirror type, PathSegment next, Map<String, VariableElement> pathParams,
                                        Element context, GeneratedClass target) {
        boolean object = next instanceof PathSegment.Name;
        if (next instanceof PathSegment.Param) {
            object = _isString(pathParams.get(((PathSegment.Param) next).param).asType());
        }
        if (type.getKind() == TypeKind.ARRAY) {
            _createContainerError(context, target, "Cannot create ensure intermediate array container for " + type);
            return null;
        }
        String erased = ctx.types.erasure(type).toString();
        if (GeneratorUtil.isObject(ctx, type)) {
            return object ? "new java.util.LinkedHashMap<>()" : "new java.util.ArrayList<>()";
        }
        if (object) {
            if (_isErasure(type, "java.util.Map") || _isErasure(type, "java.util.LinkedHashMap")) {
                return "new java.util.LinkedHashMap<>()";
            }
            if (_isErasure(type, "org.sjf4j.JsonObject")) return "new org.sjf4j.JsonObject()";
            if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) {
                return _newDeclaredContainerExpr(type, context, target, true);
            }
        } else {
            if (_isErasure(type, "java.util.List") || _isErasure(type, "java.util.ArrayList")) {
                return "new java.util.ArrayList<>()";
            }
            if (_isErasure(type, "org.sjf4j.JsonArray")) return "new org.sjf4j.JsonArray()";
            if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType)) {
                return _newDeclaredContainerExpr(type, context, target, false);
            }
        }
        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element == null) {
            _createContainerError(context, target, "Unsupported ensure intermediate container type " + type);
            return null;
        }
        if (element.getKind() == ElementKind.INTERFACE || element.getModifiers().contains(Modifier.ABSTRACT)) {
            _createContainerError(context, target, "Unsupported ensure intermediate " +
                    (object ? "object" : "array") + " container type " + erased +
                    ": interface or abstract type has no default allocation");
            return null;
        }
        if (!object && !GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) {
            _createContainerError(context, target, "Unsupported ensure intermediate array container type " + erased);
            return null;
        }
        return _newDeclaredContainerExpr(type, context, target, object);
    }

    /**
     * Builds {@code new T()} / {@code new T<>()} for concrete declared types after
     * checking that the constructor is accessible from generated code.
     */
    private String _newDeclaredContainerExpr(TypeMirror type, Element context, GeneratedClass target, boolean object) {
        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element == null) {
            _createContainerError(context, target, "Unsupported ensure intermediate container type " + type);
            return null;
        }
        String erased = ctx.types.erasure(type).toString();
        if (element.getKind() == ElementKind.INTERFACE || element.getModifiers().contains(Modifier.ABSTRACT)) {
            _createContainerError(context, target, "Unsupported ensure intermediate " +
                    (object ? "object" : "array") + " container type " + erased +
                    ": interface or abstract type has no default allocation");
            return null;
        }
        if (!_hasAccessibleNoArgConstructor(element, context)) {
            _createContainerError(context, target, "Ensure intermediate container type " + erased +
                    " must have an accessible no-arg constructor");
            return null;
        }
        return "new " + erased + (element.getTypeParameters().isEmpty() ? "()" : "<>()");
    }

    /**
     * Checks constructor accessibility from the generated implementation package:
     * public constructors are always valid, and package-private/protected ones are
     * valid only for same-package generated code.
     */
    private boolean _hasAccessibleNoArgConstructor(TypeElement element, Element context) {
        PackageElement targetPackage = context == null ? null : ctx.elements.getPackageOf(context);
        PackageElement typePackage = ctx.elements.getPackageOf(element);
        boolean samePackage = targetPackage == null || targetPackage.equals(typePackage);
        for (Element member : element.getEnclosedElements()) {
            if (member.getKind() != ElementKind.CONSTRUCTOR) continue;
            ExecutableElement ctor = (ExecutableElement) member;
            if (!ctor.getParameters().isEmpty()) continue;
            Set<Modifier> modifiers = ctor.getModifiers();
            if (modifiers.contains(Modifier.PUBLIC) ||
                    (samePackage && !modifiers.contains(Modifier.PRIVATE))) {
                return true;
            }
        }
        return false;
    }

    private boolean _isErasure(TypeMirror type, String qualifiedName) {
        TypeElement element = ctx.elements.getTypeElement(qualifiedName);
        return element != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(element.asType()));
    }

    private void _createContainerError(Element context, GeneratedClass target, String message) {
        if (context != null && target != null) _error(context, target, message);
    }

    /**
     * Emits the final put-if-absent operation: read current value, return it when
     * non-null, otherwise perform the normal final put and return null.
     */
    private TypeMirror _emitPutIfAbsentLast(SourceWriter out, ExecutableElement method, TypeMirror parent, PathSegment last,
                                            Map<String, VariableElement> pathParams, String parentVar,
                                            String valueExpr, String oldVar) {
        if (last instanceof PathSegment.Append) return _emitPutAppend(out, parent, parentVar, valueExpr);
        TypeMirror oldType = _resolvePutOldType(parent, last, pathParams);
        String oldDecl = GeneratorUtil.localTypeName(ctx, oldType);
        if (last instanceof PathSegment.Name) {
            _emitName(out, parent, ((PathSegment.Name) last).name, parentVar, oldVar, "return;", false);
        } else if (last instanceof PathSegment.Index) {
            out.line(oldDecl + " " + oldVar + " = (" + oldDecl + ") org.sjf4j.node.Nodes.getInArray(" + parentVar + ", " +
                    ((PathSegment.Index) last).index + ");");
        } else {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            if (_isString(param.asType())) {
                _emitParamName(out, parent, param.getSimpleName().toString(), parentVar, oldVar, "return;", false);
            } else {
                out.line(oldDecl + " " + oldVar + " = (" + oldDecl + ") org.sjf4j.node.Nodes.getInArray(" + parentVar + ", " +
                        param.getSimpleName() + ");");
            }
        }
        out.line("if (" + oldVar + " != null) {");
        out.indent();
        _emitPutReturn(out, method, oldVar, oldType);
        if (method.getReturnType().getKind() == TypeKind.VOID) out.line("return;");
        out.dedent();
        out.line("}");
        _emitPutLast(out, parent, last, pathParams, parentVar, valueExpr, "ignoredOld", "return;");
        out.line(oldVar + " = null;");
        return oldType;
    }

    /**
     * Emits index reads used by ensure traversal. These intentionally use
     * {@code Nodes.getInArray} so an out-of-range/missing value collapses to
     * {@code null} and can trigger container creation.
     */
    private TypeMirror _emitEnsureIndexRead(SourceWriter out, TypeMirror current, int index, String currentVar, String nextVar) {
        TypeMirror outputType = _indexValueType(current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line(declaredType + " " + nextVar + " = (" + declaredType + ") org.sjf4j.node.Nodes.getInArray(" +
                currentVar + ", " + index + ");");
        return outputType;
    }

    /**
     * Emits dynamic-index reads for ensure traversal.
     */
    private TypeMirror _emitEnsureParamIndexRead(SourceWriter out, TypeMirror current, VariableElement param,
                                                String currentVar, String nextVar) {
        TypeMirror outputType = _indexValueType(current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line(declaredType + " " + nextVar + " = (" + declaredType + ") org.sjf4j.node.Nodes.getInArray(" +
                currentVar + ", " + param.getSimpleName() + ");");
        return outputType;
    }

    private TypeMirror _indexValueType(TypeMirror current) {
        if (GeneratorUtil.isObject(ctx, current) || GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) return ctx.objectType;
        if (current.getKind() == TypeKind.ARRAY) return ((ArrayType) current).getComponentType();
        return GeneratorUtil.listValueType(ctx, current);
    }

    private TypeMirror _appendValueType(TypeMirror parent) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) return ctx.objectType;
        if (parent.getKind() == TypeKind.ARRAY) return ((ArrayType) parent).getComponentType();
        return GeneratorUtil.listValueType(ctx, parent);
    }

    /**
     * Emits a final object-name write and captures the previous value when the
     * backing structure can expose one.
     */
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

    /**
     * Emits a final array-index write using shared Nodes semantics for arrays,
     * lists, JsonArray, and Object-typed native nodes.
     */
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

    /**
     * Emits append writes. Append has no previous value, so callers must handle a
     * {@code null} old type for return validation and emission.
     */
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

    /**
     * Emits a final dynamic-key or dynamic-index write based on parameter type.
     */
    private TypeMirror _emitPutParam(SourceWriter out, TypeMirror parent, VariableElement param, String parentVar,
                                     String valueExpr, String oldVar, String missing) {
        if (_isString(param.asType())) {
            return _emitPutParamName(out, parent, param.getSimpleName().toString(), parentVar, valueExpr, oldVar);
        }
        return _emitPutParamIndex(out, parent, param, parentVar, valueExpr, oldVar, missing);
    }

    private TypeMirror _emitPutParamName(SourceWriter out, TypeMirror parent, String paramName, String parentVar,
                                         String valueExpr, String oldVar) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInObject(" + parentVar + ", " + paramName + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) {
            out.line("Object " + oldVar + " = " + parentVar + ".put(" + paramName + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (!GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            throw new AssertionError("CompiledPath PUT cannot resolve dynamic key on " + parent);
        }
        TypeMirror outputType = GeneratorUtil.mapValueType(ctx, parent);
        out.line(GeneratorUtil.localTypeName(ctx, outputType) + " " + oldVar + " = " + parentVar + ".put(" +
                paramName + ", " + valueExpr + ");");
        return outputType;
    }

    private TypeMirror _emitPutParamIndex(SourceWriter out, TypeMirror parent, VariableElement param, String parentVar,
                                          String valueExpr, String oldVar, String missing) {
        String paramName = param.getSimpleName().toString();
        String indexVar = paramName;
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInArray(" + parentVar + ", " + indexVar + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) parent).getComponentType();
            out.line(_readValueTypeName(outputType) + " " + oldVar + " = (" + _readValueTypeName(outputType) +
                    ") org.sjf4j.node.Nodes.putInArray(" + parentVar + ", " + indexVar + ", " + valueExpr + ");");
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, parent);
        out.line(GeneratorUtil.localTypeName(ctx, outputType) + " " + oldVar + " = (" +
                GeneratorUtil.localTypeName(ctx, outputType) + ") org.sjf4j.node.Nodes.putInArray(" +
                parentVar + ", " + indexVar + ", " + valueExpr + ");");
        return outputType;
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

    private String _putMissingReturn(ExecutableElement method) {
        if (method.getReturnType().getKind() == TypeKind.VOID) return "return;";
        return "return null;";
    }

}
