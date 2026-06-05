package org.sjf4j.processor.path;

import org.sjf4j.annotation.path.EnsurePutByPath;
import org.sjf4j.annotation.path.EnsurePutIfAbsentByPath;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.path.PutByPath;
import org.sjf4j.annotation.path.PutIfParentPresentByPath;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.NameAllocator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;

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
    private final FindGenerator findGenerator;

    /**
     * Creates a path generator using the shared processor context.
     */
    public PathGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
        this.findGenerator = new FindGenerator(ctx);
    }


    /**
     * Generates an implementation for one {@code @CompiledPath} interface.
     */
    public void generate(TypeElement iface) {
        if (!iface.getTypeParameters().isEmpty()) {
            ctx.error(iface, "@CompiledPath interfaces cannot declare type parameters");
            return;
        }

        GeneratedClass target = new GeneratedClass(ctx, iface, GeneratorUtil.COMPILED_IMPL_POSTFIX);

        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            Set<Modifier> mods = method.getModifiers();
            if (mods.contains(Modifier.DEFAULT) || mods.contains(Modifier.STATIC)) continue;
            if (!method.getTypeParameters().isEmpty()) {
                ctx.error(method, "@CompiledPath methods cannot declare type parameters");
                return;
            }

            String generatedAnno = null;
            GetByPath get = method.getAnnotation(GetByPath.class);
            if (get != null) {
                generatedAnno = "@GetByPath";
                genGet(method, target, get.value());
            }

            PutByPath put = method.getAnnotation(PutByPath.class);
            if (put != null) {
                if (generatedAnno != null) {
                    ctx.error(method, "Path operation annotations cannot be used together: " +
                            generatedAnno + " and @PutByPath");
                    return;
                } else {
                    generatedAnno = "@PutByPath";
                    genPut(method, target, put.value());
                }
            }

            PutIfParentPresentByPath putIfParentPresentByPath = method.getAnnotation(PutIfParentPresentByPath.class);
            if (putIfParentPresentByPath != null) {
                if (generatedAnno != null) {
                    ctx.error(method, "Path operation annotations cannot be used together: " +
                            generatedAnno + " and @PutIfParentPresentByPath");
                    return;
                } else {
                    generatedAnno = "@PutIfParentPresentByPath";
                    genPutIfParentPresent(method, target, putIfParentPresentByPath.value());
                }
            }

            EnsurePutByPath ensurePut = method.getAnnotation(EnsurePutByPath.class);
            if (ensurePut != null) {
                if (generatedAnno != null) {
                    ctx.error(method, "Path operation annotations cannot be used together: " +
                            generatedAnno + " and @EnsurePutByPath");
                    return;
                } else {
                    generatedAnno = "@EnsurePutByPath";
                    genEnsurePut(method, target, ensurePut.value());
                }
            }

            EnsurePutIfAbsentByPath ensurePutIfAbsent = method.getAnnotation(EnsurePutIfAbsentByPath.class);
            if (ensurePutIfAbsent != null) {
                if (generatedAnno != null) {
                    ctx.error(method, "Path operation annotations cannot be used together: " +
                            generatedAnno + " and @EnsurePutIfAbsentByPath");
                    return;
                } else {
                    generatedAnno = "@EnsurePutIfAbsentByPath";
                    genEnsurePutIfAbsent(method, target, ensurePutIfAbsent.value());
                }
            }

            FindByPath find = method.getAnnotation(FindByPath.class);
            if (find != null) {
                if (generatedAnno != null) {
                    ctx.error(method, "Path operation annotations cannot be used together: " +
                            generatedAnno + " and @FindByPath");
                    return;
                } else {
                    generatedAnno = "@FindByPath";
                    findGenerator.genFind(method, target, find.value());
                }
            }

            if (generatedAnno == null) {
                ctx.error(method, "@CompiledPath abstract methods must be annotated, for example @GetByPath");
                return;
            }
        }
        target.emit();
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

        if (!_validateGetReturnAssignable(method, target, finalType, method.getReturnType())) return;

        target.addMethod(out -> _emitMethodGet(out, method, root, pathParams, path));
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

    private boolean _validateGetReturnAssignable(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        if (_isAssignableBoxed(from, to)) return true;
        _error(method, target, "@GetByPath return type mismatch: cannot assign " + from + " to " + to);
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
        TypeMirror result = GeneratorUtil.resolveNameType(ctx, current, name);
        if (result == null) {
            _error(context, target, "Cannot resolve readable property '" + name + "' on " + current);
        }
        return result;
    }

    /**
     * Resolves the static element type reached by reading an array-index segment.
     */
    private TypeMirror _resolveIndexType(TypeMirror current, int index, Element context, GeneratedClass target) {
        TypeMirror result = GeneratorUtil.resolveIndexType(ctx, current, index);
        if (result == null) {
            _error(context, target, "Cannot resolve index [" + index + "] on " + current);
        }
        return result;
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
        ExecutableElement setter = GeneratorUtil.findWritable(ctx, type, parent, name);
        if (setter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, setter);
            return mt.getParameterTypes().get(0);
        }
        VariableElement field = GeneratorUtil.findWritableField(ctx, type, name);
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
        ExecutableElement setter = GeneratorUtil.findWritable(ctx, type, parent, name);
        if (setter != null) return true;
        VariableElement field = GeneratorUtil.findWritableField(ctx, type, name);
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
        if (GeneratorUtil.findReadable(ctx, type, parent, name) != null || GeneratorUtil.findReadableField(ctx, type, name) != null) return true;
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
            _error(context, target, "Cannot append on Java array " + parent);
            return null;
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
        ExecutableElement getter = GeneratorUtil.findReadable(ctx, type, parent, name);
        if (getter != null) {
            return ((ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, getter)).getReturnType();
        }
        VariableElement field = GeneratorUtil.findReadableField(ctx, type, name);
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
                                 JsonPath path) {
        PathNameScope scope = _pathNameScope(method, root, null, pathParams);
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                _methodParams(method, scope) + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        String nullReturn = _nullReturn(method, path);
        out.line("if (" + scope.param(root) + " == null) " + nullReturn);

        String currentVar = scope.param(root);
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            String nextVar = _localName(scope.names, _segmentValueType(currentType, segment, pathParams), _segmentHint(segment));
            boolean checkValueNull = i != segments.length - 1;
            if (segment instanceof PathSegment.Name) {
                currentType = _emitName(out, currentType, ((PathSegment.Name) segment).name, currentVar, nextVar, nullReturn, checkValueNull);
            } else if (segment instanceof PathSegment.Index) {
                currentType = _emitIndex(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar,
                        _indexName(scope.names), nullReturn, checkValueNull);
            } else {
                VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
                currentType = _emitParam(out, currentType, param, scope.param(param), currentVar, nextVar,
                        _indexName(scope.names), nullReturn, checkValueNull);
            }
            currentVar = nextVar;
        }

        _emitGetReturn(out, currentVar);
        out.dedent();
        out.line("}");
    }

    private void _emitGetReturn(SourceWriter out, String currentVar) {
        out.line("return " + currentVar + ";");
    }

    private String _methodParams(ExecutableElement method, PathNameScope scope) {
        StringBuilder sb = new StringBuilder();
        List<? extends VariableElement> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            VariableElement param = params.get(i);
            sb.append(GeneratorUtil.typeName(param.asType())).append(' ').append(scope.param(param));
        }
        return sb.toString();
    }

    private PathNameScope _pathNameScope(ExecutableElement method, VariableElement root, VariableElement value,
                                     Map<String, VariableElement> pathParams) {
        NameAllocator allocator = new NameAllocator();
        Map<VariableElement, String> emitted = new HashMap<VariableElement, String>();
        emitted.put(root, allocator.local("root"));
        if (value != null) emitted.put(value, allocator.local("value"));
        for (VariableElement param : method.getParameters()) {
            if (param == root || param == value) continue;
            if (pathParams.containsValue(param)) {
                emitted.put(param, allocator.local(param.getSimpleName().toString()));
            } else {
                allocator.reserve(param.getSimpleName().toString());
            }
        }
        return new PathNameScope(allocator, emitted);
    }

    private String _localName(NameAllocator names, TypeMirror type, String hint) {
        return names.prefixed(_prefix(type), hint);
    }

    private String _indexName(NameAllocator names) { return names.prefixed("n", "index"); }

    private String _segmentHint(PathSegment segment) {
        if (segment instanceof PathSegment.Name) return ((PathSegment.Name) segment).name;
        if (segment instanceof PathSegment.Param) return ((PathSegment.Param) segment).param;
        if (segment instanceof PathSegment.Append) return "value";
        return "index";
    }

    private TypeMirror _segmentValueType(TypeMirror current, PathSegment segment, Map<String, VariableElement> pathParams) {
        if (segment instanceof PathSegment.Name) return _nameValueType(current, ((PathSegment.Name) segment).name);
        if (segment instanceof PathSegment.Index || segment instanceof PathSegment.Append) return _indexValueType(current);
        VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
        return _isString(param.asType()) ? _nameValueType(current, null) : _indexValueType(current);
    }

    private TypeMirror _nameValueType(TypeMirror current, String name) {
        if (GeneratorUtil.isObject(ctx, current) || GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonObjectType)) return ctx.objectType;
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.mapType)) return GeneratorUtil.mapValueType(ctx, current);
        TypeElement type = GeneratorUtil.asTypeElement(current);
        if (type != null && name != null) {
            ExecutableElement getter = GeneratorUtil.findReadable(ctx, type, current, name);
            if (getter != null) return ((ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter)).getReturnType();
            VariableElement field = GeneratorUtil.findReadableField(ctx, type, name);
            if (field != null) return ctx.types.asMemberOf((DeclaredType) current, field);
        }
        return ctx.objectType;
    }

    private String _prefix(TypeMirror type) {
        if (type == null) return "v";
        if (type.getKind() == TypeKind.ARRAY || GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType) ||
                GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) return "a";
        if (_isNumeric(type)) return "n";
        if (_isStringLike(type)) return "s";
        if (_isBoolean(type)) return "b";
        if (GeneratorUtil.isObject(ctx, type) || GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType) ||
                GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType) || GeneratorUtil.asTypeElement(type) != null) return "o";
        return "v";
    }

    private boolean _isNumeric(TypeMirror type) {
        TypeKind k = type.getKind();
        if (k == TypeKind.BYTE || k == TypeKind.SHORT || k == TypeKind.INT || k == TypeKind.LONG ||
                k == TypeKind.FLOAT || k == TypeKind.DOUBLE) return true;
        String s = ctx.types.erasure(type).toString();
        return s.equals("java.lang.Byte") || s.equals("java.lang.Short") || s.equals("java.lang.Integer") ||
                s.equals("java.lang.Long") || s.equals("java.lang.Float") || s.equals("java.lang.Double") ||
                s.equals("java.lang.Number");
    }

    private boolean _isStringLike(TypeMirror type) {
        if (type.getKind() == TypeKind.CHAR) return true;
        String s = ctx.types.erasure(type).toString();
        if (s.equals("java.lang.String") || s.equals("java.lang.Character")) return true;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        return e != null && e.getKind() == ElementKind.ENUM;
    }

    private boolean _isBoolean(TypeMirror type) {
        if (type.getKind() == TypeKind.BOOLEAN) return true;
        return ctx.types.erasure(type).toString().equals("java.lang.Boolean");
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
            out.line(declaredType + " " + nextVar + " = " + currentVar + ".get(\"" +
                    GeneratorUtil.escape(name) + "\");");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }

        TypeElement type = GeneratorUtil.asTypeElement(current);
        if (type == null) {
            throw new AssertionError("CompiledPath emitName cannot resolve type element for " + current);
        }
        ExecutableElement getter = GeneratorUtil.findReadable(ctx, type, current, name);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            TypeMirror outputType = mt.getReturnType();
            out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "." +
                    getter.getSimpleName() + "();");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }

        VariableElement field = GeneratorUtil.findReadableField(ctx, type, name);
        if (field == null) {
            throw new AssertionError("CompiledPath emitName cannot resolve validated field '" +
                    name + "' on " + current);
        }
        TypeMirror outputType = ctx.types.asMemberOf((DeclaredType) current, field);
        out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "." + field.getSimpleName() + ";");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    /**
     * Emits a read of an array-index segment, including static bounds handling
     * for Java arrays and lists.
     */
    private TypeMirror _emitIndex(SourceWriter out, TypeMirror current, int index, String currentVar, String nextVar,
                                   String indexVar, String nullReturn, boolean checkValueNull) {
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
            out.line("int " + indexVar + " = " + _indexExpr(index, currentVar + ".length") + ";");
            out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + currentVar + ".length) " + nullReturn);
            out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "[" + indexVar + "];");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line("int " + indexVar + " = " + _indexExpr(index, currentVar + ".size()") + ";");
        out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + currentVar + ".size()) " + nullReturn);
        out.line(declaredType + " " + nextVar + " = " + currentVar + ".get(" + indexVar + ");");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    private TypeMirror _emitParam(SourceWriter out, TypeMirror current, VariableElement param, String paramName,
                                  String currentVar, String nextVar, String indexVar,
                                  String nullReturn, boolean checkValueNull) {
        if (_isString(param.asType())) {
            return _emitParamName(out, current, paramName, currentVar, nextVar, nullReturn, checkValueNull);
        }
        return _emitParamIndex(out, current, paramName, currentVar, nextVar, indexVar, nullReturn, checkValueNull);
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
        out.line(declaredType + " " + nextVar + " = " + currentVar + ".get(" + paramName + ");");
        _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
        return outputType;
    }

    private TypeMirror _emitParamIndex(SourceWriter out, TypeMirror current, String paramName, String currentVar, String nextVar,
                                       String indexVar, String nullReturn, boolean checkValueNull) {
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line("Object " + nextVar + " = org.sjf4j.node.Nodes.getInArray(" + currentVar + ", " + paramName + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            out.line("Object " + nextVar + " = " + currentVar + ".getNode(" + paramName + ");");
            _emitNullCheck(out, nextVar, ctx.objectType, checkValueNull ? nullReturn : null);
            return ctx.objectType;
        }
        if (current.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) current).getComponentType();
            out.line("int " + indexVar + " = " + paramName + " >= 0 ? " + paramName + " : " + currentVar + ".length + " + paramName + ";");
            out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + currentVar + ".length) " + nullReturn);
            out.line(_readValueTypeName(outputType) + " " + nextVar + " = " + currentVar + "[" + indexVar + "];");
            _emitNullCheck(out, nextVar, outputType, checkValueNull ? nullReturn : null);
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        out.line("int " + indexVar + " = " + paramName + " >= 0 ? " + paramName + " : " + currentVar + ".size() + " + paramName + ";");
        out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + currentVar + ".size()) " + nullReturn);
        out.line(declaredType + " " + nextVar + " = " + currentVar + ".get(" + indexVar + ");");
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
        PathNameScope scope = _pathNameScope(method, root, value, pathParams);
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                _methodParams(method, scope) + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        String missing = ifParentPresent ? _putMissingReturn(method) : _putMissingThrow(method, path);
        out.line("if (" + scope.param(root) + " == null) " + missing);

        String currentVar = scope.param(root);
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length - 1; i++) {
            PathSegment segment = segments[i];
            String nextVar = _localName(scope.names, _segmentValueType(currentType, segment, pathParams), _segmentHint(segment));
            if (segment instanceof PathSegment.Name) {
                currentType = _emitName(out, currentType, ((PathSegment.Name) segment).name, currentVar, nextVar, missing, true);
            } else if (segment instanceof PathSegment.Index) {
                currentType = _emitIndex(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar,
                        _indexName(scope.names), missing, true);
            } else {
                VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
                currentType = _emitParam(out, currentType, param, scope.param(param), currentVar, nextVar,
                        _indexName(scope.names), missing, true);
            }
            currentVar = nextVar;
        }
        if (!ctx.types.isSameType(ctx.types.erasure(currentType), ctx.types.erasure(parentType))) {
            // validation already resolved the parent type; this is only a generator guard.
            throw new AssertionError("CompiledPath PUT parent type mismatch");
        }

        String valueExpr = scope.param(value);
        String oldVar = _localName(scope.names, _resolvePutOldType(parentType, segments[segments.length - 1], pathParams), "old");
        TypeMirror oldType;
        PathSegment last = segments[segments.length - 1];
        String lastIndexVar = (last instanceof PathSegment.Index ||
                (last instanceof PathSegment.Param && _isInt(pathParams.get(((PathSegment.Param) last).param).asType())))
                ? _indexName(scope.names) : null;
        if (last instanceof PathSegment.Name) {
            oldType = _emitPutName(out, parentType, ((PathSegment.Name) last).name, currentVar, valueExpr, oldVar);
        } else if (last instanceof PathSegment.Index) {
            oldType = _emitPutIndex(out, parentType, ((PathSegment.Index) last).index, currentVar, valueExpr, oldVar, lastIndexVar);
        } else if (last instanceof PathSegment.Append) {
            oldType = _emitPutAppend(out, parentType, currentVar, valueExpr);
        } else if (last instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            oldType = _emitPutParam(out, parentType, param, scope.param(param), currentVar, valueExpr, oldVar, lastIndexVar, missing);
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
        PathNameScope scope = _pathNameScope(method, root, value, pathParams);
        out.line("");
        out.line("@Override");
        out.line("public " + GeneratorUtil.typeName(method.getReturnType()) + " " + method.getSimpleName() + "(" +
                _methodParams(method, scope) + ") {");
        out.indent();
        PathSegment[] segments = path.segments();
        out.line("java.util.Objects.requireNonNull(" + scope.param(root) + ", \"" + root.getSimpleName() + "\");");

        String currentVar = scope.param(root);
        TypeMirror currentType = root.asType();
        for (int i = 1; i < segments.length - 1; i++) {
            PathSegment segment = segments[i];
            String nextVar = _localName(scope.names, _segmentValueType(currentType, segment, pathParams), _segmentHint(segment));
            String indexVar = (segment instanceof PathSegment.Index ||
                    (segment instanceof PathSegment.Param && _isInt(pathParams.get(((PathSegment.Param) segment).param).asType())))
                    ? _indexName(scope.names) : null;
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
                    nextType = _emitEnsureIndexRead(out, currentType, ((PathSegment.Index) segment).index, currentVar, nextVar, indexVar);
                } else {
                    VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
                    nextType = _isString(param.asType())
                            ? _emitParamName(out, currentType, scope.param(param), currentVar, nextVar, "return;", false)
                            : _emitEnsureParamIndexRead(out, currentType, scope.param(param), currentVar, nextVar, indexVar);
                }
                if (!nextType.getKind().isPrimitive()) {
                    out.line("if (" + nextVar + " == null) {");
                    out.indent();
                    _emitCreateContainer(out, nextVar, nextType, segments[i + 1], pathParams);
                    _emitEnsurePutBack(out, currentType, segment, pathParams, scope, currentVar, nextVar, indexVar);
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

        String valueExpr = scope.param(value);
        String oldVar = _localName(scope.names, _resolvePutOldType(parentType, segments[segments.length - 1], pathParams), "old");
        PathSegment last = segments[segments.length - 1];
        if (ifAbsent) {
            _emitPutIfAbsentLast(out, method, parentType, last, pathParams, scope, currentVar, valueExpr, oldVar);
        } else {
            _emitPutReturn(out, method, oldVar,
                    _emitPutLast(out, parentType, last, pathParams, scope, currentVar, valueExpr, oldVar, "return;"));
        }
        out.dedent();
        out.line("}");
    }

    /**
     * Emits the final write for Name/Index/Append/Param and returns the old-value
     * type, or {@code null} when no previous value is available.
     */
    private TypeMirror _emitPutLast(SourceWriter out, TypeMirror parentType, PathSegment last,
                                    Map<String, VariableElement> pathParams, PathNameScope scope, String parentVar,
                                    String valueExpr, String oldVar, String missing) {
        if (last instanceof PathSegment.Name) return _emitPutName(out, parentType, ((PathSegment.Name) last).name, parentVar, valueExpr, oldVar);
        if (last instanceof PathSegment.Index) return _emitPutIndex(out, parentType, ((PathSegment.Index) last).index, parentVar, valueExpr, oldVar, _indexName(scope.names));
        if (last instanceof PathSegment.Append) return _emitPutAppend(out, parentType, parentVar, valueExpr);
        if (last instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            return _emitPutParam(out, parentType, param, scope.param(param), parentVar, valueExpr, oldVar,
                    _isInt(param.asType()) ? _indexName(scope.names) : null, missing);
        }
        throw new AssertionError("CompiledPath PUT unsupported segment");
    }

    /**
     * Writes a newly-created intermediate container back into its parent path
     * segment before traversal continues.
     */
    private void _emitEnsurePutBack(SourceWriter out, TypeMirror parentType, PathSegment segment,
                                    Map<String, VariableElement> pathParams, PathNameScope scope, String parentVar,
                                    String valueVar, String indexVar) {
        if (segment instanceof PathSegment.Index) {
            _emitPutIndexNoOld(out, parentType, ((PathSegment.Index) segment).index, parentVar, valueVar,
                    indexVar);
        } else if (segment instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) segment).param);
            if (_isInt(param.asType())) {
                _emitPutParamIndexNoOld(out, parentType, scope.param(param), parentVar, valueVar, "return;", indexVar);
            } else {
                _emitPutParamNameNoOld(out, parentType, scope.param(param), parentVar, valueVar);
            }
        } else {
            _emitPutLastNoOld(out, parentType, segment, pathParams, scope, parentVar, valueVar, "return;");
        }
    }

    /**
     * Emits a write when the caller does not need the previous value, avoiding
     * dead local variables and unnecessary POJO getter calls on ensure put-back
     * and absent writes.
     */
    private void _emitPutLastNoOld(SourceWriter out, TypeMirror parentType, PathSegment last,
                                   Map<String, VariableElement> pathParams, PathNameScope scope, String parentVar,
                                   String valueExpr, String missing) {
        if (last instanceof PathSegment.Name) {
            _emitPutNameNoOld(out, parentType, ((PathSegment.Name) last).name, parentVar, valueExpr);
        } else if (last instanceof PathSegment.Index) {
            _emitPutIndexNoOld(out, parentType, ((PathSegment.Index) last).index, parentVar, valueExpr, _indexName(scope.names));
        } else if (last instanceof PathSegment.Append) {
            _emitPutAppend(out, parentType, parentVar, valueExpr);
        } else if (last instanceof PathSegment.Param) {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            _emitPutParamNoOld(out, parentType, param, scope.param(param), parentVar, valueExpr, missing,
                    _isInt(param.asType()) ? _indexName(scope.names) : null);
        } else {
            throw new AssertionError("CompiledPath PUT unsupported segment");
        }
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
    private void _emitPutIfAbsentLast(SourceWriter out, ExecutableElement method, TypeMirror parent, PathSegment last,
                                      Map<String, VariableElement> pathParams, PathNameScope scope, String parentVar,
                                      String valueExpr, String oldVar) {
        if (last instanceof PathSegment.Append) {
            _emitPutAppend(out, parent, parentVar, valueExpr);
            _emitNullReturn(out, method);
            return;
        }
        TypeMirror oldType = _resolvePutOldType(parent, last, pathParams);
        String indexVar = (last instanceof PathSegment.Index ||
                (last instanceof PathSegment.Param && _isInt(pathParams.get(((PathSegment.Param) last).param).asType())))
                ? _indexName(scope.names) : null;
        if (last instanceof PathSegment.Name) {
            _emitName(out, parent, ((PathSegment.Name) last).name, parentVar, oldVar, "return;", false);
        } else if (last instanceof PathSegment.Index) {
            _emitEnsureIndexRead(out, parent, ((PathSegment.Index) last).index, parentVar, oldVar, indexVar);
        } else {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            if (_isString(param.asType())) {
                _emitParamName(out, parent, scope.param(param), parentVar, oldVar, "return;", false);
            } else {
                _emitEnsureParamIndexRead(out, parent, scope.param(param), parentVar, oldVar, indexVar);
            }
        }
        out.line("if (" + oldVar + " != null) {");
        out.indent();
        _emitPutReturn(out, method, oldVar, oldType);
        if (method.getReturnType().getKind() == TypeKind.VOID) out.line("return;");
        out.dedent();
        out.line("}");
        if (last instanceof PathSegment.Index) {
            _emitPutIndexNoOld(out, parent, ((PathSegment.Index) last).index, parentVar, valueExpr, indexVar);
        } else if (last instanceof PathSegment.Param && _isInt(pathParams.get(((PathSegment.Param) last).param).asType())) {
            VariableElement param = pathParams.get(((PathSegment.Param) last).param);
            _emitPutParamIndexNoOld(out, parent, scope.param(param), parentVar, valueExpr, "return;", indexVar);
        } else {
            _emitPutLastNoOld(out, parent, last, pathParams, scope, parentVar, valueExpr, "return;");
        }
        _emitNullReturn(out, method);
    }

    private void _emitNullReturn(SourceWriter out, ExecutableElement method) {
        if (method.getReturnType().getKind() == TypeKind.VOID) out.line("return;");
        else out.line("return null;");
    }

    /**
     * Emits index reads used by ensure traversal. Typed arrays/lists keep direct
     * access so generated code preserves static generic types and avoids helper
     * casts; missing indexes collapse to {@code null} to trigger creation.
     */
    private TypeMirror _emitEnsureIndexRead(SourceWriter out, TypeMirror current, int index, String currentVar, String nextVar, String indexVar) {
        TypeMirror outputType = _indexValueType(current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line(declaredType + " " + nextVar + " = (" + declaredType + ") org.sjf4j.node.Nodes.getInArray(" +
                    currentVar + ", " + index + ");");
        } else if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            out.line("int " + indexVar + " = " + _indexExpr(index, currentVar + ".size()") + ";");
            out.line(declaredType + " " + nextVar + " = " + indexVar + " < 0 || " + indexVar + " >= " +
                    currentVar + ".size() ? null : " + currentVar + ".getNode(" + indexVar + ");");
        } else if (current.getKind() == TypeKind.ARRAY) {
            out.line("int " + indexVar + " = " + _indexExpr(index, currentVar + ".length") + ";");
            out.line(declaredType + " " + nextVar + " = " + indexVar + " < 0 || " + indexVar + " >= " +
                    currentVar + ".length ? null : " + currentVar + "[" + indexVar + "];");
        } else {
            out.line("int " + indexVar + " = " + _indexExpr(index, currentVar + ".size()") + ";");
            out.line(declaredType + " " + nextVar + " = " + indexVar + " < 0 || " + indexVar + " >= " +
                    currentVar + ".size() ? null : " + currentVar + ".get(" + indexVar + ");");
        }
        return outputType;
    }

    /**
     * Emits dynamic-index reads for ensure traversal.
     */
    private TypeMirror _emitEnsureParamIndexRead(SourceWriter out, TypeMirror current, String paramName,
                                                String currentVar, String nextVar, String indexVar) {
        TypeMirror outputType = _indexValueType(current);
        String declaredType = GeneratorUtil.localTypeName(ctx, outputType);
        if (GeneratorUtil.isObject(ctx, current)) {
            out.line(declaredType + " " + nextVar + " = (" + declaredType + ") org.sjf4j.node.Nodes.getInArray(" +
                    currentVar + ", " + paramName + ");");
        } else if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            out.line("int " + indexVar + " = " + paramName + " >= 0 ? " + paramName +
                    " : " + currentVar + ".size() + " + paramName + ";");
            out.line(declaredType + " " + nextVar + " = " + indexVar + " < 0 || " + indexVar + " >= " +
                    currentVar + ".size() ? null : " + currentVar + ".getNode(" + indexVar + ");");
        } else if (current.getKind() == TypeKind.ARRAY) {
            out.line("int " + indexVar + " = " + paramName + " >= 0 ? " + paramName +
                    " : " + currentVar + ".length + " + paramName + ";");
            out.line(declaredType + " " + nextVar + " = " + indexVar + " < 0 || " + indexVar + " >= " +
                    currentVar + ".length ? null : " + currentVar + "[" + indexVar + "];");
        } else {
            out.line("int " + indexVar + " = " + paramName + " >= 0 ? " + paramName +
                    " : " + currentVar + ".size() + " + paramName + ";");
            out.line(declaredType + " " + nextVar + " = " + indexVar + " < 0 || " + indexVar + " >= " +
                    currentVar + ".size() ? null : " + currentVar + ".get(" + indexVar + ");");
        }
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
        ExecutableElement setter = GeneratorUtil.findWritable(ctx, type, parent, name);
        ExecutableElement getter = GeneratorUtil.findReadable(ctx, type, parent, name);
        VariableElement field = GeneratorUtil.findReadableField(ctx, type, name);
        TypeMirror oldType = null;
        if (getter != null) {
            oldType = ((ExecutableType) ctx.types.asMemberOf((DeclaredType) parent, getter)).getReturnType();
        } else if (field != null) {
            oldType = ctx.types.asMemberOf((DeclaredType) parent, field);
        }
        out.line((oldType == null ? "Object" : GeneratorUtil.localTypeName(ctx, oldType)) + " " + oldVar + " = " +
                (getter != null ? parentVar + "." + getter.getSimpleName() + "()"
                        : field != null ? parentVar + "." + field.getSimpleName() : "null") + ";");
        if (setter != null) {
            out.line(parentVar + "." + setter.getSimpleName() + "(" + valueExpr + ");");
        } else {
            if (field == null) throw new AssertionError("CompiledPath PUT cannot resolve writable field '" + name + "'");
            out.line(parentVar + "." + field.getSimpleName() + " = " + valueExpr + ";");
        }
        return oldType == null ? ctx.objectType : oldType;
    }

    private void _emitPutNameNoOld(SourceWriter out, TypeMirror parent, String name, String parentVar, String valueExpr) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("org.sjf4j.node.Nodes.putInObject(" + parentVar + ", \"" +
                    GeneratorUtil.escape(name) + "\", " + valueExpr + ");");
            return;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) {
            out.line(parentVar + ".put(\"" + GeneratorUtil.escape(name) + "\", " + valueExpr + ");");
            return;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            out.line(parentVar + ".put(\"" + GeneratorUtil.escape(name) + "\", " + valueExpr + ");");
            return;
        }
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) throw new AssertionError("CompiledPath PUT cannot resolve type element for " + parent);
        ExecutableElement setter = GeneratorUtil.findWritable(ctx, type, parent, name);
        if (setter != null) {
            out.line(parentVar + "." + setter.getSimpleName() + "(" + valueExpr + ");");
            return;
        }
        VariableElement field = GeneratorUtil.findWritableField(ctx, type, name);
        if (field == null) throw new AssertionError("CompiledPath PUT cannot resolve writable field '" + name + "'");
        out.line(parentVar + "." + field.getSimpleName() + " = " + valueExpr + ";");
    }

    /**
     * Emits a final array-index write using shared Nodes semantics for arrays,
     * lists, JsonArray, and Object-typed native nodes.
     */
    private TypeMirror _emitPutIndex(SourceWriter out, TypeMirror parent, int index, String parentVar, String valueExpr, String oldVar, String indexVar) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInArray(" +
                    parentVar + ", " + index + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            out.line("int " + indexVar + " = " + _indexExpr(index, parentVar + ".size()") + ";");
            out.line("Object " + oldVar + ";");
            out.line("if (" + indexVar + " >= 0 && " + indexVar + " < " + parentVar + ".size()) " +
                    oldVar + " = " + parentVar + ".set(" + indexVar + ", " + valueExpr + ");");
            out.line("else if (" + indexVar + " == " + parentVar + ".size()) { " + parentVar + ".add(" +
                    valueExpr + "); " + oldVar + " = null; }");
            out.line("else throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                    " + \" in JsonArray of size \" + " + parentVar + ".size());");
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) parent).getComponentType();
            out.line("int " + indexVar + " = " + _indexExpr(index, parentVar + ".length") + ";");
            out.line("if (" + indexVar + " == " + parentVar + ".length) throw new org.sjf4j.exception.JsonException(\"cannot append to a Java array\");");
            out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + parentVar + ".length) " +
                    "throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                    " + \" in Java array of size \" + " + parentVar + ".length);");
            out.line(_readValueTypeName(outputType) + " " + oldVar + " = " + parentVar + "[" + indexVar + "];");
            out.line(parentVar + "[" + indexVar + "] = " + valueExpr + ";");
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, parent);
        out.line("int " + indexVar + " = " + _indexExpr(index, parentVar + ".size()") + ";");
        out.line(GeneratorUtil.localTypeName(ctx, outputType) + " " + oldVar + ";");
        out.line("if (" + indexVar + " >= 0 && " + indexVar + " < " + parentVar + ".size()) " +
                oldVar + " = " + parentVar + ".set(" + indexVar + ", " + valueExpr + ");");
        out.line("else if (" + indexVar + " == " + parentVar + ".size()) { " + parentVar + ".add(" +
                valueExpr + "); " + oldVar + " = null; }");
        out.line("else throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                " + \" in List of size \" + " + parentVar + ".size());");
        return outputType;
    }

    private void _emitPutIndexNoOld(SourceWriter out, TypeMirror parent, int index, String parentVar,
                                    String valueExpr, String indexVar) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("org.sjf4j.node.Nodes.putInArray(" + parentVar + ", " + index + ", " + valueExpr + ");");
            return;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            out.line("if (" + indexVar + " == " + parentVar + ".size()) " + parentVar + ".add(" + valueExpr + ");");
            out.line("else " + parentVar + ".set(" + indexVar + ", " + valueExpr + ");");
            return;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            out.line("if (" + indexVar + " == " + parentVar + ".length) throw new org.sjf4j.exception.JsonException(\"cannot append to a Java array\");");
            out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + parentVar + ".length) " +
                    "throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                    " + \" in Java array of size \" + " + parentVar + ".length);");
            out.line(parentVar + "[" + indexVar + "] = " + valueExpr + ";");
            return;
        }
        out.line("if (" + indexVar + " == " + parentVar + ".size()) " + parentVar + ".add(" + valueExpr + ");");
        out.line("else if (" + indexVar + " >= 0 && " + indexVar + " < " + parentVar + ".size()) " +
                parentVar + ".set(" + indexVar + ", " + valueExpr + ");");
        out.line("else throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                " + \" in List of size \" + " + parentVar + ".size());");
    }

    /**
     * Emits append writes. Append has no previous value, so callers must handle a
     * {@code null} old type for return validation and emission.
     */
    private TypeMirror _emitPutAppend(SourceWriter out, TypeMirror parent, String parentVar, String valueExpr) {
        if (GeneratorUtil.isObject(ctx, parent)) {
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
    private TypeMirror _emitPutParam(SourceWriter out, TypeMirror parent, VariableElement param, String paramName, String parentVar,
                                     String valueExpr, String oldVar, String indexVar, String missing) {
        if (_isString(param.asType())) {
            return _emitPutParamName(out, parent, paramName, parentVar, valueExpr, oldVar);
        }
        return _emitPutParamIndex(out, parent, paramName, parentVar, valueExpr, oldVar, indexVar, missing);
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

    private void _emitPutParamNoOld(SourceWriter out, TypeMirror parent, VariableElement param, String paramName, String parentVar,
                                    String valueExpr, String missing, String indexVar) {
        if (_isString(param.asType())) {
            _emitPutParamNameNoOld(out, parent, paramName, parentVar, valueExpr);
        } else {
            _emitPutParamIndexNoOld(out, parent, paramName, parentVar, valueExpr, missing, indexVar);
        }
    }

    private void _emitPutParamNameNoOld(SourceWriter out, TypeMirror parent, String paramName, String parentVar,
                                        String valueExpr) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("org.sjf4j.node.Nodes.putInObject(" + parentVar + ", " + paramName + ", " + valueExpr + ");");
            return;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType) ||
                GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) {
            out.line(parentVar + ".put(" + paramName + ", " + valueExpr + ");");
            return;
        }
        throw new AssertionError("CompiledPath PUT cannot resolve dynamic key on " + parent);
    }

    private TypeMirror _emitPutParamIndex(SourceWriter out, TypeMirror parent, String paramName, String parentVar,
                                          String valueExpr, String oldVar, String posVar, String missing) {
        String indexVar = paramName;
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("Object " + oldVar + " = org.sjf4j.node.Nodes.putInArray(" + parentVar + ", " + indexVar + ", " + valueExpr + ");");
            return ctx.objectType;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            out.line("int " + posVar + " = " + indexVar + " >= 0 ? " + indexVar + " : " + parentVar + ".size() + " + indexVar + ";");
            out.line("Object " + oldVar + ";");
            out.line("if (" + posVar + " >= 0 && " + posVar + " < " + parentVar + ".size()) " +
                    oldVar + " = " + parentVar + ".set(" + posVar + ", " + valueExpr + ");");
            out.line("else if (" + posVar + " == " + parentVar + ".size()) { " + parentVar + ".add(" +
                    valueExpr + "); " + oldVar + " = null; }");
            out.line("else throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + posVar +
                    " + \" in JsonArray of size \" + " + parentVar + ".size());");
            return ctx.objectType;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            TypeMirror outputType = ((ArrayType) parent).getComponentType();
            out.line("int " + posVar + " = " + indexVar + " >= 0 ? " + indexVar + " : " + parentVar + ".length + " + indexVar + ";");
            out.line("if (" + posVar + " == " + parentVar + ".length) throw new org.sjf4j.exception.JsonException(\"cannot append to a Java array\");");
            out.line("if (" + posVar + " < 0 || " + posVar + " >= " + parentVar + ".length) " +
                    "throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + posVar +
                    " + \" in Java array of size \" + " + parentVar + ".length);");
            out.line(_readValueTypeName(outputType) + " " + oldVar + " = " + parentVar + "[" + posVar + "];");
            out.line(parentVar + "[" + posVar + "] = " + valueExpr + ";");
            return outputType;
        }
        TypeMirror outputType = GeneratorUtil.listValueType(ctx, parent);
        out.line("int " + posVar + " = " + indexVar + " >= 0 ? " + indexVar + " : " + parentVar + ".size() + " + indexVar + ";");
        out.line(GeneratorUtil.localTypeName(ctx, outputType) + " " + oldVar + ";");
        out.line("if (" + posVar + " >= 0 && " + posVar + " < " + parentVar + ".size()) " +
                oldVar + " = " + parentVar + ".set(" + posVar + ", " + valueExpr + ");");
        out.line("else if (" + posVar + " == " + parentVar + ".size()) { " + parentVar + ".add(" +
                valueExpr + "); " + oldVar + " = null; }");
        out.line("else throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + posVar +
                " + \" in List of size \" + " + parentVar + ".size());");
        return outputType;
    }

    private void _emitPutParamIndexNoOld(SourceWriter out, TypeMirror parent, String paramName, String parentVar,
                                         String valueExpr, String missing, String indexVar) {
        if (GeneratorUtil.isObject(ctx, parent)) {
            out.line("org.sjf4j.node.Nodes.putInArray(" + parentVar + ", " + paramName + ", " + valueExpr + ");");
            return;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) {
            out.line("if (" + indexVar + " == " + parentVar + ".size()) " + parentVar + ".add(" + valueExpr + ");");
            out.line("else " + parentVar + ".set(" + indexVar + ", " + valueExpr + ");");
            return;
        }
        if (parent.getKind() == TypeKind.ARRAY) {
            out.line("if (" + indexVar + " == " + parentVar + ".length) throw new org.sjf4j.exception.JsonException(\"cannot append to a Java array\");");
            out.line("if (" + indexVar + " < 0 || " + indexVar + " >= " + parentVar + ".length) " +
                    "throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                    " + \" in Java array of size \" + " + parentVar + ".length);");
            out.line(parentVar + "[" + indexVar + "] = " + valueExpr + ";");
            return;
        }
        out.line("if (" + indexVar + " == " + parentVar + ".size()) " + parentVar + ".add(" + valueExpr + ");");
        out.line("else if (" + indexVar + " >= 0 && " + indexVar + " < " + parentVar + ".size()) " +
                parentVar + ".set(" + indexVar + ", " + valueExpr + ");");
        out.line("else throw new org.sjf4j.exception.JsonException(\"cannot set at index \" + " + indexVar +
                " + \" in List of size \" + " + parentVar + ".size());");
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
        return ctx.types.isAssignable(GeneratorUtil.boxed(ctx, from), GeneratorUtil.boxed(ctx, to));
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
