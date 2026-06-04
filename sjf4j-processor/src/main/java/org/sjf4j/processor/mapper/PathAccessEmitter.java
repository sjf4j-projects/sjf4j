package org.sjf4j.processor.mapper;

import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.NameAllocator;
import org.sjf4j.processor.ProcessorContext;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Emits direct null-safe read access for mapper source paths. */
public final class PathAccessEmitter {
    private final ProcessorContext ctx;

    public PathAccessEmitter(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    public ReadAccess read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                            String source, String tempPrefix) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, false, null, "");
    }

    public ReadAccess read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                           String source, String tempPrefix, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, false, cache, cacheRoot);
    }

    public ReadAccess read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                           String source, NameAllocator names, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, null, names, false, cache, cacheRoot);
    }

    public ReadAccess readNullableRoot(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                                       String source, String tempPrefix) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, true, null, "");
    }

    public ReadAccess readNullableRoot(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                                       String source, String tempPrefix, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, true, cache, cacheRoot);
    }

    public ReadAccess readNullableRoot(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                                       String source, NameAllocator names, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, null, names, true, cache, cacheRoot);
    }

    private ReadAccess _read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                             String source, String tempPrefix, NameAllocator names, boolean nullableRoot,
                             Map<String, CachedPath> cache, String cacheRoot) {
        if (source.startsWith("$") || source.startsWith("/")) {
            return _pathRead(context, target, rootType, rootVar, source, tempPrefix, names, nullableRoot, cache, cacheRoot);
        }
        return _simpleRead(context, target, rootType, rootVar, source, nullableRoot);
    }

    private ReadAccess _simpleRead(Element context, GeneratedClass target, TypeMirror rootType, String rootVar, String name,
                                   boolean nullableRoot) {
        Access a = _nameAccess(context, target, rootType, rootVar, name);
        if (a == null) return null;
        if (nullableRoot) {
            return new ReadAccess(rootVar + " == null ? null : " + a.code, a.type, false, new ArrayList<String>());
        }
        return new ReadAccess(a.code, a.type, false, new ArrayList<String>());
    }

    private ReadAccess _pathRead(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                                  String source, String tempPrefix, NameAllocator names, boolean nullableRoot,
                                  Map<String, CachedPath> cache, String cacheRoot) {
        JsonPath path;
        try {
            path = JsonPath.parse(source);
        } catch (RuntimeException e) {
            _error(context, target, "Invalid @Mapping.source path '" + source + "': " + e.getMessage());
            return null;
        }
        PathSegment[] segments = path.segments();
        if (segments.length == 1) {
            _error(context, target, "@Mapping.source path must select a child value; root path is not supported");
            return null;
        }
        for (int i = 1; i < segments.length; i++) {
            if (!(segments[i] instanceof PathSegment.Name) && !(segments[i] instanceof PathSegment.Index)) {
                _error(context, target, "@Mapping.source path supports only property names and array/list indexes");
                return null;
            }
        }

        List<String> temps = new ArrayList<String>();
        TypeMirror currentType = rootType;
        String currentVar = rootVar;
        int start = 1;
        if (cache != null) {
            String root = cacheRoot == null ? "" : cacheRoot;
            for (int i = segments.length - 1; i >= 1; i--) {
                CachedPath cached = cache.get(root + _cacheKey(segments, i));
                if (cached != null) {
                    currentType = cached.type;
                    currentVar = cached.code;
                    start = i + 1;
                    break;
                }
            }
            if (start == segments.length) {
                return new ReadAccess(currentVar, currentType, true, temps);
            }
        }

        for (int i = start; i < segments.length; i++) {
            String nextVar = names == null ? tempPrefix + i : _pathTemp(names, segments, i);
            boolean checkParent = i != 1 || nullableRoot;
            PathSegment s = segments[i];
            Access a;
            String leafExpr = null;
            if (s instanceof PathSegment.Name) {
                a = _nameAccess(context, target, currentType, currentVar, ((PathSegment.Name) s).name);
                if (a == null) return null;
                _emitNameTemp(temps, a, currentType, currentVar, nextVar, checkParent);
                if (i == segments.length - 1) {
                    leafExpr = _nameLeafExpr(a, currentType, checkParent, currentVar);
                }
            } else {
                a = _indexAccess(context, target, currentType, currentVar, ((PathSegment.Index) s).index);
                if (a == null) return null;
                _emitIndexTemp(temps, a, currentType, currentVar, nextVar, ((PathSegment.Index) s).index, checkParent);
            }
            currentType = a.type;
            currentVar = nextVar;
            if (cache != null) {
                cache.put((cacheRoot == null ? "" : cacheRoot) + _cacheKey(segments, i), new CachedPath(currentVar, currentType));
            }
            if (leafExpr != null) {
                return new ReadAccess(currentVar, currentType, true, temps, leafExpr);
            }
        }
        return new ReadAccess(currentVar, currentType, true, temps);
    }

    private String _pathTemp(NameAllocator names, PathSegment[] segments, int index) {
        PathSegment s = segments[index];
        if (s instanceof PathSegment.Name) {
            return names.prefixed("s", ((PathSegment.Name) s).name);
        }
        String parent = "value";
        if (index > 1 && segments[index - 1] instanceof PathSegment.Name) {
            parent = ((PathSegment.Name) segments[index - 1]).name;
        }
        int i = ((PathSegment.Index) s).index;
        return names.prefixed("s", parent + (i < 0 ? "N" + (-i) : String.valueOf(i)));
    }

    private String _cacheKey(PathSegment[] segments, int end) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= end; i++) {
            PathSegment s = segments[i];
            if (s instanceof PathSegment.Name) {
                String name = ((PathSegment.Name) s).name;
                b.append('n').append(name.length()).append(':').append(name).append(';');
            } else {
                b.append('i').append(((PathSegment.Index) s).index).append(';');
            }
        }
        return b.toString();
    }

    private void _emitNameTemp(List<String> temps, Access a, TypeMirror parentType, String parentVar, String nextVar, boolean checkParent) {
        String type = GeneratorUtil.localTypeName(ctx, a.type);
        if (checkParent && !parentType.getKind().isPrimitive()) {
            temps.add(type + " " + nextVar + " = " + parentVar + " == null ? null : " + a.code + ";");
        } else {
            temps.add(type + " " + nextVar + " = " + a.code + ";");
        }
    }

    private String _nameLeafExpr(Access a, TypeMirror parentType, boolean checkParent, String parentVar) {
        if (checkParent && !parentType.getKind().isPrimitive()) {
            return parentVar + " == null ? null : " + a.code;
        }
        return a.code;
    }

    private void _emitIndexTemp(List<String> temps, Access a, TypeMirror parentType, String parentVar, String nextVar,
                                int index, boolean checkParent) {
        String type = GeneratorUtil.localTypeName(ctx, a.type);
        temps.add(type + " " + nextVar + " = null;");
        String guard = checkParent && !parentType.getKind().isPrimitive() ? parentVar + " != null" : null;
        if (GeneratorUtil.isObject(ctx, parentType) || GeneratorUtil.isAssignableErasure(ctx, parentType, ctx.jsonArrayType)) {
            if (guard == null) {
                temps.add(nextVar + " = " + a.code + ";");
            } else {
                temps.add("if (" + guard + ") " + nextVar + " = " + a.code + ";");
            }
            return;
        }
        String size = parentType.getKind() == TypeKind.ARRAY ? parentVar + ".length" : parentVar + ".size()";
        String idx = nextVar + "i";
        String indent = guard == null ? "" : "  ";
        if (guard != null) {
            temps.add("if (" + guard + ") {");
        }
        temps.add(indent + "int " + idx + " = " + _indexExpr(index, size) + ";");
        temps.add(indent + "if (" + idx + " >= 0 && " + idx + " < " + size + ") " + nextVar + " = " + a.code.replace("#IDX#", idx) + ";");
        if (guard != null) {
            temps.add("}");
        }
    }

    private Access _nameAccess(Element context, GeneratedClass target, TypeMirror current, String currentVar, String name) {
        if (GeneratorUtil.isObject(ctx, current)) {
            return new Access("org.sjf4j.node.Nodes.getInObject(" + currentVar + ", \"" + GeneratorUtil.escape(name) + "\")", ctx.objectType);
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonObjectType)) {
            return new Access(currentVar + ".getNode(\"" + GeneratorUtil.escape(name) + "\")", ctx.objectType);
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.mapType)) {
            TypeMirror t = GeneratorUtil.mapValueType(ctx, current);
            return new Access(currentVar + ".get(\"" + GeneratorUtil.escape(name) + "\")", t);
        }

        TypeElement type = GeneratorUtil.asTypeElement(current);
        if (type == null) {
            _error(context, target, "Cannot resolve source property '" + name + "' on " + current);
            return null;
        }
        ExecutableElement getter = _findReadable(type, current, name);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            return new Access(currentVar + "." + getter.getSimpleName() + "()", mt.getReturnType());
        }
        VariableElement field = _findReadableField(type, name);
        if (field != null) return new Access(currentVar + "." + field.getSimpleName(), ctx.types.asMemberOf((DeclaredType) current, field));

        _error(context, target, "Cannot read source property '" + name + "' on " + current);
        return null;
    }

    private Access _indexAccess(Element context, GeneratedClass target, TypeMirror current, String currentVar, int index) {
        if (GeneratorUtil.isObject(ctx, current)) {
            return new Access("org.sjf4j.node.Nodes.getInArray(" + currentVar + ", " + index + ")", ctx.objectType);
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            return new Access(currentVar + ".getNode(" + index + ")", ctx.objectType);
        }
        if (current.getKind() == TypeKind.ARRAY) {
            return new Access(currentVar + "[#IDX#]", ((ArrayType) current).getComponentType());
        }
        if (GeneratorUtil.isAssignableErasure(ctx, current, ctx.listType)) {
            TypeMirror t = GeneratorUtil.listValueType(ctx, current);
            return new Access(currentVar + ".get(#IDX#)", t);
        }
        _error(context, target, "Cannot apply index [" + index + "] to " + current);
        return null;
    }

    private ExecutableElement _findReadable(TypeElement type, TypeMirror owner, String name) {
        if (name.length() == 0) return null;
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC) || member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement e = (ExecutableElement) member;
            if (!e.getParameters().isEmpty()) continue;
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, e);
            if (mt.getReturnType().getKind() == TypeKind.VOID) continue;
            String n = e.getSimpleName().toString();
            String base = null;
            if (n.equals("getClass")) continue;
            if (n.startsWith("get") && n.length() > 3) base = GeneratorUtil.decap(n.substring(3));
            else if (n.startsWith("is") && n.length() > 2) base = GeneratorUtil.decap(n.substring(2));
            else if (_isRecord(type)) base = n;
            if (base != null && _nodeName(e, base).equals(name)) return e;
        }
        return null;
    }

    private VariableElement _findReadableField(TypeElement type, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC) || member.getKind() != ElementKind.FIELD) continue;
            if (_nodeName(member, member.getSimpleName().toString()).equals(name)) return (VariableElement) member;
        }
        return null;
    }

    private String _nodeName(Element e, String fallback) {
        return GeneratorUtil.nodePropertyName(e, fallback);
    }

    private boolean _isRecord(TypeElement t) {
        return "RECORD".equals(t.getKind().name());
    }

    private static String _indexExpr(int index, String sizeExpr) {
        if (index >= 0) return Integer.toString(index);
        if (index == Integer.MIN_VALUE) return sizeExpr + " + " + index;
        return sizeExpr + " - " + (-index);
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }

    private static final class Access {
        final String code;
        final TypeMirror type;

        Access(String c, TypeMirror t) {
            code = c;
            type = t;
        }
    }

    public static final class ReadAccess {
        public final String code;
        public final TypeMirror type;
        public final boolean path;
        public final List<String> temps;
        public final String leafExpr;

        ReadAccess(String c, TypeMirror t, boolean p, List<String> s) {
            code = c;
            type = t;
            path = p;
            temps = s;
            leafExpr = null;
        }

        ReadAccess(String c, TypeMirror t, boolean p, List<String> s, String leaf) {
            code = c;
            type = t;
            path = p;
            temps = s;
            leafExpr = leaf;
        }
    }

    public static final class CachedPath {
        final String code;
        final TypeMirror type;

        CachedPath(String c, TypeMirror t) {
            code = c;
            type = t;
        }
    }
}
