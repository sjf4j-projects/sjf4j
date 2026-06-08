package org.sjf4j.processor.mapper;

import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingCreator;
import org.sjf4j.annotation.mapper.MappingIfParentPresent;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.annotation.mapper.EnsureMapping;
import org.sjf4j.annotation.mapper.ArrayPolicy;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.annotation.mapper.ObjectPolicy;
import org.sjf4j.annotation.node.OneOf;
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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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
        if (!_validateMapperInterface(iface, target)) return;
        List<ImportedMapperRef> importedMappers = _importedMappers(iface, target);
        if (importedMappers == null) return;
        List<MappingCreatorRef> mappingCreators = _mappingCreators(iface, target);
        if (mappingCreators == null) return;
        generation = new GenerationState(iface, target, importedMappers, mappingCreators);
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

    private boolean _validateMapperInterface(TypeElement iface, GeneratedClass target) {
        if (!iface.getTypeParameters().isEmpty()) {
            _error(iface, target, "@CompiledMapper interfaces must not declare type parameters");
            return false;
        }
        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (!method.getTypeParameters().isEmpty()) {
                _error(method, target, "@CompiledMapper methods must not declare type parameters");
                return false;
            }
        }
        for (Element member : ctx.elements.getAllMembers(iface)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (!member.getModifiers().contains(Modifier.ABSTRACT)) continue;
            Element owner = member.getEnclosingElement();
            if (owner.equals(iface) || owner.toString().equals(Object.class.getName())) continue;
            _error(member, target, "Inherited abstract mapper methods are not supported; declare methods directly or use @CompiledMapper.importing");
            return false;
        }
        return true;
    }

    /**
     * Validates and emits one mapping method.
     */
    private void _genMap(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        generation.failed = false;
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
        if (_isExactJsonObject(method.getReturnType())) {
            _genJsonObjectProjection(iface, method, target);
            return;
        }
        if (_isExactJsonArray(method.getReturnType())) {
            _genJsonArrayProjection(method, target, method.getReturnType());
            return;
        }
        if (method.getReturnType().getKind() == TypeKind.ARRAY) {
            _genJavaArrayCreate(iface, method, target);
            return;
        }
        if (_isJajoTarget(method.getReturnType())) {
            _genJsonArrayProjection(method, target, method.getReturnType());
            return;
        }
        if (_isJojoTarget(method.getReturnType())) {
            _genJojoCreate(iface, method, target);
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
        if (!_validateUsingRefs(method, target, _methodUsingRefs(method))) return;

        OneOfRef rootOneOf = _oneOfRef(method, target, method.getReturnType());
        if (generation.failed) return;
        if (rootOneOf != null) {
            if (multi) {
                _error(method, target, "Root @OneOf create methods support exactly one source parameter");
                return;
            }
            MapperModel.Converter conv = _resolveConverter(iface, method, target, sources.get(0).element.asType(), method.getReturnType(), "");
            if (conv == null) return;
            target.addMethod(out -> _emitRootConverter(out, method, sources.get(0), conv));
            return;
        }

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
                continue;
            }
            if (m.ignore()) {
                ignored.add(t);
                continue;
            }
            explicitTargets.add(t);
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
                        plan.writes.get(t).type, "", nulls, plan.ctor == null);
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
            e = _maybeNestedExpr(iface, method, target, e, need, "", name);
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
        generation.failed = false;
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
        if (_isExactJsonArray(params.get(0).asType()) || params.get(0).asType().getKind() == TypeKind.ARRAY) {
            _error(method, target, "JsonArray and Java array update targets are unsupported");
            return;
        }
        if (_arrayLike(params.get(0).asType()) != null) {
            _error(method, target, "JAJO update targets are unsupported; update plain JsonArray outside CompiledMapper");
            return;
        }
        if (_isJojoTarget(params.get(0).asType())) {
            _error(method, target, "JOJO update targets are unsupported; update plain JsonObject outside CompiledMapper");
            return;
        }

        TypeElement targetType = GeneratorUtil.asTypeElement(params.get(0).asType());
        if (targetType == null) {
            _error(method, target, "@CompiledMapper supports only declared source and target types");
            return;
        }
        if (_hasOneOfAnnotation(params.get(0).asType())) {
            _error(method, target, "Root @OneOf update targets are unsupported");
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

        if (!_validateUsingRefs(method, target, _methodUsingRefs(method))) return;

        for (Mapping m : anns) {
            if (!_validateMapping(method, target, m)) return;

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
            if (m.array() != ArrayPolicy.SET) arrayPolicies.put(t, m.array());
            if (m.object() != ObjectPolicy.PUT) objectPolicies.put(t, m.object());
        }

        if (!_collectExtraPathWrites(method, target, ifParentAnns, ensureAnns, pathWrites, nestedMappers, explicitTargets)) return;

        MapperModel.Plan plan = new MapperModel.Plan(null, params.get(0).asType(), null, new ArrayList<String>(writes.keySet()), writes);
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
            ArrayPolicy arrayPolicy = arrayPolicies.get(name) == null ? defaultArrayPolicy : arrayPolicies.get(name);
            ObjectPolicy objectPolicy = objectPolicies.get(name) == null ? defaultObjectPolicy : objectPolicies.get(name);
            boolean inPlace = _inPlaceContainer(method, target, e.type, need, arrayPolicy, objectPolicy, "");
            if (inPlace) {
                if (!_validateContainerMapping(iface, method, target, e.type, need, "")) return;
            } else {
                e = _maybeNestedExpr(iface, method, target, e, need, "", name);
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

    private void _genJsonObjectProjection(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "JsonObject projection methods support exactly one source parameter");
            return;
        }
        if (!_projectionAnnotationsSupported(method, target)) return;
        VariableElement source = method.getParameters().get(0);
        if (!_jsonObjectProjectionSource(source.asType())) {
            _error(method, target, "JsonObject projection source must be a POJO, record, Map<String, ?>, or JsonObject");
            return;
        }
        if (!_validateJsonObjectProjectionSource(method, target, source.asType())) return;
        String helper = _ensureJsonObjectProjectionHelper(method, target, source.asType());
        if (helper == null) return;
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            out.line("return " + helper + "(" + source.getSimpleName() + ");");
            out.dedent();
            out.line("}");
        });
    }

    private void _genJsonArrayProjection(ExecutableElement method, GeneratedClass target, TypeMirror to) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "JsonArray projection methods support exactly one source parameter");
            return;
        }
        if (!_projectionAnnotationsSupported(method, target)) return;
        VariableElement source = method.getParameters().get(0);
        if (!_jsonArrayProjectionSource(source.asType())) {
            _error(method, target, "JsonArray projection source must be a Java array, List, Set, or JsonArray");
            return;
        }
        if (!_validateJsonArrayProjectionSource(method, target, source.asType())) return;
        if (_isJajoTarget(to) && !_hasPublicNoArgsConstructor(to)) {
            _error(method, target, "JAJO target type must provide a public no-args constructor");
            return;
        }
        String helper = _ensureJsonArrayProjectionHelper(target, source.asType(), to);
        if (helper == null) return;
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            out.line("return " + helper + "(" + source.getSimpleName() + ");");
            out.dedent();
            out.line("}");
        });
    }

    private void _genJavaArrayCreate(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "Java array create methods support exactly one source parameter");
            return;
        }
        if (!_rootMethodMappingsSupported(method, target, "Java array create methods")) return;
        VariableElement source = method.getParameters().get(0);
        TypeMirror sourceType = source.asType();
        TypeMirror targetType = method.getReturnType();
        TypeMirror targetElement = ((ArrayType) targetType).getComponentType();
        if (GeneratorUtil.isObject(ctx, sourceType)) {
            MapperModel.Converter conv = _resolveConverter(iface, method, target, ctx.objectType, targetElement, "");
            if (conv == null) return;
            String helper = _ensureObjectListJavaArrayHelper(target, targetType, targetElement, conv);
            target.addMethod(out -> {
                out.line("");
                out.line("@Override");
                out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
                out.indent();
                out.line("return " + helper + "(" + source.getSimpleName() + ");");
                out.dedent();
                out.line("}");
            });
            return;
        }
        MapperModel.ArrayLikeType arrayFrom = _arrayLike(sourceType);
        MapperModel.ContainerType containerFrom = _listOrSetSource(sourceType);
        if (containerFrom != null && containerFrom.map) containerFrom = null;
        if (arrayFrom == null && containerFrom == null) {
            _error(method, target, "Java array create source must be a Java array, List, Set, or JsonArray");
            return;
        }
        if (containerFrom != null && containerFrom.value == null && !_isListOrSetDeclared(containerFrom.mirror)) {
            _error(method, target, "Raw or non-parameterized collection/map types are unsupported");
            return;
        }
        MapperModel.Converter conv;
        if (arrayFrom != null) {
            conv = _resolveConverter(iface, method, target, arrayFrom.value, targetElement, "");
        } else {
            conv = _resolveConverter(iface, method, target, _containerSourceValueType(containerFrom), targetElement, "");
        }
        if (conv == null) return;
        String helper = _ensureJavaArrayHelper(target, sourceType, arrayFrom, containerFrom, conv, targetType, targetElement);
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            out.line("return " + helper + "(" + source.getSimpleName() + ");");
            out.dedent();
            out.line("}");
        });
    }

    private boolean _rootMethodMappingsSupported(ExecutableElement method, GeneratedClass target, String kind) {
        if (!_validateUsingRefs(method, target, _methodUsingRefs(method))) return false;
        for (Mapping m : method.getAnnotationsByType(Mapping.class)) {
            if (!_validateMapping(method, target, m)) return false;
            if (_isAutoMarker(m)) continue;
            _error(method, target, "@Mapping requires a non-empty target");
            return false;
        }
        if (method.getAnnotationsByType(MappingIfParentPresent.class).length != 0
                || method.getAnnotationsByType(EnsureMapping.class).length != 0) {
            _error(method, target, kind + " do not support target-path mappings");
            return false;
        }
        return true;
    }

    private boolean _projectionAnnotationsSupported(ExecutableElement method, GeneratedClass target) {
        for (Mapping m : method.getAnnotationsByType(Mapping.class)) {
            if (_isAutoMarker(m)) continue;
            _error(method, target, "JsonObject/JsonArray projection and Java array create methods do not support @Mapping customizations");
            return false;
        }
        if (method.getAnnotationsByType(MappingIfParentPresent.class).length != 0
                || method.getAnnotationsByType(EnsureMapping.class).length != 0) {
            _error(method, target, "JsonObject/JsonArray projection and Java array create methods do not support target-path mappings");
            return false;
        }
        return true;
    }

    private void _genContainerCreate(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.ContainerType to) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "Root collection/map create methods support exactly one source parameter");
            return;
        }
        VariableElement source = method.getParameters().get(0);
        if (!to.map && to.value == null) {
            _error(method, target, "Raw or non-parameterized collection types are unsupported");
            return;
        }
        if (to.map && _rootMapProjectionSource(source.asType())) {
            if (!_validateRootMapProjectionSource(method, target, source.asType(), to)) return;
            if (!_rootMethodMappingsSupported(method, target, "Root collection/map methods")) return;
            String helper = _ensureRootMapProjectionHelper(iface, method, target, source.asType(), to, "");
            if (helper == null) return;
            target.addMethod(out -> {
                out.line("");
                out.line("@Override");
                out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
                out.indent();
                out.line("return " + helper + "(" + source.getSimpleName() + ");");
                out.dedent();
                out.line("}");
            });
            return;
        }
        if (GeneratorUtil.isObject(ctx, source.asType())) {
            if (to.map) {
                _error(method, target, "Root collection/map create requires matching source and target container types");
                return;
            }
            if (!_rootMethodMappingsSupported(method, target, "Root collection/map methods")) return;
            MapperModel.Converter conv = _resolveConverter(iface, method, target, ctx.objectType, to.value, "");
            if (conv == null) return;
            String impl = _implType(method, target, to);
            if (impl == null) return;
            String helper = _ensureObjectListContainerHelper(target, to, conv, impl, method.getReturnType());
            target.addMethod(out -> {
                out.line("");
                out.line("@Override");
                out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
                out.indent();
                out.line("return " + helper + "(" + source.getSimpleName() + ");");
                out.dedent();
                out.line("}");
            });
            return;
        }
        MapperModel.ContainerType from = _container(source.asType());
        MapperModel.ArrayLikeType arrayFrom = _arrayLike(source.asType());
        if (from != null && !from.map && from.value == null && !_isListOrSetDeclared(from.mirror)) {
            _error(method, target, "Raw or non-parameterized collection types are unsupported");
            return;
        }
        if (!to.map && from != null && !from.map && !_isListOrSetDeclared(from.mirror)) {
            _error(method, target, "Root collection create source must be a List, Set, Java array, or JsonArray");
            return;
        }
        if (from == null || from.map != to.map) {
            if (to.map || arrayFrom == null) {
                _error(method, target, "Root collection/map create requires matching source and target container types");
                return;
            }
            if (!_rootMethodMappingsSupported(method, target, "Root collection/map methods")) return;
            MapperModel.Converter conv = _arrayLikeElementConverter(iface, method, target, arrayFrom, to, "");
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
                out.line(_containerLocalType(impl, to, method.getReturnType()) + " " + targetName + " = " + _newContainer(impl, to, _arrayLikeSize(arrayFrom, s)) + ";");
                _emitArrayLikeCopy(out, arrayFrom, conv, targetName, s, names);
                out.line("return " + targetName + ";");
                out.dedent();
                out.line("}");
            });
            return;
        }
        if (!_rootMethodMappingsSupported(method, target, "Root collection/map methods")) return;
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, "");
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
        if (!to.map && to.value == null) {
            _error(method, target, "Raw or non-parameterized collection types are unsupported");
            return;
        }
        MapperModel.ContainerType from = _container(source.asType());
        MapperModel.ArrayLikeType arrayFrom = _arrayLike(source.asType());
        if (from != null && !from.map && from.value == null && !_isListOrSetDeclared(from.mirror)) {
            _error(method, target, "Raw or non-parameterized collection types are unsupported");
            return;
        }
        if (!to.map && from != null && !from.map && !_isListOrSetDeclared(from.mirror)) {
            _error(method, target, "Root collection update source must be a List, Set, Java array, or JsonArray");
            return;
        }
        if (from == null || from.map != to.map) {
            if (to.map || arrayFrom == null) {
                _error(method, target, "Root collection/map update requires matching source and target container types");
                return;
            }
            if (!_rootMethodMappingsSupported(method, target, "Root collection/map update methods")) return;
            MapperModel.Converter conv = _arrayLikeElementConverter(iface, method, target, arrayFrom, to, "");
            if (conv == null) return;
            MapperOptions cfg = method.getAnnotation(MapperOptions.class);
            ArrayPolicy arrayPolicy = cfg == null ? ArrayPolicy.CLEAR_ADD : cfg.arrays();
            if (arrayPolicy == ArrayPolicy.SET) {
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
                if (arrayPolicy == ArrayPolicy.CLEAR_ADD) out.line(t + ".clear();");
                _emitArrayLikeCopy(out, arrayFrom, conv, t, s, names);
                out.dedent();
                out.line("}");
            });
            return;
        }
        if (!_rootMethodMappingsSupported(method, target, "Root collection/map update methods")) return;
        MapperModel.Converter conv = _containerConverter(iface, method, target, from, to, "");
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
                _emitContainerUpdate(out, iface, method, target, from, to, "", t, s, arrayPolicy, objectPolicy, names);
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
                && m.array() == ArrayPolicy.SET
                && m.object() == ObjectPolicy.PUT
                && !m.ignore();
    }

    private String[] _methodUsingRefs(ExecutableElement method) {
        MapperOptions options = method.getAnnotation(MapperOptions.class);
        return options == null ? new String[0] : options.using();
    }

    private boolean _validateUsingRefs(ExecutableElement method, GeneratedClass target, String[] refs) {
        for (int i = 0; i < refs.length; i++) {
            String ref = refs[i] == null ? "" : refs[i].trim();
            if (!_isValidMapperRef(ref, false)) {
                _error(method, target, "@MapperOptions.using expects 'method', 'this::method', 'ImportedMapper::method', or 'pkg.ImportedMapper::method'");
                return false;
            }
            if (ref.length() != 0 && !_usingRefExists(method, target, ref)) return false;
        }
        return true;
    }

    private boolean _usingRefExists(ExecutableElement method, GeneratedClass target, String ref) {
        int split = ref.indexOf("::");
        if (split < 0) {
            if (_hasNamedLocalMethod(generation.iface, method, ref) || _hasNamedImportedMethod(method, target, ref)) return true;
            _error(method, target, "Cannot resolve converter '" + ref + "'");
            generation.failed = true;
            return false;
        }
        String owner = ref.substring(0, split).trim();
        String name = ref.substring(split + 2).trim();
        if ("this".equals(owner)) {
            NamedMethodMatch match = _namedMethod(generation.iface, method, target, name);
            if (generation.failed) return false;
            if (match != null) return true;
            _error(method, target, "Cannot resolve converter 'this::" + name + "'");
            generation.failed = true;
            return false;
        }
        ImportedMapperRef imported = _importedMapperByName(method, target, owner);
        if (imported == null) return false;
        NamedMethodMatch match = _namedMethod(imported.type, method, target, name);
        if (generation.failed) return false;
        if (match != null) return true;
        _error(method, target, "Cannot resolve converter '" + ref + "'");
        generation.failed = true;
        return false;
    }

    private boolean _validateMapping(ExecutableElement method, GeneratedClass target, Mapping m) {
        if (m.sources().length > 0 && m.compute().length() == 0) {
            _error(method, target, "@Mapping.sources may be used only with @Mapping.compute");
            return false;
        }
        if (m.ignore() && (m.source().length() != 0 || m.compute().length() != 0 || m.sources().length != 0
                || m.array() != ArrayPolicy.SET || m.object() != ObjectPolicy.PUT)) {
            _error(method, target, "@Mapping.ignore cannot be combined with source, sources, compute, array, or object");
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

    private boolean _isValidMapperRef(String value, boolean legacyNestedMapper) {
        if (value.length() == 0) return true;
        if (legacyNestedMapper) return _isSimpleIdentifier(value);
        int split = value.indexOf("::");
        if (split < 0) return _isSimpleIdentifier(value);
        if (value.indexOf("::", split + 2) >= 0) return false;
        String left = value.substring(0, split).trim();
        String right = value.substring(split + 2).trim();
        if (!_isSimpleIdentifier(right)) return false;
        return "this".equals(left) || _isJavaQualifiedName(left);
    }

    private boolean _isJavaQualifiedName(String value) {
        if (value.length() == 0) return false;
        int start = 0;
        while (start < value.length()) {
            int dot = value.indexOf('.', start);
            String part = dot < 0 ? value.substring(start) : value.substring(start, dot);
            if (!_isSimpleIdentifier(part)) return false;
            if (dot < 0) return true;
            start = dot + 1;
        }
        return true;
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
            StringBuilder b = new StringBuilder("return new ").append(plan.type).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            String targetVar = state.targetRoot;
            out.line(plan.type + " " + targetVar + " = " + (plan.create == null ? "new " + plan.type + "()" : plan.create) + ";");
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

    private void _emitRootConverter(SourceWriter out, ExecutableElement method, MapperModel.SourceParam source, MapperModel.Converter conv) {
        out.line("");
        out.line("@Override");
        out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.element.asType() + " " + source.name + ") {");
        out.indent();
        out.line("if (" + source.name + " == null) return null;");
        out.line("return " + _convertValue(conv, source.name) + ";");
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
        for (Mapping m : method.getAnnotationsByType(Mapping.class)) if (m.target().equals(target)) return new MapperModel.MappingSpec(m.target(), m.source(), m.sources(), m.compute(), "");
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
        MappingCreatorMatch creator = _creatorFor(method, target, mirror);
        if (creator != null) {
            if (creator.create != null) {
                TypeElement creatorType = GeneratorUtil.asTypeElement(creator.type);
                if (creatorType == null || creatorType.getKind().isInterface() || creatorType.getModifiers().contains(Modifier.ABSTRACT)) {
                    _error(method, target, "@MappingCreator creator method must return a concrete mutable type");
                    return null;
                }
                Map<String, MapperModel.Write> creatorWrites = _writes(creatorType, creator.type);
                return new MapperModel.Plan(null, creator.type, creator.create, new ArrayList<String>(creatorWrites.keySet()), creatorWrites);
            }
            TypeElement implType = GeneratorUtil.asTypeElement(creator.type);
            Map<String, MapperModel.Write> implWrites = _writes(implType, creator.type);
            return _creationForType(method, target, implType, creator.type, implWrites);
        }
        return _creationForType(method, target, type, mirror, writes);
    }

    private MapperModel.Plan _creationForType(ExecutableElement method, GeneratedClass target, TypeElement type, TypeMirror mirror, Map<String, MapperModel.Write> writes) {
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
                return new MapperModel.Plan(null, mirror, null, new ArrayList<String>(writes.keySet()), writes);
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
        return new MapperModel.Plan(ctor, owner, null, new ArrayList<String>(w.keySet()), w);
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

    private boolean _hasPublicNoArgsConstructor(TypeMirror type) {
        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element == null) return false;
        for (ExecutableElement ctor : _publicConstructors(element)) {
            if (ctor.getParameters().isEmpty()) return true;
        }
        return false;
    }

    private List<MappingCreatorRef> _mappingCreators(TypeElement iface, GeneratedClass target) {
        List<MappingCreatorRef> refs = new ArrayList<MappingCreatorRef>();
        boolean[] failed = new boolean[1];
        _collectMappingCreators(iface, target, refs, new HashSet<String>(), failed);
        return failed[0] ? null : refs;
    }

    private void _collectMappingCreators(TypeElement iface, GeneratedClass target, List<MappingCreatorRef> refs, Set<String> seen, boolean[] failed) {
        String qn = iface.getQualifiedName().toString();
        if (!seen.add(qn)) return;
        for (AnnotationMirror mirror : iface.getAnnotationMirrors()) {
            String anno = mirror.getAnnotationType().toString();
            if (anno.equals(MappingCreator.class.getName())) {
                _addMappingCreator(iface, target, refs, mirror, failed);
            } else if (anno.equals("org.sjf4j.annotation.mapper.MappingCreators")) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : ctx.elements.getElementValuesWithDefaults(mirror).entrySet()) {
                    if (!e.getKey().getSimpleName().contentEquals("value")) continue;
                    Object raw = e.getValue().getValue();
                    if (!(raw instanceof List)) continue;
                    for (Object item : (List<?>) raw) {
                        Object value = ((AnnotationValue) item).getValue();
                        if (value instanceof AnnotationMirror) _addMappingCreator(iface, target, refs, (AnnotationMirror) value, failed);
                    }
                }
            }
        }
        for (TypeMirror parent : iface.getInterfaces()) {
            TypeElement parentType = GeneratorUtil.asTypeElement(parent);
            if (parentType != null) _collectMappingCreators(parentType, target, refs, seen, failed);
        }
    }

    private void _addMappingCreator(TypeElement iface, GeneratedClass target, List<MappingCreatorRef> refs, AnnotationMirror mirror, boolean[] failed) {
        TypeMirror targetType = null;
        TypeMirror implementation = null;
        String creator = "";
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : ctx.elements.getElementValuesWithDefaults(mirror).entrySet()) {
            String name = e.getKey().getSimpleName().toString();
            if ("targetType".equals(name)) targetType = (TypeMirror) e.getValue().getValue();
            else if ("implementation".equals(name)) implementation = (TypeMirror) e.getValue().getValue();
            else if ("creator".equals(name)) creator = String.valueOf(e.getValue().getValue()).trim();
        }
        if (targetType == null || targetType.getKind() != TypeKind.DECLARED) {
            _error(iface, target, "@MappingCreator.targetType must be a declared type");
            failed[0] = true;
            return;
        }
        boolean hasImplementation = implementation != null && !"java.lang.Void".equals(_qualifiedName(implementation));
        boolean hasCreator = creator.length() != 0;
        if (hasImplementation == hasCreator) {
            _error(iface, target, "@MappingCreator requires exactly one of implementation or creator");
            failed[0] = true;
            return;
        }
        if (hasImplementation) {
            if (implementation.getKind() != TypeKind.DECLARED) {
                _error(iface, target, "@MappingCreator.implementation must be a declared type");
                failed[0] = true;
                return;
            }
            if (!_assignable(implementation, targetType)) {
                _error(iface, target, "@MappingCreator.implementation must be assignable to targetType");
                failed[0] = true;
                return;
            }
        }
        refs.add(new MappingCreatorRef(targetType, hasImplementation ? implementation : null, hasCreator ? creator : null));
    }

    private MappingCreatorMatch _creatorFor(ExecutableElement method, GeneratedClass target, TypeMirror requestedType) {
        MappingCreatorRef best = null;
        for (MappingCreatorRef ref : generation.mappingCreators) {
            if (!ctx.types.isAssignable(requestedType, ref.targetType)) continue;
            if (best == null) {
                best = ref;
                continue;
            }
            boolean refMoreSpecific = ctx.types.isAssignable(ref.targetType, best.targetType);
            boolean bestMoreSpecific = ctx.types.isAssignable(best.targetType, ref.targetType);
            if (refMoreSpecific && !bestMoreSpecific) best = ref;
            else if (!refMoreSpecific && !bestMoreSpecific) {
                _error(method, target, "Ambiguous @MappingCreator for target type " + requestedType);
                return null;
            } else if (refMoreSpecific && bestMoreSpecific) {
                _error(method, target, "Ambiguous @MappingCreator for target type " + requestedType);
                return null;
            }
        }
        if (best == null) return null;
        if (best.implementation != null) return new MappingCreatorMatch(best.implementation, null);
        return _creatorMethod(method, target, best, requestedType);
    }

    private MappingCreatorMatch _creatorMethod(ExecutableElement method, GeneratedClass target, MappingCreatorRef ref, TypeMirror requestedType) {
        String creator = ref.creator;
        if (!creator.startsWith("this::") || !_isSimpleIdentifier(creator.substring(6))) {
            _error(method, target, "@MappingCreator.creator currently supports only this::method");
            return null;
        }
        String name = creator.substring(6);
        ExecutableElement found = null;
        for (Element element : ctx.elements.getAllMembers(generation.iface)) {
            if (element.getKind() != ElementKind.METHOD || !element.getSimpleName().contentEquals(name)) continue;
            if (element.getEnclosingElement().toString().equals(Object.class.getName())) continue;
            if (found != null) {
                _error(method, target, "Ambiguous @MappingCreator creator method '" + name + "'");
                return null;
            }
            found = (ExecutableElement) element;
        }
        if (found == null) {
            _error(method, target, "Cannot resolve @MappingCreator creator method '" + creator + "'");
            return null;
        }
        if (!found.getModifiers().contains(Modifier.DEFAULT) && !found.getModifiers().contains(Modifier.STATIC)) {
            _error(method, target, "@MappingCreator creator method must be default or static");
            return null;
        }
        if (!found.getParameters().isEmpty()) {
            _error(method, target, "@MappingCreator creator method must not declare parameters");
            return null;
        }
        TypeElement owner = (TypeElement) found.getEnclosingElement();
        ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner.asType(), found);
        if (!_assignable(mt.getReturnType(), ref.targetType)) {
            _error(method, target, "@MappingCreator creator method return type must be assignable to " + requestedType);
            return null;
        }
        String call = found.getModifiers().contains(Modifier.STATIC)
                ? owner.getQualifiedName() + "." + name + "()"
                : name + "()";
        return new MappingCreatorMatch(mt.getReturnType(), call);
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
                new MapperModel.MappingSpec(m.target(), m.source(), m.sources(), m.compute(), ""));
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

    private MapperModel.ContainerType _listOrSetSource(TypeMirror type) {
        MapperModel.ContainerType container = _container(type);
        if (container == null || container.map) return null;
        return GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.setType) ? container : null;
    }

    private TypeMirror _containerSourceValueType(MapperModel.ContainerType container) {
        return container != null && container.value != null ? container.value : ctx.objectType;
    }

    private boolean _isListOrSetDeclared(TypeMirror type) {
        return _listOrSetSource(type) != null;
    }

    private MapperModel.ArrayLikeType _arrayLike(TypeMirror type) {
        if (type == null) return null;
        if (type.getKind() == TypeKind.ARRAY) {
            return new MapperModel.ArrayLikeType(true, ((ArrayType) type).getComponentType(), type);
        }
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) {
            return new MapperModel.ArrayLikeType(false, ctx.objectType, type);
        }
        return null;
    }

    private boolean _inPlaceContainer(ExecutableElement method, GeneratedClass target, TypeMirror fromType, TypeMirror toType,
                                      ArrayPolicy arrayPolicy, ObjectPolicy objectPolicy, String nestedMapper) {
        MapperModel.ContainerType from = _container(fromType);
        MapperModel.ContainerType to = _container(toType);
        if (to == null) return false;
        if (from == null) {
            MapperModel.ArrayLikeType arrayFrom = _arrayLike(fromType);
            return arrayFrom != null && !to.map && arrayPolicy != null && arrayPolicy != ArrayPolicy.SET;
        }
        if (from.map != to.map) return false;
        if (to.map) return true;
        return arrayPolicy != null && arrayPolicy != ArrayPolicy.SET;
    }

    private boolean _validateContainerMapping(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                              TypeMirror fromType, TypeMirror toType, String nestedMapper) {
        MapperModel.ContainerType from = _container(fromType);
        MapperModel.ContainerType to = _container(toType);
        if (to == null) return false;
        if (from == null) {
            MapperModel.ArrayLikeType arrayFrom = _arrayLike(fromType);
            return arrayFrom != null && !to.map && _arrayLikeElementConverter(iface, method, target, arrayFrom, to, nestedMapper) != null;
        }
        if (from.map != to.map) return false;
        return _containerConverter(iface, method, target, from, to, nestedMapper) != null;
    }

    private MapperModel.Converter _containerConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.ContainerType from, MapperModel.ContainerType to, String nestedMapper) {
        if (to.value == null || (from.map && (from.key == null || to.key == null))) {
            _error(method, target, "Raw or non-parameterized collection/map types are unsupported");
            return null;
        }
        if (!from.map && !_isListOrSetDeclared(from.mirror)) {
            _error(method, target, "Collection source must be declared as java.util.List or java.util.Set for array-like mapping");
            return null;
        }
        if (from.map && from.value == null) {
            _error(method, target, "Raw or non-parameterized collection/map types are unsupported");
            return null;
        }
        if (from.map && !_assignable(from.key, to.key)) {
            _error(method, target, "Map key type mismatch: " + from.key + " is not assignable to " + to.key);
            return null;
        }
        TypeMirror fromValue = _containerSourceValueType(from);
        if ((nestedMapper == null || nestedMapper.length() == 0) && _assignable(fromValue, to.value)) return new MapperModel.Converter(null, to.value);
        MapperModel.ContainerType nestedFrom = _container(fromValue);
        MapperModel.ContainerType nestedTo = _container(to.value);
        if (nestedFrom != null && nestedTo != null && nestedFrom.map == nestedTo.map) {
            MapperModel.Converter nestedConv = _containerConverter(iface, method, target, nestedFrom, nestedTo, nestedMapper);
            if (nestedConv == null) return null;
            String impl = _implType(method, target, nestedTo);
            if (impl == null) return null;
            String helper = _ensureContainerHelper(target, nestedFrom, nestedTo, nestedConv, impl, to.value);
            return new MapperModel.Converter(helper, to.value);
        }
        return _resolveConverter(iface, method, target, fromValue, to.value, nestedMapper);
    }

    private MapperModel.Expr _maybeNestedExpr(TypeElement iface, ExecutableElement method, GeneratedClass target, MapperModel.Expr e, TypeMirror need, String nestedMapper, String name) {
        MapperModel.ContainerType from = _container(e.type);
        MapperModel.ContainerType to = _container(need);
        MapperModel.ArrayLikeType arrayFrom = _arrayLike(e.type);
        if (arrayFrom != null && to != null && !to.map) {
            MapperModel.Converter conv = _arrayLikeElementConverter(iface, method, target, arrayFrom, to, nestedMapper);
            if (conv == null) return null;
            String impl = _implType(method, target, to);
            if (impl == null) return null;
            String helper = _ensureArrayLikeHelper(target, arrayFrom, to, conv, impl, need);
            MapperModel.Expr r = new MapperModel.Expr(helper + "(" + e.code + ")", need, e.path, e.nullableRoot, false);
            r.temps.addAll(e.temps);
            r.nullGuardSource = e.code;
            r.nullGuardType = e.type;
            r.nullGuardLocal = e.local;
            r.nullGuardCodeTemplate = helper + "($source)";
            return r;
        }
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

    private String _ensureArrayLikeHelper(GeneratedClass target, MapperModel.ArrayLikeType from, MapperModel.ContainerType to,
                                          MapperModel.Converter conv, String impl, TypeMirror resultType) {
        String key = "arraylike:" + from.mirror + "->" + to.mirror + ":" + impl + ":" + (conv.method == null ? "" : conv.method);
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String helper = generation.helperName("ArrayLike");
        generation.helpers.put(key, helper);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            out.line("");
            out.line("private " + resultType + " " + helper + "(" + from.mirror + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line(_containerLocalType(impl, to, resultType) + " " + targetVar + " = " + _newContainer(impl, to, _arrayLikeSize(from, "source")) + ";");
            _emitArrayLikeCopy(out, from, conv, targetVar, "source", names);
            out.line("return " + targetVar + ";");
            out.dedent();
            out.line("}");
        });
        return helper;
    }

    private String _arrayLikeSize(MapperModel.ArrayLikeType from, String source) {
        return from.javaArray ? source + ".length" : source + ".size()";
    }

    private String _ensureObjectListContainerHelper(GeneratedClass target, MapperModel.ContainerType to,
                                                    MapperModel.Converter conv, String impl, TypeMirror resultType) {
        String key = "objectListContainer:" + to.mirror + ":" + impl + ":" + resultType + ":" + (conv.method == null ? "" : conv.method);
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String helper = generation.helperName("ObjectList");
        generation.helpers.put(key, helper);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            String value = names.prefixed("s", "value");
            out.line("");
            out.line("private " + resultType + " " + helper + "(Object source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line("if (!(source instanceof java.util.List)) throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to '" + GeneratorUtil.escape(resultType.toString()) + "'\");");
            out.line("java.util.List list = (java.util.List) source;");
            out.line(_containerLocalType(impl, to, resultType) + " " + targetVar + " = " + _newContainer(impl, to, "list.size()") + ";");
            out.line("for (Object " + value + " : list) {");
            out.indent();
            out.line(targetVar + ".add(" + _convertValue(conv, value) + ");");
            out.dedent();
            out.line("}");
            out.line("return " + targetVar + ";");
            out.dedent();
            out.line("}");
        });
        return helper;
    }

    private String _ensureObjectListJavaArrayHelper(GeneratedClass target, TypeMirror resultType, TypeMirror elementType,
                                                    MapperModel.Converter conv) {
        String key = "objectListArray:" + resultType + ":" + (conv.method == null ? "" : conv.method);
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String helper = generation.helperName("ObjectListArray");
        generation.helpers.put(key, helper);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            String index = names.prefixed("s", "i");
            out.line("");
            out.line("private " + resultType + " " + helper + "(Object source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line("if (!(source instanceof java.util.List)) throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to '" + GeneratorUtil.escape(resultType.toString()) + "'\");");
            out.line("java.util.List list = (java.util.List) source;");
            out.line(resultType + " " + targetVar + " = new " + _arrayComponentTypeName(elementType) + "[list.size()];");
            out.line("for (int " + index + " = 0; " + index + " < list.size(); " + index + "++) {");
            out.indent();
            out.line(targetVar + "[" + index + "] = " + _convertValue(conv, "list.get(" + index + ")") + ";");
            out.dedent();
            out.line("}");
            out.line("return " + targetVar + ";");
            out.dedent();
            out.line("}");
        });
        return helper;
    }

    private String _arrayLikeValue(MapperModel.ArrayLikeType from, String source, String index) {
        return from.javaArray ? source + "[" + index + "]" : source + ".getNode(" + index + ")";
    }

    private void _emitArrayLikeCopy(SourceWriter out, MapperModel.ArrayLikeType from, MapperModel.Converter conv,
                                    String target, String source, NameAllocator names) {
        String index = names.prefixed("s", "i");
        out.line("for (int " + index + " = 0; " + index + " < " + _arrayLikeSize(from, source) + "; " + index + "++) {");
        out.indent();
        out.line(target + ".add(" + _convertValue(conv, _arrayLikeValue(from, source, index)) + ");");
        out.dedent();
        out.line("}");
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
                out.line("for (" + GeneratorUtil.localTypeName(ctx, _containerSourceValueType(from)) + " " + value + " : source) {");
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
                out.line("for (" + GeneratorUtil.localTypeName(ctx, _containerSourceValueType(from)) + " " + value + " : source) {");
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
            return _explicitConverter(iface, method, target, from, to, nestedMapper);
        }

        MapperModel.Converter preferred = _preferredConverter(iface, method, target, from, to);
        if (preferred != null || generation.failed) return preferred;

        MapperModel.Converter oneOf = _oneOfConverter(iface, method, target, from, to);
        if (oneOf != null || generation.failed) return oneOf;

        MapperModel.Converter found = _autoLocalConverter(iface, method, target, from, to);
        if (found == null && generation.failed) return null;
        if (found == null) {
            found = _autoImportedConverter(method, target, from, to);
            if (found == null && generation.failed) return null;
        }
        if (found == null) {
            MapperModel.Converter fallback = _enumConverter(method, target, from, to);
            if (fallback != null) return fallback;
            fallback = _nodeValueConverter(method, target, from, to);
            if (fallback != null) return fallback;
            fallback = _scalarConverter(method, target, from, to);
            if (fallback != null) return fallback;
            fallback = _arrayLikeConverter(iface, method, target, from, to, nestedMapper);
            if (fallback != null) return fallback;
            fallback = _jsonObjectProjectionConverter(method, target, from, to, nestedMapper);
            if (fallback != null) return fallback;
            fallback = _objectLikeConverter(iface, method, target, from, to, nestedMapper);
            if (fallback != null) return fallback;
            fallback = _autoHelperConverter(iface, method, target, from, to);
            if (fallback != null) return fallback;
            if (errorIfMissing) _error(method, target, "Cannot find element/value converter from " + from + " to " + to);
            return null;
        }
        return found;
    }

    private MapperModel.Converter _oneOfConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        OneOfRef oneOf = _oneOfRef(method, target, to);
        if (oneOf == null) return null;
        String key = "oneof:" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return new MapperModel.Converter(existing, to);
        if (generation.inProgress.contains(key)) {
            _error(method, target, "Recursive @OneOf converter from " + from + " to " + to + " is unsupported; provide an explicit mapper");
            generation.failed = true;
            return null;
        }

        generation.inProgress.add(key);
        List<OneOfBranchRef> branches = new ArrayList<OneOfBranchRef>();
        String sourceRawJsonType = oneOf.shape ? _rawJsonTypeName(from) : null;
        for (OneOfMappingRef mapping : oneOf.mappings) {
            if (oneOf.shape && sourceRawJsonType != null
                    && !org.sjf4j.JsonType.UNKNOWN.name().equals(sourceRawJsonType)
                    && !sourceRawJsonType.equals(mapping.rawJsonType)) {
                continue;
            }
            MapperModel.Converter conv = _resolveConverter(iface, method, target, from, mapping.type, "");
            if (conv == null) {
                generation.inProgress.remove(key);
                return null;
            }
            branches.add(new OneOfBranchRef(mapping.when, mapping.rawJsonType, conv));
        }
        generation.inProgress.remove(key);

        String helper = generation.helperName("OneOf");
        generation.helpers.put(key, helper);
        target.addHelper(out -> _emitOneOfHelper(out, helper, from, to, oneOf, branches));
        return new MapperModel.Converter(helper, to);
    }

    private void _emitOneOfHelper(SourceWriter out, String helper, TypeMirror from, TypeMirror to, OneOfRef oneOf, List<OneOfBranchRef> branches) {
        out.line("");
        out.line("private " + to + " " + helper + "(" + from + " source) {");
        out.indent();
        out.line("if (source == null) return null;");
        if (oneOf.shape) {
            out.line("org.sjf4j.JsonType jsonType = org.sjf4j.JsonType.of(source);");
            for (int i = 0; i < branches.size(); i++) {
                OneOfBranchRef branch = branches.get(i);
                out.line((i == 0 ? "if (" : "else if (") + "jsonType == org.sjf4j.JsonType." + branch.rawJsonType + ") return " + _convertValue(branch.converter, "source") + ";");
            }
            if (oneOf.failbackNull) {
                out.line("return null;");
            } else {
                out.line("throw new org.sjf4j.exception.BindingException(\"Cannot resolve @OneOf target '" + GeneratorUtil.escape(to.toString()) + "' from runtime JsonType '\" + jsonType + \"'\");");
            }
            out.dedent();
            out.line("}");
            return;
        }
        out.line("Object discriminator = org.sjf4j.node.Nodes.getInObject(source, \"" + GeneratorUtil.escape(oneOf.key) + "\");");
        out.line("if (discriminator != null) {");
        out.indent();
        out.line("String discriminatorValue = String.valueOf(discriminator);");
        for (int i = 0; i < branches.size(); i++) {
            OneOfBranchRef branch = branches.get(i);
            StringBuilder cond = new StringBuilder();
            for (int j = 0; j < branch.when.length; j++) {
                if (j != 0) cond.append(" || ");
                cond.append("\"").append(GeneratorUtil.escape(branch.when[j])).append("\".equals(discriminatorValue)");
            }
            out.line((i == 0 ? "if (" : "else if (") + cond + ") return " + _convertValue(branch.converter, "source") + ";");
        }
        out.dedent();
        out.line("}");
        if (oneOf.failbackNull) {
            out.line("return null;");
        } else {
            out.line("throw new org.sjf4j.exception.BindingException(\"Cannot resolve @OneOf target '" + GeneratorUtil.escape(to.toString()) + "' from discriminator key '" + GeneratorUtil.escape(oneOf.key) + "' value '\" + discriminator + \"'\");");
        }
        out.dedent();
        out.line("}");
    }

    private boolean _hasOneOfAnnotation(TypeMirror type) {
        TypeElement element = GeneratorUtil.asTypeElement(type);
        return element != null && _oneOfMirror(element) != null;
    }

    private OneOfRef _oneOfRef(ExecutableElement method, GeneratedClass target, TypeMirror to) {
        TypeElement element = GeneratorUtil.asTypeElement(to);
        if (element == null) return null;
        AnnotationMirror mirror = _oneOfMirror(element);
        if (mirror == null) return null;

        String key = "";
        String path = "";
        String scope = OneOf.Scope.CURRENT.name();
        String onNoMatch = OneOf.OnNoMatch.FAIL.name();
        List<OneOfMappingRef> mappings = new ArrayList<OneOfMappingRef>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ctx.elements.getElementValuesWithDefaults(mirror).entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            Object value = entry.getValue().getValue();
            if ("key".equals(name)) {
                key = (String) value;
                continue;
            }
            if ("path".equals(name)) {
                path = (String) value;
                continue;
            }
            if ("scope".equals(name)) {
                scope = String.valueOf(value);
                continue;
            }
            if ("onNoMatch".equals(name)) {
                onNoMatch = String.valueOf(value);
                continue;
            }
            if (!"value".equals(name) || !(value instanceof List)) continue;
            for (Object item : (List<?>) value) {
                Object raw = ((AnnotationValue) item).getValue();
                if (!(raw instanceof AnnotationMirror)) continue;
                OneOfMappingRef mapping = _oneOfMappingRef(method, target, to, (AnnotationMirror) raw);
                if (mapping == null) {
                    generation.failed = true;
                    return null;
                }
                mappings.add(mapping);
            }
        }

        if (mappings.isEmpty()) {
            _error(method, target, "CompiledMapper requires non-empty @OneOf mappings");
            generation.failed = true;
            return null;
        }
        if (path.length() != 0) {
            _error(method, target, "CompiledMapper supports only @OneOf.path=\"\"; discriminator path dispatch is unsupported");
            generation.failed = true;
            return null;
        }
        if (!OneOf.Scope.CURRENT.name().equals(scope)) {
            _error(method, target, "CompiledMapper supports only @OneOf.scope=CURRENT");
            generation.failed = true;
            return null;
        }
        boolean shape = key.length() == 0;
        if (shape) {
            Set<String> rawTypes = new HashSet<String>();
            for (OneOfMappingRef mapping : mappings) {
                if (!rawTypes.add(mapping.rawJsonType)) {
                    _error(method, target, "CompiledMapper shape-based @OneOf has duplicate raw JsonType " + mapping.rawJsonType + " for target " + to);
                    generation.failed = true;
                    return null;
                }
            }
        }
        return new OneOfRef(shape, key, OneOf.OnNoMatch.FAILBACK_NULL.name().equals(onNoMatch), mappings);
    }

    private OneOfMappingRef _oneOfMappingRef(ExecutableElement method, GeneratedClass target, TypeMirror baseType, AnnotationMirror mirror) {
        TypeMirror subtype = null;
        List<String> when = new ArrayList<String>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ctx.elements.getElementValuesWithDefaults(mirror).entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            Object value = entry.getValue().getValue();
            if ("value".equals(name)) {
                subtype = (TypeMirror) value;
                continue;
            }
            if (!"when".equals(name) || !(value instanceof List)) continue;
            for (Object item : (List<?>) value) {
                when.add((String) ((AnnotationValue) item).getValue());
            }
        }
        if (subtype == null) {
            _error(method, target, "Invalid @OneOf mapping: missing subtype");
            return null;
        }
        if (!ctx.types.isAssignable(subtype, baseType)) {
            _error(method, target, "@OneOf mapping subtype " + subtype + " is not assignable to " + baseType);
            return null;
        }
        boolean shape = _oneOfKey(_oneOfMirror(GeneratorUtil.asTypeElement(baseType))).length() == 0;
        if (shape) {
            if (!when.isEmpty()) {
                _error(method, target, "CompiledMapper shape-based @OneOf requires empty @OneOf.Mapping.when values");
                return null;
            }
            String rawJsonType = _rawJsonTypeName(subtype);
            if (rawJsonType == null || org.sjf4j.JsonType.UNKNOWN.name().equals(rawJsonType)) {
                _error(method, target, "CompiledMapper shape-based @OneOf requires known raw JsonType for subtype " + subtype);
                return null;
            }
            return new OneOfMappingRef(subtype, new String[0], rawJsonType);
        }
        if (when.isEmpty()) {
            _error(method, target, "CompiledMapper requires non-empty @OneOf.Mapping.when values for discriminator dispatch");
            return null;
        }
        return new OneOfMappingRef(subtype, when.toArray(new String[0]), null);
    }

    private String _oneOfKey(AnnotationMirror mirror) {
        if (mirror == null) return "";
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ctx.elements.getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals("key")) return String.valueOf(entry.getValue().getValue());
        }
        return "";
    }

    private String _rawJsonTypeName(TypeMirror type) {
        if (type == null) return null;
        if (type.getKind() == TypeKind.ARRAY) return org.sjf4j.JsonType.ARRAY.name();
        if (type.getKind().isPrimitive()) {
            if (type.getKind() == TypeKind.BOOLEAN) return org.sjf4j.JsonType.BOOLEAN.name();
            if (type.getKind() == TypeKind.CHAR) return org.sjf4j.JsonType.STRING.name();
            return type.getKind() == TypeKind.VOID ? org.sjf4j.JsonType.UNKNOWN.name() : org.sjf4j.JsonType.NUMBER.name();
        }

        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType) || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType)) {
            return org.sjf4j.JsonType.OBJECT.name();
        }
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.listType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.setType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)
                || ctx.types.isAssignable(ctx.types.erasure(type), ctx.types.erasure(ctx.elements.getTypeElement("java.util.Collection").asType()))) {
            return org.sjf4j.JsonType.ARRAY.name();
        }

        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element == null) return null;
        if (element.getKind() == ElementKind.ENUM) return org.sjf4j.JsonType.STRING.name();
        if (_oneOfMirror(element) != null) return org.sjf4j.JsonType.UNKNOWN.name();
        if (_isNodeValue(type)) return _nodeValueRawJsonTypeName(type);

        String qualifiedName = element.getQualifiedName().toString();
        if (String.class.getName().equals(qualifiedName)
                || Character.class.getName().equals(qualifiedName)
                || CharSequence.class.getName().equals(qualifiedName)) return org.sjf4j.JsonType.STRING.name();
        if (Boolean.class.getName().equals(qualifiedName)) return org.sjf4j.JsonType.BOOLEAN.name();
        if (Number.class.getName().equals(qualifiedName)
                || Byte.class.getName().equals(qualifiedName)
                || Short.class.getName().equals(qualifiedName)
                || Integer.class.getName().equals(qualifiedName)
                || Long.class.getName().equals(qualifiedName)
                || Float.class.getName().equals(qualifiedName)
                || Double.class.getName().equals(qualifiedName)) return org.sjf4j.JsonType.NUMBER.name();
        if (Void.class.getName().equals(qualifiedName)) return org.sjf4j.JsonType.NULL.name();
        return org.sjf4j.JsonType.OBJECT.name();
    }

    private String _nodeValueRawJsonTypeName(TypeMirror type) {
        TypeElement element = GeneratorUtil.asTypeElement(type);
        if (element == null) return org.sjf4j.JsonType.UNKNOWN.name();
        for (Element member : ctx.elements.getAllMembers(element)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (member.getAnnotation(org.sjf4j.annotation.node.ValueToRaw.class) == null) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (method.getModifiers().contains(Modifier.STATIC)) continue;
            if (!method.getParameters().isEmpty()) continue;
            return _rawJsonTypeName(method.getReturnType());
        }
        return org.sjf4j.JsonType.UNKNOWN.name();
    }

    private AnnotationMirror _oneOfMirror(TypeElement element) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(OneOf.class.getName())) return mirror;
        }
        return null;
    }

    private MapperModel.Converter _preferredConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                      TypeMirror from, TypeMirror to) {
        String[] refs = _methodUsingRefs(method);
        for (int i = 0; i < refs.length; i++) {
            String ref = refs[i] == null ? "" : refs[i].trim();
            if (ref.length() == 0) continue;
            MapperModel.Converter conv = _preferredConverterRef(iface, method, target, from, to, ref);
            if (conv != null || generation.failed) return conv;
        }
        return null;
    }

    private MapperModel.Converter _preferredConverterRef(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                         TypeMirror from, TypeMirror to, String ref) {
        int split = ref.indexOf("::");
        if (split < 0) return _preferredUnnamedConverter(iface, method, target, from, to, ref);
        String owner = ref.substring(0, split).trim();
        String name = ref.substring(split + 2).trim();
        if ("this".equals(owner)) return _preferredLocalConverter(iface, method, target, from, to, name, "this::" + name);
        ImportedMapperRef imported = _importedMapperByName(method, target, owner);
        if (imported == null) return null;
        return _preferredImportedConverter(method, target, from, to, imported, name, ref);
    }

    private MapperModel.Converter _preferredUnnamedConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                             TypeMirror from, TypeMirror to, String name) {
        MapperModel.Converter local = _preferredLocalConverter(iface, method, target, from, to, name, name);
        if (generation.failed) return null;
        MapperModel.Converter imported = _preferredImportedConverter(method, target, from, to, name);
        if (generation.failed) return null;
        if (local != null && imported != null) {
            _error(method, target, "Ambiguous preferred converter '" + name + "'");
            generation.failed = true;
            return null;
        }
        if (local != null) return local;
        if (imported != null) return imported;
        if (!_hasNamedLocalMethod(iface, method, name) && !_hasNamedImportedMethod(method, target, name)) {
            _error(method, target, "Cannot resolve converter '" + name + "'");
            generation.failed = true;
        }
        return null;
    }

    private MapperModel.Converter _preferredLocalConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                           TypeMirror from, TypeMirror to, String name, String ref) {
        NamedMethodMatch match = _namedMethod(iface, method, target, name);
        if (match == null) {
            _error(method, target, "Cannot resolve converter '" + ref + "'");
            generation.failed = true;
            return null;
        }
        if (match.method == null) return null;
        return _compatibleConverter(iface, iface.asType(), method, target, match.method, from, to, null);
    }

    private MapperModel.Converter _preferredImportedConverter(ExecutableElement method, GeneratedClass target,
                                                              TypeMirror from, TypeMirror to, String name) {
        MapperModel.Converter result = null;
        boolean anyNamed = false;
        for (ImportedMapperRef imported : generation.importedMappers) {
            NamedMethodMatch match = _namedMethod(imported.type, method, target, name);
            if (match == null) continue;
            anyNamed = true;
            if (match.method == null) return null;
            MapperModel.Converter conv = _compatibleConverter(imported.type, imported.type.asType(), method, target, match.method, from, to, imported);
            if (conv == null) continue;
            if (result != null) {
                _error(method, target, "Ambiguous preferred imported converter '" + name + "'");
                generation.failed = true;
                return null;
            }
            result = conv;
        }
        return anyNamed ? result : null;
    }

    private MapperModel.Converter _preferredImportedConverter(ExecutableElement method, GeneratedClass target,
                                                              TypeMirror from, TypeMirror to, ImportedMapperRef imported,
                                                              String name, String ref) {
        NamedMethodMatch match = _namedMethod(imported.type, method, target, name);
        if (match == null) {
            _error(method, target, "Cannot resolve converter '" + ref + "'");
            generation.failed = true;
            return null;
        }
        if (match.method == null) return null;
        return _compatibleConverter(imported.type, imported.type.asType(), method, target, match.method, from, to, imported);
    }

    private boolean _hasNamedLocalMethod(TypeElement iface, ExecutableElement method, String name) {
        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && e != method && e.getSimpleName().contentEquals(name) && !e.getModifiers().contains(Modifier.PRIVATE)) return true;
        }
        return false;
    }

    private boolean _hasNamedImportedMethod(ExecutableElement method, GeneratedClass target, String name) {
        for (ImportedMapperRef imported : generation.importedMappers) {
            if (_namedMethod(imported.type, method, target, name) != null) return true;
        }
        return false;
    }

    private MapperModel.Converter _explicitConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                     TypeMirror from, TypeMirror to, String ref) {
        int split = ref.indexOf("::");
        if (split < 0) {
            MapperModel.Converter local = _namedLocalConverter(iface, method, target, from, to, ref, false);
            if (local != null || generation.failed) return local;
            return _namedImportedConverter(method, target, from, to, ref, false);
        }
        String owner = ref.substring(0, split).trim();
        String name = ref.substring(split + 2).trim();
        if ("this".equals(owner)) return _namedLocalConverter(iface, method, target, from, to, name, true);
        ImportedMapperRef imported = _importedMapperByName(method, target, owner);
        if (imported == null) return null;
        return _namedImportedConverter(method, target, from, to, imported, name, true);
    }

    private MapperModel.Converter _namedLocalConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                       TypeMirror from, TypeMirror to, String name, boolean explicitOwner) {
        ExecutableElement found = null;
        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;
            if (e.getModifiers().contains(Modifier.PRIVATE) || e == method) continue;
            if (found != null) {
                _error(method, target, "Ambiguous converter '" + name + "'; overloaded converter methods are not supported");
                generation.failed = true;
                return null;
            }
            found = (ExecutableElement) e;
        }
        if (found == null) {
            if (explicitOwner) {
                _error(method, target, "Cannot resolve converter 'this::" + name + "'");
                generation.failed = true;
            }
            return null;
        }
        MapperModel.Converter conv = _converterFromMethod(iface, method, target, found, from, to, null);
        if (conv == null) generation.failed = true;
        return conv;
    }

    private MapperModel.Converter _namedImportedConverter(ExecutableElement method, GeneratedClass target,
                                                          TypeMirror from, TypeMirror to, String name, boolean explicitRef) {
        MapperModel.Converter result = null;
        int compatible = 0;
        boolean anyNamed = false;
        for (ImportedMapperRef imported : generation.importedMappers) {
            NamedMethodMatch match = _namedMethod(imported.type, method, target, name);
            if (match == null) continue;
            anyNamed = true;
            if (match.method == null) return null;
            MapperModel.Converter conv = _converterFromMethod(imported.type, method, target, match.method, from, to, imported);
            if (conv == null) continue;
            compatible++;
            if (compatible > 1) {
                _error(method, target, "Ambiguous imported converter '" + name + "'; qualify it with @MapperOptions(using = ...)");
                generation.failed = true;
                return null;
            }
            result = conv;
        }
        if (result == null && explicitRef) {
            _error(method, target, "Cannot resolve converter '" + name + "'");
            generation.failed = true;
        } else if (result == null && anyNamed) {
            _error(method, target, "Cannot resolve converter '" + name + "'");
            generation.failed = true;
        }
        return result;
    }

    private MapperModel.Converter _namedImportedConverter(ExecutableElement method, GeneratedClass target,
                                                          TypeMirror from, TypeMirror to, ImportedMapperRef imported,
                                                          String name, boolean explicitRef) {
        NamedMethodMatch match = _namedMethod(imported.type, method, target, name);
        if (match == null) {
            if (explicitRef) {
                _error(method, target, "Cannot resolve converter '" + imported.qualifiedName + "::" + name + "'");
                generation.failed = true;
            }
            return null;
        }
        if (match.method == null) return null;
        MapperModel.Converter conv = _converterFromMethod(imported.type, method, target, match.method, from, to, imported);
        if (conv == null && explicitRef && !generation.failed) {
            _error(method, target, "Cannot resolve converter '" + imported.qualifiedName + "::" + name + "'");
            generation.failed = true;
        } else if (conv == null) {
            generation.failed = true;
        }
        return conv;
    }

    private MapperModel.Converter _autoLocalConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                      TypeMirror from, TypeMirror to) {
        MapperModel.Converter found = null;
        for (Element e : iface.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) continue;
            ExecutableElement h = (ExecutableElement) e;
            if (h.getModifiers().contains(Modifier.PRIVATE) || h == method) continue;
            MapperModel.Converter conv = _compatibleConverter(iface, iface.asType(), method, target, h, from, to, null);
            if (conv == null) continue;
            if (found != null) {
                _error(method, target, "Ambiguous element/value converter; specify @MapperOptions(using = ...) preference");
                generation.failed = true;
                return null;
            }
            found = conv;
        }
        return found;
    }

    private MapperModel.Converter _autoImportedConverter(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        MapperModel.Converter found = null;
        for (ImportedMapperRef imported : generation.importedMappers) {
            for (Element e : imported.type.getEnclosedElements()) {
                if (e.getKind() != ElementKind.METHOD) continue;
                ExecutableElement h = (ExecutableElement) e;
                if (h.getModifiers().contains(Modifier.PRIVATE)) continue;
                MapperModel.Converter conv = _compatibleConverter(imported.type, imported.type.asType(), method, target, h, from, to, imported);
                if (conv == null) continue;
                if (found != null) {
                    _error(method, target, "Ambiguous imported element/value converter; specify @MapperOptions(using = ...) with mapper qualification");
                    generation.failed = true;
                    return null;
                }
                found = conv;
            }
        }
        return found;
    }

    private MapperModel.Converter _compatibleConverter(TypeElement owner, TypeMirror ownerType, ExecutableElement method, GeneratedClass target,
                                                       ExecutableElement h, TypeMirror from, TypeMirror to, ImportedMapperRef imported) {
        if (h.getParameters().size() != 1 || h.getReturnType().getKind() == TypeKind.VOID) return null;
        ExecutableType ht = (ExecutableType) ctx.types.asMemberOf((DeclaredType) ownerType, h);
        if (!_assignable(from, ht.getParameterTypes().get(0)) || !_assignable(ht.getReturnType(), to)) return null;
        return _converter(owner, ht, h, imported);
    }

    private NamedMethodMatch _namedMethod(TypeElement owner, ExecutableElement method, GeneratedClass target, String name) {
        ExecutableElement found = null;
        for (Element e : owner.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD || !e.getSimpleName().contentEquals(name)) continue;
            if (e.getModifiers().contains(Modifier.PRIVATE)) continue;
            if (found != null) {
                _error(method, target, "Ambiguous converter '" + name + "'; overloaded converter methods are not supported");
                generation.failed = true;
                return new NamedMethodMatch(null);
            }
            found = (ExecutableElement) e;
        }
        if (found == null) return null;
        return new NamedMethodMatch(found);
    }

    private ImportedMapperRef _importedMapperByName(ExecutableElement method, GeneratedClass target, String owner) {
        ImportedMapperRef found = null;
        for (ImportedMapperRef imported : generation.importedMappers) {
            if (!imported.simpleName.equals(owner) && !imported.qualifiedName.equals(owner)) continue;
            if (found != null && imported.simpleName.equals(owner)) {
                _error(method, target, "Ambiguous imported mapper '" + owner + "'; use the qualified name in @MapperOptions(using = ...)");
                generation.failed = true;
                return null;
            }
            found = imported;
        }
        if (found == null) {
            _error(method, target, "Cannot resolve imported mapper '" + owner + "'; it must be listed in @CompiledMapper.importing");
            generation.failed = true;
        }
        return found;
    }

    private MapperModel.Converter _converter(TypeElement owner, ExecutableType ht, ExecutableElement h, ImportedMapperRef imported) {
        String prefix;
        if (h.getModifiers().contains(Modifier.STATIC)) {
            prefix = owner.getQualifiedName() + ".";
        } else if (imported != null) {
            prefix = _ensureImportedMapperField(imported) + ".";
        } else {
            prefix = "";
        }
        return new MapperModel.Converter(prefix + h.getSimpleName(), ht.getReturnType());
    }

    private String _ensureImportedMapperField(ImportedMapperRef imported) {
        String existing = generation.importedMapperFields.get(imported.qualifiedName);
        if (existing != null) return existing;
        String field = generation.importedMapperFieldNames.local("m_" + _lowerFirst(imported.simpleName));
        generation.importedMapperFields.put(imported.qualifiedName, field);
        String implType = _compiledMapperImplType(imported.type);
        generation.target.addField(out -> out.line("private final " + imported.qualifiedName + " " + field + " = new " + implType + "();"));
        return field;
    }

    private String _lowerFirst(String value) {
        if (value == null || value.length() == 0) return "mapper";
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private List<ImportedMapperRef> _importedMappers(TypeElement iface, GeneratedClass target) {
        ArrayList<ImportedMapperRef> imported = new ArrayList<ImportedMapperRef>();
        Set<String> seen = new HashSet<String>();
        for (AnnotationMirror mirror : iface.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(CompiledMapper.class.getName())) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : ctx.elements.getElementValuesWithDefaults(mirror).entrySet()) {
                if (!e.getKey().getSimpleName().contentEquals("importing")) continue;
                Object raw = e.getValue().getValue();
                if (!(raw instanceof List)) return imported;
                for (Object value : (List<?>) raw) {
                    AnnotationValue av = (AnnotationValue) value;
                    Object tv = av.getValue();
                    if (!(tv instanceof TypeMirror)) continue;
                    TypeElement importedType = GeneratorUtil.asTypeElement((TypeMirror) tv);
                    if (importedType == null || importedType.getKind() != ElementKind.INTERFACE) {
                        _error(iface, target, "@CompiledMapper.importing supports only interfaces annotated with @CompiledMapper");
                        return null;
                    }
                    if (!_hasCompiledMapper(importedType)) {
                        _error(iface, target, "Imported mapper '" + importedType.getQualifiedName() + "' must be annotated with @CompiledMapper");
                        return null;
                    }
                    String qn = importedType.getQualifiedName().toString();
                    if (seen.add(qn)) imported.add(new ImportedMapperRef(importedType));
                }
                return imported;
            }
        }
        return imported;
    }

    private boolean _hasCompiledMapper(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(CompiledMapper.class.getName())) return true;
        }
        return false;
    }

    private String _compiledMapperImplType(TypeElement mapperType) {
        PackageElement pkg = ctx.elements.getPackageOf(mapperType);
        String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
        String binaryName = ctx.elements.getBinaryName(mapperType).toString();
        String packagePrefix = packageName.isEmpty() ? "" : packageName + ".";
        String binarySimpleName = packageName.isEmpty() ? binaryName : binaryName.substring(packagePrefix.length());
        String simpleName = binarySimpleName + GeneratorUtil.COMPILED_IMPL_POSTFIX;
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
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

    private MapperModel.Converter _arrayLikeConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                      TypeMirror from, TypeMirror to, String nestedMapper) {
        MapperModel.ArrayLikeType arrayFrom = _arrayLike(from);
        MapperModel.ContainerType containerTo = _container(to);
        if (arrayFrom == null || containerTo == null || containerTo.map) return null;
        MapperModel.Converter conv = _arrayLikeElementConverter(iface, method, target, arrayFrom, containerTo, nestedMapper);
        if (conv == null) return null;
        String impl = _implType(method, target, containerTo);
        if (impl == null) return null;
        String helper = _ensureArrayLikeHelper(target, arrayFrom, containerTo, conv, impl, to);
        return new MapperModel.Converter(helper, to);
    }

    private MapperModel.Converter _objectLikeConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                       TypeMirror from, TypeMirror to, String nestedMapper) {
        if (nestedMapper != null && nestedMapper.length() != 0) return null;
        if (!_objectLikeSource(from) || !_pojoTarget(to)) return null;
        String helper = GeneratorUtil.isObject(ctx, from)
                ? _ensureObjectDispatchHelper(iface, method, target, from, to)
                : _ensureObjectLikeHelper(iface, method, target, from, to, _objectLikeKind(from));
        return helper == null ? null : new MapperModel.Converter(helper, to);
    }

    private MapperModel.Converter _jsonObjectProjectionConverter(ExecutableElement method, GeneratedClass target,
                                                                 TypeMirror from, TypeMirror to, String nestedMapper) {
        if (nestedMapper != null && nestedMapper.length() != 0) return null;
        if (!_isExactJsonObject(to) || !_jsonObjectProjectionSource(from)) return null;
        if (!_validateJsonObjectProjectionSource(method, target, from)) return null;
        String helper = _ensureJsonObjectProjectionHelper(method, target, from);
        return helper == null ? null : new MapperModel.Converter(helper, to);
    }

    private boolean _isExactJsonObject(TypeMirror type) {
        return type != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(ctx.jsonObjectType));
    }

    private boolean _isExactJsonArray(TypeMirror type) {
        return type != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(ctx.jsonArrayType));
    }

    private boolean _isJojoTarget(TypeMirror type) {
        return type != null && !_isExactJsonObject(type)
                && GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType);
    }

    private boolean _isJajoTarget(TypeMirror type) {
        return type != null && !_isExactJsonArray(type)
                && GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType);
    }

    private boolean _jsonObjectProjectionSource(TypeMirror type) {
        if (type == null || _isScalarLike(type) || _arrayLike(type) != null) return false;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) return true;
        if (GeneratorUtil.isObject(ctx, type)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) return false;
        if (_container(type) != null) return false;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        return e != null && e.getKind() != ElementKind.ENUM;
    }

    private boolean _validateJsonObjectProjectionSource(ExecutableElement method, GeneratedClass target, TypeMirror from) {
        if (GeneratorUtil.isObject(ctx, from)) return true;
        if (!GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)) return true;
        MapperModel.ContainerType container = _container(from);
        if (container == null || container.key == null) return true;
        TypeMirror stringType = ctx.elements.getTypeElement("java.lang.String").asType();
        if (!_assignable(container.key, stringType) || !_assignable(stringType, container.key)) {
            _error(method, target, "JsonObject projection does not support Map key conversion; source key type must be java.lang.String");
            return false;
        }
        return true;
    }

    private boolean _jsonArrayProjectionSource(TypeMirror type) {
        return GeneratorUtil.isObject(ctx, type) || _arrayLike(type) != null || _listOrSetSource(type) != null;
    }

    private boolean _validateJsonArrayProjectionSource(ExecutableElement method, GeneratedClass target, TypeMirror from) {
        return true;
    }

    private String _ensureJsonObjectProjectionHelper(ExecutableElement method, GeneratedClass target, TypeMirror from) {
        String key = "jsonProjection:" + from;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String name = generation.helperName("JsonObject");
        generation.helpers.put(key, name);
        String mapHelper = GeneratorUtil.isObject(ctx, from) ? _ensureJsonObjectProjectionHelper(method, target, ctx.mapType) : null;
        TypeElement sourceType = GeneratorUtil.asTypeElement(from);
        Map<String, MapperModel.Read> reads = sourceType == null ? null : _reads(sourceType, from);
        target.addHelper(out -> {
            String paramType = GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType) ? "java.util.Map" : from.toString();
            out.line("");
            out.line("private org.sjf4j.JsonObject " + name + "(" + paramType + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            if (GeneratorUtil.isObject(ctx, from)) {
                out.line("if (!(source instanceof java.util.Map)) throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to 'org.sjf4j.JsonObject'\");");
                out.line("return " + mapHelper + "((java.util.Map) source);");
            } else {
            out.line("org.sjf4j.JsonObject target = new org.sjf4j.JsonObject();");
            if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType)) {
                out.line("for (java.util.Map.Entry<String, Object> entry : source.entrySet()) {");
                out.indent();
                out.line("target.put(entry.getKey(), entry.getValue());");
                out.dedent();
                out.line("}");
            } else if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)) {
                out.line("for (Object entryObj : source.entrySet()) {");
                out.indent();
                out.line("java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) entryObj;");
                out.line("target.put((String) entry.getKey(), entry.getValue());");
                out.dedent();
                out.line("}");
            } else {
                for (Map.Entry<String, MapperModel.Read> entry : reads.entrySet()) {
                    MapperModel.Read r = entry.getValue();
                    String access = r.method == null ? "source." + r.javaName : "source." + r.method.getSimpleName() + "()";
                    out.line("target.put(\"" + GeneratorUtil.escape(entry.getKey()) + "\", " + access + ");");
                }
            }
            out.line("return target;");
            }
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private boolean _rootMapProjectionSource(TypeMirror type) {
        if (type == null || _isScalarLike(type) || _arrayLike(type) != null) return false;
        if (GeneratorUtil.isObject(ctx, type)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) return false;
        if (_container(type) != null || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) return false;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        return e != null && e.getKind() != ElementKind.ENUM;
    }

    private boolean _validateRootMapProjectionSource(ExecutableElement method, GeneratedClass target, TypeMirror from, MapperModel.ContainerType to) {
        TypeMirror stringType = ctx.elements.getTypeElement("java.lang.String").asType();
        if (!_assignable(to.key, stringType) || !_assignable(stringType, to.key)) {
            _error(method, target, "Root Map projection from POJO/JsonObject/Object requires target key type java.lang.String");
            return false;
        }
        return true;
    }

    private String _ensureRootMapProjectionHelper(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                  TypeMirror from, MapperModel.ContainerType to, String nestedMapper) {
        String key = "mapProjection:" + from + "->" + to.mirror + ":" + nestedMapper;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String mapHelper = GeneratorUtil.isObject(ctx, from) ? _ensureRootMapProjectionHelper(iface, method, target, ctx.mapType, to, nestedMapper) : null;
        MapperModel.Converter valueConv;
        Map<String, MapperModel.Read> reads = null;
        if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)
                || GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType)
                || GeneratorUtil.isObject(ctx, from)) {
            valueConv = _resolveConverter(iface, method, target, ctx.objectType, to.value, nestedMapper);
        } else {
            TypeElement sourceType = GeneratorUtil.asTypeElement(from);
            reads = sourceType == null ? null : _reads(sourceType, from);
            valueConv = null;
        }
        if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)
                || GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType)
                || GeneratorUtil.isObject(ctx, from)) {
            if (valueConv == null) return null;
        }
        String name = generation.helperName("Map");
        generation.helpers.put(key, name);
        Map<String, MapperModel.Read> finalReads = reads;
        MapperModel.Converter finalValueConv = valueConv;
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            String value = names.prefixed("s", "value");
            String paramType = GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType) ? "java.util.Map" : from.toString();
            out.line("");
            out.line("private " + to.mirror + " " + name + "(" + paramType + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            if (GeneratorUtil.isObject(ctx, from)) {
                out.line("if (!(source instanceof java.util.Map)) throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to '" + GeneratorUtil.escape(to.mirror.toString()) + "'\");");
                out.line("return " + mapHelper + "((java.util.Map) source);");
            } else {
                String impl = _implType(method, target, to);
                out.line(_containerLocalType(impl, to, to.mirror) + " " + targetVar + " = " + _newContainer(impl, to,
                        (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType) || GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType))
                                ? "source.size()" : String.valueOf(finalReads.size())) + ";");
                if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)) {
                    out.line("for (Object entryObj : source.entrySet()) {");
                    out.indent();
                    out.line("java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) entryObj;");
                    out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(finalValueConv, "entry.getValue()") + ";");
                    out.line(targetVar + ".put((String) entry.getKey(), " + value + ");");
                    out.dedent();
                    out.line("}");
                } else if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType)) {
                    out.line("for (java.util.Map.Entry<String, Object> entry : source.entrySet()) {");
                    out.indent();
                    out.line(GeneratorUtil.localTypeName(ctx, to.value) + " " + value + " = " + _convertValue(finalValueConv, "entry.getValue()") + ";");
                    out.line(targetVar + ".put(entry.getKey(), " + value + ");");
                    out.dedent();
                    out.line("}");
                } else {
                    for (Map.Entry<String, MapperModel.Read> entry : finalReads.entrySet()) {
                        MapperModel.Read r = entry.getValue();
                        String access = r.method == null ? "source." + r.javaName : "source." + r.method.getSimpleName() + "()";
                        MapperModel.Converter conv = _assignable(r.type, to.value) ? new MapperModel.Converter(null, to.value) : _resolveConverter(iface, method, target, r.type, to.value, nestedMapper);
                        if (conv == null) return;
                        out.line(targetVar + ".put(\"" + GeneratorUtil.escape(entry.getKey()) + "\", " + _convertValue(conv, access) + ");");
                    }
                }
                out.line("return " + targetVar + ";");
            }
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private void _genJojoCreate(TypeElement iface, ExecutableElement method, GeneratedClass target) {
        if (method.getParameters().size() != 1) {
            _error(method, target, "JOJO create methods support exactly one source parameter");
            return;
        }
        VariableElement source = method.getParameters().get(0);
        if (!_jojoCreateSource(source.asType())) {
            _error(method, target, "JOJO create source must be a POJO, record, Map<String, ?>, JsonObject, or Object(runtime Map)");
            return;
        }
        if (!_jojoCreateAnnotationsSupported(method, target)) return;
        if (!_validateJojoCreateSource(method, target, source.asType(), method.getReturnType())) return;
        String helper = _ensureJojoCreateHelper(iface, method, target, source.asType(), method.getReturnType());
        if (helper == null) return;
        target.addMethod(out -> {
            out.line("");
            out.line("@Override");
            out.line("public " + method.getReturnType() + " " + method.getSimpleName() + "(" + source.asType() + " " + source.getSimpleName() + ") {");
            out.indent();
            out.line("return " + helper + "(" + source.getSimpleName() + ");");
            out.dedent();
            out.line("}");
        });
    }

    private boolean _jojoCreateSource(TypeMirror type) {
        if (type == null || _isScalarLike(type) || _arrayLike(type) != null) return false;
        if (GeneratorUtil.isObject(ctx, type)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType)) return true;
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) return true;
        if (_container(type) != null || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) return false;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        return e != null && e.getKind() != ElementKind.ENUM;
    }

    private boolean _jojoCreateAnnotationsSupported(ExecutableElement method, GeneratedClass target) {
        for (Mapping m : method.getAnnotationsByType(Mapping.class)) {
            if (_isAutoMarker(m)) continue;
            _error(method, target, "JOJO create targets currently support only auto same-name property mapping");
            return false;
        }
        if (method.getAnnotationsByType(MappingIfParentPresent.class).length != 0
                || method.getAnnotationsByType(EnsureMapping.class).length != 0) {
            _error(method, target, "JOJO create targets do not support target-path mappings");
            return false;
        }
        return true;
    }

    private boolean _validateJojoCreateSource(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        if (!GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)) return true;
        MapperModel.ContainerType container = _container(from);
        if (container == null || container.key == null) return true;
        TypeMirror stringType = ctx.elements.getTypeElement("java.lang.String").asType();
        if (!_assignable(container.key, stringType) || !_assignable(stringType, container.key)) {
            _error(method, target, "JOJO create from Map does not support Map key conversion; source key type must be java.lang.String");
            return false;
        }
        return true;
    }

    private String _ensureJojoCreateHelper(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        String key = "jojoCreate:" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String mapHelper = GeneratorUtil.isObject(ctx, from) ? _ensureJojoCreateHelper(iface, method, target, ctx.mapType, to) : null;
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        Map<String, MapperModel.Write> writes = _writes(targetType, to);
        MapperModel.Plan plan = _creation(method, target, targetType, to, writes);
        if (plan == null) return null;
        String name = generation.helperName("Jojo");
        generation.helpers.put(key, name);
        Map<String, MapperModel.Read> reads = null;
        if (!_dynamicSource(from)) {
            TypeElement sourceType = GeneratorUtil.asTypeElement(from);
            reads = sourceType == null ? null : _reads(sourceType, from);
        }
        Map<String, MapperModel.Read> finalReads = reads;
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            String paramType = GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType) ? "java.util.Map" : from.toString();
            out.line("");
            out.line("private " + to + " " + name + "(" + paramType + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            if (GeneratorUtil.isObject(ctx, from)) {
                out.line("if (!(source instanceof java.util.Map)) throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to '" + GeneratorUtil.escape(to.toString()) + "'\");");
                out.line("return " + mapHelper + "((java.util.Map) source);");
                out.dedent();
                out.line("}");
                return;
            }
            Map<String, String> ctorValues = new LinkedHashMap<String, String>();
            for (String prop : plan.names) {
                TypeMirror need = plan.writes.get(prop).type;
                String code;
                if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)) {
                    MapperModel.Converter conv = _assignable(GeneratorUtil.mapValueType(ctx, from), need)
                            ? new MapperModel.Converter(null, need)
                            : _resolveConverter(iface, method, target, GeneratorUtil.mapValueType(ctx, from), need, "");
                    if (conv == null) return;
                    code = _convertValue(conv, "source.get(\"" + GeneratorUtil.escape(prop) + "\")");
                } else if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType)) {
                    MapperModel.Converter conv = _resolveConverter(iface, method, target, ctx.objectType, need, "");
                    if (conv == null) return;
                    code = _convertValue(conv, "source.getNode(\"" + GeneratorUtil.escape(prop) + "\")");
                } else {
                    MapperModel.Read r = finalReads.get(prop);
                    if (r == null) {
                        _error(method, target, "Cannot map JOJO target property '" + prop + "': no same-name source property was found");
                        return;
                    }
                    String access = r.method == null ? "source." + r.javaName : "source." + r.method.getSimpleName() + "()";
                    MapperModel.Converter conv = _assignable(r.type, need) ? new MapperModel.Converter(null, need) : _resolveConverter(iface, method, target, r.type, need, "");
                    if (conv == null) return;
                    code = _convertValue(conv, access);
                }
                ctorValues.put(prop, code);
            }
            if (plan.ctor != null) {
                StringBuilder b = new StringBuilder(plan.type.toString()).append(" ").append(targetVar).append(" = new ").append(plan.type.toString()).append("(");
                for (int i = 0; i < plan.names.size(); i++) {
                    if (i != 0) b.append(", ");
                    b.append(ctorValues.get(plan.names.get(i)));
                }
                out.line(b.append(");").toString());
            } else {
                out.line(plan.type + " " + targetVar + " = " + (plan.create == null ? "new " + plan.type + "()" : plan.create) + ";");
                for (String prop : plan.names) {
                    MapperModel.Write w = plan.writes.get(prop);
                    if (w.setter != null) out.line(targetVar + "." + w.setter.getSimpleName() + "(" + ctorValues.get(prop) + ");");
                    else out.line(targetVar + "." + w.javaName + " = " + ctorValues.get(prop) + ";");
                }
            }
            if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.mapType)) {
                out.line("for (Object entryObj : source.entrySet()) {");
                out.indent();
                out.line("java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) entryObj;");
                _emitJojoExtraPut(out, targetVar, plan.names, "(String) entry.getKey()", "entry.getValue()");
                out.dedent();
                out.line("}");
            } else if (GeneratorUtil.isAssignableErasure(ctx, from, ctx.jsonObjectType)) {
                out.line("for (java.util.Map.Entry<String, Object> entry : source.entrySet()) {");
                out.indent();
                _emitJojoExtraPut(out, targetVar, plan.names, "entry.getKey()", "entry.getValue()");
                out.dedent();
                out.line("}");
            }
            out.line("return " + targetVar + ";");
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private void _emitJojoExtraPut(SourceWriter out, String targetVar, List<String> names, String key, String value) {
        if (names.isEmpty()) {
            out.line(targetVar + ".put(" + key + ", " + value + ");");
            return;
        }
        StringBuilder cond = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i != 0) cond.append(" && ");
            cond.append("!\"").append(GeneratorUtil.escape(names.get(i))).append("\".equals(").append(key).append(")");
        }
        out.line("if (" + cond + ") " + targetVar + ".put(" + key + ", " + value + ");");
    }

    private String _ensureJsonArrayProjectionHelper(GeneratedClass target, TypeMirror from, TypeMirror to) {
        String key = "jsonArrayProjection:" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String name = generation.helperName("JsonArray");
        generation.helpers.put(key, name);
        MapperModel.ArrayLikeType array = _arrayLike(from);
        MapperModel.ContainerType container = _container(from);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String value = names.prefixed("s", "value");
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line(to + " target = " + (_isExactJsonArray(to) ? "new org.sjf4j.JsonArray()" : "new " + to + "()") + ";");
            if (GeneratorUtil.isObject(ctx, from)) {
                out.line("if (!(source instanceof java.util.List)) throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to '" + GeneratorUtil.escape(to.toString()) + "'\");");
                out.line("for (Object " + value + " : (java.util.List) source) {");
                out.indent();
                out.line("target.add(" + value + ");");
                out.dedent();
                out.line("}");
            } else if (array != null) {
                _emitArrayLikeCopy(out, array, new MapperModel.Converter(null, array.value), "target", "source", names);
            } else {
                String itemType = GeneratorUtil.localTypeName(ctx, _containerSourceValueType(container));
                out.line("for (" + itemType + " " + value + " : source) {");
                    out.indent();
                out.line("target.add(" + value + ");");
                out.dedent();
                out.line("}");
            }
            out.line("return target;");
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private String _ensureJavaArrayHelper(GeneratedClass target, TypeMirror fromType, MapperModel.ArrayLikeType arrayFrom,
                                          MapperModel.ContainerType containerFrom, MapperModel.Converter conv,
                                          TypeMirror resultType, TypeMirror elementType) {
        String key = "javaArray:" + fromType + "->" + resultType + ":" + (conv.method == null ? "" : conv.method);
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String name = generation.helperName("Array");
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            NameAllocator names = new NameAllocator();
            names.reserve("source");
            String targetVar = names.local("target");
            String index = names.prefixed("s", "i");
            String value = names.prefixed("s", "value");
            out.line("");
            out.line("private " + resultType + " " + name + "(" + fromType + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line(resultType + " " + targetVar + " = new " + _arrayComponentTypeName(elementType) + "[" + (arrayFrom != null ? _arrayLikeSize(arrayFrom, "source") : "source.size()") + "];");
            if (arrayFrom != null) {
                out.line("for (int " + index + " = 0; " + index + " < " + _arrayLikeSize(arrayFrom, "source") + "; " + index + "++) {");
                out.indent();
                out.line(targetVar + "[" + index + "] = " + _convertValue(conv, _arrayLikeValue(arrayFrom, "source", index)) + ";");
                out.dedent();
                out.line("}");
            } else {
                out.line("int " + index + " = 0;");
                out.line("for (" + GeneratorUtil.localTypeName(ctx, _containerSourceValueType(containerFrom)) + " " + value + " : source) {");
                out.indent();
                out.line(targetVar + "[" + index + "++] = " + _convertValue(conv, value) + ";");
                out.dedent();
                out.line("}");
            }
            out.line("return " + targetVar + ";");
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private String _arrayComponentTypeName(TypeMirror type) {
        return type.getKind().isPrimitive() ? type.toString() : GeneratorUtil.typeName(GeneratorUtil.concrete(ctx, type));
    }

    private boolean _objectLikeSource(TypeMirror type) {
        return GeneratorUtil.isObject(ctx, type)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType);
    }

    private String _objectLikeKind(TypeMirror type) {
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType)) return "json";
        if (GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)) return "map";
        return "object";
    }

    private boolean _pojoTarget(TypeMirror type) {
        if (type == null || _isScalarLike(type) || _container(type) != null || _arrayLike(type) != null) return false;
        if (GeneratorUtil.isObject(ctx, type)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.mapType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonObjectType)
                || GeneratorUtil.isAssignableErasure(ctx, type, ctx.jsonArrayType)) return false;
        TypeElement e = GeneratorUtil.asTypeElement(type);
        return e != null && e.getKind() != ElementKind.ENUM;
    }

    private String _ensureObjectDispatchHelper(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        String key = "objectdispatch:" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        if (generation.inProgress.contains(key)) {
            _error(method, target, "Recursive automatic object mapper from " + from + " to " + to + " is unsupported; provide an explicit mapper");
            return null;
        }
        generation.inProgress.add(key);
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        Map<String, MapperModel.Write> writes = _writes(targetType, to);
        MapperModel.Plan plan = _creation(method, target, targetType, to, writes);
        if (plan == null) {
            generation.inProgress.remove(key);
            return null;
        }

        Map<String, MapperModel.Expr> jsonValues = _objectLikeValues(iface, method, target, plan, ctx.jsonObjectType, "s", "json");
        Map<String, MapperModel.Expr> mapValues = _objectLikeValues(iface, method, target, plan, ctx.objectType, "s", "map");
        if (jsonValues == null || mapValues == null) {
            generation.inProgress.remove(key);
            return null;
        }
        generation.inProgress.remove(key);

        String name = generation.helperName("Object");
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            out.line("if (" + GeneratorUtil.classLiteral(ctx, to) + ".isInstance(source)) return (" + GeneratorUtil.localTypeName(ctx, to) + ") source;");
            out.line("if (source instanceof org.sjf4j.JsonObject) {");
            out.indent();
            out.line("org.sjf4j.JsonObject s = (org.sjf4j.JsonObject) source;");
            _emitObjectLikeReturn(out, to, plan, jsonValues);
            out.dedent();
            out.line("}");
            out.line("if (source instanceof java.util.Map) {");
            out.indent();
            out.line("java.util.Map s = (java.util.Map) source;");
            _emitObjectLikeReturn(out, to, plan, mapValues);
            out.dedent();
            out.line("}");
            out.line("throw new org.sjf4j.exception.BindingException(\"cannot convert node from '\" + org.sjf4j.node.Types.name(source) + \"' to '" + GeneratorUtil.escape(to.toString()) + "'\");");
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private String _ensureObjectLikeHelper(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to, String kind) {
        String key = "objectlike:" + kind + ":" + from + "->" + to;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        if (generation.inProgress.contains(key)) {
            _error(method, target, "Recursive automatic object mapper from " + from + " to " + to + " is unsupported; provide an explicit mapper");
            return null;
        }
        generation.inProgress.add(key);
        TypeElement targetType = GeneratorUtil.asTypeElement(to);
        Map<String, MapperModel.Write> writes = _writes(targetType, to);
        MapperModel.Plan plan = _creation(method, target, targetType, to, writes);
        if (plan == null) {
            generation.inProgress.remove(key);
            return null;
        }
        Map<String, MapperModel.Expr> values = _objectLikeValues(iface, method, target, plan, from, "source", kind);
        if (values == null) {
            generation.inProgress.remove(key);
            return null;
        }
        generation.inProgress.remove(key);

        String name = generation.helperName("Object");
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            out.line("");
            out.line("private " + to + " " + name + "(" + from + " source) {");
            out.indent();
            out.line("if (source == null) return null;");
            _emitObjectLikeReturn(out, to, plan, values);
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private Map<String, MapperModel.Expr> _objectLikeValues(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                            MapperModel.Plan plan, TypeMirror from, String source, String kind) {
        Map<String, MapperModel.Expr> values = new LinkedHashMap<String, MapperModel.Expr>();
        for (String name : plan.names) {
            TypeMirror rawType = _objectLikeValueType(from, kind);
            MapperModel.Expr e = new MapperModel.Expr(_objectLikeRead(source, name, kind), rawType);
            e = _maybeNestedExpr(iface, method, target, e, plan.writes.get(name).type, "", name);
            if (e == null) return null;
            if (!_assignable(e.type, plan.writes.get(name).type)) {
                _error(method, target, "Cannot auto-map object target property '" + name + "': " + e.type + " is not assignable to " + plan.writes.get(name).type);
                return null;
            }
            values.put(name, e);
        }
        return values;
    }

    private TypeMirror _objectLikeValueType(TypeMirror from, String kind) {
        if ("map".equals(kind)) {
            MapperModel.ContainerType c = _container(from);
            return c == null || c.value == null ? ctx.objectType : c.value;
        }
        return ctx.objectType;
    }

    private String _objectLikeRead(String source, String name, String kind) {
        String key = GeneratorUtil.escape(name);
        return "json".equals(kind) ? source + ".getNode(\"" + key + "\")" : source + ".get(\"" + key + "\")";
    }

    private void _emitObjectLikeReturn(SourceWriter out, TypeMirror to, MapperModel.Plan plan, Map<String, MapperModel.Expr> values) {
        for (MapperModel.Expr e : values.values()) _emitTemps(out, e);
        if (plan.ctor != null) {
            StringBuilder b = new StringBuilder("return new ").append(plan.type).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
            return;
        }
        out.line(plan.type + " target = " + (plan.create == null ? "new " + plan.type + "()" : plan.create) + ";");
        for (String n : plan.names) {
            MapperModel.Expr e = values.get(n);
            MapperModel.Write w = plan.writes.get(n);
            if (w.setter != null) out.line("target." + w.setter.getSimpleName() + "(" + e.code + ");");
            else out.line("target." + w.javaName + " = " + e.code + ";");
        }
        out.line("return target;");
    }

    private MapperModel.Converter _arrayLikeElementConverter(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                            MapperModel.ArrayLikeType from, MapperModel.ContainerType to, String nestedMapper) {
        if (to.value == null) {
            _error(method, target, "Raw or non-parameterized collection types are unsupported");
            return null;
        }
        if ((nestedMapper == null || nestedMapper.length() == 0) && _assignable(from.value, to.value)) return new MapperModel.Converter(null, to.value);

        MapperModel.ArrayLikeType nestedArrayFrom = _arrayLike(from.value);
        MapperModel.ContainerType nestedContainerTo = _container(to.value);
        if (nestedArrayFrom != null && nestedContainerTo != null && !nestedContainerTo.map) {
            MapperModel.Converter nestedConv = _arrayLikeElementConverter(iface, method, target, nestedArrayFrom, nestedContainerTo, nestedMapper);
            if (nestedConv == null) return null;
            String impl = _implType(method, target, nestedContainerTo);
            if (impl == null) return null;
            String helper = _ensureArrayLikeHelper(target, nestedArrayFrom, nestedContainerTo, nestedConv, impl, to.value);
            return new MapperModel.Converter(helper, to.value);
        }

        MapperModel.ContainerType nestedContainerFrom = _container(from.value);
        if (nestedContainerFrom != null && nestedContainerTo != null && nestedContainerFrom.map == nestedContainerTo.map) {
            MapperModel.Converter nestedConv = _containerConverter(iface, method, target, nestedContainerFrom, nestedContainerTo, nestedMapper);
            if (nestedConv == null) return null;
            String impl = _implType(method, target, nestedContainerTo);
            if (impl == null) return null;
            String helper = _ensureContainerHelper(target, nestedContainerFrom, nestedContainerTo, nestedConv, impl, to.value);
            return new MapperModel.Converter(helper, to.value);
        }

        return _resolveConverter(iface, method, target, from.value, to.value, nestedMapper);
    }

    private MapperModel.Converter _scalarConverter(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        if (from == null || to == null || _assignable(from, to)) return null;
        TypeMirror boxedTo = GeneratorUtil.boxed(ctx, to);
        boolean sourceObject = GeneratorUtil.isObject(ctx, from);

        if (_isNumberType(boxedTo)) {
            if (sourceObject) {
                String helper = _ensureScalarHelper(method, target, from, boxedTo, _nodesNumberMethod(boxedTo), null);
                return helper == null ? null : new MapperModel.Converter(helper, boxedTo);
            }
            if (_isNumberType(from)) {
                String helper = _ensureScalarHelper(method, target, from, boxedTo, "number", null);
                return helper == null ? null : new MapperModel.Converter(helper, boxedTo);
            }
            return null;
        }

        if (_isStringType(boxedTo)) {
            if (sourceObject || _isCharacterType(from) || _isEnumType(from)) {
                String helper = _ensureScalarHelper(method, target, from, boxedTo, "toString", null);
                return helper == null ? null : new MapperModel.Converter(helper, boxedTo);
            }
            return null;
        }

        if (_isCharacterType(boxedTo)) {
            if (sourceObject || _isStringType(from) || _isCharacterType(from) || _isEnumType(from)) {
                String helper = _ensureScalarHelper(method, target, from, boxedTo, "toChar", null);
                return helper == null ? null : new MapperModel.Converter(helper, boxedTo);
            }
            return null;
        }

        if (_isBooleanType(boxedTo)) {
            if (sourceObject) {
                String helper = _ensureScalarHelper(method, target, from, boxedTo, "toBoolean", null);
                return helper == null ? null : new MapperModel.Converter(helper, boxedTo);
            }
            return null;
        }

        if (_isEnumType(boxedTo)) {
            if (sourceObject || _isStringType(from) || _isCharacterType(from) || _isEnumType(from)) {
                String helper = _ensureScalarHelper(method, target, from, boxedTo, "toEnum", boxedTo);
                return helper == null ? null : new MapperModel.Converter(helper, boxedTo);
            }
        }

        return null;
    }

    private String _ensureScalarHelper(ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror boxedTo,
                                       String kind, TypeMirror enumType) {
        if (kind == null) return null;
        String key = "scalar:" + kind + ":" + from + "->" + boxedTo;
        String existing = generation.helpers.get(key);
        if (existing != null) return existing;
        String name = generation.helperName("Scalar");
        generation.helpers.put(key, name);
        target.addHelper(out -> {
            out.line("");
            out.line("private " + GeneratorUtil.localTypeName(ctx, boxedTo) + " " + name + "(" + GeneratorUtil.localTypeName(ctx, from) + " source) {");
            out.indent();
            if (!from.getKind().isPrimitive()) out.line("if (source == null) return null;");
            if ("number".equals(kind)) {
                out.line("return org.sjf4j.node.Numbers.to(source, " + GeneratorUtil.classLiteral(ctx, boxedTo) + ");");
            } else if ("toEnum".equals(kind)) {
                out.line("return org.sjf4j.node.Nodes.toEnum(source, " + GeneratorUtil.classLiteral(ctx, enumType) + ");");
            } else {
                out.line("return org.sjf4j.node.Nodes." + kind + "(source);");
            }
            out.dedent();
            out.line("}");
        });
        return name;
    }

    private String _nodesNumberMethod(TypeMirror type) {
        String name = _qualifiedName(type);
        if ("java.lang.Number".equals(name)) return "toNumber";
        if ("java.lang.Long".equals(name)) return "toLong";
        if ("java.lang.Integer".equals(name)) return "toInt";
        if ("java.lang.Short".equals(name)) return "toShort";
        if ("java.lang.Byte".equals(name)) return "toByte";
        if ("java.lang.Double".equals(name)) return "toDouble";
        if ("java.lang.Float".equals(name)) return "toFloat";
        if ("java.math.BigInteger".equals(name)) return "toBigInteger";
        if ("java.math.BigDecimal".equals(name)) return "toBigDecimal";
        return null;
    }

    private boolean _isScalarLike(TypeMirror type) {
        return _isNumberType(type) || _isStringType(type) || _isCharacterType(type) || _isBooleanType(type) || _isEnumType(type);
    }

    private boolean _isNumberType(TypeMirror type) {
        if (type == null) return false;
        TypeElement number = ctx.elements.getTypeElement("java.lang.Number");
        return number != null && ctx.types.isAssignable(GeneratorUtil.boxed(ctx, type), number.asType());
    }

    private boolean _isStringType(TypeMirror type) {
        return "java.lang.String".equals(_qualifiedName(type));
    }

    private boolean _isCharacterType(TypeMirror type) {
        return "java.lang.Character".equals(_qualifiedName(type));
    }

    private boolean _isBooleanType(TypeMirror type) {
        return "java.lang.Boolean".equals(_qualifiedName(type));
    }

    private boolean _isEnumType(TypeMirror type) {
        TypeElement e = GeneratorUtil.asTypeElement(GeneratorUtil.boxed(ctx, type));
        return e != null && e.getKind() == ElementKind.ENUM;
    }

    private String _qualifiedName(TypeMirror type) {
        if (type == null) return "";
        TypeElement e = GeneratorUtil.asTypeElement(GeneratorUtil.boxed(ctx, type));
        return e == null ? "" : e.getQualifiedName().toString();
    }

    private MapperModel.Converter _autoHelperConverter(TypeElement iface, ExecutableElement method, GeneratedClass target, TypeMirror from, TypeMirror to) {
        if (_assignable(from, to)) return new MapperModel.Converter(null, to);
        if (_isScalarLike(from) || _isScalarLike(to)) return null;
        if (_container(from) != null || _container(to) != null || _arrayLike(from) != null || _arrayLike(to) != null) return null;
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
            StringBuilder b = new StringBuilder("return new ").append(plan.type).append("(");
            for (int i = 0; i < plan.names.size(); i++) {
                if (i != 0) b.append(", ");
                b.append(values.get(plan.names.get(i)).code);
            }
            out.line(b.append(");").toString());
        } else {
            out.line(plan.type + " " + targetVar + " = " + (plan.create == null ? "new " + plan.type + "()" : plan.create) + ";");
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

    private MapperModel.Converter _converterFromMethod(TypeElement iface, ExecutableElement method, GeneratedClass target,
                                                       ExecutableElement h, TypeMirror from, TypeMirror to, ImportedMapperRef imported) {
        if (h.getParameters().size() != 1 || h.getReturnType().getKind() == TypeKind.VOID) {
            _error(method, target, "Converter must have one parameter and a non-void return type");
            return null;
        }
        ExecutableType ht = (ExecutableType) ctx.types.asMemberOf((DeclaredType) iface.asType(), h);
        if (!_assignable(from, ht.getParameterTypes().get(0)) || !_assignable(ht.getReturnType(), to)) {
            if (imported == null) {
                _error(method, target, "Converter type mismatch");
            }
            return null;
        }
        return _converter(iface, ht, h, imported);
    }

    private String _implType(ExecutableElement method, GeneratedClass target, MapperModel.ContainerType to) {
        TypeElement declared = GeneratorUtil.asTypeElement(to.mirror);
        if (declared != null && !declared.getModifiers().contains(Modifier.ABSTRACT) && declared.getKind().isClass()) {
            for (ExecutableElement c : ElementFilter.constructorsIn(declared.getEnclosedElements())) {
                if (c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC)) return to.mirror.toString();
            }
        }
        if (to.map) return "java.util.LinkedHashMap";
        TypeMirror set = ctx.elements.getTypeElement("java.util.Set").asType();
        if (ctx.types.isAssignable(ctx.types.erasure(to.mirror), ctx.types.erasure(set))) {
            return "java.util.LinkedHashSet";
        }
        return "java.util.ArrayList";
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
            out.line("for (" + GeneratorUtil.localTypeName(ctx, _containerSourceValueType(from)) + " " + value + " : " + source + ") {");
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
        MapperModel.ArrayLikeType arrayFrom = _arrayLike(e.type);
        if ((from == null && arrayFrom == null) || to == null || (from != null && from.map) || to.map) {
            out.line("// unsupported array mapping; processor validation should have rejected this");
            return;
        }
        MapperModel.Converter conv = from == null
                ? _arrayLikeElementConverter(iface, method, target, arrayFrom, to, nestedMapper)
                : _containerConverter(iface, method, target, from, to, nestedMapper);
        if (conv == null) return;
        String access = read == null ? targetName + "." + w.javaName : (read.method == null ? targetName + "." + read.javaName : targetName + "." + read.method.getSimpleName() + "()");
        String source = e.code;
        if (!e.local) {
            source = state.names.prefixed("s", name);
            out.line(e.type + " " + source + " = " + e.code + ";");
        }
        out.line("if (" + source + " != null) {");
        out.indent();
        String sourceSize = from == null ? _arrayLikeSize(arrayFrom, source) : source + ".size()";
        if (read != null && w.setter != null) {
            out.line("if (" + access + " == null) " + targetName + "." + w.setter.getSimpleName() + "(" + _newContainer(_implType(method, target, to), to, sourceSize) + ");");
        } else if (read != null && read.method == null) {
            out.line("if (" + access + " == null) " + access + " = " + _newContainer(_implType(method, target, to), to, sourceSize) + ";");
        }
        if (policy == ArrayPolicy.CLEAR_ADD) out.line(access + ".clear();");
        if (from == null) _emitArrayLikeCopy(out, arrayFrom, conv, access, source, state.names);
        else _emitContainerCopy(out, from, to, conv, access, source, state.names);
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

    private static final class ImportedMapperRef {
        final TypeElement type;
        final String simpleName;
        final String qualifiedName;

        ImportedMapperRef(TypeElement type) {
            this.type = type;
            this.simpleName = type.getSimpleName().toString();
            this.qualifiedName = type.getQualifiedName().toString();
        }
    }

    private static final class NamedMethodMatch {
        final ExecutableElement method;

        NamedMethodMatch(ExecutableElement method) {
            this.method = method;
        }
    }

    private static final class MappingCreatorRef {
        final TypeMirror targetType;
        final TypeMirror implementation;
        final String creator;

        MappingCreatorRef(TypeMirror targetType, TypeMirror implementation, String creator) {
            this.targetType = targetType;
            this.implementation = implementation;
            this.creator = creator;
        }
    }

    private static final class MappingCreatorMatch {
        final TypeMirror type;
        final String create;

        MappingCreatorMatch(TypeMirror type, String create) {
            this.type = type;
            this.create = create;
        }
    }

    private static final class OneOfRef {
        final boolean shape;
        final String key;
        final boolean failbackNull;
        final List<OneOfMappingRef> mappings;

        OneOfRef(boolean shape, String key, boolean failbackNull, List<OneOfMappingRef> mappings) {
            this.shape = shape;
            this.key = key;
            this.failbackNull = failbackNull;
            this.mappings = mappings;
        }
    }

    private static final class OneOfMappingRef {
        final TypeMirror type;
        final String[] when;
        final String rawJsonType;

        OneOfMappingRef(TypeMirror type, String[] when, String rawJsonType) {
            this.type = type;
            this.when = when;
            this.rawJsonType = rawJsonType;
        }
    }

    private static final class OneOfBranchRef {
        final String[] when;
        final String rawJsonType;
        final MapperModel.Converter converter;

        OneOfBranchRef(String[] when, String rawJsonType, MapperModel.Converter converter) {
            this.when = when;
            this.rawJsonType = rawJsonType;
            this.converter = converter;
        }
    }

    private static final class GenerationState {
        int nextCodec;
        boolean failed;
        final Map<String, String> helpers = new HashMap<String, String>();
        final Map<String, String> importedMapperFields = new HashMap<String, String>();
        final NameAllocator importedMapperFieldNames = new NameAllocator();
        final Set<String> inProgress = new HashSet<String>();
        final NameAllocator helperNames = new NameAllocator();
        final List<ImportedMapperRef> importedMappers;
        final List<MappingCreatorRef> mappingCreators;
        final TypeElement iface;
        final GeneratedClass target;

        GenerationState(TypeElement iface, GeneratedClass target, List<ImportedMapperRef> importedMappers, List<MappingCreatorRef> mappingCreators) {
            this.iface = iface;
            this.importedMappers = importedMappers;
            this.mappingCreators = mappingCreators;
            this.target = target;
            for (Element e : iface.getEnclosedElements()) {
                if (e.getKind() == ElementKind.METHOD) helperNames.reserve(e.getSimpleName().toString());
            }
        }

        String helperName(String hint) {
            return helperNames.helper(hint);
        }
    }

}
