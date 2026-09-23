package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.AnnotationMirror;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Compiles a {@link MappingPlan} into a fully resolved mapping model.
 *
 * <p>This class performs mapping semantics and type resolution only. Generated
 * Java emission is deliberately kept separate so source generation does not
 * need to rediscover properties, paths, creators or converters.</p>
 */
public final class MappingCompiler {

    private final ProcessorContext context;
    private final TypeSystem types;

    private final TypeElement mapper;

    private final CreatorResolver creators;
    private final ConverterResolver converters;


    public MappingCompiler(
            ProcessorContext context,
            TypeElement mapper,
            List<MappingPlan> plans) {

        this.context = context;
        this.types = context.types;
        this.mapper = mapper;

        this.creators =
                new CreatorResolver(context);

        this.converters =
                new ConverterResolver(
                        context,
                        mapper,
                        plans);
    }


    /**
     * Validates mapper-wide dependencies such as imported mappers.
     */
    public boolean validate(
            GeneratedClass generated) {

        return converters.validate(
                generated);
    }


    /**
     * Compiles one mapper method.
     *
     * @return compiled method, or {@code null} after a diagnostic
     */
    public CompiledMethod compile(
            MappingPlan plan,
            GeneratedClass generated) {

        /*
         * Root container / map / JsonObject / OneOf mapping has no finite set
         * of writable Java properties. Treat it as one root conversion.
         */
        if (isRootProjection(plan)) {
            return compileRoot(
                    plan,
                    generated);
        }

        CreatorResolver.Creation creation =
                null;

        TypeMirror targetType =
                plan.targetType();

        if (plan.create()) {
            creation =
                    creators.resolve(
                            mapper,
                            plan,
                            generated);

            if (creation == null) {
                return null;
            }

            targetType =
                    creation.targetType();
        }

        Map<String, MappingPlan.Rule> explicit =
                explicitRules(
                        plan.rules());

        Set<String> ignored =
                ignoredTargets(
                        plan.rules());

        List<ConstructorArgument> constructorArguments =
                new ArrayList<ConstructorArgument>();

        List<Assignment> assignments =
                new ArrayList<Assignment>();

        Set<String> consumed =
                new LinkedHashSet<String>();

        Set<String> pathParents =
                new LinkedHashSet<String>();

        /*
         * Constructor/record parameters must be resolved before ordinary
         * writable properties because they are required for object creation.
         */
        if (creation != null &&
                creation.kind() ==
                        CreatorResolver.Creation.Kind.CONSTRUCTOR) {

            if (!compileConstructorArguments(
                    plan,
                    creation,
                    explicit,
                    ignored,
                    consumed,
                    constructorArguments,
                    generated)) {

                return null;
            }
        }

        /*
         * Explicit rules retain declaration order.
         */
        for (MappingPlan.Rule rule :
                plan.rules()) {

            if (rule.ignore()) {
                /*
                 * Validate that a plain ignored property actually exists.
                 * Required constructor arguments were already rejected above.
                 */
                if (!rule.targetPath() &&
                        !consumed.contains(
                                rule.target())) {

                    NodeAccess access =
                            context.access
                                    .resolveName(
                                            targetType,
                                            rule.target());

                    if (access == null) {
                        error(
                                plan.method(),
                                generated,
                                "unknown mapping target '" +
                                        rule.target() +
                                        "'");

                        return null;
                    }
                }

                continue;
            }

            if (consumed.contains(
                    rule.target())) {

                continue;
            }

            Target target =
                    resolveTarget(
                            targetType,
                            rule,
                            generated,
                            plan.method());

            if (target == null) {
                return null;
            }

            Value value =
                    resolveValue(
                            plan,
                            rule,
                            defaultSourceName(rule),
                            target.type(),
                            true,
                            generated);

            if (value == null) {
                return null;
            }

            assignments.add(
                    new Assignment(
                            target,
                            value,
                            rule));

            if (target.kind() ==
                    Target.Kind.PATH &&
                    target.steps()
                            .get(0)
                            .segment() instanceof
                            PathSegment.Name) {

                pathParents.add(
                        ((PathSegment.Name) target.steps()
                                .get(0)
                                .segment()).name);
            }

            consumed.add(
                    rule.target());
        }

        /*
         * Same-name automatic mapping for remaining writable target
         * properties.
         */
        Map<String, Property> properties =
                context.properties
                        .resolve(targetType);

        for (Property property :
                properties.values()) {

            if (!property.writable()) {
                continue;
            }

            String name =
                    property.name();

            if (consumed.contains(name) ||
                    pathParents.contains(name) ||
                    ignored.contains(name) ||
                    explicit.containsKey(name)) {

                continue;
            }

            NodeAccess targetAccess =
                    context.access
                            .resolveName(
                                    targetType,
                                    name);

            if (targetAccess == null ||
                    !targetAccess.writable()) {

                continue;
            }

            Value value =
                    resolveValue(
                            plan,
                            null,
                            name,
                            targetAccess.writeType(),
                            false,
                            generated);

            /*
             * Auto mapping silently skips a property when the primary source
             * does not expose the same node-facing name.
             */
            if (value == null) {
                continue;
            }

            assignments.add(
                    new Assignment(
                            Target.property(
                                    name,
                                    targetAccess),
                            value,
                            null));
        }

        List<DynamicSource> dynamicSources =
                compileDynamicSources(
                        plan,
                        targetType,
                        constructorArguments,
                        assignments);

        return CompiledMethod.object(
                plan,
                targetType,
                creation,
                constructorArguments,
                assignments,
                dynamicSources);
    }


    // -------------------------------------------------------------------------
    // JOJO dynamic remainder
    // -------------------------------------------------------------------------

    private List<DynamicSource> compileDynamicSources(
            MappingPlan plan,
            TypeMirror targetType,
            List<ConstructorArgument> constructorArguments,
            List<Assignment> assignments) {

        if (types.nodeKind(targetType) !=
                NodeKind.OBJECT_JOJO) {

            return Collections.emptyList();
        }

        Map<VariableElement, Set<String>> excluded =
                new LinkedHashMap<VariableElement, Set<String>>();

        Set<String> declaredTargets =
                context.properties
                        .resolve(targetType)
                        .keySet();

        for (VariableElement source :
                plan.sources()) {

            LinkedHashSet<String> names =
                    new LinkedHashSet<String>();

            names.addAll(declaredTargets);

            excluded.put(
                    source,
                    names);
        }

        for (ConstructorArgument argument :
                constructorArguments) {

            consumeValue(
                    excluded,
                    argument.value());
        }

        for (Assignment assignment :
                assignments) {

            consumeValue(
                    excluded,
                    assignment.value());
        }

        /*
         * Ignored mappings do not produce Value instances, but their selected
         * source member is still consumed and must not fall through into the
         * JOJO dynamic namespace.
         */
        for (MappingPlan.Rule rule :
                plan.rules()) {

            if (!rule.ignore()) {
                continue;
            }

            String selector =
                    rule.explicitSource()
                            ? rule.source()
                            : defaultSourceName(rule);

            consumeSelector(
                    plan,
                    excluded,
                    selector);
        }

        List<DynamicSource> result =
                new ArrayList<DynamicSource>();

        for (VariableElement source :
                plan.sources()) {

            NodeKind kind =
                    types.nodeKind(
                            source.asType());

            switch (kind) {
                case OBJECT_MAP:
                case OBJECT_JSON_OBJECT:
                case OBJECT_JOJO:
                case OBJECT_EXTERNAL:
                case COMPILE_TIME_UNKNOWN:
                    result.add(
                            new DynamicSource(
                                    source,
                                    kind,
                                    excluded.get(source)));
                    break;

                default:
                    /*
                     * A POJO contributes only statically selected properties.
                     * JOJO is intentionally different: only its dynamic backing
                     * entries participate in the remainder copy.
                     */
                    break;
            }
        }

        return result;
    }


    private void consumeValue(
            Map<VariableElement, Set<String>> excluded,
            Value value) {

        if (value == null) {
            return;
        }

        if (value.kind() ==
                Value.Kind.READ) {

            consumeRead(
                    excluded,
                    value.read());

            return;
        }

        for (Read read :
                value.inputs()) {

            consumeRead(
                    excluded,
                    read);
        }
    }


    private void consumeRead(
            Map<VariableElement, Set<String>> excluded,
            Read read) {

        if (read == null ||
                read.steps().isEmpty()) {

            return;
        }

        PathSegment segment =
                read.steps()
                        .get(0)
                        .segment();

        if (!(segment instanceof
                PathSegment.Name)) {

            return;
        }

        Set<String> names =
                excluded.get(
                        read.root());

        if (names != null) {
            names.add(
                    ((PathSegment.Name) segment)
                            .name);
        }
    }


    private void consumeSelector(
            MappingPlan plan,
            Map<VariableElement, Set<String>> excluded,
            String selector) {

        if (selector == null ||
                selector.isEmpty()) {

            return;
        }

        SourceSelector selected =
                sourceSelector(
                        plan,
                        selector);

        String expression =
                selected.expression;

        String name = null;

        if (expression.isEmpty() ||
                "$".equals(expression)) {

            return;
        }

        if (!isPath(expression)) {
            name = expression;

        } else {
            try {
                PathSegment[] segments =
                        JsonPath.parse(expression)
                                .segments();

                if (segments.length > 1 &&
                        segments[1] instanceof
                                PathSegment.Name) {

                    name =
                            ((PathSegment.Name) segments[1])
                                    .name;
                }

            } catch (JsonException ignored) {
                return;
            }
        }

        if (name == null ||
                name.isEmpty()) {

            return;
        }

        Set<String> names =
                excluded.get(
                        selected.root);

        if (names != null) {
            names.add(name);
        }
    }


    // -------------------------------------------------------------------------
    // Root mapping
    // -------------------------------------------------------------------------

    private CompiledMethod compileRoot(
            MappingPlan plan,
            GeneratedClass generated) {

        if (plan.sources().size() != 1) {
            error(
                    plan.method(),
                    generated,
                    "root structural/container mapping requires exactly one source parameter");

            return null;
        }

        VariableElement source =
                plan.primarySource();

        if (plan.update() &&
                types.nodeKind(
                        plan.targetType()) ==
                        NodeKind.OBJECT_MAP &&
                types.nodeKind(
                        source.asType()) !=
                        NodeKind.OBJECT_MAP) {

            error(
                    plan.method(),
                    generated,
                    "root Map update requires a declared Map source");

            return null;
        }

        ConverterResolver.Conversion conversion;

        if (plan.update() &&
                types.nodeKind(
                        source.asType()) ==
                        NodeKind.OBJECT_MAP &&
                types.nodeKind(
                        plan.targetType()) ==
                        NodeKind.OBJECT_MAP) {

            conversion =
                    ConverterResolver.Conversion
                            .container(
                                    source.asType(),
                                    plan.targetType());

        } else {
            conversion =
                    converters.resolveRoot(
                            plan,
                            source.asType(),
                            plan.targetType(),
                            generated);
        }

        if (conversion == null) {
            return null;
        }

        /*
         * The current mapper method itself is naturally a compatible local
         * mapper method. At the root that would recurse immediately, so replace
         * self-selection with the underlying structural/container strategy.
         */
        if (conversion.kind() ==
                ConverterResolver.Conversion.Kind.METHOD &&
                conversion.method() ==
                        plan.method()) {

            conversion =
                    rootFallbackConversion(
                            source.asType(),
                            plan.targetType());

            if (conversion == null) {
                error(
                        plan.method(),
                        generated,
                        "cannot resolve root mapping from " +
                                source.asType() +
                                " to " +
                                plan.targetType());

                return null;
            }
        }

        return CompiledMethod.root(
                plan,
                new Read(
                        source,
                        Collections.<ReadStep>emptyList(),
                        source.asType()),
                conversion);
    }


    private ConverterResolver.Conversion rootFallbackConversion(
            TypeMirror source,
            TypeMirror target) {

        NodeKind sourceKind =
                types.nodeKind(source);

        NodeKind targetKind =
                types.nodeKind(target);

        if ((isArrayLike(sourceKind) ||
                sourceKind == NodeKind.COMPILE_TIME_UNKNOWN) &&
                isArrayLike(targetKind)) {

            return ConverterResolver.Conversion
                    .container(
                            source,
                            target);
        }

        if (sourceKind == NodeKind.OBJECT_MAP &&
                targetKind == NodeKind.OBJECT_MAP) {

            return ConverterResolver.Conversion
                    .container(
                            source,
                            target);
        }

        if ((isObjectLike(sourceKind) ||
                sourceKind == NodeKind.COMPILE_TIME_UNKNOWN) &&
                isObjectLike(targetKind)) {

            return ConverterResolver.Conversion
                    .structural(
                            source,
                            target);
        }

        return null;
    }


    private boolean isRootProjection(
            MappingPlan plan) {

        if (!plan.rules().isEmpty()) {
            return false;
        }

        NodeKind kind =
                types.nodeKind(
                        plan.targetType());

        switch (kind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:

            case ARRAY_ARRAY:
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return true;

            default:
                return hasOneOf(
                        plan.targetType());
        }
    }


    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private boolean compileConstructorArguments(
            MappingPlan plan,
            CreatorResolver.Creation creation,
            Map<String, MappingPlan.Rule> explicit,
            Set<String> ignored,
            Set<String> consumed,
            List<ConstructorArgument> result,
            GeneratedClass generated) {

        ExecutableElement constructor =
                creation.constructor();

        List<? extends VariableElement> parameters =
                constructor.getParameters();

        List<? extends TypeMirror> parameterTypes =
                creation.parameterTypes();

        for (int i = 0;
             i < parameters.size();
             i++) {

            VariableElement parameter =
                    parameters.get(i);

            TypeMirror parameterType =
                    parameterTypes.get(i);

            String name =
                    context.annotations
                            .propertyName(
                                    parameter,
                                    parameter
                                            .getSimpleName()
                                            .toString());

            MappingPlan.Rule rule =
                    explicit.get(name);

            if (ignored.contains(name) ||
                    rule != null &&
                            rule.ignore()) {

                error(
                        plan.method(),
                        generated,
                        "constructor target cannot ignore required argument '" +
                                name +
                                "'");

                return false;
            }

            /*
             * A target path can never supply a constructor argument. Only an
             * exact plain target name participates here.
             */
            if (rule != null &&
                    rule.targetPath()) {

                rule = null;
            }

            Value value =
                    resolveValue(
                            plan,
                            rule,
                            name,
                            parameterType,
                            true,
                            generated);

            if (value == null) {
                error(
                        plan.method(),
                        generated,
                        "cannot resolve source value for required constructor argument '" +
                                name +
                                "'");

                return false;
            }

            result.add(
                    new ConstructorArgument(
                            name,
                            parameterType,
                            value));

            consumed.add(name);
        }

        return true;
    }


    // -------------------------------------------------------------------------
    // Value
    // -------------------------------------------------------------------------

    private Value resolveValue(
            MappingPlan plan,
            MappingPlan.Rule rule,
            String fallbackSource,
            TypeMirror targetType,
            boolean required,
            GeneratedClass generated) {

        if (rule != null &&
                rule.computed()) {

            return resolveCompute(
                    plan,
                    rule,
                    fallbackSource,
                    targetType,
                    generated);
        }

        String selector =
                rule != null &&
                        !rule.source()
                                .isEmpty()
                        ? rule.source()
                        : fallbackSource;

        Read read =
                resolveRead(
                        plan,
                        selector,
                        required,
                        generated);

        if (read == null) {
            return null;
        }

        ConverterResolver.Conversion conversion =
                converters.resolve(
                        plan,
                        rule,
                        read.type(),
                        targetType,
                        generated);

        if (conversion == null) {
            return null;
        }

        return Value.read(
                targetType,
                read,
                conversion);
    }


    private Value resolveCompute(
            MappingPlan plan,
            MappingPlan.Rule rule,
            String fallbackSource,
            TypeMirror targetType,
            GeneratedClass generated) {

        List<String> selectors =
                new ArrayList<String>();

        String compute =
                rule.compute()
                        .trim();

        if (compute.contains("::") &&
                !compute.startsWith("this::")) {

            error(
                    plan.method(),
                    generated,
                    "compute method references support only this::defaultMethod: " +
                            compute);

            return null;
        }

        if (compute.startsWith("this::")) {
            ExecutableElement helper =
                    resolveComputeHelper(
                            plan,
                            compute.substring(6)
                                    .trim(),
                            generated);

            if (helper == null) {
                return null;
            }

            if (rule.sources()
                    .isEmpty() &&
                    rule.source()
                            .isEmpty()) {

                for (VariableElement parameter :
                        helper.getParameters()) {

                    selectors.add(
                            parameter.getSimpleName()
                                    .toString());
                }
            }
        }

        if (selectors.isEmpty() &&
                !rule.sources()
                .isEmpty()) {

            selectors.addAll(
                    rule.sources());

        } else if (selectors.isEmpty() &&
                !rule.source()
                .isEmpty()) {

            selectors.add(
                    rule.source());

        } else if (selectors.isEmpty() &&
                !compute.startsWith("this::") &&
                fallbackSource != null &&
                !fallbackSource.isEmpty()) {

            selectors.add(
                    fallbackSource);
        }

        if (selectors.isEmpty() &&
                !compute.startsWith("this::")) {
            error(
                    plan.method(),
                    generated,
                    "computed mapping for target '" +
                            rule.target() +
                            "' has no sources");

            return null;
        }

        List<Read> reads =
                new ArrayList<Read>();

        for (String selector :
                selectors) {

            Read read =
                    resolveRead(
                            plan,
                            selector,
                            true,
                            generated);

            if (read == null) {
                return null;
            }

            reads.add(read);
        }

        return Value.compute(
                targetType,
                reads,
                rule.compute());
    }


    private ExecutableElement resolveComputeHelper(
            MappingPlan plan,
            String name,
            GeneratedClass generated) {

        if (name.isEmpty()) {
            error(
                    plan.method(),
                    generated,
                    "compute method reference must use the form this::defaultMethod");

            return null;
        }

        ExecutableElement result =
                null;

        for (Element member :
                mapper.getEnclosedElements()) {

            if (member.getKind() !=
                    ElementKind.METHOD ||
                    !member.getSimpleName()
                            .contentEquals(name)) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (!method.getModifiers()
                    .contains(Modifier.DEFAULT) ||
                    method.getReturnType()
                            .getKind() ==
                            TypeKind.VOID ||
                    !method.getTypeParameters()
                            .isEmpty()) {

                continue;
            }

            if (result != null) {
                error(
                        plan.method(),
                        generated,
                        "compute helper method '" +
                                name +
                                "' is ambiguous");

                return null;
            }

            result =
                    method;
        }

        if (result == null) {
            error(
                    plan.method(),
                    generated,
                    "compute helper method '" +
                            name +
                            "' must be a current-interface default method");
        }

        return result;
    }


    // -------------------------------------------------------------------------
    // Source read
    // -------------------------------------------------------------------------

    private Read resolveRead(
            MappingPlan plan,
            String selector,
            boolean required,
            GeneratedClass generated) {

        if (selector == null ||
                selector.isEmpty()) {

            if (required) {
                error(
                        plan.method(),
                        generated,
                        "mapping source must not be empty");
            }

            return null;
        }

        SourceSelector resolved =
                sourceSelector(
                        plan,
                        selector);

        if (resolved == null) {
            if (required) {
                error(
                        plan.method(),
                        generated,
                        "unknown source parameter in '" +
                                selector +
                                "'");
            }

            return null;
        }

        VariableElement root =
                resolved.root;

        String expression =
                resolved.expression;

        if (expression.isEmpty() ||
                "$".equals(expression)) {

            return new Read(
                    root,
                    Collections.<ReadStep>emptyList(),
                    root.asType());
        }

        if (isPath(expression)) {
            return resolvePathRead(
                    plan,
                    root,
                    expression,
                    required,
                    generated);
        }

        NodeAccess access =
                context.access
                        .resolveName(
                                root.asType(),
                                expression);

        if (access == null ||
                !access.readable()) {

            if (required) {
                error(
                        plan.method(),
                        generated,
                        "source '" +
                                selector +
                                "' is not readable");
            }

            return null;
        }

        List<ReadStep> steps =
                new ArrayList<ReadStep>(1);

        steps.add(
                new ReadStep(
                        new PathSegment.Name(
                                PathSegment.Root.INSTANCE,
                                expression),
                        access));

        return new Read(
                root,
                steps,
                access.readType());
    }


    private Read resolvePathRead(
            MappingPlan plan,
            VariableElement root,
            String expression,
            boolean required,
            GeneratedClass generated) {

        JsonPath path;

        try {
            path =
                    JsonPath.parse(
                            expression);

        } catch (JsonException e) {
            if (required) {
                error(
                        plan.method(),
                        generated,
                        "invalid source path '" +
                                expression +
                                "': " +
                                e.getMessage());
            }

            return null;
        }

        PathSegment[] segments =
                path.segments();

        TypeMirror current =
                root.asType();

        List<ReadStep> steps =
                new ArrayList<ReadStep>();

        for (int i = 1;
             i < segments.length;
             i++) {

            PathSegment segment =
                    segments[i];

            NodeAccess access;

            if (segment instanceof
                    PathSegment.Name) {

                access =
                        context.access
                                .resolveName(
                                        current,
                                        ((PathSegment.Name) segment)
                                                .name);

            } else if (segment instanceof
                    PathSegment.Index) {

                access =
                        context.access
                                .resolveIndex(
                                        current);

            } else {
                if (required) {
                    error(
                            plan.method(),
                            generated,
                            "source path '" +
                                    expression +
                                    "' supports only name and index segments");
                }

                return null;
            }

            if (access == null ||
                    !access.readable()) {

                if (required) {
                    error(
                            plan.method(),
                            generated,
                            "source path '" +
                                    expression +
                                    "' is not readable at segment " +
                                    i);
                }

                return null;
            }

            steps.add(
                    new ReadStep(
                            segment,
                            access));

            current =
                    access.readType();
        }

        return new Read(
                root,
                steps,
                current);
    }


    /**
     * Supports:
     *
     * <pre>
     * name
     * $.profile.name
     * /profile/name
     * user:name
     * user:$.profile.name
     * user:/profile/name
     * </pre>
     *
     * <p>Unqualified sources always use the first source parameter.</p>
     */
    private SourceSelector sourceSelector(
            MappingPlan plan,
            String selector) {

        int colon =
                selector.indexOf(':');

        if (colon > 0) {
            String prefix =
                    selector.substring(
                            0,
                            colon);

            for (VariableElement source :
                    plan.sources()) {

                if (source.getSimpleName()
                        .contentEquals(prefix)) {

                    return new SourceSelector(
                            source,
                            selector.substring(
                                    colon + 1));
                }
            }
        }

        return new SourceSelector(
                plan.primarySource(),
                selector);
    }


    // -------------------------------------------------------------------------
    // Target
    // -------------------------------------------------------------------------

    private Target resolveTarget(
            TypeMirror rootType,
            MappingPlan.Rule rule,
            GeneratedClass generated,
            ExecutableElement method) {

        if (!rule.targetPath()) {
            NodeAccess access =
                    context.access
                            .resolveName(
                                    rootType,
                                    rule.target());

            if (access == null) {
                error(
                        method,
                        generated,
                        "unknown mapping target '" +
                                rule.target() +
                                "'");

                return null;
            }

            if (!access.writable()) {
                error(
                        method,
                        generated,
                        "mapping target '" +
                                rule.target() +
                                "' is not writable");

                return null;
            }

            return Target.property(
                    rule.target(),
                    access);
        }

        JsonPath path;

        try {
            path =
                    JsonPath.parse(
                            rule.target());

        } catch (JsonException e) {
            error(
                    method,
                    generated,
                    "invalid target path '" +
                            rule.target() +
                            "': " +
                            e.getMessage());

            return null;
        }

        PathSegment[] segments =
                path.segments();

        if (segments.length <= 1) {
            error(
                    method,
                    generated,
                    "target path must contain a non-root child");

            return null;
        }

        List<TargetStep> steps =
                new ArrayList<TargetStep>();

        TypeMirror current =
                rootType;

        for (int i = 1;
             i < segments.length;
             i++) {

            PathSegment segment =
                    segments[i];

            boolean last =
                    i == segments.length - 1;

            NodeAccess access;

            if (segment instanceof
                    PathSegment.Name) {

                access =
                        context.access
                                .resolveName(
                                        current,
                                        ((PathSegment.Name) segment)
                                                .name);

            } else if (segment instanceof
                    PathSegment.Index) {

                if (rule.mode() ==
                        MappingPlan.WriteMode.ENSURE) {

                    error(
                            method,
                            generated,
                            "@EnsureMapping does not support index target segments");

                    return null;
                }

                access =
                        context.access
                                .resolveIndex(
                                        current);

            } else if (segment instanceof
                    PathSegment.Append &&
                    last &&
                    rule.mode() ==
                            MappingPlan.WriteMode.STRICT) {

                access =
                        context.access
                                .resolveAppend(
                                        current);

            } else {
                error(
                        method,
                        generated,
                        "unsupported target path segment at index " +
                                i +
                                " in '" +
                                rule.target() +
                                "'");

                return null;
            }

            if (access == null) {
                error(
                        method,
                        generated,
                        "cannot resolve target path '" +
                                rule.target() +
                                "' at segment " +
                                i);

                return null;
            }

            if (last) {
                if (!access.writable()) {
                    error(
                            method,
                            generated,
                            "target path '" +
                                    rule.target() +
                                    "' is not writable");

                    return null;
                }

            } else {
                if (!access.readable()) {
                    error(
                            method,
                            generated,
                            "target path '" +
                                    rule.target() +
                                    "' is not readable at segment " +
                                    i);

                    return null;
                }

                /*
                 * Ensure may need to create and assign a missing intermediate
                 * node, so read-only intermediate access is insufficient.
                 */
                if (rule.mode() ==
                        MappingPlan.WriteMode.ENSURE &&
                        !access.writable()) {

                    error(
                            method,
                            generated,
                            "@EnsureMapping target path '" +
                                    rule.target() +
                                    "' contains a read-only intermediate segment");

                    return null;
                }
            }

            steps.add(
                    new TargetStep(
                            segment,
                            access));

            if (!last) {
                current =
                        access.readType();
            }
        }

        TargetStep tail =
                steps.get(
                        steps.size() - 1);

        return Target.path(
                rule.target(),
                rule.mode(),
                steps,
                tail.access.writeType());
    }


    // -------------------------------------------------------------------------
    // Rule helpers
    // -------------------------------------------------------------------------

    private Map<String, MappingPlan.Rule> explicitRules(
            List<MappingPlan.Rule> rules) {

        Map<String, MappingPlan.Rule> result =
                new LinkedHashMap<String, MappingPlan.Rule>();

        for (MappingPlan.Rule rule :
                rules) {

            result.put(
                    rule.target(),
                    rule);
        }

        return result;
    }


    private Set<String> ignoredTargets(
            List<MappingPlan.Rule> rules) {

        Set<String> result =
                new LinkedHashSet<String>();

        for (MappingPlan.Rule rule :
                rules) {

            if (rule.ignore()) {
                result.add(
                        rule.target());
            }
        }

        return result;
    }


    /**
     * When source is omitted, use the target property name.
     *
     * <p>For a target path, only a final name segment has an obvious default
     * source name. Index/append paths therefore require source or compute.</p>
     */
    private String defaultSourceName(
            MappingPlan.Rule rule) {

        if (!rule.targetPath()) {
            return rule.target();
        }

        try {
            PathSegment[] segments =
                    JsonPath.parse(
                                    rule.target())
                            .segments();

            if (segments.length > 1) {
                PathSegment tail =
                        segments[
                                segments.length - 1];

                if (tail instanceof
                        PathSegment.Name) {

                    return ((PathSegment.Name) tail)
                            .name;
                }
            }
        } catch (JsonException ignored) {
            /*
             * Target validation reports the real diagnostic.
             */
        }

        return "";
    }


    private boolean isPath(String value) {
        return value != null &&
                !value.isEmpty() &&
                (value.charAt(0) == '$' ||
                        value.charAt(0) == '/');
    }


    // -------------------------------------------------------------------------
    // Node categories
    // -------------------------------------------------------------------------

    private boolean isArrayLike(
            NodeKind kind) {

        switch (kind) {
            case ARRAY_ARRAY:
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return true;

            default:
                return false;
        }
    }


    private boolean isObjectLike(
            NodeKind kind) {

        switch (kind) {
            case OBJECT_POJO:
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
                return true;

            default:
                return false;
        }
    }


    private boolean hasOneOf(
            TypeMirror type) {

        TypeElement element =
                types.typeElement(
                        types.concrete(type));

        if (element == null) {
            return false;
        }

        for (AnnotationMirror annotation :
                element.getAnnotationMirrors()) {

            Element annotationType =
                    annotation.getAnnotationType()
                            .asElement();

            if (!(annotationType instanceof
                    TypeElement)) {

                continue;
            }

            String name =
                    ((TypeElement) annotationType)
                            .getQualifiedName()
                            .toString();

            if (name.startsWith(
                    "org.sjf4j.annotation.") &&
                    name.endsWith(
                            ".OneOf")) {

                return true;
            }
        }

        return false;
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
    // Compiled model
    // -------------------------------------------------------------------------

    /**
     * Fully validated mapper-method model.
     */
    static final class CompiledMethod {

        enum Kind {
            OBJECT,
            ROOT
        }


        private final Kind kind;

        private final MappingPlan plan;

        private final TypeMirror targetType;

        private final CreatorResolver.Creation creation;

        private final List<ConstructorArgument>
                constructorArguments;

        private final List<Assignment> assignments;

        private final List<DynamicSource> dynamicSources;

        private final Read rootSource;

        private final ConverterResolver.Conversion
                rootConversion;


        private CompiledMethod(
                Kind kind,
                MappingPlan plan,
                TypeMirror targetType,
                CreatorResolver.Creation creation,
                List<ConstructorArgument> constructorArguments,
                List<Assignment> assignments,
                List<DynamicSource> dynamicSources,
                Read rootSource,
                ConverterResolver.Conversion rootConversion) {

            this.kind = kind;
            this.plan = plan;
            this.targetType = targetType;
            this.creation = creation;

            this.constructorArguments =
                    constructorArguments == null
                            ? Collections.<ConstructorArgument>emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<ConstructorArgument>(
                                    constructorArguments));

            this.assignments =
                    assignments == null
                            ? Collections.<Assignment>emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<Assignment>(
                                    assignments));

            this.dynamicSources =
                    dynamicSources == null
                            ? Collections.<DynamicSource>emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<DynamicSource>(
                                    dynamicSources));

            this.rootSource = rootSource;
            this.rootConversion =
                    rootConversion;
        }


        static CompiledMethod object(
                MappingPlan plan,
                TypeMirror targetType,
                CreatorResolver.Creation creation,
                List<ConstructorArgument> constructorArguments,
                List<Assignment> assignments,
                List<DynamicSource> dynamicSources) {

            return new CompiledMethod(
                    Kind.OBJECT,
                    plan,
                    targetType,
                    creation,
                    constructorArguments,
                    assignments,
                    dynamicSources,
                    null,
                    null);
        }


        static CompiledMethod root(
                MappingPlan plan,
                Read source,
                ConverterResolver.Conversion conversion) {

            return new CompiledMethod(
                    Kind.ROOT,
                    plan,
                    plan.targetType(),
                    null,
                    null,
                    null,
                    null,
                    source,
                    conversion);
        }


        Kind kind() {
            return kind;
        }

        MappingPlan plan() {
            return plan;
        }

        TypeMirror targetType() {
            return targetType;
        }

        CreatorResolver.Creation creation() {
            return creation;
        }

        List<ConstructorArgument>
        constructorArguments() {
            return constructorArguments;
        }

        List<Assignment> assignments() {
            return assignments;
        }

        List<DynamicSource> dynamicSources() {
            return dynamicSources;
        }

        Read rootSource() {
            return rootSource;
        }

        ConverterResolver.Conversion
        rootConversion() {
            return rootConversion;
        }
    }


    static final class ConstructorArgument {

        private final String name;
        private final TypeMirror type;
        private final Value value;


        ConstructorArgument(
                String name,
                TypeMirror type,
                Value value) {

            this.name = name;
            this.type = type;
            this.value = value;
        }


        String name() {
            return name;
        }

        TypeMirror type() {
            return type;
        }

        Value value() {
            return value;
        }
    }


    static final class Assignment {

        private final Target target;
        private final Value value;

        private final MappingPlan.Rule rule;


        Assignment(
                Target target,
                Value value,
                MappingPlan.Rule rule) {

            this.target = target;
            this.value = value;
            this.rule = rule;
        }


        Target target() {
            return target;
        }

        Value value() {
            return value;
        }

        MappingPlan.Rule rule() {
            return rule;
        }
    }


    static final class DynamicSource {

        private final VariableElement source;
        private final NodeKind kind;
        private final Set<String> excludedNames;


        DynamicSource(
                VariableElement source,
                NodeKind kind,
                Set<String> excludedNames) {

            this.source =
                    source;

            this.kind =
                    kind;

            this.excludedNames =
                    excludedNames == null
                            ? Collections.<String>emptySet()
                            : Collections.unmodifiableSet(
                            new LinkedHashSet<String>(
                                    excludedNames));
        }


        VariableElement source() {
            return source;
        }

        NodeKind kind() {
            return kind;
        }

        Set<String> excludedNames() {
            return excludedNames;
        }
    }


    // -------------------------------------------------------------------------
    // Target model
    // -------------------------------------------------------------------------

    static final class Target {

        enum Kind {
            PROPERTY,
            PATH
        }


        private final Kind kind;
        private final String name;

        private final TypeMirror type;

        private final NodeAccess access;

        private final MappingPlan.WriteMode mode;

        private final List<TargetStep> steps;


        private Target(
                Kind kind,
                String name,
                TypeMirror type,
                NodeAccess access,
                MappingPlan.WriteMode mode,
                List<TargetStep> steps) {

            this.kind = kind;
            this.name = name;
            this.type = type;
            this.access = access;
            this.mode = mode;

            this.steps =
                    steps == null
                            ? Collections.<TargetStep>emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<TargetStep>(
                                    steps));
        }


        static Target property(
                String name,
                NodeAccess access) {

            return new Target(
                    Kind.PROPERTY,
                    name,
                    access.writeType(),
                    access,
                    MappingPlan.WriteMode.STRICT,
                    null);
        }


        static Target path(
                String expression,
                MappingPlan.WriteMode mode,
                List<TargetStep> steps,
                TypeMirror type) {

            return new Target(
                    Kind.PATH,
                    expression,
                    type,
                    null,
                    mode,
                    steps);
        }


        Kind kind() {
            return kind;
        }

        String name() {
            return name;
        }

        TypeMirror type() {
            return type;
        }

        NodeAccess access() {
            return access;
        }

        MappingPlan.WriteMode mode() {
            return mode;
        }

        List<TargetStep> steps() {
            return steps;
        }
    }


    static final class TargetStep {

        private final PathSegment segment;
        private final NodeAccess access;


        TargetStep(
                PathSegment segment,
                NodeAccess access) {

            this.segment = segment;
            this.access = access;
        }


        PathSegment segment() {
            return segment;
        }

        NodeAccess access() {
            return access;
        }
    }


    // -------------------------------------------------------------------------
    // Value model
    // -------------------------------------------------------------------------

    static final class Value {

        enum Kind {
            READ,
            COMPUTE
        }


        private final Kind kind;
        private final TypeMirror type;

        private final Read read;

        private final ConverterResolver.Conversion
                conversion;

        private final List<Read> inputs;

        private final String compute;


        private Value(
                Kind kind,
                TypeMirror type,
                Read read,
                ConverterResolver.Conversion conversion,
                List<Read> inputs,
                String compute) {

            this.kind = kind;
            this.type = type;
            this.read = read;
            this.conversion = conversion;

            this.inputs =
                    inputs == null
                            ? Collections.<Read>emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<Read>(
                                    inputs));

            this.compute = compute;
        }


        static Value read(
                TypeMirror targetType,
                Read read,
                ConverterResolver.Conversion conversion) {

            return new Value(
                    Kind.READ,
                    targetType,
                    read,
                    conversion,
                    null,
                    null);
        }


        static Value compute(
                TypeMirror targetType,
                List<Read> inputs,
                String compute) {

            return new Value(
                    Kind.COMPUTE,
                    targetType,
                    null,
                    null,
                    inputs,
                    compute);
        }


        Kind kind() {
            return kind;
        }

        TypeMirror type() {
            return type;
        }

        Read read() {
            return read;
        }

        ConverterResolver.Conversion
        conversion() {
            return conversion;
        }

        List<Read> inputs() {
            return inputs;
        }

        String compute() {
            return compute;
        }
    }


    // -------------------------------------------------------------------------
    // Read model
    // -------------------------------------------------------------------------

    static final class Read {

        private final VariableElement root;
        private final List<ReadStep> steps;

        private final TypeMirror type;


        Read(
                VariableElement root,
                List<ReadStep> steps,
                TypeMirror type) {

            this.root = root;

            this.steps =
                    Collections.unmodifiableList(
                            new ArrayList<ReadStep>(
                                    steps));

            this.type = type;
        }


        VariableElement root() {
            return root;
        }

        List<ReadStep> steps() {
            return steps;
        }

        TypeMirror type() {
            return type;
        }

        boolean rootValue() {
            return steps.isEmpty();
        }
    }


    static final class ReadStep {

        private final PathSegment segment;
        private final NodeAccess access;


        ReadStep(
                PathSegment segment,
                NodeAccess access) {

            this.segment = segment;
            this.access = access;
        }


        PathSegment segment() {
            return segment;
        }

        NodeAccess access() {
            return access;
        }
    }


    private static final class SourceSelector {

        final VariableElement root;
        final String expression;


        SourceSelector(
                VariableElement root,
                String expression) {

            this.root = root;
            this.expression = expression;
        }
    }
}
