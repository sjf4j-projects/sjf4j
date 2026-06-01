package org.sjf4j.processor.mapper;

import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Generates small direct implementations for {@code @CompiledMapper} interfaces. */
public final class MapperGenerator {
    private final ProcessorContext ctx;
    private final PathAccessEmitter pathAccess;

    public MapperGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
        this.pathAccess = new PathAccessEmitter(ctx);
    }

    /**
     * Generates an implementation for one {@code @CompiledMapper} interface.
     */
    public void generate(TypeElement iface) {
        GeneratedClass target = new GeneratedClass(ctx, iface, GeneratorUtil.COMPILED_IMPL_POSTFIX);
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            Set<Modifier> mods = method.getModifiers();
            if (!mods.contains(Modifier.ABSTRACT)) continue;

            _genMap(iface, method, target);
        }
        target.emit();
    }

    /**
     * Validates and emits one mapping method.
     */
    private void _genMap(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            _error(method, target, "@CompiledMapper method must return a target type");
            return;
        }
        if (method.getParameters().isEmpty()) {
            _error(method, target, "@CompiledMapper method must have at least one source parameter");
            return;
        }

        TypeElement targetType = GeneratorUtil.asTypeElement(method.getReturnType());
        if (targetType == null) {
            _error(method, target, "@CompiledMapper supports only declared source and target types");
            return;
        }

        List<SourceParam> sources = new ArrayList<SourceParam>();
        for (VariableElement source : method.getParameters()) {
            TypeElement sourceType = GeneratorUtil.asTypeElement(source.asType());
            if (sourceType == null) {
                _error(method, target, "@CompiledMapper supports only declared source and target types");
                return;
            }
            sources.add(new SourceParam(source, _reads(sourceType, source.asType())));
        }
        boolean multi = sources.size() > 1;

        Map<String, Write> writes = _writes(targetType, method.getReturnType());
        Plan plan = _creation(method, target, targetType, method.getReturnType(), writes);
        if (plan == null) return;
        if (multi && plan.ctor != null) {
            _error(method, target, "Multi-source @CompiledMapper methods do not support constructor or record targets");
            return;
        }

        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        Map<String, Expr> explicit = new HashMap<String, Expr>();
        Set<String> explicitTargets = new HashSet<String>();
        Set<String> ignored = new HashSet<String>();

        for (Mapping m : anns) {
            if (!_validateMapping(method, target, m)) return;

            String t = m.target();
            if (_isAutoMarker(m)) continue;
            if (m.ignore()) {
                ignored.add(t);
                continue;
            }
            explicitTargets.add(t);
        }

        MethodState state = new MethodState(_readCounts(iface, anns, plan, ignored, explicitTargets, sources, multi));

        for (Mapping m : anns) {
            String t = m.target();
            if (_isAutoMarker(m) || m.ignore()) continue;
            if (t.length() == 0) {
                _error(method, target, "@Mapping requires a non-empty target");
                return;
            }

            Expr e;
            if (m.compute().length() != 0) {
                e = _compute(iface, method, target, sources, multi, state, m);
            } else {
                e = _readExpr(method, target, sources, multi, state, m.source().length() == 0 ? t : m.source(), t, null);
            }
            if (e == null) return;
            explicit.put(t, e);
        }

        // Every writable/constructor target property must be assigned, either
        // explicitly or by same-name auto mapping.  Abstract methods with no
        // @Mapping annotations therefore still generate useful mappers.
        Map<String, Expr> values = new LinkedHashMap<String, Expr>();
        for (String name : plan.names) {
            if (ignored.contains(name)) continue;

            Expr e = explicit.get(name);
            if (e == null && _hasAutoSource(sources, multi, name)) {
                e = _readExpr(method, target, sources, multi, state, name, name, null);
            }
            if (e == null) {
                _error(method, target, "Cannot map target property '" + name + "': no source property, @Mapping, or compute expression was found");
                return;
            }

            TypeMirror need = plan.writes.get(name).type;
            if (!_assignable(e.type, need)) {
                _error(method, target, "Cannot assign source expression to target property '" + name + "': " + e.type + " is not assignable to " + need);
                return;
            }
            values.put(name, e);
        }
        if (plan.ctor != null && values.size() != plan.names.size()) {
            _error(method, target, "Constructor and record target properties cannot be ignored");
            return;
        }

        target.addMethod(out -> _emit(out, method, sources, multi, state, plan, values));
    }

    private boolean _isAutoMarker(Mapping m) {
        return m.target().length() == 0
                && m.source().length() == 0
                && m.sources().length == 0
                && m.compute().length() == 0
                && !m.ignore();
    }

    private boolean _validateMapping(ExecutableElement method, GeneratedClass target, Mapping m) {
        if (m.sources().length > 0 && m.compute().length() == 0) {
            _error(method, target, "@Mapping.sources may be used only with @Mapping.compute");
            return false;
        }
        if (m.ignore() && (m.source().length() != 0 || m.compute().length() != 0 || m.sources().length != 0)) {
            _error(method, target, "@Mapping.ignore cannot be combined with source, sources, or compute");
            return false;
        }
        if (m.ignore() && m.target().length() == 0) {
            _error(method, target, "@Mapping.ignore requires a non-empty target");
            return false;
        }
        return true;
    }

    private Map<String, Integer> _readCounts(TypeElement iface, Mapping[] anns, Plan plan, Set<String> ignored,
                                             Set<String> explicitTargets, List<SourceParam> sources, boolean multi) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Mapping m : anns) {
            if (_isAutoMarker(m) || m.ignore() || m.target().length() == 0) continue;

            if (m.compute().length() != 0) {
                String[] paths = _computeSourcePaths(iface, m);
                for (int i = 0; i < paths.length; i++) _count(counts, paths[i]);
            } else {
                _count(counts, m.source().length() == 0 ? m.target() : m.source());
            }
        }
        for (String name : plan.names) {
            if (!ignored.contains(name) && !explicitTargets.contains(name) && _hasAutoSource(sources, multi, name)) {
                _count(counts, name);
            }
        }
        return counts;
    }

    private boolean _hasAutoSource(List<SourceParam> sources, boolean multi, String name) {
        if (!multi) return sources.get(0).reads.containsKey(name);
        // Multi-source unqualified names are allowed only when at least one
        // source exposes the property; _resolveSource performs the uniqueness
        // check and reports ambiguity if more than one source matches.
        int count = 0;
        for (SourceParam s : sources) if (s.reads.containsKey(name)) count++;
        return count > 0;
    }

    private void _count(Map<String, Integer> counts, String source) {
        Integer n = counts.get(source);
        counts.put(source, n == null ? 1 : n + 1);
    }

    private void _emit(SourceWriter out, ExecutableElement method, List<SourceParam> sources, boolean multi, MethodState state,
                       Plan plan, Map<String, Expr> values) {
        out.line("");
        out.line("@Override");
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i != 0) sig.append(", ");
            sig.append(sources.get(i).element.asType()).append(" ").append(sources.get(i).name);
        }
        out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + sig + ") {");
        out.indent();
        if (multi) {
            StringBuilder guard = new StringBuilder("if (");
            for (int i = 0; i < sources.size(); i++) {
                if (i != 0) guard.append(" && ");
                guard.append(sources.get(i).name).append(" == null");
            }
            out.line(guard.append(") return null;").toString());
        } else {
            out.line("if (" + sources.get(0).name + " == null) return null;");
        }

        for (String temp : state.readTemps) {
            out.line(temp);
        }
        for (Expr e : values.values()) {
            _emitTemps(out, e);
        }

        if (plan.ctor != null) {
            StringBuilder b = new StringBuilder("return new ").append(method.getReturnType()).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            out.line(method.getReturnType() + " _target = new " + method.getReturnType() + "();");
            for (String name : plan.names) {
                Expr e = values.get(name);
                if (e == null) continue;

                Write w = plan.writes.get(name);
                if (w.setter != null) {
                    out.line("_target." + w.setter.getSimpleName() + "(" + e.code + ");");
                } else {
                    out.line("_target." + name + " = " + e.code + ";");
                }
            }
            out.line("return _target;");
        }
        out.dedent();
        out.line("}");
    }

    private void _emitTemps(SourceWriter out, Expr e) {
        for (String t : e.temps) {
            out.line(t);
        }
    }

    /**
     * Collects simple source read properties. V1 deliberately stays on public
     * getters, public fields, and record accessors.
     */
    private Map<String, Read> _reads(TypeElement type, TypeMirror owner) {
        Map<String, Read> r = new HashMap<String, Read>();
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC)) continue;

            if (member.getKind() == ElementKind.FIELD) {
                TypeMirror fieldType = ctx.types.asMemberOf((DeclaredType) owner, member);
                r.put(member.getSimpleName().toString(), new Read(null, fieldType));
                continue;
            }

            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement e = (ExecutableElement) member;
                if (!e.getParameters().isEmpty()) continue;

                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, e);
                if (mt.getReturnType().getKind() == TypeKind.VOID) continue;

                String n = e.getSimpleName().toString();
                if (n.equals("getClass")) continue;
                if (n.startsWith("get") && n.length() > 3) {
                    r.put(GeneratorUtil.decap(n.substring(3)), new Read(e, mt.getReturnType()));
                } else if (n.startsWith("is") && n.length() > 2) {
                    r.put(GeneratorUtil.decap(n.substring(2)), new Read(e, mt.getReturnType()));
                } else if (_isRecord(type)) {
                    r.put(n, new Read(e, mt.getReturnType()));
                }
            }
        }
        return r;
    }

    /**
     * Collects target write properties. Setters intentionally override public
     * fields so user validation or normalization in setters is preserved.
     */
    private Map<String, Write> _writes(TypeElement type, TypeMirror owner) {
        Map<String, Write> w = new LinkedHashMap<String, Write>();
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC)) continue;

            if (member.getKind() == ElementKind.FIELD && !m.contains(Modifier.FINAL)) {
                String n = member.getSimpleName().toString();
                if (!w.containsKey(n)) {
                    w.put(n, new Write(null, ctx.types.asMemberOf((DeclaredType) owner, member)));
                }
                continue;
            }

            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement e = (ExecutableElement) member;
                if (!e.getSimpleName().toString().startsWith("set") || e.getParameters().size() != 1) continue;

                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, e);
                if (mt.getReturnType().getKind() == TypeKind.VOID) {
                    String name = GeneratorUtil.decap(e.getSimpleName().toString().substring(3));
                    w.put(name, new Write(e, mt.getParameterTypes().get(0)));
                }
            }
        }
        return w;
    }

    /**
     * Selects the target construction plan. No-args targets are assigned after
     * construction; records and unique public constructors are assigned through
     * constructor arguments.
     */
    private Plan _creation(ExecutableElement method, GeneratedClass target, TypeElement type, TypeMirror mirror, Map<String, Write> writes) {
        if (_isRecord(type)) {
            List<ExecutableElement> ctors = _publicConstructors(type);
            ExecutableElement ctor = ctors.isEmpty() ? null : ctors.get(0);
            if (ctor == null) {
                _error(method, target, "Record target type has no public canonical constructor");
                return null;
            }
            return _ctorPlan(ctor, mirror);
        }

        List<ExecutableElement> ctors = _publicConstructors(type);
        for (ExecutableElement c : ctors) {
            if (c.getParameters().isEmpty()) {
                return new Plan(null, new ArrayList<String>(writes.keySet()), writes);
            }
        }
        if (ctors.size() != 1) {
            _error(method, target, "Target type must provide a public no-args constructor, be a record, or have exactly one public constructor");
            return null;
        }
        return _ctorPlan(ctors.get(0), mirror);
    }

    private Plan _ctorPlan(ExecutableElement ctor, TypeMirror owner) {
        Map<String, Write> w = new LinkedHashMap<String, Write>();
        ExecutableType ct = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, ctor);
        for (int i = 0; i < ctor.getParameters().size(); i++) {
            String n = ctor.getParameters().get(i).getSimpleName().toString();
            w.put(n, new Write(null, ct.getParameterTypes().get(i)));
        }
        return new Plan(ctor, new ArrayList<String>(w.keySet()), w);
    }

    private List<ExecutableElement> _publicConstructors(TypeElement t) {
        List<ExecutableElement> r = new ArrayList<ExecutableElement>();
        for (Element e : t.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR && e.getModifiers().contains(Modifier.PUBLIC)) {
                r.add((ExecutableElement) e);
            }
        }
        return r;
    }

    private Expr _readExpr(ExecutableElement method, GeneratedClass target, List<SourceParam> sources, boolean multi, MethodState state,
                           String path, String targetName, String preferredTemp) {
        ResolvedSource resolved = _resolveSource(method, target, sources, multi, path);
        if (resolved == null) return null;

        String key = resolved.param.name + ":" + resolved.path + ":" + resolved.nullableRoot;
        CachedRead cached = state.cache.get(key);
        if (cached != null) return new Expr(cached.code, cached.type, cached.path, cached.nullableRoot, true);

        PathAccessEmitter.ReadAccess r = resolved.nullableRoot
                ? pathAccess.readNullableRoot(method, target, resolved.param.element.asType(), resolved.param.name,
                resolved.path, _tempName(targetName, "path", 0) + "_", state.pathCache, _pathCacheRoot(resolved))
                : pathAccess.read(method, target, resolved.param.element.asType(), resolved.param.name,
                resolved.path, _tempName(targetName, "path", 0) + "_", state.pathCache, _pathCacheRoot(resolved));
        if (r == null) return null;
        Expr e = new Expr(r.code, r.type, r.path, resolved.nullableRoot, false);
        if (r.path) {
            state.readTemps.addAll(r.temps);
            e.local = true;
            state.cache.put(key, new CachedRead(r.code, r.type, true, resolved.nullableRoot));
        } else if (_readCount(state, path) > 1) {
            String temp = preferredTemp == null ? _tempName(targetName, "read", 0) : preferredTemp;
            state.readTemps.add(_localTypeName(r.type, resolved.nullableRoot) + " " + temp + " = " + r.code + ";");
            e.code = temp;
            e.local = true;
            state.cache.put(key, new CachedRead(temp, r.type, false, resolved.nullableRoot));
        } else {
            e.temps.addAll(r.temps);
        }
        return e;
    }

    private ResolvedSource _resolveSource(ExecutableElement method, GeneratedClass target, List<SourceParam> sources,
                                          boolean multi, String source) {
        if (!multi) return new ResolvedSource(sources.get(0), source, false);

        int colon = source.indexOf(':');
        if (colon > 0) {
            String left = source.substring(0, colon);
            String right = source.substring(colon + 1);
            SourceParam p = _sourceByName(sources, left);
            if (p != null && right.length() != 0) {
                return new ResolvedSource(p, right, true);
            }
            if (p != null) {
                _error(method, target, "Invalid multi-source mapping '" + source + "': expected a property, JSONPath, or JSON Pointer after ':'");
                return null;
            }
        }

        SourceParam found = null;
        // Unqualified multi-source names bind by unique readable property name
        // across all source parameters.  They never default to the first source.
        for (SourceParam p : sources) {
            if (p.reads.containsKey(source)) {
                if (found != null) {
                    _error(method, target, "Ambiguous source property '" + source + "'; qualify it with a source parameter name");
                    return null;
                }
                found = p;
            }
        }
        if (found == null) {
            _error(method, target, "Cannot resolve source '" + source + "' on any source parameter");
            return null;
        }
        return new ResolvedSource(found, source, true);
    }

    private SourceParam _sourceByName(List<SourceParam> sources, String name) {
        for (SourceParam p : sources) if (p.name.equals(name)) return p;
        return null;
    }

    private int _readCount(MethodState state, String path) {
        Integer n = state.readCounts.get(path);
        return n == null ? 0 : n;
    }

    private String _pathCacheRoot(ResolvedSource resolved) {
        return resolved.param.name + ':' + resolved.nullableRoot + ':';
    }

    /**
     * Resolves V1 compute forms: an inline lambda-like expression or a local
     * {@code this::helper} method. Inline bodies are emitted as Java expressions,
     * not as runtime lambda objects.
     */
    private Expr _compute(TypeElement iface, ExecutableElement method, GeneratedClass target, List<SourceParam> sources,
                          boolean multi, MethodState state, Mapping m) {
        String c = m.compute().trim();
        if (c.indexOf('{') >= 0 || c.indexOf('}') >= 0 || c.indexOf(';') >= 0 || c.contains("return")) {
            _error(method, target, "@Mapping.compute supports only expression bodies");
            return null;
        }
        if (c.startsWith("this::")) {
            return _helper(iface, method, target, sources, multi, state, m, c.substring(6));
        }

        int arrow = c.indexOf("->");
        if (arrow < 0) {
            _error(method, target, "@Mapping.compute must be a lambda expression or this::helper");
            return null;
        }

        String left = c.substring(0, arrow).trim();
        if (left.startsWith("(") && left.endsWith(")")) left = left.substring(1, left.length() - 1).trim();
        String[] params = left.length() == 0 ? new String[0] : left.split("\\s*,\\s*");
        String[] paths = m.sources().length == 0 ? params : m.sources();
        if (params.length != paths.length) {
            _error(method, target, "@Mapping.sources must match the lambda parameters in @Mapping.compute");
            return null;
        }

        String body = c.substring(arrow + 2).trim();
        Expr e = new Expr(body, null);
        for (int i = 0; i < params.length; i++) {
            String temp = _tempName(m.target(), params[i], i);
            Expr v = _readExpr(method, target, sources, multi, state, paths[i], m.target() + i, temp);
            if (v == null) return null;

            e.temps.addAll(v.temps);
            if (!v.local) {
                e.temps.add(_localTypeName(v.type, v.nullableRoot) + " " + temp + " = " + v.code + ";");
                v.code = temp;
            }
            e.code = e.code.replaceAll("\\b" + Pattern.quote(params[i]) + "\\b", v.code);
        }
        return e;
    }

    private Expr _helper(TypeElement iface, ExecutableElement method, GeneratedClass target, List<SourceParam> sources,
                         boolean multi, MethodState state, Mapping m, String name) {
        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;

            ExecutableElement h = (ExecutableElement) e;
            if (!h.getModifiers().contains(Modifier.DEFAULT) && !h.getModifiers().contains(Modifier.STATIC)) continue;

            ExecutableType ht = (ExecutableType) ctx.types.asMemberOf((DeclaredType) iface.asType(), h);
            String[] paths = m.sources();
            if (paths.length == 0) {
                paths = new String[h.getParameters().size()];
                for (int i = 0; i < paths.length; i++) {
                    paths[i] = h.getParameters().get(i).getSimpleName().toString();
                }
            }
            if (paths.length != h.getParameters().size()) {
                _error(method, target, "@Mapping.sources must match the helper method parameters in @Mapping.compute");
                return null;
            }

            Expr result = new Expr(null, ht.getReturnType());
            StringBuilder call = new StringBuilder(h.getModifiers().contains(Modifier.STATIC)
                    ? iface.getQualifiedName() + "." + name + "("
                    : name + "(");
            for (int i = 0; i < paths.length; i++) {
                if (i != 0) call.append(", ");

                String preferred = _tempName(m.target(), h.getParameters().get(i).getSimpleName().toString(), i);
                Expr v = _readExpr(method, target, sources, multi, state, paths[i], m.target() + i, preferred);
                if (v == null) return null;
                if (!_assignable(v.type, ht.getParameterTypes().get(i))) {
                    _error(method, target, "@Mapping.compute helper parameter type mismatch");
                    return null;
                }
                result.temps.addAll(v.temps);
                call.append(v.code);
            }
            result.code = call.append(")").toString();
            return result;
        }
        _error(method, target, "Cannot resolve @Mapping.compute helper '" + name + "'");
        return null;
    }

    private String[] _computeSourcePaths(TypeElement iface, Mapping m) {
        String c = m.compute().trim();
        if (c.startsWith("this::")) {
            String name = c.substring(6);
            for (Element e : iface.getEnclosedElements()) {
                if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;
                ExecutableElement h = (ExecutableElement) e;
                if (!h.getModifiers().contains(Modifier.DEFAULT) && !h.getModifiers().contains(Modifier.STATIC)) continue;
                if (m.sources().length != 0) return m.sources();

                String[] paths = new String[h.getParameters().size()];
                for (int i = 0; i < paths.length; i++) {
                    paths[i] = h.getParameters().get(i).getSimpleName().toString();
                }
                return paths;
            }
            return new String[0];
        }

        int arrow = c.indexOf("->");
        if (arrow < 0) return new String[0];

        String left = c.substring(0, arrow).trim();
        if (left.startsWith("(") && left.endsWith(")")) left = left.substring(1, left.length() - 1).trim();
        String[] params = left.length() == 0 ? new String[0] : left.split("\\s*,\\s*");
        return m.sources().length == 0 ? params : m.sources();
    }

    private String _localTypeName(TypeMirror type, boolean boxPrimitive) {
        if (boxPrimitive) return GeneratorUtil.localTypeName(ctx, type);
        if (type.getKind().isPrimitive()) return type.toString();
        return GeneratorUtil.typeName(GeneratorUtil.concrete(ctx, type));
    }

    private boolean _assignable(TypeMirror from, TypeMirror to) {
        if (from == null) return true;
        return GeneratorUtil.isAssignableBoxed(ctx, from, to);
    }

    private boolean _isRecord(TypeElement t) {
        return "RECORD".equals(t.getKind().name());
    }

    private String _tempName(String target, String param, int index) {
        String t = _javaId(target);
        String p = _javaId(param);
        return "_" + (t.length() == 0 ? "compute" : t) + "_" + (p.length() == 0 ? String.valueOf(index) : p);
    }

    private String _javaId(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c))) {
                b.append(c);
            }
        }
        return b.toString();
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }

    private static final class Read {
        final ExecutableElement method;
        final TypeMirror type;

        Read(ExecutableElement m, TypeMirror t) {
            method = m;
            type = t;
        }
    }

    private static final class SourceParam {
        final VariableElement element;
        final String name;
        final Map<String, Read> reads;

        SourceParam(VariableElement e, Map<String, Read> r) {
            element = e;
            name = e.getSimpleName().toString();
            reads = r;
        }
    }

    private static final class ResolvedSource {
        final SourceParam param;
        final String path;
        final boolean nullableRoot;

        ResolvedSource(SourceParam p, String s, boolean n) {
            param = p;
            path = s;
            nullableRoot = n;
        }
    }

    private static final class Write {
        final ExecutableElement setter;
        final TypeMirror type;

        Write(ExecutableElement s, TypeMirror t) {
            setter = s;
            type = t;
        }
    }

    private static final class Expr {
        String code;
        final TypeMirror type;
        final boolean path;
        final boolean nullableRoot;
        boolean local;
        final List<String> temps = new ArrayList<String>();

        Expr(String c, TypeMirror t) {
            this(c, t, false);
        }

        Expr(String c, TypeMirror t, boolean p) {
            this(c, t, p, false, false);
        }

        Expr(String c, TypeMirror t, boolean p, boolean n, boolean l) {
            code = c;
            type = t;
            path = p;
            nullableRoot = n;
            local = l;
        }
    }

    private static final class CachedRead {
        final String code;
        final TypeMirror type;
        final boolean path;
        final boolean nullableRoot;

        CachedRead(String c, TypeMirror t, boolean p, boolean n) {
            code = c;
            type = t;
            path = p;
            nullableRoot = n;
        }
    }

    private static final class MethodState {
        final Map<String, Integer> readCounts;
        final List<String> readTemps = new ArrayList<String>();
        final Map<String, CachedRead> cache = new HashMap<String, CachedRead>();
        final Map<String, PathAccessEmitter.CachedPath> pathCache = new HashMap<String, PathAccessEmitter.CachedPath>();

        MethodState(Map<String, Integer> counts) {
            readCounts = counts;
        }
    }

    private static final class Plan {
        final ExecutableElement ctor;
        final List<String> names;
        final Map<String, Write> writes;

        Plan(ExecutableElement c, List<String> n, Map<String, Write> w) {
            ctor = c;
            names = n;
            writes = w;
        }
    }
}
