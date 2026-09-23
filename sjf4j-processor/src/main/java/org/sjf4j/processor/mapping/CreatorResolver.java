package org.sjf4j.processor.mapping;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Resolves creation strategy for mapper create targets.
 *
 * <p>Explicit {@code @MappingCreator} rules are considered before normal
 * constructor discovery. Method-level rules take precedence over mapper-level
 * rules. Mapper-level rules are inherited from parent mapper interfaces.</p>
 */
public final class CreatorResolver {

    private static final String MAPPING_CREATOR =
            "org.sjf4j.annotation.mapping.MappingCreator";

    private static final String MAPPING_CREATORS =
            "org.sjf4j.annotation.mapping.MappingCreators";


    private final ProcessorContext context;
    private final TypeSystem types;


    public CreatorResolver(ProcessorContext context) {
        this.context = context;
        this.types = context.types;
    }


    /**
     * Resolves creation for one create mapping method.
     *
     * @return creation model, or {@code null} after reporting a diagnostic
     */
    public Creation resolve(
            TypeElement mapper,
            MappingPlan plan,
            GeneratedClass generated) {

        if (!plan.create()) {
            throw new IllegalArgumentException(
                    "CreatorResolver supports create mappings only");
        }

        return resolve(
                mapper,
                plan.method(),
                plan.targetType(),
                generated);
    }


    /**
     * Resolves target creation for a non-MappingPlan caller such as JDBC.
     */
    public Creation resolve(
            TypeElement mapper,
            ExecutableElement method,
            TypeMirror targetType,
            GeneratedClass generated) {

        TypeMirror declaredTarget =
                types.concrete(
                        targetType);

        /*
         * Method-local creator rules have explicit precedence.
         */
        List<CreatorRule> methodRules =
                creatorRules(
                        method,
                        mapper);

        CreatorRule rule =
                selectRule(
                        declaredTarget,
                        methodRules,
                        method,
                        generated);

        if (rule == INVALID_RULE) {
            return null;
        }

        if (rule == null) {
            List<CreatorRule> mapperRules =
                    mapperCreatorRules(
                            mapper);

            rule =
                    selectRule(
                            declaredTarget,
                            mapperRules,
                            method,
                            generated);

            if (rule == INVALID_RULE) {
                return null;
            }
        }

        if (rule != null) {
            return resolveExplicit(
                    mapper,
                    method,
                    rule,
                    declaredTarget,
                    generated);
        }

        return resolveStandard(
                declaredTarget,
                declaredTarget,
                method,
                generated);
    }




    /*
     * --------------------------------------------------------------
     * Explicit @MappingCreator
     * --------------------------------------------------------------
     */

    private Creation resolveExplicit(
            TypeElement mapper,
            ExecutableElement method,
            CreatorRule rule,
            TypeMirror declaredTarget,
            GeneratedClass generated) {

        boolean hasImplementation =
                rule.implementation != null;

        boolean hasCreator =
                rule.creator != null &&
                        !rule.creator.isEmpty();

        if (hasImplementation ==
                hasCreator) {

            error(
                    method,
                    generated,
                    "@MappingCreator for " +
                            rule.targetType +
                            " must declare exactly one of implementation or creator");

            return null;
        }

        if (hasImplementation) {
            TypeMirror implementation =
                    types.concrete(
                            rule.implementation);

            if (!types.isAssignableBoxedGeneric(
                    implementation,
                    rule.targetType)) {

                error(
                        method,
                        generated,
                        "@MappingCreator implementation " +
                                implementation +
                                " is not assignable to targetType " +
                                rule.targetType);

                return null;
            }

            if (!types.isAssignableBoxedGeneric(
                    implementation,
                    declaredTarget)) {

                error(
                        method,
                        generated,
                        "@MappingCreator implementation " +
                                implementation +
                                " is not assignable to mapper target " +
                                declaredTarget);

                return null;
            }

            /*
             * An implementation is only a type substitution. It must satisfy
             * normal target construction rules itself.
             */
            return resolveStandard(
                    declaredTarget,
                    implementation,
                    method,
                    generated);
        }

        return resolveFactory(
                mapper,
                method,
                rule,
                declaredTarget,
                generated);
    }


    private Creation resolveFactory(
            TypeElement mapper,
            ExecutableElement method,
            CreatorRule rule,
            TypeMirror declaredTarget,
            GeneratedClass generated) {

        String creator =
                rule.creator;

        if (!creator.startsWith("this::") ||
                creator.length() <= 6) {

            error(
                    method,
                    generated,
                    "@MappingCreator creator must use the form this::method");

            return null;
        }

        String methodName =
                creator.substring(6);

        ExecutableElement factory =
                findFactoryMethod(
                        mapper,
                        rule.owner,
                        methodName);

        if (factory == null) {
            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' was not found");

            return null;
        }

        if (!factory.getParameters()
                .isEmpty()) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' must not declare parameters");

            return null;
        }

        if (!factory.getTypeParameters()
                .isEmpty()) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' cannot declare type parameters");

            return null;
        }

        Set<Modifier> modifiers =
                factory.getModifiers();

        if (!modifiers.contains(Modifier.DEFAULT) &&
                !modifiers.contains(Modifier.STATIC)) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' must be default or static");

            return null;
        }

        if (modifiers.contains(Modifier.PRIVATE)) {
            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' is not accessible");

            return null;
        }

        TypeMirror returnType =
                factoryReturnType(
                        mapper,
                        factory);

        if (returnType == null ||
                returnType.getKind() ==
                        TypeKind.VOID) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' must return a target value");

            return null;
        }

        if (!types.isAssignableBoxedGeneric(
                returnType,
                rule.targetType)) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' returns " +
                            returnType +
                            ", which is not assignable to targetType " +
                            rule.targetType);

            return null;
        }

        if (!types.isAssignableBoxedGeneric(
                returnType,
                declaredTarget)) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' returns " +
                            returnType +
                            ", which is not assignable to mapper target " +
                            declaredTarget);

            return null;
        }

        TypeMirror workingType =
                types.concrete(
                        returnType);

        TypeElement workingElement =
                types.typeElement(
                        workingType);

        if (workingElement == null) {
            error(
                    method,
                    generated,
                    "@MappingCreator factory method '" +
                            methodName +
                            "' must return a declared object type");

            return null;
        }

        if (!isTypeAccessible(
                workingElement,
                generatedPackage(method))) {

            error(
                    method,
                    generated,
                    "@MappingCreator factory return type " +
                            workingType +
                            " is not accessible from generated code");

            return null;
        }

        return Creation.factory(
                declaredTarget,
                workingType,
                factory,
                factory.getModifiers()
                        .contains(Modifier.STATIC)
                        ? rule.owner
                        : null);
    }


    /**
     * Searches normal inherited members first. Static methods are not inherited,
     * so the rule-declaring interface is checked separately.
     */
    private ExecutableElement findFactoryMethod(
            TypeElement mapper,
            TypeElement ruleOwner,
            String name) {

        for (Element member :
                context.elements
                        .getAllMembers(mapper)) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            if (!member.getSimpleName()
                    .contentEquals(name)) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (!method.getParameters()
                    .isEmpty()) {

                continue;
            }

            if (method.getModifiers()
                    .contains(Modifier.DEFAULT)) {

                return method;
            }

            if (method.getModifiers()
                    .contains(Modifier.STATIC) &&
                    method.getEnclosingElement()
                            .equals(mapper)) {

                return method;
            }
        }

        /*
         * Static methods declared on a parent mapper are not inherited.
         */
        if (ruleOwner != null) {
            for (Element member :
                    ruleOwner.getEnclosedElements()) {

                if (member.getKind() !=
                        ElementKind.METHOD ||
                        !member.getSimpleName()
                                .contentEquals(name)) {

                    continue;
                }

                ExecutableElement method =
                        (ExecutableElement) member;

                if (method.getModifiers()
                        .contains(Modifier.STATIC) &&
                        method.getParameters()
                                .isEmpty()) {

                    return method;
                }
            }
        }

        return null;
    }


    private TypeMirror factoryReturnType(
            TypeElement mapper,
            ExecutableElement factory) {

        if (factory.getModifiers()
                .contains(Modifier.STATIC)) {

            return factory.getReturnType();
        }

        TypeMirror mapperType =
                mapper.asType();

        if (!(mapperType instanceof
                DeclaredType)) {

            return factory.getReturnType();
        }

        try {
            ExecutableType resolved =
                    (ExecutableType)
                            context.typeUtils
                                    .asMemberOf(
                                            (DeclaredType) mapperType,
                                            factory);

            return resolved.getReturnType();

        } catch (IllegalArgumentException e) {
            /*
             * Defensive fallback for unusual inherited interface models.
             */
            return factory.getReturnType();
        }
    }


    /*
     * --------------------------------------------------------------
     * Standard Construction
     * --------------------------------------------------------------
     */

    private Creation resolveStandard(
            TypeMirror declaredTarget,
            TypeMirror workingType,
            ExecutableElement method,
            GeneratedClass generated) {

        workingType =
                types.concrete(
                        workingType);

        TypeElement type =
                types.typeElement(
                        workingType);

        if (type == null) {
            error(
                    method,
                    generated,
                    "cannot create target type " +
                            workingType);

            return null;
        }

        if (type.getKind() ==
                ElementKind.INTERFACE) {

            error(
                    method,
                    generated,
                    "target type " +
                            workingType +
                            " is an interface and requires @MappingCreator");

            return null;
        }

        if (type.getModifiers()
                .contains(Modifier.ABSTRACT)) {

            error(
                    method,
                    generated,
                    "target type " +
                            workingType +
                            " is abstract and requires @MappingCreator");

            return null;
        }

        PackageElement generatedPackage =
                generatedPackage(method);

        if (!isTypeAccessible(
                type,
                generatedPackage)) {

            error(
                    method,
                    generated,
                    "target type " +
                            workingType +
                            " is not accessible from generated code");

            return null;
        }

        if (isRecord(type)) {
            ExecutableElement constructor =
                    recordConstructor(type);

            if (constructor == null) {
                error(
                        method,
                        generated,
                        "cannot resolve canonical constructor for record " +
                                workingType);

                return null;
            }

            return constructorCreation(
                    declaredTarget,
                    workingType,
                    constructor);
        }

        List<ExecutableElement> publicConstructors =
                publicConstructors(type);

        /*
         * No declared constructor means an implicit no-args constructor.
         */
        if (constructors(type).isEmpty()) {
            return Creation.noArgs(
                    declaredTarget,
                    workingType);
        }

        for (ExecutableElement constructor :
                publicConstructors) {

            if (constructor.getParameters()
                    .isEmpty()) {

                return Creation.noArgs(
                        declaredTarget,
                        workingType);
            }
        }

        if (publicConstructors.size() == 1) {
            return constructorCreation(
                    declaredTarget,
                    workingType,
                    publicConstructors.get(0));
        }

        if (publicConstructors.isEmpty()) {
            error(
                    method,
                    generated,
                    "target type " +
                            workingType +
                            " has no public constructor");

        } else {
            error(
                    method,
                    generated,
                    "target type " +
                            workingType +
                            " has multiple public constructors and no public no-args constructor");
        }

        return null;
    }


    private Creation constructorCreation(
            TypeMirror declaredTarget,
            TypeMirror workingType,
            ExecutableElement constructor) {

        List<? extends TypeMirror> parameterTypes =
                resolvedConstructorParameterTypes(
                        workingType,
                        constructor);

        return Creation.constructor(
                declaredTarget,
                workingType,
                constructor,
                parameterTypes);
    }


    private List<? extends TypeMirror> resolvedConstructorParameterTypes(
            TypeMirror owner,
            ExecutableElement constructor) {

        if (!(owner instanceof
                DeclaredType)) {

            List<TypeMirror> result =
                    new ArrayList<TypeMirror>();

            for (VariableElement parameter :
                    constructor.getParameters()) {

                result.add(
                        parameter.asType());
            }

            return result;
        }

        try {
            ExecutableType type =
                    (ExecutableType)
                            context.typeUtils
                                    .asMemberOf(
                                            (DeclaredType) owner,
                                            constructor);

            return new ArrayList<TypeMirror>(
                    type.getParameterTypes());

        } catch (IllegalArgumentException e) {
            List<TypeMirror> result =
                    new ArrayList<TypeMirror>();

            for (VariableElement parameter :
                    constructor.getParameters()) {

                result.add(
                        parameter.asType());
            }

            return result;
        }
    }


    private List<ExecutableElement> constructors(
            TypeElement type) {

        List<ExecutableElement> result =
                new ArrayList<ExecutableElement>();

        for (Element element :
                type.getEnclosedElements()) {

            if (element.getKind() ==
                    ElementKind.CONSTRUCTOR) {

                result.add(
                        (ExecutableElement) element);
            }
        }

        return result;
    }


    private List<ExecutableElement> publicConstructors(
            TypeElement type) {

        List<ExecutableElement> result =
                new ArrayList<ExecutableElement>();

        for (ExecutableElement constructor :
                constructors(type)) {

            if (constructor.getModifiers()
                    .contains(Modifier.PUBLIC)) {

                result.add(constructor);
            }
        }

        return result;
    }


    private ExecutableElement recordConstructor(
            TypeElement type) {

        List<Element> components =
                new ArrayList<Element>();

        for (Element element :
                type.getEnclosedElements()) {

            if ("RECORD_COMPONENT".equals(
                    element.getKind().name())) {

                components.add(element);
            }
        }

        for (ExecutableElement constructor :
                constructors(type)) {

            List<? extends VariableElement> parameters =
                    constructor.getParameters();

            if (parameters.size() !=
                    components.size()) {

                continue;
            }

            boolean match = true;

            for (int i = 0;
                 i < parameters.size();
                 i++) {

                if (!parameters.get(i)
                        .getSimpleName()
                        .contentEquals(
                                components.get(i)
                                        .getSimpleName())) {

                    match = false;
                    break;
                }
            }

            if (match) {
                return constructor;
            }
        }

        return null;
    }


    private boolean isRecord(
            TypeElement type) {

        return "RECORD".equals(
                type.getKind().name());
    }


    /*
     * --------------------------------------------------------------
     * Creator Rule Selection
     * --------------------------------------------------------------
     */

    private static final CreatorRule INVALID_RULE =
            new CreatorRule(
                    null,
                    null,
                    null,
                    null,
                    null);


    private CreatorRule selectRule(
            TypeMirror actualTarget,
            List<CreatorRule> rules,
            ExecutableElement method,
            GeneratedClass generated) {

        List<CreatorRule> matches =
                new ArrayList<CreatorRule>();

        for (CreatorRule rule :
                rules) {

            /*
             * The actual mapper target must be assignable to the rule's
             * targetType. A rule for a supertype therefore applies to its
             * concrete subtypes.
             */
            if (rule.targetType != null &&
                    types.isAssignableErasure(
                            actualTarget,
                            rule.targetType)) {

                matches.add(rule);
            }
        }

        if (matches.isEmpty()) {
            return null;
        }

        CreatorRule best =
                null;

        for (CreatorRule candidate :
                matches) {

            if (best == null) {
                best = candidate;
                continue;
            }

            boolean candidateMoreSpecific =
                    types.isAssignableErasure(
                            candidate.targetType,
                            best.targetType);

            boolean bestMoreSpecific =
                    types.isAssignableErasure(
                            best.targetType,
                            candidate.targetType);

            if (candidateMoreSpecific &&
                    !bestMoreSpecific) {

                best = candidate;
                continue;
            }

            if (!candidateMoreSpecific &&
                    !bestMoreSpecific) {

                error(
                        method,
                        generated,
                        "ambiguous @MappingCreator rules for target " +
                                actualTarget +
                                ": " +
                                best.targetType +
                                " and " +
                                candidate.targetType);

                return INVALID_RULE;
            }

            if (candidateMoreSpecific &&
                    bestMoreSpecific) {

                /*
                 * Same effective target type declared twice.
                 */
                error(
                        method,
                        generated,
                        "duplicate @MappingCreator rules for targetType " +
                                candidate.targetType);

                return INVALID_RULE;
            }
        }

        return best;
    }


    private List<CreatorRule> creatorRules(
            Element element,
            TypeElement owner) {

        List<CreatorRule> result =
                new ArrayList<CreatorRule>();

        readCreatorRules(
                element,
                owner,
                result);

        return result;
    }


    private List<CreatorRule> mapperCreatorRules(
            TypeElement mapper) {

        List<CreatorRule> result =
                new ArrayList<CreatorRule>();

        Set<String> visited =
                new LinkedHashSet<String>();

        collectMapperCreatorRules(
                mapper,
                result,
                visited);

        return result;
    }


    private void collectMapperCreatorRules(
            TypeElement mapper,
            List<CreatorRule> result,
            Set<String> visited) {

        String name =
                mapper.getQualifiedName()
                        .toString();

        if (!visited.add(name)) {
            return;
        }

        readCreatorRules(
                mapper,
                mapper,
                result);

        for (TypeMirror superType :
                mapper.getInterfaces()) {

            TypeElement parent =
                    types.typeElement(
                            types.concrete(
                                    superType));

            if (parent != null) {
                collectMapperCreatorRules(
                        parent,
                        result,
                        visited);
            }
        }
    }


    private void readCreatorRules(
            Element element,
            TypeElement owner,
            List<CreatorRule> result) {

        for (AnnotationMirror annotation :
                element.getAnnotationMirrors()) {

            String name =
                    annotationName(
                            annotation);

            if (MAPPING_CREATOR.equals(name)) {
                CreatorRule rule =
                        creatorRule(
                                annotation,
                                owner);

                if (rule != null) {
                    result.add(rule);
                }

            } else if (MAPPING_CREATORS.equals(name)) {
                for (AnnotationMirror nested :
                        annotationArray(
                                annotation,
                                "value")) {

                    CreatorRule rule =
                            creatorRule(
                                    nested,
                                    owner);

                    if (rule != null) {
                        result.add(rule);
                    }
                }
            }
        }
    }


    private CreatorRule creatorRule(
            AnnotationMirror annotation,
            TypeElement owner) {

        TypeMirror targetType =
                typeValue(
                        annotation,
                        "targetType");

        if (targetType == null) {
            return null;
        }

        TypeMirror implementation =
                hasExplicitValue(
                        annotation,
                        "implementation")
                        ? typeValue(
                        annotation,
                        "implementation")
                        : null;

        String creator =
                stringValue(
                        annotation,
                        "creator");

        if (creator != null) {
            creator =
                    creator.trim();

            if (creator.isEmpty()) {
                creator = null;
            }
        }

        return new CreatorRule(
                annotation,
                owner,
                types.concrete(
                        targetType),
                implementation == null
                        ? null
                        : types.concrete(
                        implementation),
                creator);
    }


    /*
     * --------------------------------------------------------------
     * Annotation Helpers
     * --------------------------------------------------------------
     */

    private String annotationName(
            AnnotationMirror annotation) {

        Element element =
                annotation.getAnnotationType()
                        .asElement();

        return element instanceof TypeElement
                ? ((TypeElement) element)
                .getQualifiedName()
                .toString()
                : "";
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


    private boolean hasExplicitValue(
            AnnotationMirror annotation,
            String name) {

        for (ExecutableElement member :
                annotation.getElementValues()
                        .keySet()) {

            if (member.getSimpleName()
                    .contentEquals(name)) {

                return true;
            }
        }

        return false;
    }


    private String stringValue(
            AnnotationMirror annotation,
            String name) {

        AnnotationValue value =
                annotationValue(
                        annotation,
                        name);

        if (value == null) {
            return null;
        }

        Object raw =
                value.getValue();

        return raw instanceof String
                ? (String) raw
                : null;
    }


    private TypeMirror typeValue(
            AnnotationMirror annotation,
            String name) {

        AnnotationValue value =
                annotationValue(
                        annotation,
                        name);

        if (value == null) {
            return null;
        }

        Object raw =
                value.getValue();

        return raw instanceof TypeMirror
                ? (TypeMirror) raw
                : null;
    }


    @SuppressWarnings("unchecked")
    private List<AnnotationMirror> annotationArray(
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

        List<AnnotationMirror> result =
                new ArrayList<AnnotationMirror>();

        for (AnnotationValue item :
                (List<? extends AnnotationValue>)
                        value.getValue()) {

            if (item.getValue() instanceof
                    AnnotationMirror) {

                result.add(
                        (AnnotationMirror)
                                item.getValue());
            }
        }

        return result;
    }


    /*
     * --------------------------------------------------------------
     * Accessibility
     * --------------------------------------------------------------
     */

    private PackageElement generatedPackage(
            Element method) {

        return context.elements
                .getPackageOf(method);
    }


    private boolean isTypeAccessible(
            TypeElement type,
            PackageElement generatedPackage) {

        Element current =
                type;

        while (current instanceof
                TypeElement) {

            TypeElement currentType =
                    (TypeElement) current;

            Set<Modifier> modifiers =
                    currentType.getModifiers();

            if (modifiers.contains(
                    Modifier.PRIVATE)) {

                return false;
            }

            if (!modifiers.contains(
                    Modifier.PUBLIC)) {

                PackageElement ownerPackage =
                        context.elements
                                .getPackageOf(
                                        currentType);

                if (!ownerPackage.equals(
                        generatedPackage)) {

                    return false;
                }
            }

            current =
                    currentType
                            .getEnclosingElement();
        }

        return true;
    }


    /*
     * --------------------------------------------------------------
     * Diagnostics
     * --------------------------------------------------------------
     */

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


    /*
     * --------------------------------------------------------------
     * Models
     * --------------------------------------------------------------
     */

    /**
     * Fully resolved target creation strategy.
     */
    public static final class Creation {

        public enum Kind {
            NO_ARGS,
            CONSTRUCTOR,
            FACTORY
        }


        private final Kind kind;

        /*
         * Type declared by the mapper method.
         */
        private final TypeMirror declaredType;

        /*
         * Concrete/static type used by generated mapping code.
         */
        private final TypeMirror targetType;

        private final ExecutableElement constructor;
        private final List<? extends TypeMirror> parameterTypes;

        private final ExecutableElement factory;

        /*
         * Non-null only for a static factory. It identifies the interface that
         * owns the static method.
         */
        private final TypeElement factoryOwner;


        private Creation(
                Kind kind,
                TypeMirror declaredType,
                TypeMirror targetType,
                ExecutableElement constructor,
                List<? extends TypeMirror> parameterTypes,
                ExecutableElement factory,
                TypeElement factoryOwner) {

            this.kind = kind;
            this.declaredType = declaredType;
            this.targetType = targetType;
            this.constructor = constructor;
            this.parameterTypes =
                    parameterTypes == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<TypeMirror>(
                                    parameterTypes));
            this.factory = factory;
            this.factoryOwner = factoryOwner;
        }


        static Creation noArgs(
                TypeMirror declaredType,
                TypeMirror targetType) {

            return new Creation(
                    Kind.NO_ARGS,
                    declaredType,
                    targetType,
                    null,
                    null,
                    null,
                    null);
        }


        static Creation constructor(
                TypeMirror declaredType,
                TypeMirror targetType,
                ExecutableElement constructor,
                List<? extends TypeMirror> parameterTypes) {

            return new Creation(
                    Kind.CONSTRUCTOR,
                    declaredType,
                    targetType,
                    constructor,
                    parameterTypes,
                    null,
                    null);
        }


        static Creation factory(
                TypeMirror declaredType,
                TypeMirror targetType,
                ExecutableElement factory,
                TypeElement factoryOwner) {

            return new Creation(
                    Kind.FACTORY,
                    declaredType,
                    targetType,
                    null,
                    null,
                    factory,
                    factoryOwner);
        }


        public Kind kind() {
            return kind;
        }

        public TypeMirror declaredType() {
            return declaredType;
        }

        public TypeMirror targetType() {
            return targetType;
        }

        public ExecutableElement constructor() {
            return constructor;
        }

        public List<? extends TypeMirror> parameterTypes() {
            return parameterTypes;
        }

        public ExecutableElement factory() {
            return factory;
        }

        public TypeElement factoryOwner() {
            return factoryOwner;
        }

        public boolean mutable() {
            return kind == Kind.NO_ARGS ||
                    kind == Kind.FACTORY;
        }
    }


    private static final class CreatorRule {

        final AnnotationMirror annotation;
        final TypeElement owner;

        final TypeMirror targetType;
        final TypeMirror implementation;

        final String creator;


        CreatorRule(
                AnnotationMirror annotation,
                TypeElement owner,
                TypeMirror targetType,
                TypeMirror implementation,
                String creator) {

            this.annotation = annotation;
            this.owner = owner;
            this.targetType = targetType;
            this.implementation = implementation;
            this.creator = creator;
        }
    }
}
