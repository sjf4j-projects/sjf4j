package org.sjf4j.processor.mapper;

import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.processor.GeneratedClass;
import org.sjf4j.processor.GeneratorUtil;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;
import org.sjf4j.processor.path.PathAccessEmitter;

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
            _error(method, target, "@CompiledMapper methods must return a target type");
            return;
        }
        if (method.getParameters().size() != 1) {
            _error(method, target, "@CompiledMapper V1 methods must have exactly one source parameter");
            return;
        }

        VariableElement source = method.getParameters().get(0);
        TypeElement sourceType = GeneratorUtil.asTypeElement(source.asType());
        TypeElement targetType = GeneratorUtil.asTypeElement(method.getReturnType());
        if (sourceType == null || targetType == null) {
            _error(method, target, "@CompiledMapper V1 supports declared source and target types only");
            return;
        }

        Map<String, Read> reads = _reads(sourceType, source.asType());
        Map<String, Write> writes = _writes(targetType, method.getReturnType());
        Plan plan = _creation(method, target, targetType, method.getReturnType(), writes);
        if (plan == null) return;

        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        Map<String, Expr> explicit = new HashMap<String, Expr>();
        Set<String> ignored = new HashSet<String>();

        for (Mapping m : anns) {
            if (!_validateMapping(method, target, m)) return;

            String t = m.target();
            if (_isAutoMarker(m)) continue;
            if (m.ignore()) {
                ignored.add(t);
                continue;
            }
            if (t.length() == 0) {
                _error(method, target, "@Mapping target is required");
                return;
            }

            Expr e;
            if (m.compute().length() != 0) {
                e = _compute(iface, method, target, source, reads, m);
            } else {
                e = _readExpr(method, target, source, reads, m.source().length() == 0 ? t : m.source(), t);
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
            if (e == null && reads.containsKey(name)) e = _readExpr(method, target, source, reads, name, name);
            if (e == null) {
                _error(method, target, "Cannot assign target property '" + name + "'");
                return;
            }

            TypeMirror need = plan.writes.get(name).type;
            if (e.path && need.getKind().isPrimitive()) {
                _error(method, target, "@CompiledMapper V1 path source cannot target primitive property '" + name + "'");
                return;
            }
            if (!_assignable(e.type, need)) {
                _error(method, target, "@Mapping type mismatch for target '" + name + "': " + e.type + " -> " + need);
                return;
            }
            values.put(name, e);
        }
        if (plan.ctor != null && values.size() != plan.names.size()) {
            _error(method, target, "Constructor target properties cannot be ignored in V1");
            return;
        }

        target.addMethod(out -> _emit(out, method, source, plan, values));
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
            _error(method, target, "@Mapping sources is supported only with compute in V1");
            return false;
        }
        if (m.ignore() && (m.source().length() != 0 || m.compute().length() != 0 || m.sources().length != 0)) {
            _error(method, target, "@Mapping ignore cannot be combined with source/sources/compute");
            return false;
        }
        if (m.ignore() && m.target().length() == 0) {
            _error(method, target, "@Mapping ignore requires target");
            return false;
        }
        return true;
    }

    private void _emit(SourceWriter out, ExecutableElement method, VariableElement source, Plan plan, Map<String, Expr> values) {
        out.line("");
        out.line("@Override");
        out.line("public " + method.getReturnType() + " " + method.getSimpleName() +
                "(" + source.asType() + " " + source.getSimpleName() + ") {");
        out.indent();
        out.line("if (" + source.getSimpleName() + " == null) return null;");

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
                _error(method, target, "Record target has no public canonical constructor");
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
            _error(method, target, "Target type must have a no-args constructor, record constructor, or unique public constructor");
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

    private Expr _readExpr(ExecutableElement method, GeneratedClass target, VariableElement source, Map<String, Read> reads,
                           String path, String targetName) {
        PathAccessEmitter.ReadAccess r = pathAccess.read(method, target, source.asType(), source.getSimpleName().toString(),
                path, _tempName(targetName, "path", 0) + "_");
        if (r == null) return null;
        Expr e = new Expr(r.code, r.type, r.path);
        e.temps.addAll(r.temps);
        return e;
    }

    /**
     * Resolves V1 compute forms: an inline lambda-like expression or a local
     * {@code this::helper} method. Inline bodies are emitted as Java expressions,
     * not as runtime lambda objects.
     */
    private Expr _compute(TypeElement iface, ExecutableElement method, GeneratedClass target, VariableElement source, Map<String, Read> reads, Mapping m) {
        String c = m.compute().trim();
        if (c.indexOf('{') >= 0 || c.indexOf('}') >= 0 || c.indexOf(';') >= 0 || c.contains("return")) {
            _error(method, target, "@Mapping compute supports expression bodies only");
            return null;
        }
        if (c.startsWith("this::")) {
            return _helper(iface, method, target, source, reads, m, c.substring(6));
        }

        int arrow = c.indexOf("->");
        if (arrow < 0) {
            _error(method, target, "@Mapping compute must be a lambda expression or this::helper");
            return null;
        }

        String left = c.substring(0, arrow).trim();
        if (left.startsWith("(") && left.endsWith(")")) left = left.substring(1, left.length() - 1).trim();
        String[] params = left.length() == 0 ? new String[0] : left.split("\\s*,\\s*");
        String[] paths = m.sources().length == 0 ? params : m.sources();
        if (params.length != paths.length) {
            _error(method, target, "@Mapping compute sources must match lambda parameters");
            return null;
        }

        String body = c.substring(arrow + 2).trim();
        Expr e = new Expr(body, null);
        for (int i = 0; i < params.length; i++) {
            Expr v = _readExpr(method, target, source, reads, paths[i], m.target() + i);
            if (v == null) return null;

            String temp = _tempName(m.target(), params[i], i);
            e.code = e.code.replaceAll("\\b" + Pattern.quote(params[i]) + "\\b", temp);
            e.temps.addAll(v.temps);
            e.temps.add(GeneratorUtil.localTypeName(ctx, v.type) + " " + temp + " = " + v.code + ";");
        }
        return e;
    }

    private Expr _helper(TypeElement iface, ExecutableElement method, GeneratedClass target, VariableElement source, Map<String, Read> reads, Mapping m, String name) {
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
                _error(method, target, "@Mapping compute sources must match helper parameters");
                return null;
            }

            StringBuilder call = new StringBuilder(h.getModifiers().contains(Modifier.STATIC)
                    ? iface.getQualifiedName() + "." + name + "("
                    : name + "(");
            for (int i = 0; i < paths.length; i++) {
                if (i != 0) call.append(", ");

                Expr v = _readExpr(method, target, source, reads, paths[i], m.target() + i);
                if (v == null) return null;
                if (!_assignable(v.type, ht.getParameterTypes().get(i))) {
                    _error(method, target, "@Mapping helper parameter type mismatch");
                    return null;
                }
                call.append(v.code);
            }
            return new Expr(call.append(")").toString(), ht.getReturnType());
        }
        _error(method, target, "Cannot resolve @Mapping compute helper '" + name + "'");
        return null;
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
        final List<String> temps = new ArrayList<String>();

        Expr(String c, TypeMirror t) {
            this(c, t, false);
        }

        Expr(String c, TypeMirror t, boolean p) {
            code = c;
            type = t;
            path = p;
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
