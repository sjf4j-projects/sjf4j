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
import org.sjf4j.processor.NameAllocator;
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
        generation = new GenerationState(iface);
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

        MapperModel.ContainerType rootContainer = _container(method.getReturnType());
        if (rootContainer != null) {
            _genContainerCreate(iface, method, target, rootContainer);
            return;
        }

        TypeElement targetType = GeneratorUtil.asTypeElement(method.getReturnType());
        if (targetType == null) {
            _error(method, target, "@CompiledMapper supports only declared source and target types");
            return;
        }

        List<MapperModel.SourceParam> sources = new ArrayList<MapperModel.SourceParam>();
        for (VariableElement source : method.getParameters()) {
            TypeElement sourceType = GeneratorUtil.asTypeElement(source.asType());
            if (sourceType == null) {
                _error(method, target, "@CompiledMapper supports only declared source and target types");
                return;
            }
            boolean dynamic = _dynamicSource(source.asType());
            sources.add(new MapperModel.SourceParam(source, dynamic ? Collections.<String, MapperModel.Read>emptyMap() : _reads(sourceType, source.asType()), dynamic));
        }
        boolean multi = sources.size() > 1;

        Map<String, MapperModel.Write> writes = _writes(targetType, method.getReturnType());
        MapperModel.Plan plan = _creation(method, target, targetType, method.getReturnType(), writes);
        if (plan == null) return;
        MapperOptions cfg = method.getAnnotation(MapperOptions.class);
        NullValuePolicy nulls = cfg == null ? NullValuePolicy.SET_TO_NULL : cfg.nulls();
        if (plan.ctor != null && nulls == NullValuePolicy.IGNORE) {
            _error(method, target, "NullValuePolicy.IGNORE is supported only for mutable no-args create targets and update targets");
            return;
        }

        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        MappingIfParentPresent[] ifParentAnns = method.getAnnotationsByType(MappingIfParentPresent.class);
        EnsureMapping[] ensureAnns = method.getAnnotationsByType(EnsureMapping.class);
        Map<String, MapperModel.Expr> explicit = new HashMap<String, MapperModel.Expr>();
        Map<String, String> nestedMappers = new HashMap<String, String>();
        List<MapperModel.TargetPathWrite> pathWrites = new ArrayList<MapperModel.TargetPathWrite>();
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
                if (!_addPathWrite(method, target, pathWrites, t, MapperModel.PathWriteMode.STRICT)) return;
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
        for (MapperModel.TargetPathWrite w : pathWrites) {
            String rootName = _firstPathName(w);
            if (rootName != null) ignored.add(rootName);
        }

        MethodState state = new MethodState(sources,
                _readCounts(iface, anns, plan, ignored, explicitTargets, sources, multi), _groupParentCounts(anns));

        for (Mapping m : anns) {
            String t = m.target();
            if (_isAutoMarker(m) || m.ignore()) continue;
            if (_isTargetPath(t)) continue;
            if (t.length() == 0) {
                _error(method, target, "@Mapping requires a non-empty target");
                return;
            }

            MapperModel.Expr e;
            if (m.compute().length() != 0) {
                e = _compute(iface, method, target, sources, multi, state, m);
            } else {
                e = _readExprOrGrouped(method, target, sources, multi, state, m.source().length() == 0 ? t : m.source(), t,
                        plan.writes.get(t).type, nestedMappers.get(t) == null ? "" : nestedMappers.get(t), nulls, plan.ctor == null);
            }
            if (e == null) return;
            explicit.put(t, e);
        }

        Map<MapperModel.TargetPathWrite, MapperModel.Expr> pathValues = _pathValues(iface, method, target, sources, multi, state, pathWrites, nestedMappers, method.getReturnType(), false, nulls);
        if (pathValues == null) return;

        // Every writable/constructor target property must be assigned, either
        // explicitly or by same-name auto mapping.  Abstract methods with no
        // @Mapping annotations therefore still generate useful mappers.
        Map<String, MapperModel.Expr> values = new LinkedHashMap<String, MapperModel.Expr>();
        for (String name : plan.names) {
            if (ignored.contains(name)) continue;

            MapperModel.Expr e = explicit.get(name);
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
        if (plan.ctor != null && values.size() != plan.names.size()) {
            _error(method, target, "Constructor and record target properties cannot be ignored");
            return;
        }

        target.addMethod(out -> _emit(out, method, sources, multi, state, plan, values, pathValues, nulls));
    }

    private void _genUpdate(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() < 2) {
            _error(method, target, "@CompiledMapper update method must have a target parameter and at least one source parameter");
            return;
        }

        MapperModel.ContainerType rootContainer = _container(params.get(0).asType());
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

        List<MapperModel.SourceParam> sources = new ArrayList<MapperModel.SourceParam>();
        for (int i = 1; i < params.size(); i++) {
            VariableElement source = params.get(i);
            TypeElement sourceType = GeneratorUtil.asTypeElement(source.asType());
            if (sourceType == null) {
                _error(method, target, "@CompiledMapper supports only declared source and target types");
                return;
            }
            boolean dynamic = _dynamicSource(source.asType());
            sources.add(new MapperModel.SourceParam(source, dynamic ? Collections.<String, MapperModel.Read>emptyMap() : _reads(sourceType, source.asType()), dynamic));
        }
        boolean multi = sources.size() > 1;
        Map<String, MapperModel.Write> writes = _writes(targetType, params.get(0).asType());
        if (writes.isEmpty()) {
            _error(method, target, "Update target type must expose writable public setters or non-final fields");
            return;
        }

        Mapping[] anns = method.getAnnotationsByType(Mapping.class);
        MappingIfParentPresent[] ifParentAnns = method.getAnnotationsByType(MappingIfParentPresent.class);
        EnsureMapping[] ensureAnns = method.getAnnotationsByType(EnsureMapping.class);
        Map<String, MapperModel.Expr> explicit = new HashMap<String, MapperModel.Expr>();
        Map<String, String> nestedMappers = new HashMap<String, String>();
        Map<String, ArrayPolicy> arrayPolicies = new HashMap<String, ArrayPolicy>();
        Map<String, ObjectPolicy> objectPolicies = new HashMap<String, ObjectPolicy>();
        List<MapperModel.TargetPathWrite> pathWrites = new ArrayList<MapperModel.TargetPathWrite>();
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
                    if (!_addPathWrite(method, target, pathWrites, t, MapperModel.PathWriteMode.STRICT)) return;
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

        MapperModel.Plan plan = new MapperModel.Plan(null, new ArrayList<String>(writes.keySet()), writes);
        MethodState state = new MethodState(params.get(0).getSimpleName().toString(), sources,
                _readCounts(iface, anns, plan, ignored, explicitTargets, sources, multi));

        for (Mapping m : anns) {
            String t = m.target();
            if (_isAutoMarker(m) || m.ignore()) continue;
            if (_isTargetPath(t)) continue;

            MapperModel.Expr e;
            if (m.compute().length() != 0) {
                e = _compute(iface, method, target, sources, multi, state, m);
            } else {
                e = _readExpr(method, target, sources, multi, state, m.source().length() == 0 ? t : m.source(), t, null);
            }
            if (e == null) return;
            explicit.put(t, e);
        }

        MapperOptions cfg = method.getAnnotation(MapperOptions.class);
        NullValuePolicy nulls = cfg == null ? NullValuePolicy.SET_TO_NULL : cfg.nulls();
        ArrayPolicy defaultArrayPolicy = cfg == null ? ArrayPolicy.CLEAR_ADD : cfg.arrays();
        ObjectPolicy defaultObjectPolicy = cfg == null ? ObjectPolicy.PUT : cfg.objects();
        Map<MapperModel.TargetPathWrite, MapperModel.Expr> pathValues = _pathValues(iface, method, target, sources, multi, state, pathWrites, nestedMappers, params.get(0).asType(), true, nulls);
        if (pathValues == null) return;
        Map<String, MapperModel.Expr> values = new LinkedHashMap<String, MapperModel.Expr>();
        for (String name : plan.names) {
            if (ignored.contains(name)) continue;

            MapperModel.Expr e = explicit.get(name);
            if (e == null && !explicitTargets.contains(name) && _hasAutoSource(sources, name)) {
                e = _readExpr(method, target, sources, multi, state, name, name, null);
            }
            if (e == null) continue;

            TypeMirror need = plan.writes.get(name).type;
            String nestedMapper = nestedMappers.get(name) == null ? "" : nestedMappers.get(name);
            ArrayPolicy arrayPolicy = arrayPolicies.get(name) == null ? defaultArrayPolicy : arrayPolicies.get(name);
            ObjectPolicy objectPolicy = objectPolicies.get(name) == null ? defaultObjectPolicy : objectPolicies.get(name);
            boolean inPlace = _inPlaceContainer(method, target, e.type, need, arrayPolicy, objectPolicy, nestedMapper);
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

        Map<String, MapperModel.Read> targetReads = _reads(targetType, params.get(0).asType());
        for (String name : plan.names) {
            MapperModel.Expr e = values.get(name);
            if (e == null) continue;
            MapperModel.ContainerType to = _container(plan.writes.get(name).type);
            if (to == null) continue;
            boolean needsReadable = to.map || (!to.map && (arrayPolicies.containsKey(name) || defaultArrayPolicy != ArrayPolicy.SET));
            if (needsReadable && !targetReads.containsKey(name) && plan.writes.get(name).setter != null) {
                _error(method, target, "Cannot update target property '" + name + "' in place: setter-only target has no readable collection/map");
                return;
            }
        }
        target.addMethod(out -> _emitUpdate(out, method, params.get(0), sources, multi, state, plan, values, pathValues, nulls,
                defaultArrayPolicy, defaultObjectPolicy, arrayPolicies, objectPolicies, targetReads, nestedMappers, iface, target));
    }

    private void _genContainerCreate(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.ContainerType to) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "Root collection/map create methods support exactly one source parameter");
            return;
        }
        VariableElement source = method.getParameters().get(0);
        MapperModel.ContainerType from = _container(source.asType());
        if (from == null || from.map != to.map) {
            _error(method, target, "Root collection/map create requires matching source and target container types");
            return;
        }
        String nestedMapper = _methodNestedMapper(method, target);
        if (nestedMapper == null) return;
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        String impl = _implType(method, target, to);
        if (impl == null) return;
        target.addMethod(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve(source.getSimpleName().toString());
            String targetName = names.local("target");
            out.line("");
            out.line("@Override");
            out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            String s = source.getSimpleName().toString();
            out.line("if (" + s + " == null) return null;");
            out.line(_containerLocalType(impl, to, method.getReturnType()) + " " + targetName + " = " + _newContainer(impl, to, s + ".size()") + ";");
            _emitContainerCopy(out, from, to, conv, targetName, s, names);
            out.line("return " + targetName + ";");
            out.dedent();
            out.line("}");
        });
    }

    private void _genContainerUpdate(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.ContainerType to) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() != 2) {
            _error(method, target, "Root collection/map update methods support exactly target and source parameters");
            return;
        }
        VariableElement source = params.get(1);
        MapperModel.ContainerType from = _container(source.asType());
        if (from == null || from.map != to.map) {
            _error(method, target, "Root collection/map update requires matching source and target container types");
            return;
        }
        String nestedMapper = _methodNestedMapper(method, target);
        if (nestedMapper == null) return;
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        MapperOptions cfg = method.getAnnotation(MapperOptions.class);
        ArrayPolicy arrayPolicy = cfg == null ? ArrayPolicy.CLEAR_ADD : cfg.arrays();
        ObjectPolicy objectPolicy = cfg == null ? ObjectPolicy.PUT : cfg.objects();
        if (!to.map && arrayPolicy == ArrayPolicy.SET) {
            _error(method, target, "Root collection update does not support ArrayPolicy.SET; use CLEAR_ADD or ADD");
            return;
        }
        target.addMethod(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve(params.get(0).getSimpleName().toString());
            names.reserve(source.getSimpleName().toString());
            out.line("");
            out.line("@Override");
            out.line("public void " + method.getSimpleName() + "(" + params.get(0).asType() + " " + params.get(0).getSimpleName() + ", " + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            String t = params.get(0).getSimpleName().toString();
            String s = source.getSimpleName().toString();
            out.line("if (" + s + " == null) return;");
            if (to.map) {
                _emitContainerUpdate(out, iface, method, target, from, to, nestedMapper, t, s, arrayPolicy, objectPolicy, names);
            } else {
                if (arrayPolicy == ArrayPolicy.CLEAR_ADD) out.line(t + ".clear();");
                _emitContainerCopy(out, from, to, conv, t, s, names);
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

    private Map<String, Integer> _readCounts(TypeElement iface, Mapping[] anns, MapperModel.Plan plan, Set<String> ignored,
                                             Set<String> explicitTargets, List<MapperModel.SourceParam> sources, boolean multi) {
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

    private boolean _hasAutoSource(List<MapperModel.SourceParam> sources, String name) {
        MapperModel.SourceParam first = sources.get(0);
        return first.dynamic || first.reads.containsKey(name);
    }

    private void _count(Map<String, Integer> counts, String source) {
        Integer n = counts.get(source);
        counts.put(source, n == null ? 1 : n + 1);
    }

    private Map<String, Integer> _groupParentCounts(Mapping[] anns) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Mapping m : anns) {
            if (_isAutoMarker(m) || m.ignore() || m.compute().length() != 0 || _isTargetPath(m.target())) continue;
            String source = m.source().length() == 0 ? m.target() : m.source();
            if (!source.startsWith("$") && !source.startsWith("/")) continue;
            JsonPath path;
            try {
                path = JsonPath.parse(source);
            } catch (RuntimeException e) {
                continue;
            }
            PathSegment[] segments = path.segments();
            if (segments.length < 3) continue;
            boolean namesOnly = true;
            for (int i = 1; i < segments.length; i++) {
                if (!(segments[i] instanceof PathSegment.Name)) {
                    namesOnly = false;
                    break;
                }
            }
            if (!namesOnly) continue;
            for (int end = 1; end < segments.length - 1; end++) {
                _count(counts, _groupParentKey(segments, end));
            }
        }
        return counts;
    }

    private void _emit(SourceWriter out, ExecutableElement method, List<MapperModel.SourceParam> sources, boolean multi, MethodState state,
                       MapperModel.Plan plan, Map<String, MapperModel.Expr> values, Map<MapperModel.TargetPathWrite, MapperModel.Expr> pathValues, NullValuePolicy nulls) {
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
            for (MapperModel.Expr e : values.values()) {
                _emitTemps(out, e);
            }
            StringBuilder b = new StringBuilder("return new ").append(method.getReturnType()).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            String targetVar = state.targetRoot;
            out.line(method.getReturnType() + " " + targetVar + " = new " + method.getReturnType() + "();");
            for (String temp : state.readTemps) out.line(temp);
            _emitGroupedAssigns(out, state, plan, nulls, targetVar);
            for (String name : plan.names) {
                MapperModel.Expr e = values.get(name);
                if (e == null) continue;
                if (state.groupTargets.contains(name)) continue;

                _emitTemps(out, e);
                _emitCreateAssign(out, state, targetVar, name, plan.writes.get(name), e, nulls);
            }
            for (Map.Entry<MapperModel.TargetPathWrite, MapperModel.Expr> entry : pathValues.entrySet()) {
                _emitTemps(out, entry.getValue());
                _emitTargetPath(out, method, state, targetVar, method.getReturnType(), entry.getKey(), entry.getValue(), nulls);
            }
            out.line("return " + targetVar + ";");
        }
        out.dedent();
        out.line("}");
    }

    private void _emitCreateAssign(SourceWriter out, MethodState state, String targetVar, String name, MapperModel.Write w, MapperModel.Expr e, NullValuePolicy nulls) {
        String assign = _targetAssign(targetVar, w, e.code);
        if (nulls == NullValuePolicy.IGNORE && (e.type == null || !e.type.getKind().isPrimitive())) {
            if (e.nullGuardSource != null) {
                String source = e.nullGuardSource;
                if (!e.nullGuardLocal) {
                    source = state.names.prefixed("s", name);
                    out.line(_localTypeName(e.nullGuardType, true) + " " + source + " = " + e.nullGuardSource + ";");
                }
                out.line("if (" + source + " != null) " + _targetAssign(targetVar, w, _nullGuardCode(e, source)));
            } else if (e.local || e.type == null) {
                out.line("if (" + e.code + " != null) " + assign);
            } else {
                String temp = state.names.prefixed("s", name);
                out.line(_localTypeName(e.type, true) + " " + temp + " = " + e.code + ";");
                String tempAssign = _targetAssign(targetVar, w, temp);
                out.line("if (" + temp + " != null) " + tempAssign);
            }
        } else {
            out.line(assign);
        }
    }

    private String _targetAssign(String target, MapperModel.Write w, String value) {
        return w.setter != null
                ? target + "." + w.setter.getSimpleName() + "(" + value + ");"
                : target + "." + w.javaName + " = " + value + ";";
    }

    private void _emitTemps(SourceWriter out, MapperModel.Expr e) {
        for (String t : e.temps) {
            out.line(t);
        }
    }

    private boolean _collectExtraPathWrites(ExecutableElement method, GeneratedClass target,
                                            MappingIfParentPresent[] ifParentAnns, EnsureMapping[] ensureAnns,
                                            List<MapperModel.TargetPathWrite> pathWrites, Map<String, String> nestedMappers,
                                            Set<String> explicitTargets) {
        for (MappingIfParentPresent m : ifParentAnns) {
            if (!_validatePathMapping(method, target, "@MappingIfParentPresent", m.target(), m.sources(), m.compute(), m.nestedMapper(), m.array(), m.object())) return false;
            if (!_addPathWrite(method, target, pathWrites, m.target(), MapperModel.PathWriteMode.IF_PARENT_PRESENT)) return false;
            explicitTargets.add(m.target());
            if (m.nestedMapper().length() != 0) nestedMappers.put(m.target(), m.nestedMapper().trim());
        }
        for (EnsureMapping m : ensureAnns) {
            if (!_validatePathMapping(method, target, "@EnsureMapping", m.target(), m.sources(), m.compute(), m.nestedMapper(), m.array(), m.object())) return false;
            if (!_addPathWrite(method, target, pathWrites, m.target(), MapperModel.PathWriteMode.ENSURE)) return false;
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

    private boolean _addPathWrite(ExecutableElement method, GeneratedClass target, List<MapperModel.TargetPathWrite> writes, String expr, MapperModel.PathWriteMode mode) {
        for (MapperModel.TargetPathWrite w : writes) {
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
            if (mode == MapperModel.PathWriteMode.ENSURE && s instanceof PathSegment.Index) {
                _error(method, target, "@EnsureMapping does not support index-based target path segments");
                return false;
            }
        }
        writes.add(new MapperModel.TargetPathWrite(expr, path.segments(), mode));
        return true;
    }

    private Map<MapperModel.TargetPathWrite, MapperModel.Expr> _pathValues(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                   List<MapperModel.SourceParam> sources, boolean multi, MethodState state,
                                                   List<MapperModel.TargetPathWrite> pathWrites, Map<String, String> nestedMappers,
                                                   TypeMirror rootType, boolean update, NullValuePolicy nulls) {
        Map<MapperModel.TargetPathWrite, MapperModel.Expr> values = new LinkedHashMap<MapperModel.TargetPathWrite, MapperModel.Expr>();
        for (MapperModel.TargetPathWrite w : pathWrites) {
            TypeMirror need = _targetPathValueType(method, target, rootType, w);
            if (need == null) return null;
            MapperModel.Expr e;
            MapperModel.MappingSpec spec = _mappingSpec(method, w.path);
            if (spec == null) return null;
            if (spec.compute.length() != 0) {
                e = _compute(iface, method, target, sources, multi, state, spec);
            } else {
                String src = spec.source.length() == 0 ? _tailName(w) : spec.source;
                e = _readExpr(method, target, sources, multi, state, src, _pathValueTempName(w), null);
            }
            if (e == null) return null;
            e = _maybeNestedExpr(iface, method, target, e, need, nestedMappers.get(w.path) == null ? "" : nestedMappers.get(w.path), w.path);
            if (e == null) return null;
            if (!_assignable(e.type, need)) {
                _error(method, target, "Cannot assign source expression to target path '" + w.path + "': " + e.type + " is not assignable to " + need);
                return null;
            }
            if (nulls == NullValuePolicy.IGNORE && need.getKind().isPrimitive() && e.type == null) {
                _error(method, target, "Cannot map target path '" + w.path + "': NullValuePolicy.IGNORE cannot guard computed expression for primitive target type " + need);
                return null;
            }
            if (need.getKind().isPrimitive() && (e.nullableRoot || e.path)
                    && !(nulls == NullValuePolicy.IGNORE && e.type != null && !e.type.getKind().isPrimitive())) {
                _error(method, target, "Cannot map target path '" + w.path + "': nullable source or path expression cannot be assigned to primitive target type " + need);
                return null;
            }
            values.put(w, e);
        }
        return values;
    }

    private MapperModel.MappingSpec _mappingSpec(ExecutableElement method, String target) {
        for (Mapping m : method.getAnnotationsByType(Mapping.class)) if (m.target().equals(target)) return new MapperModel.MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper());
        for (MappingIfParentPresent m : method.getAnnotationsByType(MappingIfParentPresent.class)) if (m.target().equals(target)) return new MapperModel.MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper());
        for (EnsureMapping m : method.getAnnotationsByType(EnsureMapping.class)) if (m.target().equals(target)) return new MapperModel.MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper());
        return null;
    }

    private String _tailName(MapperModel.TargetPathWrite w) {
        PathSegment s = w.segments[w.segments.length - 1];
        return s instanceof PathSegment.Name ? ((PathSegment.Name) s).name : w.path;
    }

    private String _pathValueTempName(MapperModel.TargetPathWrite w) {
        PathSegment tail = w.segments[w.segments.length - 1];
        if (tail instanceof PathSegment.Name) return ((PathSegment.Name) tail).name;
        if (w.segments.length > 2 && w.segments[w.segments.length - 2] instanceof PathSegment.Name) {
            return ((PathSegment.Name) w.segments[w.segments.length - 2]).name + "Value";
        }
        return "value";
    }

    private String _firstPathName(MapperModel.TargetPathWrite w) {
        return w.segments.length > 1 && w.segments[1] instanceof PathSegment.Name ? ((PathSegment.Name) w.segments[1]).name : null;
    }

    private TypeMirror _targetPathValueType(ExecutableElement method, GeneratedClass target, TypeMirror rootType, MapperModel.TargetPathWrite w) {
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
        MapperModel.Read r = _reads(type, parent).get(name);
        if (r != null) return r.type;
        _error(method, target, "Cannot resolve readable target path property '" + name + "' on " + parent);
        return null;
    }

    private TypeMirror _writeNameType(ExecutableElement method, GeneratedClass target, TypeMirror parent, String name) {
        if (GeneratorUtil.isObject(ctx, parent) || GeneratorUtil.isAssignableErasure(ctx, parent, ctx.jsonObjectType)) return ctx.objectType;
        if (GeneratorUtil.isAssignableErasure(ctx, parent, ctx.mapType)) return GeneratorUtil.mapValueType(ctx, parent);
        TypeElement type = GeneratorUtil.asTypeElement(parent);
        if (type == null) { _error(method, target, "Cannot resolve writable target path property '" + name + "' on " + parent); return null; }
        MapperModel.Write w = _writes(type, parent).get(name);
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

    private void _emitTargetPath(SourceWriter out, ExecutableElement method, MethodState state, String rootVar, TypeMirror rootType, MapperModel.TargetPathWrite w, MapperModel.Expr e, NullValuePolicy nulls) {
        String value = e.code;
        if (nulls == NullValuePolicy.IGNORE && (e.type == null || !e.type.getKind().isPrimitive())) {
            String temp = state.names.prefixed("s", "value");
            if (e.nullGuardSource != null) {
                value = e.nullGuardSource;
                if (!e.nullGuardLocal) {
                    value = temp;
                    out.line(_localTypeName(e.nullGuardType, true) + " " + value + " = " + e.nullGuardSource + ";");
                }
            } else if (!e.local && e.type != null) {
                out.line(_localTypeName(e.type, true) + " " + temp + " = " + e.code + ";");
                value = temp;
            }
            out.line("if (" + value + " != null) {");
            out.indent();
            if (e.nullGuardSource != null) value = _nullGuardCode(e, value);
        }
        String currentVar = rootVar;
        TypeMirror currentType = rootType;
        boolean openSkip = false;
        for (int i = 1; i < w.segments.length - 1; i++) {
            PathSegment s = w.segments[i];
            String nextVar = state.names.prefixed("t", s instanceof PathSegment.Name ? ((PathSegment.Name) s).name : "parent");
            TypeMirror nextType;
            if (s instanceof PathSegment.Name) {
                String name = ((PathSegment.Name) s).name;
                nextType = _readNameType(method, null, currentType, name);
                _emitReadName(out, currentType, currentVar, name, nextVar, nextType);
                if (w.mode == MapperModel.PathWriteMode.ENSURE) _emitEnsure(out, currentType, currentVar, name, nextVar, nextType, w.segments[i + 1]);
            } else {
                int idx = ((PathSegment.Index) s).index;
                nextType = _indexType(method, null, currentType, idx);
                _emitReadIndex(out, currentType, currentVar, idx, nextVar, nextType);
            }
            if (w.mode == MapperModel.PathWriteMode.IF_PARENT_PRESENT) {
                out.line("if (" + nextVar + " != null) {");
                out.indent();
                openSkip = true;
            } else if (w.mode == MapperModel.PathWriteMode.STRICT) {
                out.line("if (" + nextVar + " == null) throw new org.sjf4j.exception.JsonException(\"Missing target path parent: " + GeneratorUtil.escape(w.path) + "\");");
            }
            currentVar = nextVar;
            currentType = nextType;
        }
        _emitFinalWrite(out, currentType, currentVar, w.segments[w.segments.length - 1], value);
        if (openSkip) {
            for (int i = 1; i < w.segments.length - 1; i++) {
                if (w.mode == MapperModel.PathWriteMode.IF_PARENT_PRESENT) { out.dedent(); out.line("}"); }
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
            MapperModel.Read r = _reads(te, parent).get(name);
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
                MapperModel.Write w = _writes(te, parent).get(name);
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

    private void _emitUpdate(SourceWriter out, ExecutableElement method, VariableElement targetParam, List<MapperModel.SourceParam> sources,
                             boolean multi, MethodState state, MapperModel.Plan plan, Map<String, MapperModel.Expr> values, Map<MapperModel.TargetPathWrite, MapperModel.Expr> pathValues, NullValuePolicy nulls,
                             ArrayPolicy defaultArrayPolicy, ObjectPolicy defaultObjectPolicy,
                             Map<String, ArrayPolicy> arrayPolicies, Map<String, ObjectPolicy> objectPolicies, Map<String, MapperModel.Read> targetReads,
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
        for (MapperModel.Expr e : values.values()) _emitTemps(out, e);
        for (MapperModel.Expr e : pathValues.values()) _emitTemps(out, e);

        String targetName = targetParam.getSimpleName().toString();
        for (String name : plan.names) {
            MapperModel.Expr e = values.get(name);
            if (e == null) continue;

            MapperModel.Write w = plan.writes.get(name);
            MapperModel.ContainerType to = _container(w.type);
            if (to != null && to.map) {
                ArrayPolicy arrayPolicy = arrayPolicies.get(name) == null ? defaultArrayPolicy : arrayPolicies.get(name);
                ObjectPolicy objectPolicy = objectPolicies.get(name) == null ? defaultObjectPolicy : objectPolicies.get(name);
                _emitObjectField(out, iface, method, genTarget, state, targetName, name, w, targetReads.get(name), e,
                        nestedMappers.get(name) == null ? "" : nestedMappers.get(name), arrayPolicy, objectPolicy, nulls);
                continue;
            }
            if (to != null && !to.map && (arrayPolicies.containsKey(name) || defaultArrayPolicy != ArrayPolicy.SET)) {
                ArrayPolicy arrayPolicy = arrayPolicies.get(name) == null ? defaultArrayPolicy : arrayPolicies.get(name);
                _emitArrayField(out, iface, method, genTarget, state, targetName, name, w, targetReads.get(name), e,
                        nestedMappers.get(name) == null ? "" : nestedMappers.get(name), arrayPolicy, nulls);
                continue;
            }
            String assign = w.setter != null
                    ? targetName + "." + w.setter.getSimpleName() + "(" + e.code + ");"
                    : targetName + "." + w.javaName + " = " + e.code + ";";
            if (nulls == NullValuePolicy.IGNORE && (e.type == null || !e.type.getKind().isPrimitive())) {
                if (e.nullGuardSource != null) {
                    String source = e.nullGuardSource;
                    if (!e.nullGuardLocal) {
                        source = state.names.prefixed("s", name);
                        out.line(_localTypeName(e.nullGuardType, true) + " " + source + " = " + e.nullGuardSource + ";");
                    }
                    String tempAssign = w.setter != null
                            ? targetName + "." + w.setter.getSimpleName() + "(" + _nullGuardCode(e, source) + ");"
                            : targetName + "." + w.javaName + " = " + _nullGuardCode(e, source) + ";";
                    out.line("if (" + source + " != null) " + tempAssign);
                } else if (e.local) {
                    out.line("if (" + e.code + " != null) " + assign);
                } else if (e.type == null) {
                    out.line("if (" + e.code + " != null) " + assign);
                } else {
                    String temp = state.names.prefixed("s", name);
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
        for (Map.Entry<MapperModel.TargetPathWrite, MapperModel.Expr> entry : pathValues.entrySet()) {
            _emitTargetPath(out, method, state, targetName, targetParam.asType(), entry.getKey(), entry.getValue(), nulls);
        }
        out.dedent();
        out.line("}");
    }

    /**
     * Collects simple source read properties. V1 deliberately stays on public
     * getters, public fields, and record accessors.
     */
    private Map<String, MapperModel.Read> _reads(TypeElement type, TypeMirror owner) {
        Map<String, MapperModel.Read> r = new HashMap<String, MapperModel.Read>();
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC)) continue;

            if (member.getKind() == ElementKind.FIELD) {
                TypeMirror fieldType = ctx.types.asMemberOf((DeclaredType) owner, member);
                String n = member.getSimpleName().toString();
                r.put(_nodeName(member, n), new MapperModel.Read(null, n, fieldType));
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
                    r.put(_nodeName(e, base), new MapperModel.Read(e, base, mt.getReturnType()));
                } else if (n.startsWith("is") && n.length() > 2) {
                    String base = GeneratorUtil.decap(n.substring(2));
                    r.put(_nodeName(e, base), new MapperModel.Read(e, base, mt.getReturnType()));
                } else if (_isRecord(type)) {
                    r.put(_nodeName(e, n), new MapperModel.Read(e, n, mt.getReturnType()));
                }
            }
        }
        return r;
    }

    /**
     * Collects target write properties. Setters intentionally override public
     * fields so user validation or normalization in setters is preserved.
     */
    private Map<String, MapperModel.Write> _writes(TypeElement type, TypeMirror owner) {
        Map<String, MapperModel.Write> w = new LinkedHashMap<String, MapperModel.Write>();
        for (Element member : ctx.elements.getAllMembers(type)) {
            Set<Modifier> m = member.getModifiers();
            if (!m.contains(Modifier.PUBLIC) || m.contains(Modifier.STATIC)) continue;

            if (member.getKind() == ElementKind.FIELD && !m.contains(Modifier.FINAL)) {
                String n = member.getSimpleName().toString();
                String nodeName = _nodeName(member, n);
                if (!w.containsKey(nodeName)) {
                    w.put(nodeName, new MapperModel.Write(null, n, ctx.types.asMemberOf((DeclaredType) owner, member)));
                }
                continue;
            }

            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement e = (ExecutableElement) member;
                if (!e.getSimpleName().toString().startsWith("set") || e.getParameters().size() != 1) continue;

                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, e);
                if (mt.getReturnType().getKind() == TypeKind.VOID) {
                    String name = GeneratorUtil.decap(e.getSimpleName().toString().substring(3));
                    w.put(_nodeName(e, name), new MapperModel.Write(e, name, mt.getParameterTypes().get(0)));
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
    private MapperModel.Plan _creation(ExecutableElement method, GeneratedClass target, TypeElement type, TypeMirror mirror, Map<String, MapperModel.Write> writes) {
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
                return new MapperModel.Plan(null, new ArrayList<String>(writes.keySet()), writes);
            }
        }
        if (ctors.size() != 1) {
            _error(method, target, "Target type must provide a public no-args constructor, be a record, or have exactly one public constructor");
            return null;
        }
        return _ctorPlan(ctors.get(0), mirror);
    }

    private MapperModel.Plan _ctorPlan(ExecutableElement ctor, TypeMirror owner) {
        Map<String, MapperModel.Write> w = new LinkedHashMap<String, MapperModel.Write>();
        ExecutableType ct = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, ctor);
        for (int i = 0; i < ctor.getParameters().size(); i++) {
            VariableElement p = ctor.getParameters().get(i);
            String n = p.getSimpleName().toString();
            w.put(_nodeName(p, n), new MapperModel.Write(null, n, ct.getParameterTypes().get(i)));
        }
        return new MapperModel.Plan(ctor, new ArrayList<String>(w.keySet()), w);
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

    private MapperModel.Expr _readExprOrGrouped(ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources, boolean multi,
                                    MethodState state, String path, String targetName, TypeMirror targetType, String nestedMapper,
                                    NullValuePolicy nulls, boolean allowGrouped) {
        if (allowGrouped) {
            MapperModel.Expr grouped = _tryGroupedReadExpr(sources, multi, state, path, targetName, targetType, nestedMapper);
            if (grouped != null) return grouped;
        }
        if (nulls == NullValuePolicy.SET_TO_NULL) {
            MapperModel.Expr helper = _tryPathHelperExpr(method, target, sources, multi, path, targetName, targetType, nestedMapper);
            if (helper != null) return helper;
        }
        return _readExpr(method, target, sources, multi, state, path, targetName, null);
    }

    private MapperModel.Expr _tryPathHelperExpr(ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources, boolean multi,
                                    String source, String targetName, TypeMirror targetType, String nestedMapper) {
        if (multi || sources.size() != 1 || (!source.startsWith("$") && !source.startsWith("/"))) return null;
        if (nestedMapper != null && nestedMapper.length() != 0) return null;
        MapperModel.SourceParam param = sources.get(0);
        if (param.dynamic) return null;

        JsonPath path;
        try {
            path = JsonPath.parse(source);
        } catch (RuntimeException e) {
            return null;
        }
        PathSegment[] segments = path.segments();
        if (segments.length < 3) return null;
        for (int i = 1; i < segments.length; i++) {
            if (!(segments[i] instanceof PathSegment.Name)) return null;
        }

        TypeMirror[] types = new TypeMirror[segments.length];
        String currentVar = param.name;
        TypeMirror currentType = param.element.asType();
        types[0] = currentType;
        for (int i = 1; i < segments.length; i++) {
            String name = ((PathSegment.Name) segments[i]).name;
            MapperModel.GroupAccess access = _groupNameAccess(currentType, currentVar, name);
            if (access == null) return null;
            types[i] = access.type;
            currentType = access.type;
            currentVar = "s_" + _javaId(name);
        }

        TypeMirror leafType = types[segments.length - 1];
        if (_container(leafType) != null || _container(targetType) != null || !_assignable(leafType, targetType)) return null;
        String key = "pathhelper:" + param.element.asType() + ":" + source + ":" + leafType;
        String helper = generation.helpers.get(key);
        if (helper == null) {
            helper = generation.helperName("Path");
            generation.helpers.put(key, helper);
            String helperName = helper;
            target.addHelper(out -> _emitPathHelper(out, helperName, param, segments, types));
        }
        return new MapperModel.Expr(helper + "(" + param.name + ")", leafType, true, false, false);
    }

    private void _emitPathHelper(SourceWriter out, String helper, MapperModel.SourceParam param, PathSegment[] segments,
                                 TypeMirror[] types) {
        TypeMirror leafType = types[segments.length - 1];
        out.line("");
        out.line("private " + _localTypeName(leafType, true) + " " + helper + "(" + param.element.asType() + " " + param.name + ") {");
        out.indent();
        NameAllocator names = new NameAllocator();
        names.reserve(param.name);
        String currentVar = param.name;
        TypeMirror currentType = param.element.asType();
        for (int i = 1; i < segments.length - 1; i++) {
            String name = ((PathSegment.Name) segments[i]).name;
            MapperModel.GroupAccess access = _groupNameAccess(currentType, currentVar, name);
            String temp = names.prefixed("s", name);
            out.line(_localTypeName(access.type, true) + " " + temp + " = " + access.code + ";");
            if (!access.type.getKind().isPrimitive()) out.line("if (" + temp + " == null) return null;");
            currentType = access.type;
            currentVar = temp;
        }
        MapperModel.GroupAccess leaf = _groupNameAccess(currentType, currentVar, ((PathSegment.Name) segments[segments.length - 1]).name);
        out.line("return " + leaf.code + ";");
        out.dedent();
        out.line("}");
    }

    private MapperModel.Expr _tryGroupedReadExpr(List<MapperModel.SourceParam> sources, boolean multi, MethodState state, String source, String targetName,
                                     TypeMirror targetType, String nestedMapper) {
        if (multi || sources.size() != 1 || (!source.startsWith("$") && !source.startsWith("/"))) return null;
        if (nestedMapper != null && nestedMapper.length() != 0) return null;
        MapperModel.SourceParam param = sources.get(0);
        if (param.dynamic) return null;

        JsonPath path;
        try {
            path = JsonPath.parse(source);
        } catch (RuntimeException e) {
            return null;
        }
        PathSegment[] segments = path.segments();
        if (segments.length < 3) return null;
        for (int i = 1; i < segments.length; i++) {
            if (!(segments[i] instanceof PathSegment.Name)) return null;
        }
        if (!_hasSharedGroupParent(state, segments)) return null;

        int count = segments.length - 1;
        String[] names = new String[count];
        String[] temps = new String[count];
        MapperModel.GroupAccess[] accesses = new MapperModel.GroupAccess[count];
        TypeMirror currentType = param.element.asType();
        String currentTemp = param.name;
        for (int i = 1; i < segments.length; i++) {
            String name = ((PathSegment.Name) segments[i]).name;
            MapperModel.GroupAccess access = _groupNameAccess(currentType, currentTemp, name);
            if (access == null) return null;
            names[i - 1] = name;
            temps[i - 1] = state.names.prefixed("s", name);
            accesses[i - 1] = access;
            currentType = access.type;
            currentTemp = temps[i - 1];
        }

        MapperModel.GroupNode root = state.groupRoot;
        if (root == null) {
            root = new MapperModel.GroupNode(param.name, param.element.asType(), param.name, null);
            state.groupRoot = root;
        }

        MapperModel.GroupNode parent = root;
        for (int i = 0; i < count - 1; i++) {
            String name = names[i];
            MapperModel.GroupAccess access = accesses[i];
            MapperModel.GroupNode child = parent.children.get(name);
            if (child == null) {
                MapperModel.GroupAccess actual = _groupNameAccess(parent.type, parent.temp, name);
                if (actual == null) return null;
                child = new MapperModel.GroupNode(name, access.type, temps[i], actual.code);
                parent.children.put(name, child);
            }
            parent = child;
        }

        MapperModel.GroupAccess leaf = accesses[count - 1];
        if (_container(leaf.type) != null || _container(targetType) != null || !_assignable(leaf.type, targetType)) return null;
        leaf = _groupNameAccess(parent.type, parent.temp, names[count - 1]);
        if (leaf == null) return null;
        String temp = state.names.prefixed("s", targetName);
        parent.leaves.add(new MapperModel.GroupLeaf(targetName, temp, leaf.type, leaf.code));
        state.groupTargets.add(targetName);
        MapperModel.Expr e = new MapperModel.Expr(temp, leaf.type, true, true, true);
        return e;
    }

    private MapperModel.GroupAccess _groupNameAccess(TypeMirror parentType, String parentVar, String name) {
        if (_dynamicSource(parentType) || GeneratorUtil.isAssignableErasure(ctx, parentType, ctx.listType)
                || parentType.getKind() == TypeKind.ARRAY) return null;
        TypeElement type = GeneratorUtil.asTypeElement(parentType);
        if (type == null) return null;
        MapperModel.Read read = _reads(type, parentType).get(name);
        if (read == null) return null;
        String code = read.method == null ? parentVar + "." + read.javaName : parentVar + "." + read.method.getSimpleName() + "()";
        return new MapperModel.GroupAccess(code, read.type);
    }

    private boolean _hasSharedGroupParent(MethodState state, PathSegment[] segments) {
        for (int end = 1; end < segments.length - 1; end++) {
            Integer count = state.groupParentCounts.get(_groupParentKey(segments, end));
            if (count != null && count > 1) return true;
        }
        return false;
    }

    private String _groupParentKey(PathSegment[] segments, int end) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= end; i++) {
            String name = ((PathSegment.Name) segments[i]).name;
            b.append(name.length()).append(':').append(name).append(';');
        }
        return b.toString();
    }

    private void _emitGroupedAssigns(SourceWriter out, MethodState state, MapperModel.Plan plan, NullValuePolicy nulls, String targetVar) {
        if (state.groupRoot == null) return;
        _emitGroupedChildren(out, state.groupRoot, plan, nulls, targetVar);
    }

    private void _emitGroupedChildren(SourceWriter out, MapperModel.GroupNode node, MapperModel.Plan plan, NullValuePolicy nulls, String targetVar) {
        for (MapperModel.GroupLeaf leaf : node.leaves) {
            MapperModel.Write w = plan.writes.get(leaf.target);
            if (nulls == NullValuePolicy.IGNORE && (leaf.type == null || !leaf.type.getKind().isPrimitive())) {
                out.line(_localTypeName(leaf.type, true) + " " + leaf.temp + " = " + leaf.code + ";");
                out.line("if (" + leaf.temp + " != null) " + _targetAssign(targetVar, w, leaf.temp));
            } else {
                out.line(_targetAssign(targetVar, w, leaf.code));
            }
        }
        for (MapperModel.GroupNode child : node.children.values()) {
            out.line(_localTypeName(child.type, true) + " " + child.temp + " = " + child.code + ";");
            if (child.type.getKind().isPrimitive()) {
                _emitGroupedChildren(out, child, plan, nulls, targetVar);
            } else if (nulls == NullValuePolicy.IGNORE) {
                out.line("if (" + child.temp + " != null) {");
                out.indent();
                _emitGroupedChildren(out, child, plan, nulls, targetVar);
                out.dedent();
                out.line("}");
            } else {
                out.line("if (" + child.temp + " == null) {");
                out.indent();
                _emitGroupedNulls(out, child, plan, targetVar);
                out.dedent();
                out.line("} else {");
                out.indent();
                _emitGroupedChildren(out, child, plan, nulls, targetVar);
                out.dedent();
                out.line("}");
            }
        }
    }

    private void _emitGroupedNulls(SourceWriter out, MapperModel.GroupNode node, MapperModel.Plan plan, String targetVar) {
        for (MapperModel.GroupLeaf leaf : node.leaves) {
            out.line(_targetAssign(targetVar, plan.writes.get(leaf.target), "null"));
        }
        for (MapperModel.GroupNode child : node.children.values()) {
            _emitGroupedNulls(out, child, plan, targetVar);
        }
    }

    private MapperModel.Expr _readExpr(ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources, boolean multi, MethodState state,
                           String path, String targetName, String preferredTemp) {
        MapperModel.ResolvedSource resolved = _resolveSource(method, target, sources, multi, path);
        if (resolved == null) return null;

        String key = resolved.param.name + ":" + resolved.path + ":" + resolved.nullableRoot;
        MapperModel.CachedRead cached = state.cache.get(key);
        if (cached != null) return new MapperModel.Expr(cached.code, cached.type, cached.path, cached.nullableRoot, true);

        PathAccessEmitter.ReadAccess r = resolved.nullableRoot
                ? pathAccess.readNullableRoot(method, target, resolved.param.element.asType(), resolved.param.name,
                resolved.path, state.names, state.pathCache, _pathCacheRoot(resolved))
                : pathAccess.read(method, target, resolved.param.element.asType(), resolved.param.name,
                resolved.path, state.names, state.pathCache, _pathCacheRoot(resolved));
        if (r == null) return null;
        MapperModel.Expr e = new MapperModel.Expr(r.code, r.type, r.path, resolved.nullableRoot, false);
        if (r.path) {
            if (r.leafExpr != null && _readCount(state, path) <= 1) {
                // inline leaf expression, suppress the leaf temp declaration
                for (int i = 0, n = r.temps.size() - 1; i < n; i++) {
                    state.readTemps.add(r.temps.get(i));
                }
                e.code = r.leafExpr;
            } else {
                state.readTemps.addAll(r.temps);
                e.local = true;
            }
            state.cache.put(key, new MapperModel.CachedRead(r.code, r.type, true, resolved.nullableRoot));
        } else if (_readCount(state, path) > 1) {
            String temp = preferredTemp == null ? state.names.prefixed("s", targetName) : preferredTemp;
            state.readTemps.add(_localTypeName(r.type, resolved.nullableRoot) + " " + temp + " = " + r.code + ";");
            e.code = temp;
            e.local = true;
            state.cache.put(key, new MapperModel.CachedRead(temp, r.type, false, resolved.nullableRoot));
        } else {
            e.temps.addAll(r.temps);
        }
        return e;
    }

    private MapperModel.ResolvedSource _resolveSource(ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources,
                                          boolean multi, String source) {
        if (!multi) return new MapperModel.ResolvedSource(sources.get(0), source, false);

        int colon = source.indexOf(':');
        if (colon > 0) {
            String left = source.substring(0, colon);
            String right = source.substring(colon + 1);
            MapperModel.SourceParam p = _sourceByName(sources, left);
            if (p != null && right.length() != 0) {
                return new MapperModel.ResolvedSource(p, right, true);
            }
            if (p != null) {
                _error(method, target, "Invalid multi-source mapping '" + source + "': expected a property, JSONPath, or JSON Pointer after ':'");
                return null;
            }
        }

        MapperModel.SourceParam first = sources.get(0);
        if (!first.dynamic && !first.reads.containsKey(source) && !source.startsWith("$") && !source.startsWith("/")) {
            _error(method, target, "Cannot resolve source '" + source + "' on first source parameter '" + first.name + "'");
            return null;
        }
        return new MapperModel.ResolvedSource(first, source, true);
    }

    private MapperModel.SourceParam _sourceByName(List<MapperModel.SourceParam> sources, String name) {
        for (MapperModel.SourceParam p : sources) if (p.name.equals(name)) return p;
        return null;
    }

    private int _readCount(MethodState state, String path) {
        Integer n = state.readCounts.get(path);
        return n == null ? 0 : n;
    }

    private String _pathCacheRoot(MapperModel.ResolvedSource resolved) {
        return resolved.param.name + ':' + resolved.nullableRoot + ':';
    }

    /**
     * Resolves V1 compute forms: an inline lambda-like expression or a local
     * {@code this::helper} method. Inline bodies are emitted as Java expressions,
     * not as runtime lambda objects.
     */
    private MapperModel.Expr _compute(TypeElement iface, ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources,
                          boolean multi, MethodState state, Mapping m) {
        return _compute(iface, method, target, sources, multi, state,
                new MapperModel.MappingSpec(m.target(), m.source(), m.sources(), m.compute(), m.nestedMapper()));
    }

    private MapperModel.Expr _compute(TypeElement iface, ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources,
                          boolean multi, MethodState state, MapperModel.MappingSpec m) {
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
        MapperModel.Expr e = new MapperModel.Expr(body, null);
        for (int i = 0; i < params.length; i++) {
            String temp = state.names.prefixed("s", params[i]);
            MapperModel.Expr v = _readExpr(method, target, sources, multi, state, paths[i], m.target + i, temp);
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

    private MapperModel.Expr _helper(TypeElement iface, ExecutableElement method, GeneratedClass target, List<MapperModel.SourceParam> sources,
                         boolean multi, MethodState state, MapperModel.MappingSpec m, String name) {
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

            MapperModel.Expr result = new MapperModel.Expr(null, ht.getReturnType());
            StringBuilder call = new StringBuilder(h.getModifiers().contains(Modifier.STATIC)
                    ? iface.getQualifiedName() + "." + name + "("
                    : name + "(");
            for (int i = 0; i < paths.length; i++) {
                if (i != 0) call.append(", ");

                String preferred = state.names.prefixed("s", h.getParameters().get(i).getSimpleName().toString());
                MapperModel.Expr v = _readExpr(method, target, sources, multi, state, paths[i], m.target + i, preferred);
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

    private MapperModel.ContainerType _container(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return null;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        if (e == null) return null;
        TypeMirror erased = ctx.types.erasure(type);
        TypeMirror coll = ctx.elements.getTypeElement("java.util.Collection").asType();
        TypeMirror map = ctx.elements.getTypeElement("java.util.Map").asType();
        if (ctx.types.isAssignable(erased, ctx.types.erasure(map))) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() != 2) return new MapperModel.ContainerType(true, null, null, type);
            return new MapperModel.ContainerType(true, args.get(0), args.get(1), type);
        }
        if (ctx.types.isAssignable(erased, ctx.types.erasure(coll))) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() != 1) return new MapperModel.ContainerType(false, null, null, type);
            return new MapperModel.ContainerType(false, null, args.get(0), type);
        }
        return null;
    }

    private boolean _inPlaceContainer(ExecutableElement method, GeneratedClass target, TypeMirror fromType, TypeMirror toType,
                                      ArrayPolicy arrayPolicy, ObjectPolicy objectPolicy, String nestedMapper) {
        MapperModel.ContainerType from = _container(fromType);
        MapperModel.ContainerType to = _container(toType);
        if (from == null || to == null || from.map != to.map) return false;
        if (to.map) return true;
        return arrayPolicy != null && arrayPolicy != ArrayPolicy.SET;
    }

    private boolean _validateContainerMapping(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                              TypeMirror fromType, TypeMirror toType, String nestedMapper) {
        MapperModel.ContainerType from = _container(fromType);
        MapperModel.ContainerType to = _container(toType);
        if (from == null || to == null || from.map != to.map) return false;
        return _containerConverter(iface, method, target, from, to, nestedMapper) != null;
    }

    private MapperModel.Converter _containerConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.ContainerType from, MapperModel.ContainerType to, String nestedMapper) {
        if (from.value == null || to.value == null || (from.map && (from.key == null || to.key == null))) {
            _error(method, target, "Raw or non-parameterized collection/map types are unsupported");
            return null;
        }
        if (from.map && !_assignable(from.key, to.key)) {
            _error(method, target, "Map key type mismatch: " + from.key + " is not assignable to " + to.key);
            return null;
        }
        if ((nestedMapper == null || nestedMapper.length() == 0) && _assignable(from.value, to.value)) return new MapperModel.Converter(null, to.value);
        MapperModel.ContainerType nestedFrom = _container(from.value);
        MapperModel.ContainerType nestedTo = _container(to.value);
        if (nestedFrom != null && nestedTo != null && nestedFrom.map == nestedTo.map) {
            MapperModel.Converter nestedConv = _containerConverter(iface, method, target, nestedFrom, nestedTo, nestedMapper);
            if (nestedConv == null) return null;
            String impl = _implType(method, target, nestedTo);
            if (impl == null) return null;
            String helper = _ensureContainerHelper(target, nestedFrom, nestedTo, nestedConv, impl, to.value);
            return new MapperModel.Converter(helper, to.value);
        }
        return _resolveConverter(iface, method, target, from.value, to.value, nestedMapper);
    }

    private MapperModel.Expr _maybeNestedExpr(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.Expr e, TypeMirror need, String nestedMapper, String name) {
        MapperModel.ContainerType from = _container(e.type);
        MapperModel.ContainerType to = _container(need);
        if (from == null || to == null || from.map != to.map) {
            boolean named = nestedMapper != null && nestedMapper.length() != 0;
            if (!named && _assignable(e.type, need)) return e;
            MapperModel.Converter conv = _resolveConverter(iface, method, target, e.type, need, nestedMapper, named);
            if (conv == null) return named ? null : e;
            MapperModel.Expr r = new MapperModel.Expr(_convertValue(conv, e.code), conv.type, e.path, e.nullableRoot, false);
            r.temps.addAll(e.temps);
            return r;
        }
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return null;
        String impl = _implType(method, target, to);
        if (impl == null) return null;
        String helper = _ensureContainerHelper(target, from, to, conv, impl, need);
        MapperModel.Expr r = new MapperModel.Expr(helper + "(" + e.code + ")", need, e.path, e.nullableRoot, false);
        r.temps.addAll(e.temps);
        r.nullGuardSource = e.code;
        r.nullGuardType = e.type;
        r.nullGuardLocal = e.local;
        r.nullGuardCodeTemplate = helper + "($source)";
        return r;
    }

    private String _nullGuardCode(MapperModel.Expr e, String source) {
        return e.nullGuardCodeTemplate.replace("$source", source);
    }

    private String _ensureContainerHelper(GeneratedClass target, MapperModel.ContainerType from, MapperModel.ContainerType to, MapperModel.Converter conv,
                                          String impl, TypeMirror resultType) {
        String key = "container:" + from.mirror + "->" + to.mirror + ":" + impl + ":" + (conv.method == null ? "" : conv.method);
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String helper = generation.helperName("Container");
        generation.helpers.put(key, helper);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            out.line("");
            out.line("private " + resultType + " " + helper + "(" + from.mirror + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line(_containerLocalType(impl, to, resultType) + " " + targetVar + " = " + _newContainer(impl, to, "source.size()") + ";");
        if (to.map) {
                String entry = names.prefixed("s", "entry");
                out.line("for (java.util.Map.Entry<" + GeneratorUtil.localTypeName(ctx, from.key) + ", " + GeneratorUtil.localTypeName(ctx, from.value) + "> " + entry + " : source.entrySet()) {");
                out.indent();
                out.line(targetVar + ".put(" + entry + ".getKey(), " + _convertValue(conv, entry + ".getValue()") + ");");
                out.dedent();
                out.line("}");
        } else {
                String value = names.prefixed("s", "value");
                out.line("for (" + GeneratorUtil.localTypeName(ctx, from.value) + " " + value + " : source) {");
                out.indent();
                out.line(targetVar + ".add(" + _convertValue(conv, value) + ");");
                out.dedent();
                out.line("}");
        }
            out.line("return " + targetVar + ";");
            out.dedent();
            out.line("}");
        });
        return helper;
    }

    private String _containerUpdateHelper(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                          TypeMirror fromType, TypeMirror toType, String nestedMapper,
                                          ArrayPolicy arrayPolicy, ObjectPolicy objectPolicy) {
        MapperModel.ContainerType from = _container(fromType);
        MapperModel.ContainerType to = _container(toType);
        if (from == null || to == null || from.map != to.map) return null;
        if (!to.map && arrayPolicy == ArrayPolicy.SET) return null;
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return null;
        String nestedUpdate = null;
        if (to.map && objectPolicy == ObjectPolicy.PUT) {
            nestedUpdate = _containerUpdateHelper(iface, method, target, from.value, to.value, nestedMapper, arrayPolicy, objectPolicy);
        }
        return _ensureContainerUpdateHelper(target, from, to, conv, nestedUpdate, arrayPolicy, objectPolicy);
    }

    private String _ensureContainerUpdateHelper(GeneratedClass target, MapperModel.ContainerType from, MapperModel.ContainerType to, MapperModel.Converter conv,
                                                String nestedUpdateHelper, ArrayPolicy arrayPolicy, ObjectPolicy objectPolicy) {
        String key = "containerUpdate:" + from.mirror + "->" + to.mirror + ":" + arrayPolicy + ":" + objectPolicy + ":"
                + (conv.method == null ? "" : conv.method) + ":" + (nestedUpdateHelper == null ? "" : nestedUpdateHelper);
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String helper = generation.helperName("ContainerUpdate");
        generation.helpers.put(key, helper);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("target");
            names.reserve("source");
            out.line("");
            out.line("private void " + helper + "(" + to.mirror + " target, " + from.mirror + " source) {");
            out.indent();
            out.line("if (source == null) return;");
            if (to.map) {
                if (objectPolicy == ObjectPolicy.CLEAR_PUT) out.line("target.clear();");
                String entry = names.prefixed("s", "entry");
                out.line("for (java.util.Map.Entry<" + GeneratorUtil.localTypeName(ctx, from.key) + ", " + GeneratorUtil.localTypeName(ctx, from.value) + "> " + entry + " : source.entrySet()) {");
                out.indent();
                if (objectPolicy == ObjectPolicy.PUT_IF_ABSENT) {
                    out.line("if (!target.containsKey(" + entry + ".getKey()) || target.get(" + entry + ".getKey()) == null) {");
                    out.indent();
                    String value = names.prefixed("s", "value");
                    out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                    out.line("target.put(" + entry + ".getKey(), " + value + ");");
                    out.dedent();
                    out.line("}");
                } else if (nestedUpdateHelper != null) {
                    String existingValue = names.prefixed("t", "value");
                    out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + existingValue + " = target.get(" + entry + ".getKey());");
                    out.line("if (" + existingValue + " != null && " + entry + ".getValue() != null) {");
                    out.indent();
                    out.line(nestedUpdateHelper + "(" + existingValue + ", " + entry + ".getValue());");
                    out.dedent();
                    out.line("} else {");
                    out.indent();
                    String value = names.prefixed("s", "value");
                    out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                    out.line("target.put(" + entry + ".getKey(), " + value + ");");
                    out.dedent();
                    out.line("}");
                } else {
                    String value = names.prefixed("s", "value");
                    out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                    out.line("target.put(" + entry + ".getKey(), " + value + ");");
                }
                out.dedent();
                out.line("}");
            } else {
                if (arrayPolicy == ArrayPolicy.CLEAR_ADD) out.line("target.clear();");
                String value = names.prefixed("s", "value");
                out.line("for (" + GeneratorUtil.localTypeName(ctx, from.value) + " " + value + " : source) {");
                out.indent();
                out.line("target.add(" + _convertValue(conv, value) + ");");
                out.dedent();
                out.line("}");
            }
            out.dedent();
            out.line("}");
        });
        return helper;
    }

    private MapperModel.Converter _resolveConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String nestedMapper) {
        return _resolveConverter(iface, method, target, from, to, nestedMapper, true);
    }

    private MapperModel.Converter _resolveConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String nestedMapper, boolean errorIfMissing) {
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
            MapperModel.Converter fallback = _enumConverter(method, target, from, to);
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

    private MapperModel.Converter _enumConverter(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        if (targetType == null || targetType.getKind() != ElementKind.ENUM) return null;
        TypeElement sourceType = GeneratorUtil.asTypeElement(from);
        if (sourceType == null) return null;
        String sourceName = sourceType.getQualifiedName().toString();
        if ("java.lang.String".equals(sourceName)) {
            String helper = _ensureEnumHelper(method, target, from, to, "string");
            return helper == null ? null : new MapperModel.Converter(helper, to);
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
            return helper == null ? null : new MapperModel.Converter(helper, to);
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

    private MapperModel.Converter _nodeValueConverter(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        boolean fromValue = _isNodeValue(from);
        boolean toValue = _isNodeValue(to);
        if (!fromValue && !toValue) return null;
        if (fromValue && toValue) {
            String helper = _ensureNodeValueHelper(method, target, from, to, "value_value");
            return helper == null ? null : new MapperModel.Converter(helper, to);
        }
        if (fromValue) {
            String helper = _ensureNodeValueHelper(method, target, from, to, "value_raw");
            return helper == null ? null : new MapperModel.Converter(helper, to);
        }
        String helper = _ensureNodeValueHelper(method, target, from, to, "raw_value");
        return helper == null ? null : new MapperModel.Converter(helper, to);
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
        String name = generation.helperName(_upperCamel(kind));
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String raw = names.prefixed("s", "raw");
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " source) {");
            out.indent();
            if (!from.getKind().isPrimitive() && !to.getKind().isPrimitive()) out.line("if (source == null) return null;");
            if ("value_raw".equals(kind)) {
                out.line("return (" + GeneratorUtil.localTypeName(ctx, to) + ") " + fromCodec + ".valueToRaw(source);");
            } else if ("raw_value".equals(kind)) {
                out.line("return (" + GeneratorUtil.localTypeName(ctx, to) + ") " + toCodec + ".rawToValue(source);");
            } else {
                out.line("Object " + raw + " = " + fromCodec + ".valueToRaw(source);");
                out.line("return (" + GeneratorUtil.localTypeName(ctx, to) + ") " + toCodec + ".rawToValue(" + raw + ");");
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
        String name = generation.helperName("string".equals(kind) ? "String" : "Enum");
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            if ("string".equals(kind)) {
                out.line("return " + to + ".valueOf(source);");
            } else {
                out.line("return " + to + ".valueOf(source.name());");
            }
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private MapperModel.Converter _autoHelperConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        if (_assignable(from, to)) return new MapperModel.Converter(null, to);
        TypeElement sourceType = GeneratorUtil.asTypeElement(from);
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        if (sourceType == null || targetType == null) return null;
        if (sourceType.getKind() == ElementKind.ENUM || targetType.getKind() == ElementKind.ENUM) return null;
        String helper = _ensureAutoHelper(iface, method, target, from, to, sourceType, targetType);
        return helper == null ? null : new MapperModel.Converter(helper, to);
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
        Map<String, MapperModel.Read> reads = _reads(sourceType, from);
        Map<String, MapperModel.Write> writes = _writes(targetType, to);
        MapperModel.Plan plan = _creation(method, target, targetType, to, writes);
        if (plan == null) return null;

        generation.inProgress.add(key);
        Map<String, MapperModel.Expr> values = new LinkedHashMap<String, MapperModel.Expr>();
        for (String name : plan.names) {
            MapperModel.Read r = reads.get(name);
            if (r == null) {
                generation.inProgress.remove(key);
                _error(method, target, "Cannot auto-map nested target property '" + name + "' from " + from + " to " + to + ": no same-name source property was found");
                return null;
            }
            String access = r.method == null ? "source." + r.javaName : "source." + r.method.getSimpleName() + "()";
            MapperModel.Expr e = new MapperModel.Expr(access, r.type);
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

        String name = generation.helperName("Auto");
        generation.helpers.put(key, name);
        target.addHelper(out -> _emitAutoHelper(out, name, from, to, plan, values));
        return name;
    }

    private void _emitAutoHelper(SourceWriter out, String helper, TypeMirror from, TypeMirror to, MapperModel.Plan plan, Map<String, MapperModel.Expr> values) {
        NameAllocator names = new NameAllocator();
        names.reserve("source");
        String targetVar = names.local("target");
        out.line("");
        out.line("private " + to + " " + helper + "(" + from + " source) {");
        out.indent();
        out.line("if (source == null) return null;");
        for (MapperModel.Expr e : values.values()) _emitTemps(out, e);
        if (plan.ctor != null) {
            StringBuilder b = new StringBuilder("return new ").append(to).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            out.line(to + " " + targetVar + " = new " + to + "();");
            for (String n : plan.names) {
                MapperModel.Expr e = values.get(n);
                MapperModel.Write w = plan.writes.get(n);
                if (w.setter != null) out.line(targetVar + "." + w.setter.getSimpleName() + "(" + e.code + ");");
                else out.line(targetVar + "." + w.javaName + " = " + e.code + ";");
            }
            out.line("return " + targetVar + ";");
        }
        out.dedent();
        out.line("}");
    }

    private MapperModel.Converter _converterFromMethod(TypeElement iface, ExecutableElement method, GeneratedClass target, ExecutableElement h, TypeMirror from, TypeMirror to) {
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
        return new MapperModel.Converter(prefix + h.getSimpleName(), ht.getReturnType());
    }

    private String _implType(ExecutableElement method, GeneratedClass target, MapperModel.ContainerType to) {
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

    private String _newContainer(String impl, MapperModel.ContainerType to, String size) {
        if (impl.equals("java.util.ArrayList") || impl.equals("java.util.LinkedHashSet") || impl.equals("java.util.LinkedHashMap")) {
            return "new " + impl + "<>(" + size + ")";
        }
        return "new " + impl + "()";
    }

    private String _containerLocalType(String impl, MapperModel.ContainerType to, TypeMirror fallback) {
        if (impl.equals("java.util.ArrayList")) {
            return "java.util.ArrayList<" + GeneratorUtil.localTypeName(ctx, to.value) + ">";
        }
        if (impl.equals("java.util.LinkedHashSet")) {
            return "java.util.LinkedHashSet<" + GeneratorUtil.localTypeName(ctx, to.value) + ">";
        }
        if (impl.equals("java.util.LinkedHashMap")) {
            return "java.util.LinkedHashMap<" + GeneratorUtil.localTypeName(ctx, to.key) + ", " + GeneratorUtil.localTypeName(ctx, to.value) + ">";
        }
        return fallback.toString();
    }

    private void _emitContainerCopy(SourceWriter out, MapperModel.ContainerType from, MapperModel.ContainerType to, MapperModel.Converter conv, String target, String source) {
        NameAllocator names = new NameAllocator();
        names.reserve(target);
        names.reserve(source);
        _emitContainerCopy(out, from, to, conv, target, source, ObjectPolicy.PUT, names);
    }

    private void _emitContainerCopy(SourceWriter out, MapperModel.ContainerType from, MapperModel.ContainerType to, MapperModel.Converter conv, String target, String source, NameAllocator names) {
        _emitContainerCopy(out, from, to, conv, target, source, ObjectPolicy.PUT, names);
    }

    private void _emitContainerCopy(SourceWriter out, MapperModel.ContainerType from, MapperModel.ContainerType to, MapperModel.Converter conv, String target, String source, ObjectPolicy objectPolicy) {
        NameAllocator names = new NameAllocator();
        names.reserve(target);
        names.reserve(source);
        _emitContainerCopy(out, from, to, conv, target, source, objectPolicy, names);
    }

    private void _emitContainerCopy(SourceWriter out, MapperModel.ContainerType from, MapperModel.ContainerType to, MapperModel.Converter conv, String target, String source, ObjectPolicy objectPolicy, NameAllocator names) {
        if (to.map) {
            String entry = names.prefixed("s", "entry");
            out.line("for (java.util.Map.Entry<" + GeneratorUtil.localTypeName(ctx, from.key) + ", " + GeneratorUtil.localTypeName(ctx, from.value) + "> " + entry + " : " + source + ".entrySet()) {");
            out.indent();
            if (objectPolicy == ObjectPolicy.PUT_IF_ABSENT) {
                out.line("if (!" + target + ".containsKey(" + entry + ".getKey()) || " + target + ".get(" + entry + ".getKey()) == null) {");
                out.indent();
                String value = names.prefixed("s", "value");
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                out.line(target + ".put(" + entry + ".getKey(), " + value + ");");
                out.dedent();
                out.line("}");
            } else {
                String value = names.prefixed("s", "value");
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                out.line(target + ".put(" + entry + ".getKey(), " + value + ");");
            }
            out.dedent();
            out.line("}");
        } else {
            String value = names.prefixed("s", "value");
            out.line("for (" + GeneratorUtil.localTypeName(ctx, from.value) + " " + value + " : " + source + ") {");
            out.indent();
            out.line(target + ".add(" + _convertValue(conv, value) + ");");
            out.dedent();
            out.line("}");
        }
    }

    private void _emitContainerUpdate(SourceWriter out, TypeElement iface, ExecutableElement method, GeneratedClass targetClass,
                                      MapperModel.ContainerType from, MapperModel.ContainerType to, String nestedMapper,
                                      String target, String source, ArrayPolicy arrayPolicy, ObjectPolicy objectPolicy, NameAllocator names) {
        MapperModel.Converter conv = _containerConverter(iface, method, targetClass, from, to, nestedMapper);
        if (conv == null) return;
        String updateHelper = null;
        if (to.map && objectPolicy == ObjectPolicy.PUT) {
            updateHelper = _containerUpdateHelper(iface, method, targetClass, from.value, to.value, nestedMapper, arrayPolicy, objectPolicy);
        }
        if (to.map) {
            if (objectPolicy == ObjectPolicy.CLEAR_PUT) out.line(target + ".clear();");
            String entry = names.prefixed("s", "entry");
            out.line("for (java.util.Map.Entry<" + GeneratorUtil.localTypeName(ctx, from.key) + ", " + GeneratorUtil.localTypeName(ctx, from.value) + "> " + entry + " : " + source + ".entrySet()) {");
            out.indent();
            if (objectPolicy == ObjectPolicy.PUT_IF_ABSENT) {
                out.line("if (!" + target + ".containsKey(" + entry + ".getKey()) || " + target + ".get(" + entry + ".getKey()) == null) {");
                out.indent();
                String value = names.prefixed("s", "value");
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                out.line(target + ".put(" + entry + ".getKey(), " + value + ");");
                out.dedent();
                out.line("}");
            } else if (updateHelper != null) {
                String existing = names.prefixed("t", "value");
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + existing + " = " + target + ".get(" + entry + ".getKey());");
                out.line("if (" + existing + " != null && " + entry + ".getValue() != null) {");
                out.indent();
                out.line(updateHelper + "(" + existing + ", " + entry + ".getValue());");
                out.dedent();
                out.line("} else {");
                out.indent();
                String value = names.prefixed("s", "value");
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                out.line(target + ".put(" + entry + ".getKey(), " + value + ");");
                out.dedent();
                out.line("}");
            } else {
                String value = names.prefixed("s", "value");
                out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(conv, entry + ".getValue()") + ";");
                out.line(target + ".put(" + entry + ".getKey(), " + value + ");");
            }
            out.dedent();
            out.line("}");
            return;
        }
        if (arrayPolicy == ArrayPolicy.CLEAR_ADD) out.line(target + ".clear();");
        _emitContainerCopy(out, from, to, conv, target, source, names);
    }

    private String _convertValue(MapperModel.Converter conv, String value) {
        return conv.method == null ? value : conv.method + "(" + value + ")";
    }

    private void _emitArrayField(SourceWriter out, TypeElement iface, ExecutableElement method, GeneratedClass target,
                                  MethodState state, String targetName, String name, MapperModel.Write w, MapperModel.Read read, MapperModel.Expr e, String nestedMapper,
                                  ArrayPolicy policy, NullValuePolicy nulls) {
        MapperModel.ContainerType from = _container(e.type);
        MapperModel.ContainerType to = _container(w.type);
        if (from == null || to == null || from.map || to.map) {
            out.line("// unsupported array mapping; processor validation should have rejected this");
            return;
        }
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        String access = read == null ? targetName + "." + w.javaName : (read.method == null ? targetName + "." + read.javaName : targetName + "." + read.method.getSimpleName() + "()");
        String source = e.code;
        if (!e.local) {
            source = state.names.prefixed("s", name);
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
        _emitContainerCopy(out, from, to, conv, access, source, state.names);
        out.dedent();
        if (nulls == NullValuePolicy.SET_TO_NULL) {
            out.line("} else {");
            out.indent();
            out.line(w.setter != null
                    ? targetName + "." + w.setter.getSimpleName() + "(null);"
                    : targetName + "." + w.javaName + " = null;");
            out.dedent();
        }
        out.line("}");
    }

    private void _emitObjectField(SourceWriter out, TypeElement iface, ExecutableElement method, GeneratedClass target,
                                    MethodState state, String targetName, String name, MapperModel.Write w, MapperModel.Read read, MapperModel.Expr e, String nestedMapper,
                                    ArrayPolicy arrayPolicy, ObjectPolicy policy, NullValuePolicy nulls) {
        MapperModel.ContainerType from = _container(e.type);
        MapperModel.ContainerType to = _container(w.type);
        if (from == null || to == null || !from.map || !to.map) {
            out.line("// unsupported object mapping; processor validation should have rejected this");
            return;
        }
        String access = read == null ? targetName + "." + w.javaName : (read.method == null ? targetName + "." + read.javaName : targetName + "." + read.method.getSimpleName() + "()");
        String source = e.code;
        if (!e.local) {
            source = state.names.prefixed("s", name);
            out.line(e.type + " " + source + " = " + e.code + ";");
        }
        out.line("if (" + source + " != null) {");
        out.indent();
        if (read != null && w.setter != null) {
            out.line("if (" + access + " == null) " + targetName + "." + w.setter.getSimpleName() + "(" + _newContainer(_implType(method, target, to), to, source + ".size()") + ");");
        } else if (read != null && read.method == null) {
            out.line("if (" + access + " == null) " + access + " = " + _newContainer(_implType(method, target, to), to, source + ".size()") + ";");
        }
        _emitContainerUpdate(out, iface, method, target, from, to, nestedMapper, access, source, arrayPolicy, policy, state.names);
        out.dedent();
        if (nulls == NullValuePolicy.SET_TO_NULL) {
            out.line("} else {");
            out.indent();
            out.line(w.setter != null
                    ? targetName + "." + w.setter.getSimpleName() + "(null);"
                    : targetName + "." + w.javaName + " = null;");
            out.dedent();
        }
        out.line("}");
    }

    private String _localTypeName(TypeMirror type, boolean boxPrimitive) {
        if (boxPrimitive) return GeneratorUtil.localTypeName(ctx, type);
        if (type.getKind().isPrimitive()) return type.toString();
        return GeneratorUtil.typeName(GeneratorUtil.concrete(ctx, type));
    }

    private boolean _assignable(TypeMirror from, TypeMirror to) {
        if (from == null) return true;
        return GeneratorUtil.isAssignableBoxedGeneric(ctx, from, to);
    }

    private boolean _isRecord(TypeElement t) {
        return "RECORD".equals(t.getKind().name());
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

    private String _upperCamel(String s) {
        String id = _javaId(s);
        if (id.length() == 0) return "Value";
        StringBuilder b = new StringBuilder(id.length());
        boolean upper = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_') {
                upper = true;
                continue;
            }
            b.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return b.length() == 0 ? "Value" : b.toString();
    }

    private void _error(Element element, GeneratedClass target, String message) {
        ctx.error(element, target.originName() + ": " + message);
    }

    private static final class MethodState {
        final Map<String, Integer> readCounts;
        final Map<String, Integer> groupParentCounts;
        final NameAllocator names;
        final String targetRoot;
        final List<String> readTemps = new ArrayList<String>();
        final Map<String, MapperModel.CachedRead> cache = new HashMap<String, MapperModel.CachedRead>();
        final Map<String, PathAccessEmitter.CachedPath> pathCache = new HashMap<String, PathAccessEmitter.CachedPath>();
        final Set<String> groupTargets = new HashSet<String>();
        MapperModel.GroupNode groupRoot;

        MethodState(List<MapperModel.SourceParam> sources, Map<String, Integer> counts) {
            this(null, sources, counts, Collections.<String, Integer>emptyMap());
        }

        MethodState(List<MapperModel.SourceParam> sources, Map<String, Integer> counts, Map<String, Integer> groupCounts) {
            this(null, sources, counts, groupCounts);
        }

        MethodState(String targetParam, List<MapperModel.SourceParam> sources, Map<String, Integer> counts) {
            this(targetParam, sources, counts, Collections.<String, Integer>emptyMap());
        }

        MethodState(String targetParam, List<MapperModel.SourceParam> sources, Map<String, Integer> counts, Map<String, Integer> groupCounts) {
            names = new NameAllocator();
            if (targetParam != null) names.reserve(targetParam);
            for (MapperModel.SourceParam source : sources) names.reserve(source.name);
            targetRoot = names.local("target");
            readCounts = counts;
            groupParentCounts = groupCounts;
        }
    }

    private static final class GenerationState {
        int nextCodec;
        final Map<String, String> helpers = new HashMap<String, String>();
        final Set<String> inProgress = new HashSet<String>();
        final NameAllocator helperNames = new NameAllocator();

        GenerationState(TypeElement iface) {
            for (Element e : iface.getEnclosedElements()) {
                if (e.getKind() == ElementKind.METHOD) helperNames.reserve(e.getSimpleName().toString());
            }
        }

        String helperName(String hint) {
            return helperNames.helper(hint);
        }
    }

}
