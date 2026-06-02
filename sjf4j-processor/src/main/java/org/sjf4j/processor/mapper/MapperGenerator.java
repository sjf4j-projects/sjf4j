package org.sjf4j.processor.mapper;

import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingIfParentPresent;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.annotation.mapper.EnsureMapping;
import org.sjf4j.annotation.mapper.ArrayPolicy;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.annotation.mapper.ObjectPolicy;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates small direct implementations for {@code @CompiledMapper} interfaces. */
public final class MapperGenerator {
    private final ProcessorContext ctx;
    private final PathAccessEmitter pathAccess;
    private GenerationState generation;

    public MapperGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
        this.pathAccess = new PathAccessEmitter(ctx);
    }

    /**
     * Generates an implementation for one {@code @CompiledMapper} interface.
     */
    public void generate(TypeElement iface) {
        GeneratedClass target = new GeneratedClass(ctx, iface, GeneratorUtil.COMPILED_IMPL_POSTFIX);
        generation = new GenerationState();
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            Set<Modifier> mods = method.getModifiers();
            if (!mods.contains(Modifier.ABSTRACT)) continue;

            _genMap(iface, method, target);
        }
        target.emit();
        generation = null;
    }

    /**
     * Validates and emits one mapping method.
     */
    private void _genMap(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            _genUpdate(iface, method, target);
            return;
        }
        if (method.getParameters().isEmpty()) {
            _error(method, target, "@CompiledMapper method must have at least one source parameter");
            return;
        }

        ContainerType rootContainer = _container(method.getReturnType());
        if (rootContainer != null) {
            _genContainerCreate(iface, method, target, rootContainer);
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
            boolean dynamic = _dynamicSource(source.asType());
            sources.add(new SourceParam(source, dynamic ? Collections.<String, Read>emptyMap() : _reads(sourceType, source.asType()), dynamic));
        }
        boolean multi = sources.size() > 1;

        Map<String, Write> writes = _writes(targetType, method.getReturnType());
        Plan plan = _creation(method, target, targetType, method.getReturnType(), writes);
        if (plan == null) return;

        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        MappingIfParentPresent[] ifParentAnns = method.getAnnotationsByType(MappingIfParentPresent.class);
        EnsureMapping[] ensureAnns = method.getAnnotationsByType(EnsureMapping.class);
        Map<String, Expr> explicit = new HashMap<String, Expr>();
        Map<String, String> nestedMappers = new HashMap<String, String>();
        List<TargetPathWrite> pathWrites = new ArrayList<TargetPathWrite>();
        Set<String> explicitTargets = new HashSet<String>();
        Set<String> ignored = new HashSet<String>();

        for (Mapping m : anns) {
            if (!_validateMapping(method, target, m)) return;
            if (_isRootNestedMapperMarker(m)) {
                _error(method, target, "@Mapping.nestedMapper root marker is supported only on root collection/map methods");
                return;
            }

            String t = m.target();
            if (_isAutoMarker(m)) continue;
            if (_isTargetPath(t)) {
                if (plan.ctor != null) {
                    _error(method, target, "Target paths are supported only for mutable no-args create targets and update targets");
                    return;
                }
                if (m.ignore()) {
                    _error(method, target, "@Mapping.ignore does not support target paths");
                    return;
                }
                if (!_addPathWrite(method, target, pathWrites, t, PathWriteMode.STRICT)) return;
                explicitTargets.add(t);
                if (m.nestedMapper().length() != 0) nestedMappers.put(t, m.nestedMapper().trim());
                continue;
            }
            if (m.ignore()) {
                ignored.add(t);
                continue;
            }
            explicitTargets.add(t);
            if (m.nestedMapper().length() != 0) nestedMappers.put(t, m.nestedMapper().trim());
        }

        if (!_collectExtraPathWrites(method, target, ifParentAnns, ensureAnns, pathWrites, nestedMappers, explicitTargets)) return;
        for (TargetPathWrite w : pathWrites) {
            String rootName = _firstPathName(w);
            if (rootName != null) ignored.add(rootName);
        }

        MethodState state = new MethodState(_readCounts(iface, anns, plan, ignored, explicitTargets, sources, multi));

        for (Mapping m : anns) {
            String t = m.target();
            if (_isAutoMarker(m) || m.ignore()) continue;
            if (_isTargetPath(t)) continue;
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

        Map<TargetPathWrite, Expr> pathValues = _pathValues(iface, method, target, sources, multi, state, pathWrites, nestedMappers, method.getReturnType(), false, NullValuePolicy.SET);
        if (pathValues == null) return;

        // Every writable/constructor target property must be assigned, either
        // explicitly or by same-name auto mapping.  Abstract methods with no
        // @Mapping annotations therefore still generate useful mappers.
        Map<String, Expr> values = new LinkedHashMap<String, Expr>();
        for (String name : plan.names) {
            if (ignored.contains(name)) continue;

            Expr e = explicit.get(name);
            if (e == null && _hasAutoSource(sources, name)) {
                e = _readExpr(method, target, sources, multi, state, name, name, null);
            }
            if (e == null) {
                _error(method, target, "Cannot map target property '" + name + "': no source property, @Mapping, or compute expression was found");
                return;
            }

            TypeMirror need = plan.writes.get(name).type;
            e = _maybeNestedExpr(iface, method, target, e, need, nestedMappers.get(name) == null ? "" : nestedMappers.get(name), name);
            if (e == null) return;
            if (!_assignable(e.type, need)) {
                _error(method, target, "Cannot assign source expression to target property '" + name + "': " + e.type + " is not assignable to " + need);
                return;
            }
            if (need.getKind().isPrimitive() && (e.nullableRoot || e.path)) {
                _error(method, target, "Cannot map target property '" + name + "': nullable source or path expression cannot be assigned to primitive target type " + need);
                return;
            }
            values.put(name, e);
        }
        if (plan.ctor != null && values.size() != plan.names.size()) {
            _error(method, target, "Constructor and record target properties cannot be ignored");
            return;
        }

        target.addMethod(out -> _emit(out, method, sources, multi, state, plan, values, pathValues));
    }

    private void _genUpdate(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() < 2) {
            _error(method, target, "@CompiledMapper update method must have a target parameter and at least one source parameter");
            return;
        }

        ContainerType rootContainer = _container(params.get(0).asType());
        if (rootContainer != null) {
            _genContainerUpdate(iface, method, target, rootContainer);
            return;
        }

        TypeElement targetType = GeneratorUtil.asTypeElement(params.get(0).asType());
        if (targetType == null) {
            _error(method, target, "@CompiledMapper supports only declared source and target types");
            return;
        }
        if (_isRecord(targetType)) {
            _error(method, target, "Update target type must be mutable; records and constructor-only targets are unsupported");
            return;
        }

        List<SourceParam> sources = new ArrayList<SourceParam>();
        for (int i = 1; i < params.size(); i++) {
            VariableElement source = params.get(i);
            TypeElement sourceType = GeneratorUtil.asTypeElement(source.asType());
            if (sourceType == null) {
                _error(method, target, "@CompiledMapper supports only declared source and target types");
                return;
            }
            boolean dynamic = _dynamicSource(source.asType());
            sources.add(new SourceParam(source, dynamic ? Collections.<String, Read>emptyMap() : _reads(sourceType, source.asType()), dynamic));
        }
        boolean multi = sources.size() > 1;
        Map<String, Write> writes = _writes(targetType, params.get(0).asType());
        if (writes.isEmpty()) {
            _error(method, target, "Update target type must expose writable public setters or non-final fields");
            return;
        }

        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        MappingIfParentPresent[] ifParentAnns = method.getAnnotationsByType(MappingIfParentPresent.class);
        EnsureMapping[] ensureAnns = method.getAnnotationsByType(EnsureMapping.class);
        Map<String, Expr> explicit = new HashMap<String, Expr>();
        Map<String, String> nestedMappers = new HashMap<String, String>();
        Map<String, ArrayPolicy> arrayPolicies = new HashMap<String, ArrayPolicy>();
        Map<String, ObjectPolicy> objectPolicies = new HashMap<String, ObjectPolicy>();
        List<TargetPathWrite> pathWrites = new ArrayList<TargetPathWrite>();
        Set<String> explicitTargets = new HashSet<String>();
        Set<String> ignored = new HashSet<String>();

        for (Mapping m : anns) {
            if (!_validateMapping(method, target, m)) return;
            if (_isRootNestedMapperMarker(m)) {
                _error(method, target, "@Mapping.nestedMapper root marker is supported only on root collection/map methods");
                return;
            }

            String t = m.target();
            if (_isAutoMarker(m)) continue;
            if (t.length() == 0) {
                _error(method, target, "@Mapping requires a non-empty target");
                return;
            }
            if (!writes.containsKey(t)) {
                if (_isTargetPath(t)) {
                    if (m.ignore()) {
                        _error(method, target, "@Mapping.ignore does not support target paths");
                        return;
                    }
                    if (!_addPathWrite(method, target, pathWrites, t, PathWriteMode.STRICT)) return;
                    explicitTargets.add(t);
                    if (m.nestedMapper().length() != 0) nestedMappers.put(t, m.nestedMapper().trim());
                    continue;
                }
                _error(method, target, "Cannot map target property '" + t + "': target is not writable");
                return;
            }
            if (m.ignore()) {
                ignored.add(t);
                continue;
            }
            explicitTargets.add(t);
            if (m.nestedMapper().length() != 0) nestedMappers.put(t, m.nestedMapper().trim());
            if (m.array() != ArrayPolicy.SET) arrayPolicies.put(t, m.array());
            if (m.object() != ObjectPolicy.PUT) objectPolicies.put(t, m.object());
        }

        if (!_collectExtraPathWrites(method, target, ifParentAnns, ensureAnns, pathWrites, nestedMappers, explicitTargets)) return;

        Plan plan = new Plan(null, new ArrayList<String>(writes.keySet()), writes);
        MethodState state = new MethodState(_readCounts(iface, anns, plan, ignored, explicitTargets, sources, multi));

        for (Mapping m : anns) {
            String t = m.target();
            if (_isAutoMarker(m) || m.ignore()) continue;
            if (_isTargetPath(t)) continue;

            Expr e;
            if (m.compute().length() != 0) {
                e = _compute(iface, method, target, sources, multi, state, m);
            } else {
                e = _readExpr(method, target, sources, multi, state, m.source().length() == 0 ? t : m.source(), t, null);
            }
            if (e == null) return;
            explicit.put(t, e);
        }

        MapperOptions cfg = method.getAnnotation(MapperOptions.class);
        NullValuePolicy nulls = cfg == null ? NullValuePolicy.SET : cfg.nulls();
        Map<TargetPathWrite, Expr> pathValues = _pathValues(iface, method, target, sources, multi, state, pathWrites, nestedMappers, params.get(0).asType(), true, nulls);
        if (pathValues == null) return;
        Map<String, Expr> values = new LinkedHashMap<String, Expr>();
        for (String name : plan.names) {
            if (ignored.contains(name)) continue;

            Expr e = explicit.get(name);
            if (e == null && !explicitTargets.contains(name) && _hasAutoSource(sources, name)) {
                e = _readExpr(method, target, sources, multi, state, name, name, null);
            }
            if (e == null) continue;

            TypeMirror need = plan.writes.get(name).type;
            String nestedMapper = nestedMappers.get(name) == null ? "" : nestedMappers.get(name);
            boolean inPlace = _inPlaceContainer(method, target, e.type, need, arrayPolicies.get(name), objectPolicies.get(name), nestedMapper);
            if (inPlace) {
                if (!_validateContainerMapping(iface, method, target, e.type, need, nestedMapper)) return;
            } else {
                e = _maybeNestedExpr(iface, method, target, e, need, nestedMapper, name);
                if (e == null) return;
                if (!_assignable(e.type, need)) {
                    _error(method, target, "Cannot assign source expression to target property '" + name + "': " + e.type + " is not assignable to " + need);
                    return;
                }
            }
            if (nulls == NullValuePolicy.IGNORE && need.getKind().isPrimitive() && e.type == null) {
                _error(method, target, "Cannot map target property '" + name + "': NullValuePolicy.IGNORE cannot guard computed expression for primitive target type " + need);
                return;
            }
            if (need.getKind().isPrimitive() && (e.nullableRoot || e.path)
                    && !(nulls == NullValuePolicy.IGNORE && e.type != null && !e.type.getKind().isPrimitive())) {
                _error(method, target, "Cannot map target property '" + name + "': nullable source or path expression cannot be assigned to primitive target type " + need);
                return;
            }
            values.put(name, e);
        }

        Map<String, Read> targetReads = _reads(targetType, params.get(0).asType());
        for (String name : plan.names) {
            Expr e = values.get(name);
            if (e == null) continue;
            ContainerType to = _container(plan.writes.get(name).type);
            if (to == null) continue;
            if ((to.map || arrayPolicies.containsKey(name)) && !targetReads.containsKey(name) && plan.writes.get(name).setter != null) {
                _error(method, target, "Cannot update target property '" + name + "' in place: setter-only target has no readable collection/map");
                return;
            }
        }
        target.addMethod(out -> _emitUpdate(out, method, params.get(0), sources, multi, state, plan, values, pathValues, nulls, arrayPolicies, objectPolicies, targetReads, nestedMappers, iface, target));
    }

    private void _genContainerCreate(TypeElement iface, ExecutableElement method, GeneratedClass target, ContainerType to) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "Root collection/map create methods support exactly one source parameter");
            return;
        }
        VariableElement source = method.getParameters().get(0);
        ContainerType from = _container(source.asType());
        if (from == null || from.map != to.map) {
            _error(method, target, "Root collection/map create requires matching source and target container types");
            return;
        }
        String nestedMapper = _methodNestedMapper(method, target);
        if (nestedMapper == null) return;
        Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        String impl = _implType(method, target, to);
        if (impl == null) return;
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            String s = source.getSimpleName().toString();
            out.line("if (" + s + " == null) return null;");
            out.line(method.getReturnType() + " _target = " + _newContainer(impl, to, s + ".size()") + ";");
            _emitContainerCopy(out, from, to, conv, "_target", s);
            out.line("return _target;");
            out.dedent();
            out.line("}");
        });
    }

    private void _genContainerUpdate(TypeElement iface, ExecutableElement method, GeneratedClass target, ContainerType to) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() != 2) {
            _error(method, target, "Root collection/map update methods support exactly target and source parameters");
            return;
        }
        VariableElement source = params.get(1);
        ContainerType from = _container(source.asType());
        if (from == null || from.map != to.map) {
            _error(method, target, "Root collection/map update requires matching source and target container types");
            return;
        }
        String nestedMapper = _methodNestedMapper(method, target);
        if (nestedMapper == null) return;
        Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        MapperOptions cfg = method.getAnnotation(MapperOptions.class);
        ArrayPolicy arrayPolicy = cfg == null ? ArrayPolicy.CLEAR_ADD : cfg.arrays();
        ObjectPolicy objectPolicy = cfg == null ? ObjectPolicy.PUT : cfg.objects();
        if (!to.map && arrayPolicy == ArrayPolicy.SET) {
            _error(method, target, "Root collection update does not support ArrayPolicy.SET; use CLEAR_ADD or ADD");
            return;
        }
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public void " + method.getSimpleName() + "(" + params.get(0).asType() + " " + params.get(0).getSimpleName() + ", " + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            String t = params.get(0).getSimpleName().toString();
            String s = source.getSimpleName().toString();
            out.line("if (" + s + " == null) return;");
            if (to.map) {
                if (objectPolicy == ObjectPolicy.CLEAR_PUT) out.line(t + ".clear();");
                _emitContainerCopy(out, from, to, conv, t, s, objectPolicy);
            } else {
                if (arrayPolicy == ArrayPolicy.CLEAR_ADD) out.line(t + ".clear();");
                _emitContainerCopy(out, from, to, conv, t, s);
            }
            out.dedent();
            out.line("}");
        });
    }

    private boolean _isAutoMarker(Mapping m) {
        return m.target().length() == 0
                && m.source().length() == 0
                && m.sources().length == 0
                && m.compute().length() == 0
                && m.nestedMapper().length() == 0
                && m.array() == ArrayPolicy.SET
                && m.object() == ObjectPolicy.PUT
                && !m.ignore();
    }

    private boolean _validateMapping(ExecutableElement method, GeneratedClass target, Mapping m) {
        if (m.sources().length > 0 && m.compute().length() == 0) {
            _error(method, target, "@Mapping.sources may be used only with @Mapping.compute");
            return false;
        }
        if (m.ignore() && (m.source().length() != 0 || m.compute().length() != 0 || m.sources().length != 0
                || m.nestedMapper().length() != 0 || m.array() != ArrayPolicy.SET || m.object() != ObjectPolicy.PUT)) {
            _error(method, target, "@Mapping.ignore cannot be combined with source, sources, compute, nestedMapper, array, or object");
            return false;
        }
        if (m.nestedMapper().length() != 0 && (m.compute().length() != 0 || m.ignore())) {
            _error(method, target, "@Mapping.nestedMapper cannot be combined with compute or ignore");
            return false;
        }
        if (m.nestedMapper().length() != 0 && !_isSimpleIdentifier(m.nestedMapper().trim())) {
            _error(method, target, "@Mapping.nestedMapper expects a mapper method name");
            return false;
        }
        if (method.getReturnType().getKind() != TypeKind.VOID && (m.array() != ArrayPolicy.SET || m.object() != ObjectPolicy.PUT)) {
            _error(method, target, "@Mapping.array and @Mapping.object are supported only on void update mapper methods");
            return false;
        }
        if (m.ignore() && m.target().length() == 0) {
            _error(method, target, "@Mapping.ignore requires a non-empty target");
            return false;
        }
        return true;
    }

    private boolean _isRootNestedMapperMarker(Mapping m) {
        return m.nestedMapper().length() != 0
                && m.target().length() == 0
                && m.source().length() == 0
                && m.sources().length == 0
                && m.compute().length() == 0
                && m.array() == ArrayPolicy.SET
                && m.object() == ObjectPolicy.PUT
                && !m.ignore();
    }

    private String _methodNestedMapper(ExecutableElement method, GeneratedClass target) {
        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        String nestedMapper = "";
        int count = 0;
        for (Mapping m : anns) {
            if (!_validateMapping(method, target, m)) return null;
            if (m.nestedMapper().length() == 0 && _isAutoMarker(m)) continue;
            if (!_isRootNestedMapperMarker(m)) {
                _error(method, target, "Root collection/map methods support only @Mapping(nestedMapper = \"methodName\") markers");
                return null;
            }
            count++;
            nestedMapper = m.nestedMapper().trim();
        }
        if (count > 1) {
            _error(method, target, "Root collection/map methods allow exactly one @Mapping.nestedMapper marker");
            return null;
        }
        return nestedMapper;
    }

    private boolean _isSimpleIdentifier(String value) {
        if (value.length() == 0 || !Character.isJavaIdentifierStart(value.charAt(0))) return false;
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) return false;
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
            if (!ignored.contains(name) && !explicitTargets.contains(name) && _hasAutoSource(sources, name)) {
                _count(counts, name);
            }
        }
        return counts;
    }

    private boolean _hasAutoSource(List<SourceParam> sources, String name) {
        SourceParam first = sources.get(0);
        return first.dynamic || first.reads.containsKey(name);
    }

    private void _count(Map<String, Integer> counts, String source) {
        Integer n = counts.get(source);
        counts.put(source, n == null ? 1 : n + 1);
    }

    private void _emit(SourceWriter out, ExecutableElement method, List<SourceParam> sources, boolean multi, MethodState state,
                       Plan plan, Map<String, Expr> values, Map<TargetPathWrite, Expr> pathValues) {
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

        if (plan.ctor != null) {
            for (String temp : state.readTemps) {
                out.line(temp);
            }
            for (Expr e : values.values()) {
                _emitTemps(out, e);
            }
            StringBuilder b = new StringBuilder("return new ").append(method.getReturnType()).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            out.line(method.getReturnType() + " _target = new " + method.getReturnType() + "();");
            for (String temp : state.readTemps) {
                out.line(temp);
            }
            for (String name : plan.names) {
                Expr e = values.get(name);
                if (e == null) continue;

                _emitTemps(out, e);
                Write w = plan.writes.get(name);
                if (w.setter != null) {
                    out.line("_target." + w.setter.getSimpleName() + "(" + e.code + ");");
                } else {
                    out.line("_target." + w.javaName + " = " + e.code + ";");
                }
            }
            for (Map.Entry<TargetPathWrite, Expr> entry : pathValues.entrySet()) {
                _emitTemps(out, entry.getValue());
                _emitTargetPath(out, method, "_target", method.getReturnType(), entry.getKey(), entry.getValue(), NullValuePolicy.SET);
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

    private boolean _collectExtraPathWrites(ExecutableElement method, GeneratedClass target,
                                            MappingIfParentPresent[] ifParentAnns, EnsureMapping[] ensureAnns,
                                            List<TargetPathWrite> pathWrites, Map<String, String> nestedMappers,
                                            Set<String> explicitTargets) {
        for (MappingIfParentPresent m : ifParentAnns) {
            if (!_validatePathMapping(method, target, "@MappingIfParentPresent", m.target(), m.sources(), m.compute(), m.nestedMapper(), m.array(), m.object())) return false;
            if (!_addPathWrite(method, target, pathWrites, m.target(), PathWriteMode.IF_PARENT_PRESENT)) return false;
            explicitTargets.add(m.target());
            if (m.nestedMapper().length() != 0) nestedMappers.put(m.target(), m.nestedMapper().trim());
        }
        for (EnsureMapping m : ensureAnns) {
            if (!_validatePathMapping(method, target, "@EnsureMapping", m.target(), m.sources(), m.compute(), m.nestedMapper(), m.array(), m.object())) return false;
            if (!_addPathWrite(method, target, pathWrites, m.target(), PathWriteMode.ENSURE)) return false;
            explicitTargets.add(m.target());
            if (m.nestedMapper().length() != 0) nestedMappers.put(m.target(), m.nestedMapper().trim());
        }
        return true;
    }

    private boolean _validatePathMapping(ExecutableElement method, GeneratedClass target, String anno, String t, String[] sources,
                                         String compute, String nestedMapper, ArrayPolicy array, ObjectPolicy object) {
        if (t.length() == 0) {
            _error(method, target, anno + " requires a non-empty target path");
            return false;
        }
        if (!_isTargetPath(t)) {
            _error(method, target, anno + " supports only JSONPath or JSON Pointer targets");
            return false;
        }
        if (sources.length > 0 && compute.length() == 0) {
            _error(method, target, anno + ".sources may be used only with compute");
            return false;
        }
        if (nestedMapper.length() != 0 && compute.length() != 0) {
            _error(method, target, anno + ".nestedMapper cannot be combined with compute");
            return false;
        }
        if (nestedMapper.length() != 0 && !_isSimpleIdentifier(nestedMapper.trim())) {
            _error(method, target, anno + ".nestedMapper expects a mapper method name");
            return false;
        }
        if (array != ArrayPolicy.SET || object != ObjectPolicy.PUT) {
            _error(method, target, anno + ".array and object policies are not supported for target-path writes");
            return false;
        }
        return true;
    }

    private boolean _isTargetPath(String t) {
        return t.startsWith("$") || t.startsWith("/");
    }

    private boolean _addPathWrite(ExecutableElement method, GeneratedClass target, List<TargetPathWrite> writes, String expr, PathWriteMode mode) {
        for (TargetPathWrite w : writes) {
            if (w.path.equals(expr)) {
                _error(method, target, "Duplicate target path '" + expr + "'");
                return false;
            }
        }
        JsonPath path;
        try {
            path = JsonPath.parse(expr);
        } catch (RuntimeException e) {
            _error(method, target, "Invalid target path '" + expr + "': " + e.getMessage());
            return false;
        }
        if (path.segments().length < 2) {
            _error(method, target, "Target path requires a non-root child path");
            return false;
        }
        for (int i = 1; i < path.segments().length; i++) {
            PathSegment s = path.segments()[i];
            if (!(s instanceof PathSegment.Name) && !(s instanceof PathSegment.Index)) {
                _error(method, target, "Target paths support only property names and array/list indexes");
                return false;
            }
            if (mode == PathWriteMode.ENSURE && s instanceof PathSegment.Index) {
                _error(method, target, "@EnsureMapping does not support index-based target path segments");
                return false;
            }
        }
        writes.add(new TargetPathWrite(expr, path.segments(), mode));
        return true;
    }

    private Map<TargetPathWrite, Expr> _pathValues(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                   List<SourceParam> sources, boolean multi, MethodState state,
                                                   List<TargetPathWrite> pathWrites, Map<String, String> nestedMappers,
                                                   TypeMirror rootType, boolean update, NullValuePolicy nulls) {
        Map<TargetPathWrite, Expr> values = new LinkedHashMap<TargetPathWrite, Expr>();
        for (TargetPathWrite w : pathWrites) {
            TypeMirror need = _targetPathValueType(method, target, rootType, w);
            if (need == null) return null;
            Expr e;
            MappingSpec spec = _mappingSpec(method, w.path);
            if (spec == null) return null;
            if (spec.compute.length() != 0) {
                e = _compute(iface, method, target, sources, multi, state, spec);
            } else {
                String src = spec.source.length() == 0 ? _tailName(w) : spec.source;
                e = _readExpr(method, target, sources, multi, state, src, w.path, null);
            }
            if (e == null) return null;
            e = _maybeNestedExpr(iface, method, target, e, need, nestedMappers.get(w.path) == null ? "" : nestedMappers.get(w.path), w.path);
            if (e == null) return null;
            if (!_assignable(e.type, need)) {
                _error(method, target, "Cannot assign source expression to target path '" + w.path + "': " + e.type + " is not assignable to " + need);
                return null;
            }
            if (need.getKind().isPrimitive() && (e.nullableRoot || e.path)
                    && !(update && nulls == NullValuePolicy.IGNORE && e.type != null && !e.type.getKind().isPrimitive())) {
                _error(method, target, "Cannot map target path '" + w.path + "': nullable source or path expression cannot be assigned to primitive target type " + need);
                return null;
            }
            values.put(w, e);
        }
        return values;
    }

    private MappingSpec _mappingSpec(ExecutableElement method, String target) {
        for (Mapping m : method.getAnnotationsByType(Mapping.class)) if (m.target().equals(target)) return new MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper());
        for (MappingIfParentPresent m : method.getAnnotationsByType(MappingIfParentPresent.class)) if (m.target().equals(target)) return new MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper());
        for (EnsureMapping m : method.getAnnotationsByType(EnsureMapping.class)) if (m.target().equals(target)) return new MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper());
        return null;
    }

    private String _tailName(TargetPathWrite w) {
        PathSegment s = w.segments[w.segments.length - 1];
        return s instanceof PathSegment.Name ? ((PathSegment.Name) s).name : w.path;
    }

    private String _firstPathName(TargetPathWrite w) {
        return w.segments.length > 1 && w.segments[1] instanceof PathSegment.Name ? ((PathSegment.Name) w.segments[1]).name : null;
    }

    private TypeMirror _targetPathValueType(ExecutableElement method, GeneratedClass target, TypeMirror rootType, TargetPathWrite w) {
        TypeMirror current = rootType;
        for (int i = 1; i < w.segments.length - 1; i++) {
            PathSegment s = w.segments[i];
            if (s instanceof PathSegment.Name) current = _readNameType(method, target, current, ((PathSegment.Name) s).name);
            else current = _indexType(method, target, current, ((PathSegment.Index) s).index);
            if (current == null) return null;
        }
        PathSegment tail = w.segments[w.segments.length - 1];
        return tail instanceof PathSegment.Name
                ? _writeNameType(method, target, current, ((PathSegment.Name) tail).name)
                : _indexType(method, target, current, ((PathSegment.Index) tail).index);
    }

    private TypeMirror _readNameType(ExecutableElement method, GeneratedClass target, TypeMirror parent, String name) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) return ctx.objectType;
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) return GeneratorUtil.mapValueType(ctx, parent);
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) { _error(method, target, "Cannot resolve target path property '" + name + "' on " + parent); return null; }
        Read r = _reads(type, parent).get(name);
        if (r != null) return r.type;
        _error(method, target, "Cannot resolve readable target path property '" + name + "' on " + parent);
        return null;
    }

    private TypeMirror _writeNameType(ExecutableElement method, GeneratedClass target, TypeMirror parent, String name) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) return ctx.objectType;
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) return GeneratorUtil.mapValueType(ctx, parent);
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) { _error(method, target, "Cannot resolve writable target path property '" + name + "' on " + parent); return null; }
        Write w = _writes(type, parent).get(name);
        if (w != null) return w.type;
        _error(method, target, "Cannot resolve writable target path property '" + name + "' on " + parent);
        return null;
    }

    private TypeMirror _indexType(ExecutableElement method, GeneratedClass target, TypeMirror parent, int index) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) return ctx.objectType;
        if (parent.getKind() == TypeKind.ARRAY) return ((ArrayType) parent).getComponentType();
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.listType)) return GeneratorUtil.listValueType(ctx, parent);
        _error(method, target, "Cannot resolve target path index [" + index + "] on " + parent);
        return null;
    }

    private void _emitTargetPath(SourceWriter out, ExecutableElement method, String rootVar, TypeMirror rootType, TargetPathWrite w, Expr e, NullValuePolicy nulls) {
        String value = e.code;
        if (nulls == NullValuePolicy.IGNORE && (e.type == null || !e.type.getKind().isPrimitive())) {
            String temp = _tempName(w.path, "value", 0);
            if (!e.local) {
                out.line(_localTypeName(e.type, true) + " " + temp + " = " + e.code + ";");
                value = temp;
            }
            out.line("if (" + value + " != null) {");
            out.indent();
        }
        String currentVar = rootVar;
        TypeMirror currentType = rootType;
        boolean openSkip = false;
        for (int i = 1; i < w.segments.length - 1; i++) {
            PathSegment s = w.segments[i];
            String nextVar = _tempName(w.path, "parent" + i, i);
            TypeMirror nextType;
            if (s instanceof PathSegment.Name) {
                String name = ((PathSegment.Name) s).name;
                nextType = _readNameType(method, null, currentType, name);
                _emitReadName(out, currentType, currentVar, name, nextVar, nextType);
                if (w.mode == PathWriteMode.ENSURE) _emitEnsure(out, currentType, currentVar, name, nextVar, nextType, w.segments[i + 1]);
            } else {
                int idx = ((PathSegment.Index) s).index;
                nextType = _indexType(method, null, currentType, idx);
                _emitReadIndex(out, currentType, currentVar, idx, nextVar, nextType);
            }
            if (w.mode == PathWriteMode.IF_PARENT_PRESENT) {
                out.line("if (" + nextVar + " != null) {");
                out.indent();
                openSkip = true;
            } else if (w.mode == PathWriteMode.STRICT) {
                out.line("if (" + nextVar + " == null) throw new org.sjf4j.exception.JsonException(\"Missing target path parent: " + GeneratorUtil.escape(w.path) + "\");");
            }
            currentVar = nextVar;
            currentType = nextType;
        }
        _emitFinalWrite(out, currentType, currentVar, w.segments[w.segments.length - 1], value);
        if (openSkip) {
            for (int i = 1; i < w.segments.length - 1; i++) {
                if (w.mode == PathWriteMode.IF_PARENT_PRESENT) { out.dedent(); out.line("}"); }
            }
        }
        if (nulls == NullValuePolicy.IGNORE && (e.type == null || !e.type.getKind().isPrimitive())) {
            out.dedent();
            out.line("}");
        }
    }

    private void _emitReadName(SourceWriter out, TypeMirror parent, String var, String name, String next, TypeMirror type) {
        if (GeneratorUtil.isObject(ctx, parent)) out.line(_localTypeName(type, true) + " " + next + " = org.sjf4j.node.Nodes.getInObject(" + var + ", \"" + GeneratorUtil.escape(name) + "\");");
        else if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) out.line(_localTypeName(type, true) + " " + next + " = " + var + ".getNode(\"" + GeneratorUtil.escape(name) + "\");");
        else if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) out.line(_localTypeName(type, true) + " " + next + " = (" + _localTypeName(type, true) + ") " + var + ".get(\"" + GeneratorUtil.escape(name) + "\");");
        else {
            TypeElement te = GeneratorUtil.asTypeElement(parent);
            Read r = _reads(te, parent).get(name);
            out.line(_localTypeName(type, true) + " " + next + " = " + (r.method != null ? var + "." + r.method.getSimpleName() + "()" : var + "." + r.javaName) + ";");
        }
    }

    private void _emitReadIndex(SourceWriter out, TypeMirror parent, String var, int idx, String next, TypeMirror type) {
        out.line(_localTypeName(type, true) + " " + next + " = null;");
        if (GeneratorUtil.isObject(ctx, parent)) out.line(next + " = org.sjf4j.node.Nodes.getInArray(" + var + ", " + idx + ");");
        else if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) out.line(next + " = " + var + ".getNode(" + idx + ");");
        else if (parent.getKind() == TypeKind.ARRAY) out.line("if (" + idx + " >= 0 && " + idx + " < " + var + ".length) " + next + " = " + var + "[" + idx + "];");
        else out.line("if (" + idx + " >= 0 && " + idx + " < " + var + ".size()) " + next + " = (" + _localTypeName(type, true) + ") " + var + ".get(" + idx + ");");
    }

    private void _emitEnsure(SourceWriter out, TypeMirror parent, String var, String name, String child, TypeMirror childType, PathSegment next) {
        if (childType.getKind().isPrimitive()) return;
        String create = _createExpr(childType, next);
        if (create == null) return;
        out.line("if (" + child + " == null) {");
        out.indent();
        out.line(child + " = " + create + ";");
        _emitFinalWrite(out, parent, var, new PathSegment.Name(null, name), child);
        out.dedent();
        out.line("}");
    }

    private String _createExpr(TypeMirror type, PathSegment next) {
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) return "new java.util.LinkedHashMap<>()";
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType)) return "new java.util.ArrayList<>()";
        if (GeneratorUtil.isObject(ctx, type)) return next instanceof PathSegment.Index ? "new java.util.ArrayList<>()" : "new java.util.LinkedHashMap<>()";
        TypeElement te = GeneratorUtil.asTypeElement(type);
        if (te == null) return null;
        for (ExecutableElement c : _publicConstructors(te)) if (c.getParameters().isEmpty()) return "new " + GeneratorUtil.localTypeName(ctx, type) + "()";
        return null;
    }

    private void _emitFinalWrite(SourceWriter out, TypeMirror parent, String var, PathSegment tail, String value) {
        if (tail instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) tail).name;
            if (GeneratorUtil.isObject(ctx, parent)) out.line("org.sjf4j.node.Nodes.putInObject(" + var + ", \"" + GeneratorUtil.escape(name) + "\", " + value + ");");
            else if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) out.line(var + ".put(\"" + GeneratorUtil.escape(name) + "\", " + value + ");");
            else if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) out.line(var + ".put(\"" + GeneratorUtil.escape(name) + "\", " + value + ");");
            else {
                TypeElement te = GeneratorUtil.asTypeElement(parent);
                Write w = _writes(te, parent).get(name);
                out.line(w.setter != null ? var + "." + w.setter.getSimpleName() + "(" + value + ");" : var + "." + w.javaName + " = " + value + ";");
            }
        } else {
            int idx = ((PathSegment.Index) tail).index;
            if (GeneratorUtil.isObject(ctx, parent)) out.line("org.sjf4j.node.Nodes.putInArray(" + var + ", " + idx + ", " + value + ");");
            else if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonArrayType)) out.line(var + ".set(" + idx + ", " + value + ");");
            else if (parent.getKind() == TypeKind.ARRAY) out.line(var + "[" + idx + "] = " + value + ";");
            else out.line(var + ".set(" + idx + ", " + value + ");");
        }
    }

    private void _emitUpdate(SourceWriter out, ExecutableElement method, VariableElement targetParam, List<SourceParam> sources,
                             boolean multi, MethodState state, Plan plan, Map<String, Expr> values, Map<TargetPathWrite, Expr> pathValues, NullValuePolicy nulls,
                             Map<String, ArrayPolicy> arrayPolicies, Map<String, ObjectPolicy> objectPolicies, Map<String, Read> targetReads,
                              Map<String, String> nestedMappers, TypeElement iface, GeneratedClass genTarget) {
        out.line("");
        out.line("@Override");
        StringBuilder sig = new StringBuilder();
        sig.append(targetParam.asType()).append(" ").append(targetParam.getSimpleName());
        for (int i = 0; i < sources.size(); i++) {
            sig.append(", ").append(sources.get(i).element.asType()).append(" ").append(sources.get(i).name);
        }
        out.line("public void " + method.getSimpleName() + "(" + sig + ") {");
        out.indent();
        if (multi) {
            StringBuilder guard = new StringBuilder("if (");
            for (int i = 0; i < sources.size(); i++) {
                if (i != 0) guard.append(" && ");
                guard.append(sources.get(i).name).append(" == null");
            }
            out.line(guard.append(") return;").toString());
        } else {
            out.line("if (" + sources.get(0).name + " == null) return;");
        }

        for (String temp : state.readTemps) out.line(temp);
        for (Expr e : values.values()) _emitTemps(out, e);
        for (Expr e : pathValues.values()) _emitTemps(out, e);

        String targetName = targetParam.getSimpleName().toString();
        for (String name : plan.names) {
            Expr e = values.get(name);
            if (e == null) continue;

            Write w = plan.writes.get(name);
            ContainerType to = _container(w.type);
            if (to != null && to.map) {
                _emitObjectField(out, iface, method, genTarget, targetName, name, w, targetReads.get(name), e,
                        nestedMappers.get(name) == null ? "" : nestedMappers.get(name), objectPolicies.get(name) == null ? ObjectPolicy.PUT : objectPolicies.get(name));
                continue;
            }
            if (arrayPolicies.containsKey(name)) {
                _emitArrayField(out, iface, method, genTarget, targetName, name, w, targetReads.get(name), e,
                        nestedMappers.get(name) == null ? "" : nestedMappers.get(name), arrayPolicies.get(name));
                continue;
            }
            String assign = w.setter != null
                    ? targetName + "." + w.setter.getSimpleName() + "(" + e.code + ");"
                    : targetName + "." + w.javaName + " = " + e.code + ";";
            if (nulls == NullValuePolicy.IGNORE && (e.type == null || !e.type.getKind().isPrimitive())) {
                if (e.local) {
                    out.line("if (" + e.code + " != null) " + assign);
                } else if (e.type == null) {
                    out.line("if (" + e.code + " != null) " + assign);
                } else {
                    String temp = _tempName(name, "value", 0);
                    out.line(_localTypeName(e.type, true) + " " + temp + " = " + e.code + ";");
                    String tempAssign = w.setter != null
                            ? targetName + "." + w.setter.getSimpleName() + "(" + temp + ");"
                            : targetName + "." + w.javaName + " = " + temp + ";";
                    out.line("if (" + temp + " != null) " + tempAssign);
                }
            } else {
                out.line(assign);
            }
        }
        for (Map.Entry<TargetPathWrite, Expr> entry : pathValues.entrySet()) {
            _emitTargetPath(out, method, targetName, targetParam.asType(), entry.getKey(), entry.getValue(), nulls);
        }
        out.dedent();
        out.line("}");
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
                String n = member.getSimpleName().toString();
                r.put(_nodeName(member, n), new Read(null, n, fieldType));
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
                    String base = GeneratorUtil.decap(n.substring(3));
                    r.put(_nodeName(e, base), new Read(e, base, mt.getReturnType()));
                } else if (n.startsWith("is") && n.length() > 2) {
                    String base = GeneratorUtil.decap(n.substring(2));
                    r.put(_nodeName(e, base), new Read(e, base, mt.getReturnType()));
                } else if (_isRecord(type)) {
                    r.put(_nodeName(e, n), new Read(e, n, mt.getReturnType()));
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
                String nodeName = _nodeName(member, n);
                if (!w.containsKey(nodeName)) {
                    w.put(nodeName, new Write(null, n, ctx.types.asMemberOf((DeclaredType) owner, member)));
                }
                continue;
            }

            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement e = (ExecutableElement) member;
                if (!e.getSimpleName().toString().startsWith("set") || e.getParameters().size() != 1) continue;

                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, e);
                if (mt.getReturnType().getKind() == TypeKind.VOID) {
                    String name = GeneratorUtil.decap(e.getSimpleName().toString().substring(3));
                    w.put(_nodeName(e, name), new Write(e, name, mt.getParameterTypes().get(0)));
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
            VariableElement p = ctor.getParameters().get(i);
            String n = p.getSimpleName().toString();
            w.put(_nodeName(p, n), new Write(null, n, ct.getParameterTypes().get(i)));
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

    private String _nodeName(Element e, String fallback) {
        return GeneratorUtil.nodePropertyName(e, fallback);
    }

    private boolean _dynamicSource(TypeMirror type) {
        return GeneratorUtil.isObject(ctx, type)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType);
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

        SourceParam first = sources.get(0);
        if (!first.dynamic && !first.reads.containsKey(source) && !source.startsWith("$") && !source.startsWith("/")) {
            _error(method, target, "Cannot resolve source '" + source + "' on first source parameter '" + first.name + "'");
            return null;
        }
        return new ResolvedSource(first, source, true);
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
        return _compute(iface, method, target, sources, multi, state,
                new MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper()));
    }

    private Expr _compute(TypeElement iface, ExecutableElement method, GeneratedClass target, List<SourceParam> sources,
                          boolean multi, MethodState state, MappingSpec m) {
        String c = m.compute.trim();
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
        String[] paths = m.sources.length == 0 ? params : m.sources;
        if (params.length != paths.length) {
            _error(method, target, "@Mapping.sources must match the lambda parameters in @Mapping.compute");
            return null;
        }

        String body = c.substring(arrow + 2).trim();
        Expr e = new Expr(body, null);
        for (int i = 0; i < params.length; i++) {
            String temp = _tempName(m.target, params[i], i);
            Expr v = _readExpr(method, target, sources, multi, state, paths[i], m.target + i, temp);
            if (v == null) return null;

            e.temps.addAll(v.temps);
            if (!v.local) {
                e.temps.add(_localTypeName(v.type, v.nullableRoot) + " " + temp + " = " + v.code + ";");
                v.code = temp;
            }
            e.code = e.code.replaceAll("\\b" + Pattern.quote(params[i]) + "\\b", Matcher.quoteReplacement(v.code));
        }
        return e;
    }

    private Expr _helper(TypeElement iface, ExecutableElement method, GeneratedClass target, List<SourceParam> sources,
                         boolean multi, MethodState state, MappingSpec m, String name) {
        int matches = 0;
        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;
            if (!e.getModifiers().contains(Modifier.DEFAULT) && !e.getModifiers().contains(Modifier.STATIC)) continue;
            matches++;
        }
        if (matches > 1) {
            _error(method, target, "Ambiguous @Mapping.compute helper '" + name + "'; overloaded helper methods are not supported");
            return null;
        }

        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;

            ExecutableElement h = (ExecutableElement) e;
            if (!h.getModifiers().contains(Modifier.DEFAULT) && !h.getModifiers().contains(Modifier.STATIC)) continue;

            ExecutableType ht = (ExecutableType) ctx.types.asMemberOf((DeclaredType) iface.asType(), h);
            String[] paths = m.sources;
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

                String preferred = _tempName(m.target, h.getParameters().get(i).getSimpleName().toString(), i);
                Expr v = _readExpr(method, target, sources, multi, state, paths[i], m.target + i, preferred);
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

    private ContainerType _container(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return null;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        if (e == null) return null;
        TypeMirror erased = ctx.types.erasure(type);
        TypeMirror coll = ctx.elements.getTypeElement("java.util.Collection").asType();
        TypeMirror map = ctx.elements.getTypeElement("java.util.Map").asType();
        if (ctx.types.isAssignable(erased, ctx.types.erasure(map))) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() != 2) return new ContainerType(true, null, null, type);
            return new ContainerType(true, args.get(0), args.get(1), type);
        }
        if (ctx.types.isAssignable(erased, ctx.types.erasure(coll))) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() != 1) return new ContainerType(false, null, null, type);
            return new ContainerType(false, null, args.get(0), type);
        }
        return null;
    }

    private boolean _inPlaceContainer(ExecutableElement method, GeneratedClass target, TypeMirror fromType, TypeMirror toType,
                                      ArrayPolicy arrayPolicy, ObjectPolicy objectPolicy, String nestedMapper) {
        ContainerType from = _container(fromType);
        ContainerType to = _container(toType);
        if (from == null || to == null || from.map != to.map) return false;
        if (to.map) return true;
        return arrayPolicy != null && arrayPolicy != ArrayPolicy.SET;
    }

    private boolean _validateContainerMapping(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                              TypeMirror fromType, TypeMirror toType, String nestedMapper) {
        ContainerType from = _container(fromType);
        ContainerType to = _container(toType);
        if (from == null || to == null || from.map != to.map) return false;
        return _containerConverter(iface, method, target, from, to, nestedMapper) != null;
    }

    private Converter _containerConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, ContainerType from, ContainerType to, String nestedMapper) {
        if (from.value == null || to.value == null || (from.map && (from.key == null || to.key == null))) {
            _error(method, target, "Raw or non-parameterized collection/map types are unsupported");
            return null;
        }
        if (from.map && !_assignable(from.key, to.key)) {
            _error(method, target, "Map key type mismatch: " + from.key + " is not assignable to " + to.key);
            return null;
        }
        if ((nestedMapper == null || nestedMapper.length() == 0) && _assignable(from.value, to.value)) return new Converter(null, to.value);
        return _resolveConverter(iface, method, target, from.value, to.value, nestedMapper);
    }

    private Expr _maybeNestedExpr(TypeElement iface, ExecutableElement method, GeneratedClass target, Expr e, TypeMirror need, String nestedMapper, String name) {
        ContainerType from = _container(e.type);
        ContainerType to = _container(need);
        if (from == null || to == null || from.map != to.map) {
            boolean named = nestedMapper != null && nestedMapper.length() != 0;
            if (!named && _assignable(e.type, need)) return e;
            Converter conv = _resolveConverter(iface, method, target, e.type, need, nestedMapper, named);
            if (conv == null) return named ? null : e;
            Expr r = new Expr(_convertValue(conv, e.code), conv.type, e.path, e.nullableRoot, false);
            r.temps.addAll(e.temps);
            return r;
        }
        Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return null;
        String impl = _implType(method, target, to);
        if (impl == null) return null;
        String temp = _tempName(name, "container", 0);
        String source = e.code;
        Expr r = new Expr(temp, need);
        r.local = true;
        r.temps.addAll(e.temps);
        if (!e.local) {
            source = _tempName(name, "source", 0);
            r.temps.add(e.type + " " + source + " = " + e.code + ";");
        }
        r.temps.add(need + " " + temp + ";");
        r.temps.add("if (" + source + " == null) {");
        r.temps.add("    " + temp + " = null;");
        r.temps.add("} else {");
        r.temps.add("    " + temp + " = " + _newContainer(impl, to, source + ".size()") + ";");
        if (to.map) {
            r.temps.add("    for (java.util.Map.Entry<" + GeneratorUtil.localTypeName(ctx, from.key) + ", " + GeneratorUtil.localTypeName(ctx, from.value) + "> _entry : " + source + ".entrySet()) {");
            r.temps.add("        " + temp + ".put(_entry.getKey(), " + _convertValue(conv, "_entry.getValue()") + ");");
            r.temps.add("    }");
        } else {
            r.temps.add("    for (" + GeneratorUtil.localTypeName(ctx, from.value) + " _value : " + source + ") {");
            r.temps.add("        " + temp + ".add(" + _convertValue(conv, "_value") + ");");
            r.temps.add("    }");
        }
        r.temps.add("}");
        return r;
    }

    private Converter _resolveConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String nestedMapper) {
        return _resolveConverter(iface, method, target, from, to, nestedMapper, true);
    }

    private Converter _resolveConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String nestedMapper, boolean errorIfMissing) {
        if (nestedMapper != null && nestedMapper.length() != 0) {
            String name = nestedMapper;
            int named = 0;
            ExecutableElement found = null;
            for (Element e : iface.getEnclosedElements()) {
                if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;
                if (e.getModifiers().contains(Modifier.PRIVATE)) continue;
                named++;
                found = (ExecutableElement) e;
            }
            if (named > 1) {
                _error(method, target, "Ambiguous converter '" + name + "'; overloaded converter methods are not supported");
                return null;
            }
            if (found == null) {
                _error(method, target, "Cannot resolve converter '" + name + "'");
                return null;
            }
            return _converterFromMethod(iface, method, target, found, from, to);
        }

        ExecutableElement found = null;
        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) continue;
            ExecutableElement h = (ExecutableElement) e;
            if (h.getModifiers().contains(Modifier.PRIVATE)) continue;
            if (h == method || h.getParameters().size() != 1 || h.getReturnType().getKind() == TypeKind.VOID) continue;
            ExecutableType ht = (ExecutableType) ctx.types.asMemberOf((DeclaredType) iface.asType(), h);
            if (_assignable(from, ht.getParameterTypes().get(0)) && _assignable(ht.getReturnType(), to)) {
                if (found != null) {
                    _error(method, target, "Ambiguous element/value converter; specify @Mapping.nestedMapper");
                    return null;
                }
                found = h;
            }
        }
        if (found == null) {
            Converter fallback = _enumConverter(method, target, from, to);
            if (fallback != null) return fallback;
            fallback = _nodeValueConverter(method, target, from, to);
            if (fallback != null) return fallback;
            fallback = _autoHelperConverter(iface, method, target, from, to);
            if (fallback != null) return fallback;
            if (errorIfMissing) _error(method, target, "Cannot find element/value converter from " + from + " to " + to);
            return null;
        }
        return _converterFromMethod(iface, method, target, found, from, to);
    }

    private Converter _enumConverter(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        if (targetType == null || targetType.getKind() != ElementKind.ENUM) return null;
        TypeElement sourceType = GeneratorUtil.asTypeElement(from);
        if (sourceType == null) return null;
        String sourceName = sourceType.getQualifiedName().toString();
        if ("java.lang.String".equals(sourceName)) {
            String helper = _ensureEnumHelper(method, target, from, to, "string");
            return helper == null ? null : new Converter(helper, to);
        }
        if (sourceType.getKind() == ElementKind.ENUM) {
            Set<String> targetConstants = _enumConstants(targetType);
            for (String c : _enumConstants(sourceType)) {
                if (!targetConstants.contains(c)) {
                    _error(method, target, "Cannot map enum " + from + " to " + to + ": target enum is missing constant '" + c + "'; provide a mapper or compute expression");
                    return null;
                }
            }
            String helper = _ensureEnumHelper(method, target, from, to, "enum");
            return helper == null ? null : new Converter(helper, to);
        }
        return null;
    }

    private Set<String> _enumConstants(TypeElement type) {
        Set<String> r = new HashSet<String>();
        for (Element e : type.getEnclosedElements()) {
            if (e.getKind() == ElementKind.ENUM_CONSTANT) r.add(e.getSimpleName().toString());
        }
        return r;
    }

    private Converter _nodeValueConverter(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        boolean fromValue = _isNodeValue(from);
        boolean toValue = _isNodeValue(to);
        if (!fromValue && !toValue) return null;
        if (fromValue && toValue) {
            String helper = _ensureNodeValueHelper(method, target, from, to, "value_value");
            return helper == null ? null : new Converter(helper, to);
        }
        if (fromValue) {
            String helper = _ensureNodeValueHelper(method, target, from, to, "value_raw");
            return helper == null ? null : new Converter(helper, to);
        }
        String helper = _ensureNodeValueHelper(method, target, from, to, "raw_value");
        return helper == null ? null : new Converter(helper, to);
    }

    private boolean _isNodeValue(TypeMirror type) {
        TypeElement e = GeneratorUtil.asTypeElement(type);
        return e != null && e.getAnnotation(org.sjf4j.annotation.node.NodeValue.class) != null;
    }

    private String _ensureNodeValueHelper(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String kind) {
        String key = "nodevalue:" + kind + ":" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String fromCodec = _isNodeValue(from) ? _ensureCodecField(target, from) : null;
        String toCodec = _isNodeValue(to) ? _ensureCodecField(target, to) : null;
        String name = "_sjf4j_" + kind + "_" + generation.nextHelper++;
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " value) {");
            out.indent();
            if (!from.getKind().isPrimitive() && !to.getKind().isPrimitive()) out.line("if (value == null) return null;");
            if ("value_raw".equals(kind)) {
                out.line("return (" + GeneratorUtil.localTypeName(ctx, to) + ") " + fromCodec + ".valueToRaw(value);");
            } else if ("raw_value".equals(kind)) {
                out.line("return (" + GeneratorUtil.localTypeName(ctx, to) + ") " + toCodec + ".rawToValue(value);");
            } else {
                out.line("Object _raw = " + fromCodec + ".valueToRaw(value);");
                out.line("return (" + GeneratorUtil.localTypeName(ctx, to) + ") " + toCodec + ".rawToValue(_raw);");
            }
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private String _ensureCodecField(GeneratedClass target, TypeMirror type) {
        String key = "codec:" + type;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String field = "_sjf4j_codec_" + generation.nextCodec++;
        generation.helpers.put(key, field);
        String raw = GeneratorUtil.classLiteral(ctx, type);
        target.addField(out -> out.line("private static final org.sjf4j.node.NodeRegistry.ValueCodecInfo " + field + " = org.sjf4j.node.NodeRegistry.resolveValueCodecOrElseThrow(" + raw + ", \"\");"));
        return field;
    }

    private String _ensureEnumHelper(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String kind) {
        String key = "enum:" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String name = "_sjf4j_" + kind + "_" + generation.nextHelper++;
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " value) {");
            out.indent();
            out.line("if (value == null) return null;");
            if ("string".equals(kind)) {
                out.line("return " + to + ".valueOf(value);");
            } else {
                out.line("return " + to + ".valueOf(value.name());");
            }
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private Converter _autoHelperConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        if (_assignable(from, to)) return new Converter(null, to);
        TypeElement sourceType = GeneratorUtil.asTypeElement(from);
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        if (sourceType == null || targetType == null) return null;
        if (sourceType.getKind() == ElementKind.ENUM || targetType.getKind() == ElementKind.ENUM) return null;
        String helper = _ensureAutoHelper(iface, method, target, from, to, sourceType, targetType);
        return helper == null ? null : new Converter(helper, to);
    }

    private String _ensureAutoHelper(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to,
                                     TypeElement sourceType, TypeElement targetType) {
        String key = "bean:" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        if (generation.inProgress.contains(key)) {
            _error(method, target, "Recursive automatic nested mapper from " + from + " to " + to + " is unsupported; provide an explicit mapper");
            return null;
        }
        Map<String, Read> reads = _reads(sourceType, from);
        Map<String, Write> writes = _writes(targetType, to);
        Plan plan = _creation(method, target, targetType, to, writes);
        if (plan == null) return null;

        generation.inProgress.add(key);
        Map<String, Expr> values = new LinkedHashMap<String, Expr>();
        for (String name : plan.names) {
            Read r = reads.get(name);
            if (r == null) {
                generation.inProgress.remove(key);
                _error(method, target, "Cannot auto-map nested target property '" + name + "' from " + from + " to " + to + ": no same-name source property was found");
                return null;
            }
            String access = r.method == null ? "value." + r.javaName : "value." + r.method.getSimpleName() + "()";
            Expr e = new Expr(access, r.type);
            e = _maybeNestedExpr(iface, method, target, e, plan.writes.get(name).type, "", name);
            if (e == null) {
                generation.inProgress.remove(key);
                return null;
            }
            if (!_assignable(e.type, plan.writes.get(name).type)) {
                generation.inProgress.remove(key);
                _error(method, target, "Cannot auto-map nested target property '" + name + "': " + e.type + " is not assignable to " + plan.writes.get(name).type);
                return null;
            }
            values.put(name, e);
        }
        generation.inProgress.remove(key);

        String name = "_sjf4j_map_" + generation.nextHelper++;
        generation.helpers.put(key, name);
        target.addHelper(out -> _emitAutoHelper(out, name, from, to, plan, values));
        return name;
    }

    private void _emitAutoHelper(SourceWriter out, String helper, TypeMirror from, TypeMirror to, Plan plan, Map<String, Expr> values) {
        out.line("");
        out.line("private " + to + " " + helper + "(" + from + " value) {");
        out.indent();
        out.line("if (value == null) return null;");
        for (Expr e : values.values()) _emitTemps(out, e);
        if (plan.ctor != null) {
            StringBuilder b = new StringBuilder("return new ").append(to).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            out.line(to + " _target = new " + to + "();");
            for (String n : plan.names) {
                Expr e = values.get(n);
                Write w = plan.writes.get(n);
                if (w.setter != null) out.line("_target." + w.setter.getSimpleName() + "(" + e.code + ");");
                else out.line("_target." + w.javaName + " = " + e.code + ";");
            }
            out.line("return _target;");
        }
        out.dedent();
        out.line("}");
    }

    private Converter _converterFromMethod(TypeElement iface, ExecutableElement method, GeneratedClass target, ExecutableElement h, TypeMirror from, TypeMirror to) {
        if (h.getParameters().size() != 1 || h.getReturnType().getKind() == TypeKind.VOID) {
            _error(method, target, "Converter must have one parameter and a non-void return type");
            return null;
        }
        ExecutableType ht = (ExecutableType) ctx.types.asMemberOf((DeclaredType) iface.asType(), h);
        if (!_assignable(from, ht.getParameterTypes().get(0)) || !_assignable(ht.getReturnType(), to)) {
            _error(method, target, "Converter type mismatch");
            return null;
        }
        String prefix = h.getModifiers().contains(Modifier.STATIC) ? iface.getQualifiedName() + "." : "";
        return new Converter(prefix + h.getSimpleName(), ht.getReturnType());
    }

    private String _implType(ExecutableElement method, GeneratedClass target, ContainerType to) {
        TypeElement declared = GeneratorUtil.asTypeElement(to.mirror);
        if (declared != null && !declared.getModifiers().contains(Modifier.ABSTRACT) && declared.getKind().isClass()) {
            for (ExecutableElement c : ElementFilter.constructorsIn(declared.getEnclosedElements())) {
                if (c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC)) return to.mirror.toString();
            }
        }
        if (to.map) return _optionType(method, "mapType", "java.util.LinkedHashMap");
        TypeMirror set = ctx.elements.getTypeElement("java.util.Set").asType();
        if (ctx.types.isAssignable(ctx.types.erasure(to.mirror), ctx.types.erasure(set))) {
            return _optionType(method, "setType", "java.util.LinkedHashSet");
        }
        return _optionType(method, "listType", "java.util.ArrayList");
    }

    private String _optionType(ExecutableElement method, String name, String def) {
        // Class-valued annotation members can throw in processors; mirror parsing keeps this local and dependency-free.
        for (javax.lang.model.element.AnnotationMirror a : method.getAnnotationMirrors()) {
            if (!a.getAnnotationType().toString().equals(MapperOptions.class.getName())) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> e : a.getElementValues().entrySet()) {
                if (e.getKey().getSimpleName().contentEquals(name)) return e.getValue().getValue().toString();
            }
        }
        return def;
    }

    private String _newContainer(String impl, ContainerType to, String size) {
        if (impl.equals("java.util.ArrayList") || impl.equals("java.util.LinkedHashSet") || impl.equals("java.util.LinkedHashMap")) {
            return "new " + impl + "<>(" + size + ")";
        }
        return "new " + impl + "()";
    }

    private void _emitContainerCopy(SourceWriter out, ContainerType from, ContainerType to, Converter conv, String target, String source) {
        _emitContainerCopy(out, from, to, conv, target, source, ObjectPolicy.PUT);
    }

    private void _emitContainerCopy(SourceWriter out, ContainerType from, ContainerType to, Converter conv, String target, String source, ObjectPolicy objectPolicy) {
        if (to.map) {
            out.line("for (java.util.Map.Entry<" + GeneratorUtil.localTypeName(ctx, from.key) + ", " + GeneratorUtil.localTypeName(ctx, from.value) + "> _entry : " + source + ".entrySet()) {");
            out.indent();
            if (objectPolicy == ObjectPolicy.PUT_IF_ABSENT) {
                out.line("if (!" + target + ".containsKey(_entry.getKey()) || " + target + ".get(_entry.getKey()) == null) {");
                out.indent();
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " _value = " + _convertValue(conv, "_entry.getValue()") + ";");
                out.line(target + ".put(_entry.getKey(), _value);");
                out.dedent();
                out.line("}");
            } else {
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " _value = " + _convertValue(conv, "_entry.getValue()") + ";");
                out.line(target + ".put(_entry.getKey(), _value);");
            }
            out.dedent();
            out.line("}");
        } else {
            out.line("for (" + GeneratorUtil.localTypeName(ctx, from.value) + " _value : " + source + ") {");
            out.indent();
            out.line(target + ".add(" + _convertValue(conv, "_value") + ");");
            out.dedent();
            out.line("}");
        }
    }

    private String _convertValue(Converter conv, String value) {
        return conv.method == null ? value : conv.method + "(" + value + ")";
    }

    private void _emitArrayField(SourceWriter out, TypeElement iface, ExecutableElement method, GeneratedClass target,
                                  String targetName, String name, Write w, Read read, Expr e, String nestedMapper, ArrayPolicy policy) {
        ContainerType from = _container(e.type);
        ContainerType to = _container(w.type);
        if (from == null || to == null || from.map || to.map) {
            out.line("// unsupported array mapping; processor validation should have rejected this");
            return;
        }
        Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        String access = read == null ? targetName + "." + w.javaName : (read.method == null ? targetName + "." + read.javaName : targetName + "." + read.method.getSimpleName() + "()");
        String source = e.code;
        if (!e.local) {
            source = _tempName(name, "source", 0);
            out.line(e.type + " " + source + " = " + e.code + ";");
        }
        out.line("if (" + source + " != null) {");
        out.indent();
        if (read != null && w.setter != null) {
            out.line("if (" + access + " == null) " + targetName + "." + w.setter.getSimpleName() + "(" + _newContainer(_implType(method, target, to), to, source + ".size()") + ");");
        } else if (read != null && read.method == null) {
            out.line("if (" + access + " == null) " + access + " = " + _newContainer(_implType(method, target, to), to, source + ".size()") + ";");
        }
        if (policy == ArrayPolicy.CLEAR_ADD) out.line(access + ".clear();");
        _emitContainerCopy(out, from, to, conv, access, source);
        out.dedent();
        out.line("}");
    }

    private void _emitObjectField(SourceWriter out, TypeElement iface, ExecutableElement method, GeneratedClass target,
                                   String targetName, String name, Write w, Read read, Expr e, String nestedMapper, ObjectPolicy policy) {
        ContainerType from = _container(e.type);
        ContainerType to = _container(w.type);
        if (from == null || to == null || !from.map || !to.map) {
            out.line("// unsupported object mapping; processor validation should have rejected this");
            return;
        }
        Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        String access = read == null ? targetName + "." + w.javaName : (read.method == null ? targetName + "." + read.javaName : targetName + "." + read.method.getSimpleName() + "()");
        String source = e.code;
        if (!e.local) {
            source = _tempName(name, "source", 0);
            out.line(e.type + " " + source + " = " + e.code + ";");
        }
        out.line("if (" + source + " != null) {");
        out.indent();
        if (read != null && w.setter != null) {
            out.line("if (" + access + " == null) " + targetName + "." + w.setter.getSimpleName() + "(" + _newContainer(_implType(method, target, to), to, source + ".size()") + ");");
        } else if (read != null && read.method == null) {
            out.line("if (" + access + " == null) " + access + " = " + _newContainer(_implType(method, target, to), to, source + ".size()") + ";");
        }
        if (policy == ObjectPolicy.CLEAR_PUT) out.line(access + ".clear();");
        _emitContainerCopy(out, from, to, conv, access, source, policy);
        out.dedent();
        out.line("}");
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
        final String javaName;
        final TypeMirror type;

        Read(ExecutableElement m, String n, TypeMirror t) {
            method = m;
            javaName = n;
            type = t;
        }
    }

    private static final class SourceParam {
        final VariableElement element;
        final String name;
        final Map<String, Read> reads;
        final boolean dynamic;

        SourceParam(VariableElement e, Map<String, Read> r, boolean d) {
            element = e;
            name = e.getSimpleName().toString();
            reads = r;
            dynamic = d;
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
        final String javaName;
        final TypeMirror type;

        Write(ExecutableElement s, String n, TypeMirror t) {
            setter = s;
            javaName = n;
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

    private static final class ContainerType {
        final boolean map;
        final TypeMirror key;
        final TypeMirror value;
        final TypeMirror mirror;

        ContainerType(boolean m, TypeMirror k, TypeMirror v, TypeMirror type) {
            map = m;
            key = k;
            value = v;
            mirror = type;
        }
    }

    private static final class Converter {
        final String method;
        final TypeMirror type;

        Converter(String m, TypeMirror t) {
            method = m;
            type = t;
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

    private enum PathWriteMode { STRICT, IF_PARENT_PRESENT, ENSURE }

    private static final class TargetPathWrite {
        final String path;
        final PathSegment[] segments;
        final PathWriteMode mode;

        TargetPathWrite(String p, PathSegment[] s, PathWriteMode m) {
            path = p;
            segments = s;
            mode = m;
        }
    }

    private static final class MappingSpec {
        final String target;
        final String source;
        final String[] sources;
        final String compute;
        final String nestedMapper;

        MappingSpec(String t, String s, String[] ss, String c, String n) {
            target = t;
            source = s;
            sources = ss;
            compute = c;
            nestedMapper = n;
        }
    }

    private static final class GenerationState {
        int nextHelper;
        int nextCodec;
        final Map<String, String> helpers = new HashMap<String, String>();
        final Set<String> inProgress = new HashSet<String>();
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
