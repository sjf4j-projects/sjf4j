package org.sjf4j.processor.mapper;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Emits direct, null-safe read access for mapper source paths.
 *
 * <p>The mapper generator uses this helper for both simple property reads and
 * JSONPath/JSON Pointer style source paths.  It resolves each segment against
 * the compile-time source type and returns source snippets plus any temporary
 * declarations needed to preserve null-safe parent traversal.  Callers can share
 * a cache so multiple mappings that read the same path prefix reuse the same
 * generated local variable.</p>
 */
public final class PathAccessEmitter {
    private final ProcessorContext ctx;

    public PathAccessEmitter(ProcessorContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Reads a simple source property or absolute path from a non-null root.
     */
    public ReadAccess read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                            String source, String tempPrefix) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, false, null, "");
    }

    /**
     * Reads from a non-null root while reusing cached path-prefix temporaries.
     */
    public ReadAccess read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                           String source, String tempPrefix, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, false, cache, cacheRoot);
    }

    /**
     * Reads from a non-null root using a {@link NameAllocator} for readable and
     * collision-free path temporary names.
     */
    public ReadAccess read(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                           String source, NameAllocator names, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, null, names, false, cache, cacheRoot);
    }

    /**
     * Reads a simple source property or absolute path when the root itself may
     * be null; generated code returns {@code null} instead of dereferencing it.
     */
    public ReadAccess readNullableRoot(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                                       String source, String tempPrefix) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, true, null, "");
    }

    /**
     * Reads from a nullable root while reusing cached path-prefix temporaries.
     */
    public ReadAccess readNullableRoot(Element context, GeneratedClass target, TypeMirror rootType, String rootVar,
                                       String source, String tempPrefix, Map<String, CachedPath> cache, String cacheRoot) {
        return _read(context, target, rootType, rootVar, source, tempPrefix, null, true, cache, cacheRoot);
    }

    /**
     * Reads from a nullable root using allocator-provided path temporary names.
     */
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
        temps.add(indent + "int " + idx + " = " + GeneratorUtil.indexExpr(index, size) + ";");
        temps.add(indent + "if (" + idx + " >= 0 && " + idx + " < " + size + ") " + nextVar + " = " + a.code.replace("#IDX#", idx) + ";");
        if (guard != null) {
            temps.add("}");
        }
    }

    private Access _nameAccess(Element context, GeneratedClass target, TypeMirror current, String currentVar, String name) {
        if (GeneratorUtil.isObject(ctx, current)) {
            return new Access("org.sjf4j.node.Nodes.getInObject(" + currentVar + ", \"" + GeneratorUtil.escape(name) + "\")", ctx.objectType);
        }
        if (GeneratorUtil.isJojoType(ctx, current)) {
            TypeElement type = GeneratorUtil.asTypeElement(current);
            ExecutableElement getter = GeneratorUtil.findJojoReadable(ctx, type, current, name);
            if (getter != null) {
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
                return new Access(currentVar + "." + getter.getSimpleName() + "()", mt.getReturnType());
            }
            VariableElement field = GeneratorUtil.findJojoReadableField(ctx, type, name);
            if (field != null) return new Access(currentVar + "." + field.getSimpleName(), ctx.types.asMemberOf((DeclaredType) current, field));
            if (GeneratorUtil.findJojoWritable(ctx, type, current, name) != null || GeneratorUtil.findJojoWritableField(ctx, type, name) != null) {
                _error(context, target, "Cannot read source property '" + name + "' on " + current);
                return null;
            }
            return new Access(currentVar + ".getNode(\"" + GeneratorUtil.escape(name) + "\")", ctx.objectType);
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
        ExecutableElement getter = GeneratorUtil.findReadable(ctx, type, current, name);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            return new Access(currentVar + "." + getter.getSimpleName() + "()", mt.getReturnType());
        }
        VariableElement field = GeneratorUtil.findReadableField(ctx, type, name);
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

    /**
     * Result of resolving a mapper source read.
     *
     * <p>{@link #temps} must be emitted before {@link #code} is used.  For a
     * path read, {@link #leafExpr} may contain an inline expression for the final
     * segment so callers can avoid an otherwise unnecessary leaf temporary.</p>
     */
    public static final class ReadAccess {
        /** Java expression or local variable that evaluates to the read value. */
        public final String code;
        /** Compile-time type of {@link #code}. */
        public final TypeMirror type;
        /** True when the source string was parsed as a path rather than a property name. */
        public final boolean path;
        /** Temporary declarations/statements required before reading {@link #code}. */
        public final List<String> temps;
        /** Optional null-safe inline expression for the final path segment. */
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

    /**
     * Cached generated local for an already-read path prefix.
     */
    public static final class CachedPath {
        /** Local variable or expression containing the prefix value. */
        final String code;
        /** Compile-time type of {@link #code}. */
        final TypeMirror type;

        CachedPath(String c, TypeMirror t) {
            code = c;
            type = t;
        }
    }
}
