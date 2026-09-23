package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Resolves compile-time value conversion for object mapping.
 *
 * <p>This class performs conversion selection only. It does not emit Java
 * source and does not generate structural/container helper methods.</p>
 */
public final class ConverterResolver {

    private static final String COMPILED_MAPPER =
            "org.sjf4j.annotation.mapping.CompiledMapper";


    private final ProcessorContext context;
    private final TypeSystem types;

    private final TypeElement mapper;
    private final List<MappingPlan> plans;

    private final List<MethodCandidate> localMethods;
    private final List<ImportedMapper> importedMappers;


    public ConverterResolver(
            ProcessorContext context,
            TypeElement mapper,
            List<MappingPlan> plans) {

        this.context = context;
        this.types = context.types;
        this.mapper = mapper;
        this.plans =
                Collections.unmodifiableList(
                        new ArrayList<MappingPlan>(
                                plans));

        this.localMethods =
                Collections.unmodifiableList(
                        resolveLocalMethods());

        this.importedMappers =
                Collections.unmodifiableList(
                        resolveImportedMappers());
    }


    /**
     * Validates imported mapper declarations.
     *
     * <p>This should be called once before mapping compilation begins.</p>
     */
    public boolean validate(
            GeneratedClass generated) {

        boolean valid = true;

        for (ImportedMapper imported :
                importedMappers) {

            if (!hasAnnotation(
                    imported.type,
                    COMPILED_MAPPER)) {

                error(
                        mapper,
                        generated,
                        "imported mapper " +
                                imported.type
                                        .getQualifiedName() +
                                " is not annotated with @CompiledMapper");

                valid = false;
            }
        }

        for (MappingPlan plan :
                plans) {

            if (!validateReferences(
                    plan,
                    generated)) {

                valid = false;
            }
        }

        return valid;
    }


    private boolean validateReferences(
            MappingPlan plan,
            GeneratedClass generated) {

        boolean valid = true;

        for (String reference :
                using(plan)) {

            if (!validateReference(
                    plan,
                    reference,
                    "using",
                    generated)) {

                valid = false;
            }
        }

        for (MappingPlan.Rule rule :
                plan.rules()) {

            if (!rule.nestedMapper()
                    .isEmpty() &&
                    !validateReference(
                            plan,
                            rule.nestedMapper(),
                            "nestedMapper",
                            generated)) {

                valid = false;
            }
        }

        return valid;
    }


    private boolean validateReference(
            MappingPlan plan,
            String reference,
            String member,
            GeneratedClass generated) {

        String[] names =
                qualifiedReference(
                        plan,
                        reference,
                        member,
                        generated);

        if (names == null) {
            return false;
        }

        if (!referencedMethods(
                names[0],
                names[1]).isEmpty()) {

            return true;
        }

        error(
                plan.method(),
                generated,
                member + " reference '" +
                        reference +
                        "' cannot be resolved");

        return false;
    }


    // -------------------------------------------------------------------------
    // Resolve
    // -------------------------------------------------------------------------

    /**
     * Resolves conversion for one selected mapping value.
     *
     * @param plan mapper method currently being compiled
     * @param rule explicit mapping rule, or null for auto mapping
     * @param sourceType selected source value type
     * @param targetType required target value type
     *
     * @return resolved conversion, or null after reporting an error
     */
    public Conversion resolve(
            MappingPlan plan,
            MappingPlan.Rule rule,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated) {

        return resolve(
                plan,
                rule,
                sourceType,
                targetType,
                generated,
                false);
    }


    /**
     * Resolves a root conversion. The root method itself wins over other
     * automatic local candidates so MappingCompiler can expand it structurally.
     */
    public Conversion resolveRoot(
            MappingPlan plan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated) {

        return resolve(
                plan,
                null,
                sourceType,
                targetType,
                generated,
                true);
    }


    private Conversion resolve(
            MappingPlan plan,
            MappingPlan.Rule rule,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            boolean root) {

        if (sourceType == null ||
                targetType == null) {

            error(
                    plan.method(),
                    generated,
                    "cannot resolve mapping types");

            return null;
        }

        /*
         * Direct assignment is checked against the declared types before any
         * readable normalization. This preserves javac's wildcard/capture
         * safety and avoids turning a writable target capture into its upper
         * bound.
         */
        if (types.isAssignableBoxedGeneric(
                sourceType,
                targetType) &&
                !requiresRuntimeContainerConversion(
                        sourceType,
                        targetType)) {

            return Conversion.direct(
                    sourceType,
                    targetType);
        }

        /*
         * Source values are read, so an extends wildcard/type variable may be
         * normalized to its readable upper bound. A target value is written; a
         * top-level wildcard/type variable cannot be treated as its upper bound.
         */
        sourceType =
                types.concrete(
                        sourceType);

        if (targetType.getKind() ==
                TypeKind.WILDCARD ||
                targetType.getKind() ==
                        TypeKind.TYPEVAR) {

            error(
                    plan.method(),
                    generated,
                    "mapping target type is not writable: " +
                            targetType);

            return null;
        }

        /*
         * nestedMapper is explicit rather than a preference.
         */
        if (rule != null &&
                !rule.nestedMapper()
                        .isEmpty()) {

            return resolveExplicitMethod(
                    plan,
                    rule.nestedMapper(),
                    sourceType,
                    targetType,
                    generated);
        }

        /*
         * Method-level converter preferences.
         */
        List<String> using =
                using(plan);

        Conversion preferred =
                resolvePreferred(
                        using,
                        sourceType,
                        targetType,
                        plan,
                        generated);

        if (!generated.isValid()) {
            return null;
        }

        if (preferred != null) {
            return preferred;
        }

        /*
         * Type-level @OneOf is a structural dispatch strategy rather than an
         * ordinary mapper method.
         */
        if (context.annotations.hasOneOf(types.concrete(targetType))) {
            return Conversion.oneOf(
                    sourceType,
                    targetType);
        }

        /*
         * Local mapper methods.
         */
        if (root) {
            for (MethodCandidate candidate :
                    localMethods) {

                if (candidate.method == plan.method() &&
                        compatible(
                                candidate,
                                sourceType,
                                targetType)) {

                    return Conversion.method(
                            sourceType,
                            targetType,
                            candidate.method,
                            null);
                }
            }
        }

        MethodCandidate local =
                selectBestMethod(
                        localMethods,
                        sourceType,
                        targetType,
                        plan,
                        generated,
                        "local mapper");

        if (local == INVALID_METHOD) {
            return null;
        }

        if (local != null) {
            return Conversion.method(
                    sourceType,
                    targetType,
                    local.method,
                    null);
        }

        /*
         * Imported mapper methods.
         */
        List<MethodCandidate> imported =
                compatibleImportedMethods(
                        sourceType,
                        targetType);

        MethodCandidate importedMethod =
                selectBestMethod(
                        imported,
                        sourceType,
                        targetType,
                        plan,
                        generated,
                        "imported mapper");

        if (importedMethod == INVALID_METHOD) {
            return null;
        }

        if (importedMethod != null) {
            return Conversion.method(
                    sourceType,
                    targetType,
                    importedMethod.method,
                    importedMethod.owner);
        }

        /*
         * Recursive container conversion. Container generic arguments are
         * intentionally resolved directionally by ConversionCompiler:
         * ? extends T may contribute a readable source element type but never
         * a writable target element type.
         */
        if (isContainerConversion(
                sourceType,
                targetType)) {

            return Conversion.container(
                    sourceType,
                    targetType);
        }

        /*
         * Outside container conversion, unresolved generic structure remains
         * unsupported. Direct assignment and explicit mapper methods above are
         * still allowed when javac considers them type-safe.
         */
        if (!types.isFullyConcrete(sourceType) ||
                !types.isFullyConcrete(targetType)) {

            error(
                    plan.method(),
                    generated,
                    "compiled mapping requires fully concrete non-container types: " +
                            sourceType +
                            " -> " +
                            targetType);

            return null;
        }

        /*
         * Strict scalar conversion.
         */
        if (isScalarConversion(
                sourceType,
                targetType)) {

            return Conversion.scalar(
                    sourceType,
                    targetType);
        }

        /*
         * Structural OBNT conversion.
         */
        if (isStructuralConversion(
                sourceType,
                targetType)) {

            return Conversion.structural(
                    sourceType,
                    targetType);
        }

        error(
                plan.method(),
                generated,
                "cannot map value from " +
                        sourceType +
                        " to " +
                        targetType);

        return null;
    }


    // -------------------------------------------------------------------------
    // Explicit nested mapper
    // -------------------------------------------------------------------------

    private Conversion resolveExplicitMethod(
            MappingPlan plan,
            String reference,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated) {

        String[] names =
                qualifiedReference(
                        plan,
                        reference,
                        "nestedMapper",
                        generated);

        if (names == null) {
            return null;
        }

        List<MethodCandidate> candidates =
                referencedMethods(
                        names[0],
                        names[1]);

        if (candidates.isEmpty()) {
            error(
                    plan.method(),
                    generated,
                    "nestedMapper '" +
                            reference +
                            "' cannot be resolved");

            return null;
        }

        candidates = compatibleMethods(
                candidates,
                sourceType,
                targetType);

        if (candidates.isEmpty()) {
            error(
                    plan.method(),
                    generated,
                    "nestedMapper '" +
                            reference +
                            "' cannot map " +
                            sourceType +
                            " to " +
                            targetType);

            return null;
        }

        MethodCandidate candidate =
                selectBestMethod(
                        candidates,
                        sourceType,
                        targetType,
                        plan,
                        generated,
                        "nestedMapper '" +
                                reference +
                                "'");

        if (candidate == null ||
                candidate == INVALID_METHOD) {

            return null;
        }

        return Conversion.method(
                sourceType,
                targetType,
                candidate.method,
                candidate.owner);
    }


    // -------------------------------------------------------------------------
    // using
    // -------------------------------------------------------------------------

    private Conversion resolvePreferred(
            List<String> using,
            TypeMirror sourceType,
            TypeMirror targetType,
            MappingPlan plan,
            GeneratedClass generated) {

        for (String reference :
                using) {

            String[] names =
                    qualifiedReference(
                            plan,
                            reference,
                            "using",
                            generated);

            if (names == null) {
                return null;
            }

            List<MethodCandidate> candidates =
                    referencedMethods(
                            names[0],
                            names[1]);

            if (candidates.isEmpty()) {
                error(
                        plan.method(),
                        generated,
                        "preferred mapper '" +
                                reference +
                                "' cannot be resolved");

                return null;
            }

            candidates = compatibleMethods(
                    candidates,
                    sourceType,
                    targetType);

            if (candidates.isEmpty()) {
                continue;
            }

            MethodCandidate candidate =
                    selectBestMethod(
                            candidates,
                            sourceType,
                            targetType,
                            plan,
                            generated,
                            "preferred mapper '" +
                                    reference +
                                    "'");

            if (candidate == INVALID_METHOD) {
                return null;
            }

            if (candidate != null) {
                return Conversion.method(
                        sourceType,
                        targetType,
                        candidate.method,
                        candidate.owner);
            }
        }

        /*
         * using is deliberately a preference. If none of its entries match this
         * particular source/target pair, normal conversion resolution continues.
         */
        return null;
    }


    private String[] qualifiedReference(
            MappingPlan plan,
            String reference,
            String member,
            GeneratedClass generated) {

        if (reference == null) {
            error(
                    plan.method(),
                    generated,
                    member + " reference must use mapper::method");

            return null;
        }

        int separator =
                reference.indexOf("::");

        if (separator <= 0 ||
                separator != reference.lastIndexOf("::") ||
                separator + 2 >= reference.length()) {

            error(
                    plan.method(),
                    generated,
                    member + " reference '" +
                            reference +
                            "' must use mapper::method");

            return null;
        }

        return new String[]{
                reference.substring(0, separator),
                reference.substring(separator + 2)};
    }


    private List<MethodCandidate> referencedMethods(
            String mapperName,
            String methodName) {

        List<MethodCandidate> result =
                new ArrayList<MethodCandidate>();

        if ("this".equals(mapperName)) {
            addNamedMethods(
                    result,
                    localMethods,
                    methodName);

            return result;
        }

        for (ImportedMapper imported :
                importedMappers) {

            if (matchesMapperName(
                    imported.type,
                    mapperName)) {

                addNamedMethods(
                        result,
                        imported.methods,
                        methodName);
            }
        }

        return result;
    }


    private void addNamedMethods(
            List<MethodCandidate> result,
            List<MethodCandidate> methods,
            String methodName) {

        for (MethodCandidate candidate :
                methods) {

            if (candidate.method
                    .getSimpleName()
                    .contentEquals(methodName)) {

                result.add(candidate);
            }
        }
    }


    private List<MethodCandidate> compatibleMethods(
            List<MethodCandidate> candidates,
            TypeMirror sourceType,
            TypeMirror targetType) {

        List<MethodCandidate> result =
                new ArrayList<MethodCandidate>();

        for (MethodCandidate candidate :
                candidates) {

            if (compatible(
                    candidate,
                    sourceType,
                    targetType)) {

                result.add(candidate);
            }
        }

        return result;
    }


    private List<String> using(
            MappingPlan plan) {

        AnnotationMirror options =
                plan.options();

        if (options == null) {
            return Collections.emptyList();
        }

        return stringArrayValue(
                options,
                "using");
    }


    // -------------------------------------------------------------------------
    // Method selection
    // -------------------------------------------------------------------------

    private static final MethodCandidate INVALID_METHOD =
            new MethodCandidate(
                    null,
                    null,
                    null,
                    null);


    private MethodCandidate selectBestMethod(
            List<MethodCandidate> candidates,
            TypeMirror sourceType,
            TypeMirror targetType,
            MappingPlan plan,
            GeneratedClass generated,
            String description) {

        List<MethodCandidate> compatible =
                new ArrayList<MethodCandidate>();

        for (MethodCandidate candidate :
                candidates) {

            if (candidate != null &&
                    compatible(
                            candidate,
                            sourceType,
                            targetType)) {

                compatible.add(candidate);
            }
        }

        if (compatible.isEmpty()) {
            return null;
        }

        MethodCandidate best =
                compatible.get(0);

        for (int i = 1;
             i < compatible.size();
             i++) {

            MethodCandidate candidate =
                    compatible.get(i);

            int preference =
                    compare(
                            candidate,
                            best,
                            sourceType,
                            targetType);

            if (preference > 0) {
                best = candidate;
                continue;
            }

            if (preference == 0) {
                error(
                        plan.method(),
                        generated,
                        "ambiguous " +
                                description +
                                " methods for " +
                                sourceType +
                                " -> " +
                                targetType +
                                ": " +
                                methodName(best) +
                                " and " +
                                methodName(candidate));

                return INVALID_METHOD;
            }
        }

        return best;
    }


    /**
     * Returns positive when first is preferred, negative when second is
     * preferred, or zero when neither is more specific.
     */
    private int compare(
            MethodCandidate first,
            MethodCandidate second,
            TypeMirror sourceType,
            TypeMirror targetType) {

        boolean firstExactSource =
                types.isSameErasure(
                        first.sourceType,
                        sourceType);

        boolean secondExactSource =
                types.isSameErasure(
                        second.sourceType,
                        sourceType);

        if (firstExactSource !=
                secondExactSource) {

            return firstExactSource
                    ? 1
                    : -1;
        }

        boolean firstSourceMoreSpecific =
                types.isAssignableErasure(
                        first.sourceType,
                        second.sourceType);

        boolean secondSourceMoreSpecific =
                types.isAssignableErasure(
                        second.sourceType,
                        first.sourceType);

        if (firstSourceMoreSpecific !=
                secondSourceMoreSpecific) {

            return firstSourceMoreSpecific
                    ? 1
                    : -1;
        }

        boolean firstExactTarget =
                types.isSameErasure(
                        first.targetType,
                        targetType);

        boolean secondExactTarget =
                types.isSameErasure(
                        second.targetType,
                        targetType);

        if (firstExactTarget !=
                secondExactTarget) {

            return firstExactTarget
                    ? 1
                    : -1;
        }

        boolean firstTargetMoreSpecific =
                types.isAssignableErasure(
                        first.targetType,
                        second.targetType);

        boolean secondTargetMoreSpecific =
                types.isAssignableErasure(
                        second.targetType,
                        first.targetType);

        if (firstTargetMoreSpecific !=
                secondTargetMoreSpecific) {

            return firstTargetMoreSpecific
                    ? 1
                    : -1;
        }

        return 0;
    }


    private boolean compatible(
            MethodCandidate candidate,
            TypeMirror sourceType,
            TypeMirror targetType) {

        if (candidate.sourceType == null ||
                candidate.targetType == null) {

            return false;
        }

        /*
         * Generated call:
         *
         *     converter(sourceValue)
         *
         * Therefore sourceValue must be assignable to the method parameter.
         */
        if (!types.isAssignableBoxedGeneric(
                sourceType,
                candidate.sourceType)) {

            return false;
        }

        /*
         * Method result must be assignable to the requested target.
         */
        return types.isAssignableBoxedGeneric(
                candidate.targetType,
                targetType);
    }


    private String methodName(
            MethodCandidate candidate) {

        if (candidate.owner == null) {
            return candidate.method
                    .getSimpleName()
                    .toString();
        }

        return candidate.owner
                .getSimpleName() +
                "::" +
                candidate.method
                        .getSimpleName();
    }


    // -------------------------------------------------------------------------
    // Local mapper methods
    // -------------------------------------------------------------------------

    private List<MethodCandidate> resolveLocalMethods() {
        List<MethodCandidate> result =
                new ArrayList<MethodCandidate>();

        for (MappingPlan plan :
                plans) {

            if (!plan.create()) {
                continue;
            }

            if (plan.sources()
                    .size() != 1) {

                continue;
            }

            ExecutableElement method =
                    plan.method();

            result.add(
                    new MethodCandidate(
                            null,
                            method,
                            plan.primarySourceType(),
                            plan.targetType()));
        }

        for (Element member :
                mapper.getEnclosedElements()) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (!method.getModifiers()
                    .contains(Modifier.DEFAULT) ||
                    method.getReturnType()
                            .getKind() ==
                            TypeKind.VOID ||
                    method.getParameters()
                            .size() != 1 ||
                    !method.getTypeParameters()
                            .isEmpty()) {

                continue;
            }

            VariableElement source =
                    method.getParameters()
                            .get(0);

            result.add(
                    new MethodCandidate(
                            null,
                            method,
                            source.asType(),
                            method.getReturnType()));
        }

        return result;
    }


    // -------------------------------------------------------------------------
    // Imported mappers
    // -------------------------------------------------------------------------

    private List<ImportedMapper> resolveImportedMappers() {
        AnnotationMirror compiledMapper =
                findAnnotation(
                        mapper,
                        COMPILED_MAPPER);

        if (compiledMapper == null) {
            return Collections.emptyList();
        }

        List<TypeMirror> importedTypes =
                typeArrayValue(
                        compiledMapper,
                        "importing");

        List<ImportedMapper> result =
                new ArrayList<ImportedMapper>();

        Set<String> seen =
                new LinkedHashSet<String>();

        for (TypeMirror importedType :
                importedTypes) {

            TypeElement imported =
                    types.typeElement(
                            types.concrete(
                                    importedType));

            if (imported == null) {
                continue;
            }

            String name =
                    imported.getQualifiedName()
                            .toString();

            if (!seen.add(name)) {
                continue;
            }

            result.add(
                    new ImportedMapper(
                            imported,
                            resolveImportedMethods(
                                    imported)));
        }

        return result;
    }


    private List<MethodCandidate> resolveImportedMethods(
            TypeElement imported) {

        List<MethodCandidate> result =
                new ArrayList<MethodCandidate>();

        for (Element member :
                context.elements
                        .getAllMembers(imported)) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (method.getModifiers()
                    .contains(Modifier.STATIC)) {

                continue;
            }

            if (method.getReturnType()
                    .getKind() ==
                    TypeKind.VOID) {

                continue;
            }

            if (method.getParameters()
                    .size() != 1) {

                continue;
            }

            if (!method.getTypeParameters()
                    .isEmpty()) {

                continue;
            }

            javax.lang.model.type.ExecutableType methodType =
                    types.resolveMethodType(
                            imported.asType(),
                            method);

            if (methodType == null) {
                continue;
            }

            result.add(
                    new MethodCandidate(
                            imported,
                            method,
                            methodType.getParameterTypes()
                                    .get(0),
                            methodType.getReturnType()));
        }

        return result;
    }


    private List<MethodCandidate> compatibleImportedMethods(
            TypeMirror sourceType,
            TypeMirror targetType) {

        List<MethodCandidate> result =
                new ArrayList<MethodCandidate>();

        for (ImportedMapper imported :
                importedMappers) {

            if (!hasAnnotation(
                    imported.type,
                    COMPILED_MAPPER)) {

                continue;
            }

            for (MethodCandidate candidate :
                    imported.methods) {

                if (compatible(
                        candidate,
                        sourceType,
                        targetType)) {

                    result.add(candidate);
                }
            }
        }

        return result;
    }


    private boolean matchesMapperName(
            TypeElement mapper,
            String name) {

        return mapper.getSimpleName()
                .contentEquals(name)
                || mapper.getQualifiedName()
                .contentEquals(name);
    }


    // -------------------------------------------------------------------------
    // Conversion categories
    // -------------------------------------------------------------------------

    private boolean isScalarConversion(
            TypeMirror source,
            TypeMirror target) {

        source =
                types.concrete(source);

        target =
                types.concrete(target);

        NodeKind sourceKind =
                types.nodeKind(source);

        if (sourceKind ==
                NodeKind.COMPILE_TIME_UNKNOWN) {

            return isScalar(target);
        }

        NodeKind targetKind =
                types.nodeKind(target);

        /*
         * Number -> Number
         */
        if (isNumberKind(sourceKind) &&
                isNumberKind(targetKind)) {

            return true;
        }

        /*
         * String / char / enum / string-backed @NodeValue conversions.
         */
        if (isStringKind(sourceKind) &&
                isStringKind(targetKind)) {

            return true;
        }

        /*
         * Boolean is intentionally strict. Non-boolean coercions are not
         * generated.
         */
        if (isBooleanKind(sourceKind) &&
                isBooleanKind(targetKind)) {

            return true;
        }

        /*
         * @NodeValue participates according to its compile-time raw JSON kind.
         */
        if (types.isNodeValue(source) ||
                types.isNodeValue(target)) {

            return sameScalarFamily(
                    sourceKind,
                    targetKind);
        }

        return false;
    }


    private boolean isScalar(
            TypeMirror type) {

        NodeKind kind =
                types.nodeKind(type);

        return isStringKind(kind)
                || isNumberKind(kind)
                || isBooleanKind(kind)
                || kind == NodeKind.VALUE_NULL;
    }


    private boolean sameScalarFamily(
            NodeKind first,
            NodeKind second) {

        return isStringKind(first) &&
                isStringKind(second)
                || isNumberKind(first) &&
                isNumberKind(second)
                || isBooleanKind(first) &&
                isBooleanKind(second)
                || first == NodeKind.VALUE_NULL &&
                second == NodeKind.VALUE_NULL;
    }


    private boolean isStringKind(
            NodeKind kind) {

        switch (kind) {
            case VALUE_STRING:
            case VALUE_STRING_CHARACTER:
            case VALUE_STRING_ENUM:
            case VALUE_STRING_EXTERNAL:
                return true;

            default:
                return false;
        }
    }


    private boolean isNumberKind(
            NodeKind kind) {

        switch (kind) {
            case VALUE_NUMBER:
            case VALUE_NUMBER_EXTERNAL:
                return true;

            default:
                return false;
        }
    }


    private boolean isBooleanKind(
            NodeKind kind) {

        switch (kind) {
            case VALUE_BOOLEAN:
            case VALUE_BOOLEAN_EXTERNAL:
                return true;

            default:
                return false;
        }
    }


    private boolean isContainerConversion(
            TypeMirror source,
            TypeMirror target) {

        NodeKind sourceKind =
                types.nodeKind(source);

        NodeKind targetKind =
                types.nodeKind(target);

        if ((types.isArrayNode(sourceKind) ||
                sourceKind == NodeKind.COMPILE_TIME_UNKNOWN) &&
                types.isArrayNode(targetKind)) {

            return true;
        }

        /*
         * Map<K,V> -> Map<K2,V2> is recursive container conversion.
         *
         * Map -> POJO, POJO -> Map, JsonObject -> Map, etc. belong to
         * structural mapping instead.
         */
        return sourceKind == NodeKind.OBJECT_MAP &&
                targetKind == NodeKind.OBJECT_MAP;
    }


    /**
     * Raw Java containers expose Object members to the mapper type system. Do
     * not let Java's unchecked raw-to-parameterized assignment bypass SJF4J's
     * runtime member conversion when the target requires a concrete type.
     */
    private boolean requiresRuntimeContainerConversion(
            TypeMirror source,
            TypeMirror target) {

        NodeKind sourceKind =
                types.nodeKind(source);

        NodeKind targetKind =
                types.nodeKind(target);

        if (types.isArrayNode(sourceKind) &&
                types.isArrayNode(targetKind)) {

            return requiresRuntimeMemberConversion(
                    types.readElementType(source),
                    types.writeElementType(target));
        }

        if (sourceKind == NodeKind.OBJECT_MAP &&
                targetKind == NodeKind.OBJECT_MAP) {

            return requiresRuntimeMemberConversion(
                    types.mapReadKeyType(source),
                    types.mapWriteKeyType(target)) ||
                    requiresRuntimeMemberConversion(
                            types.mapReadValueType(source),
                            types.mapWriteValueType(target));
        }

        return false;
    }


    private boolean requiresRuntimeMemberConversion(
            TypeMirror source,
            TypeMirror target) {

        return source != null &&
                target != null &&
                types.isCompileTimeUnknown(source) &&
                !types.isObject(target);
    }


    private boolean isStructuralConversion(
            TypeMirror source,
            TypeMirror target) {

        NodeKind sourceKind =
                types.nodeKind(source);

        NodeKind targetKind =
                types.nodeKind(target);

        return (types.isObjectNode(sourceKind) ||
                sourceKind == NodeKind.COMPILE_TIME_UNKNOWN)
                && types.isObjectNode(targetKind);
    }

    // -------------------------------------------------------------------------
    // Annotation helpers
    // -------------------------------------------------------------------------

    private AnnotationMirror findAnnotation(
            Element element,
            String qualifiedName) {

        for (AnnotationMirror annotation :
                element.getAnnotationMirrors()) {

            Element type =
                    annotation.getAnnotationType()
                            .asElement();

            if (!(type instanceof
                    TypeElement)) {

                continue;
            }

            if (((TypeElement) type)
                    .getQualifiedName()
                    .contentEquals(
                            qualifiedName)) {

                return annotation;
            }
        }

        return null;
    }


    private boolean hasAnnotation(
            Element element,
            String qualifiedName) {

        return findAnnotation(
                element,
                qualifiedName) != null;
    }


    private AnnotationValue annotationValue(
            AnnotationMirror annotation,
            String name) {

        Map<? extends ExecutableElement,
                ? extends AnnotationValue> values =
                context.elements
                        .getElementValuesWithDefaults(
                                annotation);

        for (Map.Entry<? extends ExecutableElement,
                ? extends AnnotationValue> entry :
                values.entrySet()) {

            if (entry.getKey()
                    .getSimpleName()
                    .contentEquals(name)) {

                return entry.getValue();
            }
        }

        return null;
    }


    @SuppressWarnings("unchecked")
    private List<String> stringArrayValue(
            AnnotationMirror annotation,
            String name) {

        AnnotationValue value =
                annotationValue(
                        annotation,
                        name);

        if (value == null ||
                !(value.getValue() instanceof List)) {

            return Collections.emptyList();
        }

        List<String> result =
                new ArrayList<String>();

        for (AnnotationValue item :
                (List<? extends AnnotationValue>)
                        value.getValue()) {

            Object raw =
                    item.getValue();

            if (raw instanceof String) {
                String text =
                        ((String) raw)
                                .trim();

                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }

        return result;
    }


    @SuppressWarnings("unchecked")
    private List<TypeMirror> typeArrayValue(
            AnnotationMirror annotation,
            String name) {

        AnnotationValue value =
                annotationValue(
                        annotation,
                        name);

        if (value == null ||
                !(value.getValue() instanceof List)) {

            return Collections.emptyList();
        }

        List<TypeMirror> result =
                new ArrayList<TypeMirror>();

        for (AnnotationValue item :
                (List<? extends AnnotationValue>)
                        value.getValue()) {

            Object raw =
                    item.getValue();

            if (raw instanceof TypeMirror) {
                result.add(
                        (TypeMirror) raw);
            }
        }

        return result;
    }


    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    private void error(
            Element element,
            GeneratedClass generated,
            String message) {

        generated.invalidate();

        context.error(
                element,
                generated.originName() +
                        ": " +
                        message);
    }


    // -------------------------------------------------------------------------
    // Models
    // -------------------------------------------------------------------------

    /**
     * Selected conversion strategy.
     */
    static final class Conversion {

        enum Kind {
            DIRECT,
            METHOD,
            ONE_OF,
            SCALAR,
            CONTAINER,
            STRUCTURAL
        }


        private final Kind kind;

        private final TypeMirror sourceType;
        private final TypeMirror targetType;

        /*
         * METHOD only.
         */
        private final ExecutableElement method;

        /*
         * METHOD only.
         *
         * null means the method belongs to the current mapper.
         * non-null means the method belongs to an imported mapper.
         */
        private final TypeElement mapperType;


        private Conversion(
                Kind kind,
                TypeMirror sourceType,
                TypeMirror targetType,
                ExecutableElement method,
                TypeElement mapperType) {

            this.kind = kind;
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.method = method;
            this.mapperType = mapperType;
        }


        static Conversion direct(
                TypeMirror sourceType,
                TypeMirror targetType) {

            return new Conversion(
                    Kind.DIRECT,
                    sourceType,
                    targetType,
                    null,
                    null);
        }


        static Conversion method(
                TypeMirror sourceType,
                TypeMirror targetType,
                ExecutableElement method,
                TypeElement mapperType) {

            return new Conversion(
                    Kind.METHOD,
                    sourceType,
                    targetType,
                    method,
                    mapperType);
        }


        static Conversion oneOf(
                TypeMirror sourceType,
                TypeMirror targetType) {

            return new Conversion(
                    Kind.ONE_OF,
                    sourceType,
                    targetType,
                    null,
                    null);
        }


        static Conversion scalar(
                TypeMirror sourceType,
                TypeMirror targetType) {

            return new Conversion(
                    Kind.SCALAR,
                    sourceType,
                    targetType,
                    null,
                    null);
        }


        static Conversion container(
                TypeMirror sourceType,
                TypeMirror targetType) {

            return new Conversion(
                    Kind.CONTAINER,
                    sourceType,
                    targetType,
                    null,
                    null);
        }


        static Conversion structural(
                TypeMirror sourceType,
                TypeMirror targetType) {

            return new Conversion(
                    Kind.STRUCTURAL,
                    sourceType,
                    targetType,
                    null,
                    null);
        }


        Kind kind() {
            return kind;
        }

        TypeMirror sourceType() {
            return sourceType;
        }

        TypeMirror targetType() {
            return targetType;
        }

        ExecutableElement method() {
            return method;
        }

        TypeElement mapperType() {
            return mapperType;
        }

        boolean importedMethod() {
            return kind == Kind.METHOD &&
                    mapperType != null;
        }
    }


    private static final class MethodCandidate {

        /*
         * null for current mapper methods.
         */
        final TypeElement owner;

        final ExecutableElement method;

        final TypeMirror sourceType;
        final TypeMirror targetType;


        MethodCandidate(
                TypeElement owner,
                ExecutableElement method,
                TypeMirror sourceType,
                TypeMirror targetType) {

            this.owner = owner;
            this.method = method;
            this.sourceType = sourceType;
            this.targetType = targetType;
        }
    }


    private static final class ImportedMapper {

        final TypeElement type;
        final List<MethodCandidate> methods;


        ImportedMapper(
                TypeElement type,
                List<MethodCandidate> methods) {

            this.type = type;

            this.methods =
                    Collections.unmodifiableList(
                            new ArrayList<MethodCandidate>(
                                    methods));
        }
    }
}
