package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Expands a selected {@link ConverterResolver.Conversion} into a fully
 * resolved conversion plan.
 *
 * <p>Recursive container and structural conversion is resolved here rather
 * than while Java source is being written. The emitter therefore performs
 * no semantic lookup and never mutates {@link GeneratedClass} while it is
 * being written.</p>
 */
public final class ConversionCompiler {

    private final ProcessorContext context;
    private final TypeSystem types;

    private final TypeElement mapper;

    private final CreatorResolver creators;
    private final ConverterResolver converters;


    public ConversionCompiler(
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
     * Compiles a previously selected conversion.
     */
    public CompiledConversion compile(
            MappingPlan plan,
            ConverterResolver.Conversion conversion,
            GeneratedClass generated) {

        return compile(
                plan,
                conversion,
                generated,
                new LinkedHashSet<String>());
    }


    private CompiledConversion compile(
            MappingPlan plan,
            ConverterResolver.Conversion conversion,
            GeneratedClass generated,
            Set<String> stack) {

        if (conversion == null) {
            return null;
        }

        switch (conversion.kind()) {
            case DIRECT:
                return CompiledConversion.direct(
                        conversion.sourceType(),
                        conversion.targetType());

            case METHOD:
                return CompiledConversion.method(
                        conversion.sourceType(),
                        conversion.targetType(),
                        conversion.method(),
                        conversion.mapperType());

            case SCALAR:
                return CompiledConversion.scalar(
                        conversion.sourceType(),
                        conversion.targetType());

            case ONE_OF:
                /*
                 * OneOf dispatch stays a leaf strategy. Its runtime-shape /
                 * discriminator dispatch can be emitted without discovering
                 * further Java structure.
                 */
                return CompiledConversion.oneOf(
                        conversion.sourceType(),
                        conversion.targetType());

            case CONTAINER:
                return compileContainer(
                        plan,
                        conversion.sourceType(),
                        conversion.targetType(),
                        generated,
                        stack);

            case STRUCTURAL:
                return compileStructural(
                        plan,
                        conversion.sourceType(),
                        conversion.targetType(),
                        generated,
                        stack);

            default:
                throw new AssertionError(
                        conversion.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Container
    // -------------------------------------------------------------------------

    private CompiledConversion compileContainer(
            MappingPlan plan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            Set<String> stack) {

        NodeKind sourceKind =
                types.nodeKind(sourceType);

        NodeKind targetKind =
                types.nodeKind(targetType);

        /*
         * Map -> Map is a key/value container conversion.
         */
        if (sourceKind == NodeKind.OBJECT_MAP &&
                targetKind == NodeKind.OBJECT_MAP) {

            TypeMirror sourceKey =
                    types.mapReadKeyType(
                            sourceType);

            TypeMirror sourceValue =
                    types.mapReadValueType(
                            sourceType);

            TypeMirror targetKey =
                    types.mapWriteKeyType(
                            targetType);

            TypeMirror targetValue =
                    types.mapWriteValueType(
                            targetType);

            if (sourceKey == null ||
                    sourceValue == null) {

                error(
                        plan.method(),
                        generated,
                        "source Map key/value type is not readable: " +
                                sourceType);

                return null;
            }

            if (targetKey == null ||
                    targetValue == null) {

                error(
                        plan.method(),
                        generated,
                        "target Map key/value type is not writable: " +
                                targetType);

                return null;
            }

            ConverterResolver.Conversion keyConversion =
                    converters.resolve(
                            plan,
                            null,
                            sourceKey,
                            targetKey,
                            generated);

            if (keyConversion == null) {
                return null;
            }

            ConverterResolver.Conversion valueConversion =
                    converters.resolve(
                            plan,
                            null,
                            sourceValue,
                            targetValue,
                            generated);

            if (valueConversion == null) {
                return null;
            }

            CompiledConversion key =
                    compile(
                            plan,
                            keyConversion,
                            generated,
                            stack);

            if (key == null) {
                return null;
            }

            CompiledConversion value =
                    compile(
                            plan,
                            valueConversion,
                            generated,
                            stack);

            if (value == null) {
                return null;
            }

            return CompiledConversion.map(
                    sourceType,
                    targetType,
                    sourceKey,
                    sourceValue,
                    targetKey,
                    targetValue,
                    key,
                    value);
        }

        TypeMirror sourceElement =
                sourceKind == NodeKind.COMPILE_TIME_UNKNOWN
                        ? types.objectType()
                        : types.readElementType(
                        sourceType);

        if (sourceElement == null) {
            error(
                    plan.method(),
                    generated,
                    "source container element type is not readable: " +
                            sourceType);

            return null;
        }

        TypeMirror targetElement =
                types.writeElementType(
                        targetType);

        if (targetElement == null) {
            error(
                    plan.method(),
                    generated,
                    "target container element type is not writable: " +
                            targetType);

            return null;
        }

        ConverterResolver.Conversion elementConversion =
                converters.resolve(
                        plan,
                        null,
                        sourceElement,
                        targetElement,
                        generated);

        if (elementConversion == null) {
            return null;
        }

        CompiledConversion element =
                compile(
                        plan,
                        elementConversion,
                        generated,
                        stack);

        if (element == null) {
            return null;
        }

        return CompiledConversion.container(
                sourceType,
                targetType,
                sourceElement,
                targetElement,
                element);
    }




    // -------------------------------------------------------------------------
    // Structural
    // -------------------------------------------------------------------------

    private CompiledConversion compileStructural(
            MappingPlan parentPlan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            Set<String> stack) {

        sourceType =
                types.concrete(
                        sourceType);

        targetType =
                types.concrete(
                        targetType);

        String key =
                structuralKey(
                        sourceType,
                        targetType);

        /*
         * A recursive structural pair without an explicit mapper method would
         * otherwise expand forever. Recursive models should expose a mapper
         * method for the recursive edge; ConverterResolver will then select
         * METHOD before STRUCTURAL.
         */
        if (!stack.add(key)) {
            error(
                    parentPlan.method(),
                    generated,
                    "recursive structural mapping " +
                            sourceType +
                            " -> " +
                            targetType +
                            " requires an explicit mapper method");

            return null;
        }

        try {
            NodeKind sourceKind =
                    types.nodeKind(
                            sourceType);

            NodeKind targetKind =
                    types.nodeKind(
                            targetType);

            /*
             * JOJO is a hybrid target: declared properties are compiled like a
             * POJO and the unconsumed source object members are copied into its
             * dynamic namespace.
             */
            if (targetKind ==
                    NodeKind.OBJECT_JOJO) {

                return compileToTypedObject(
                        parentPlan,
                        sourceType,
                        targetType,
                        generated,
                        stack);
            }

            /*
             * Dynamic/open object source -> dynamic target. Runtime Object is
             * included deliberately; only that source kind needs runtime OBNT
             * dispatch through Nodes.
             */
            if (isDynamicObjectSource(sourceKind) &&
                    isDynamicObjectTarget(targetKind)) {

                return compileDynamicObject(
                        parentPlan,
                        sourceType,
                        targetType,
                        generated,
                        stack);
            }

            /*
             * Plain Java properties -> Map / JsonObject.
             */
            if (isPojoLike(sourceKind) &&
                    isDynamicObjectTarget(targetKind)) {

                return compilePojoToDynamic(
                        parentPlan,
                        sourceType,
                        targetType,
                        generated,
                        stack);
            }

            /*
             * Any statically/dynamically readable object -> ordinary typed
             * object. COMPILE_TIME_UNKNOWN is resolved through NodeAccess using
             * Nodes.getInObject at generated-code runtime.
             */
            if (isTypedObject(targetKind)) {
                return compileToTypedObject(
                        parentPlan,
                        sourceType,
                        targetType,
                        generated,
                        stack);
            }

            error(
                    parentPlan.method(),
                    generated,
                    "unsupported structural mapping " +
                            sourceType +
                            " -> " +
                            targetType);

            return null;

        } finally {
            stack.remove(key);
        }
    }


    // -------------------------------------------------------------------------
    // Structural: typed target
    // -------------------------------------------------------------------------

    private CompiledConversion compileToTypedObject(
            MappingPlan parentPlan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            Set<String> stack) {

        MappingPlan nestedPlan =
                nestedPlan(
                        parentPlan,
                        targetType);

        CreatorResolver.Creation creation =
                creators.resolve(
                        mapper,
                        nestedPlan,
                        generated);

        if (creation == null) {
            return null;
        }

        TypeMirror workingTarget =
                creation.targetType();

        List<StructuralArgument> arguments =
                new ArrayList<StructuralArgument>();

        Set<String> consumed =
                new LinkedHashSet<String>();

        /*
         * Constructor / record arguments.
         */
        if (creation.kind() ==
                CreatorResolver.Creation.Kind.CONSTRUCTOR) {

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

                String name =
                        context.annotations
                                .propertyName(
                                        parameter,
                                        parameter
                                                .getSimpleName()
                                                .toString());

                NodeAccess source =
                        context.access
                                .resolveName(
                                        sourceType,
                                        name);

                if (source == null ||
                        !source.readable()) {

                    error(
                            parentPlan.method(),
                            generated,
                            "cannot resolve source property '" +
                                    name +
                                    "' for constructor of " +
                                    workingTarget);

                    return null;
                }

                TypeMirror argumentType =
                        parameterTypes.get(i);

                CompiledConversion conversion =
                        compileNested(
                                parentPlan,
                                source.readType(),
                                argumentType,
                                generated,
                                stack);

                if (conversion == null) {
                    return null;
                }

                arguments.add(
                        new StructuralArgument(
                                name,
                                source,
                                argumentType,
                                conversion));

                consumed.add(name);
            }
        }

        List<StructuralProperty> properties =
                new ArrayList<StructuralProperty>();

        Map<String, Property> targetProperties =
                context.properties
                        .resolve(
                                workingTarget);

        for (Property property :
                targetProperties.values()) {

            if (!property.writable()) {
                continue;
            }

            String name =
                    property.name();

            if (consumed.contains(name)) {
                continue;
            }

            NodeAccess source =
                    context.access
                            .resolveName(
                                    sourceType,
                                    name);

            if (source == null ||
                    !source.readable()) {

                /*
                 * Structural auto mapping ignores unmatched source properties.
                 */
                continue;
            }

            NodeAccess target =
                    context.access
                            .resolveName(
                                    workingTarget,
                                    name);

            if (target == null ||
                    !target.writable()) {

                continue;
            }

            CompiledConversion conversion =
                    compileNested(
                            parentPlan,
                            source.readType(),
                            target.writeType(),
                            generated,
                            stack);

            if (conversion == null) {
                return null;
            }

            properties.add(
                    new StructuralProperty(
                            name,
                            source,
                            target,
                            conversion));
        }

        DynamicObjectPlan dynamicObject =
                types.nodeKind(workingTarget) ==
                        NodeKind.OBJECT_JOJO
                        ? compileJojoRemainder(
                        parentPlan,
                        sourceType,
                        targetProperties.keySet(),
                        generated,
                        stack)
                        : null;

        return CompiledConversion.structural(
                sourceType,
                targetType,
                workingTarget,
                creation,
                arguments,
                properties,
                dynamicObject);
    }


    // -------------------------------------------------------------------------
    // Structural: POJO -> dynamic object
    // -------------------------------------------------------------------------

    private CompiledConversion compilePojoToDynamic(
            MappingPlan plan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            Set<String> stack) {

        TypeMirror targetValue =
                dynamicObjectWriteType(
                        targetType);

        if (targetValue == null) {
            error(
                    plan.method(),
                    generated,
                    "target object type is not writable: " +
                            targetType);

            return null;
        }

        List<DynamicProperty> properties =
                new ArrayList<DynamicProperty>();

        Map<String, Property> sourceProperties =
                context.properties
                        .resolve(
                                sourceType);

        for (Property property :
                sourceProperties.values()) {

            if (!property.readable()) {
                continue;
            }

            String name =
                    property.name();

            NodeAccess source =
                    context.access
                            .resolveName(
                                    sourceType,
                                    name);

            if (source == null ||
                    !source.readable()) {

                continue;
            }

            CompiledConversion conversion =
                    compileNested(
                            plan,
                            source.readType(),
                            targetValue,
                            generated,
                            stack);

            if (conversion == null) {
                return null;
            }

            properties.add(
                    new DynamicProperty(
                            name,
                            source,
                            conversion));
        }

        return CompiledConversion.structural(
                sourceType,
                targetType,
                targetType,
                null,
                null,
                null,
                new DynamicObjectPlan(
                        DynamicObjectPlan.Kind.STATIC_PROPERTIES,
                        types.nodeKind(sourceType),
                        targetValue,
                        properties,
                        null,
                        Collections.<String>emptySet()));
    }


    // -------------------------------------------------------------------------
    // Structural: dynamic -> dynamic
    // -------------------------------------------------------------------------

    private CompiledConversion compileDynamicObject(
            MappingPlan plan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            Set<String> stack) {

        TypeMirror sourceValue =
                dynamicObjectReadType(
                        sourceType);

        TypeMirror targetValue =
                dynamicObjectWriteType(
                        targetType);

        if (sourceValue == null ||
                targetValue == null) {

            error(
                    plan.method(),
                    generated,
                    "cannot resolve dynamic object value type for " +
                            sourceType +
                            " -> " +
                            targetType);

            return null;
        }

        CompiledConversion valueConversion =
                compileNested(
                        plan,
                        sourceValue,
                        targetValue,
                        generated,
                        stack);

        if (valueConversion == null) {
            return null;
        }

        return CompiledConversion.structural(
                sourceType,
                targetType,
                targetType,
                null,
                null,
                null,
                new DynamicObjectPlan(
                        dynamicKind(sourceType, false),
                        types.nodeKind(sourceType),
                        targetValue,
                        null,
                        valueConversion,
                        Collections.<String>emptySet()));
    }


    // -------------------------------------------------------------------------
    // Structural: JOJO dynamic remainder
    // -------------------------------------------------------------------------

    private DynamicObjectPlan compileJojoRemainder(
            MappingPlan plan,
            TypeMirror sourceType,
            Set<String> excludedNames,
            GeneratedClass generated,
            Set<String> stack) {

        NodeKind sourceKind =
                types.nodeKind(sourceType);

        switch (sourceKind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
            case COMPILE_TIME_UNKNOWN:
                break;

            default:
                return null;
        }

        TypeMirror sourceValue =
                dynamicObjectReadType(
                        sourceType);

        if (sourceValue == null) {
            return null;
        }

        CompiledConversion valueConversion =
                compileNested(
                        plan,
                        sourceValue,
                        types.objectType(),
                        generated,
                        stack);

        if (valueConversion == null) {
            return null;
        }

        return new DynamicObjectPlan(
                dynamicKind(sourceType, true),
                sourceKind,
                types.objectType(),
                null,
                valueConversion,
                excludedNames);
    }


    private DynamicObjectPlan.Kind dynamicKind(
            TypeMirror sourceType,
            boolean remainder) {

        NodeKind kind =
                types.nodeKind(sourceType);

        if (remainder &&
                kind == NodeKind.OBJECT_JOJO) {

            return DynamicObjectPlan.Kind.DYNAMIC_ENTRIES;
        }

        switch (kind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
                return DynamicObjectPlan.Kind.ENTRIES;

            case COMPILE_TIME_UNKNOWN:
                return DynamicObjectPlan.Kind.RUNTIME_ENTRIES;

            default:
                throw new IllegalStateException(
                        "unsupported dynamic object source " +
                                sourceType);
        }
    }


    // -------------------------------------------------------------------------
    // Nested conversion
    // -------------------------------------------------------------------------

    private CompiledConversion compileNested(
            MappingPlan plan,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated,
            Set<String> stack) {

        ConverterResolver.Conversion selected =
                converters.resolve(
                        plan,
                        null,
                        sourceType,
                        targetType,
                        generated);

        if (selected == null) {
            return null;
        }

        return compile(
                plan,
                selected,
                generated,
                stack);
    }


    // -------------------------------------------------------------------------
    // Dynamic object type
    // -------------------------------------------------------------------------

    private TypeMirror dynamicObjectReadType(
            TypeMirror type) {

        NodeKind kind =
                types.nodeKind(type);

        switch (kind) {
            case OBJECT_MAP:
                return types.mapReadValueType(
                        type);

            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
            case COMPILE_TIME_UNKNOWN:
                return types.objectType();

            default:
                return null;
        }
    }


    private TypeMirror dynamicObjectWriteType(
            TypeMirror type) {

        NodeKind kind =
                types.nodeKind(type);

        switch (kind) {
            case OBJECT_MAP:
                return types.mapWriteValueType(
                        type);

            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
                return types.objectType();

            default:
                return null;
        }
    }


    // -------------------------------------------------------------------------
    // Nested plan
    // -------------------------------------------------------------------------

    private MappingPlan nestedPlan(
            MappingPlan parent,
            TypeMirror targetType) {

        return new MappingPlan(
                parent.method(),
                parent.methodType(),
                MappingPlan.Kind.CREATE,
                targetType,
                null,
                parent.sources(),
                parent.sourceTypes(),
                Collections.<MappingPlan.Rule>emptyList(),
                parent.options());
    }


    // -------------------------------------------------------------------------
    // Kind helpers
    // -------------------------------------------------------------------------

    private boolean isPojoLike(
            NodeKind kind) {

        return kind == NodeKind.OBJECT_POJO;
    }


    private boolean isTypedObject(
            NodeKind kind) {

        return kind == NodeKind.OBJECT_POJO ||
                kind == NodeKind.OBJECT_JOJO;
    }


    private boolean isDynamicObjectSource(
            NodeKind kind) {

        return kind == NodeKind.OBJECT_MAP ||
                kind == NodeKind.OBJECT_JSON_OBJECT ||
                kind == NodeKind.OBJECT_JOJO ||
                kind == NodeKind.COMPILE_TIME_UNKNOWN;
    }


    private boolean isDynamicObjectTarget(
            NodeKind kind) {

        return kind == NodeKind.OBJECT_MAP ||
                kind == NodeKind.OBJECT_JSON_OBJECT;
    }


    private String structuralKey(
            TypeMirror source,
            TypeMirror target) {

        return source.toString() +
                " -> " +
                target.toString();
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
    // Compiled conversion
    // -------------------------------------------------------------------------

    static final class CompiledConversion {

        enum Kind {
            DIRECT,
            METHOD,
            SCALAR,
            ONE_OF,
            CONTAINER,
            MAP,
            STRUCTURAL
        }


        private final Kind kind;

        private final TypeMirror sourceType;
        private final TypeMirror targetType;

        /*
         * METHOD
         */
        private final ExecutableElement method;
        private final TypeElement mapperType;

        /*
         * CONTAINER
         */
        private final TypeMirror sourceElementType;
        private final TypeMirror targetElementType;
        private final CompiledConversion elementConversion;

        /*
         * MAP
         */
        private final TypeMirror sourceKeyType;
        private final TypeMirror sourceValueType;
        private final TypeMirror targetKeyType;
        private final TypeMirror targetValueType;

        private final CompiledConversion keyConversion;
        private final CompiledConversion valueConversion;

        /*
         * STRUCTURAL
         */
        private final TypeMirror workingTargetType;
        private final CreatorResolver.Creation creation;

        private final List<StructuralArgument> arguments;
        private final List<StructuralProperty> properties;

        private final DynamicObjectPlan dynamicObject;


        private CompiledConversion(
                Kind kind,
                TypeMirror sourceType,
                TypeMirror targetType,
                ExecutableElement method,
                TypeElement mapperType,
                TypeMirror sourceElementType,
                TypeMirror targetElementType,
                CompiledConversion elementConversion,
                TypeMirror sourceKeyType,
                TypeMirror sourceValueType,
                TypeMirror targetKeyType,
                TypeMirror targetValueType,
                CompiledConversion keyConversion,
                CompiledConversion valueConversion,
                TypeMirror workingTargetType,
                CreatorResolver.Creation creation,
                List<StructuralArgument> arguments,
                List<StructuralProperty> properties,
                DynamicObjectPlan dynamicObject) {

            this.kind = kind;

            this.sourceType = sourceType;
            this.targetType = targetType;

            this.method = method;
            this.mapperType = mapperType;

            this.sourceElementType = sourceElementType;
            this.targetElementType = targetElementType;
            this.elementConversion = elementConversion;

            this.sourceKeyType = sourceKeyType;
            this.sourceValueType = sourceValueType;
            this.targetKeyType = targetKeyType;
            this.targetValueType = targetValueType;

            this.keyConversion = keyConversion;
            this.valueConversion = valueConversion;

            this.workingTargetType = workingTargetType;
            this.creation = creation;

            this.arguments =
                    immutable(arguments);

            this.properties =
                    immutable(properties);

            this.dynamicObject =
                    dynamicObject;
        }


        static CompiledConversion direct(
                TypeMirror source,
                TypeMirror target) {

            return simple(
                    Kind.DIRECT,
                    source,
                    target);
        }


        static CompiledConversion scalar(
                TypeMirror source,
                TypeMirror target) {

            return simple(
                    Kind.SCALAR,
                    source,
                    target);
        }


        static CompiledConversion oneOf(
                TypeMirror source,
                TypeMirror target) {

            return simple(
                    Kind.ONE_OF,
                    source,
                    target);
        }


        static CompiledConversion method(
                TypeMirror source,
                TypeMirror target,
                ExecutableElement method,
                TypeElement mapperType) {

            return new CompiledConversion(
                    Kind.METHOD,
                    source,
                    target,
                    method,
                    mapperType,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }


        static CompiledConversion container(
                TypeMirror source,
                TypeMirror target,
                TypeMirror sourceElement,
                TypeMirror targetElement,
                CompiledConversion elementConversion) {

            return new CompiledConversion(
                    Kind.CONTAINER,
                    source,
                    target,
                    null,
                    null,
                    sourceElement,
                    targetElement,
                    elementConversion,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }


        static CompiledConversion map(
                TypeMirror source,
                TypeMirror target,
                TypeMirror sourceKey,
                TypeMirror sourceValue,
                TypeMirror targetKey,
                TypeMirror targetValue,
                CompiledConversion keyConversion,
                CompiledConversion valueConversion) {

            return new CompiledConversion(
                    Kind.MAP,
                    source,
                    target,
                    null,
                    null,
                    null,
                    null,
                    null,
                    sourceKey,
                    sourceValue,
                    targetKey,
                    targetValue,
                    keyConversion,
                    valueConversion,
                    null,
                    null,
                    null,
                    null,
                    null);
        }


        static CompiledConversion structural(
                TypeMirror source,
                TypeMirror target,
                TypeMirror workingTarget,
                CreatorResolver.Creation creation,
                List<StructuralArgument> arguments,
                List<StructuralProperty> properties,
                DynamicObjectPlan dynamicObject) {

            return new CompiledConversion(
                    Kind.STRUCTURAL,
                    source,
                    target,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    workingTarget,
                    creation,
                    arguments,
                    properties,
                    dynamicObject);
        }


        private static CompiledConversion simple(
                Kind kind,
                TypeMirror source,
                TypeMirror target) {

            return new CompiledConversion(
                    kind,
                    source,
                    target,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
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

        TypeMirror sourceElementType() {
            return sourceElementType;
        }

        TypeMirror targetElementType() {
            return targetElementType;
        }

        CompiledConversion elementConversion() {
            return elementConversion;
        }

        TypeMirror sourceKeyType() {
            return sourceKeyType;
        }

        TypeMirror sourceValueType() {
            return sourceValueType;
        }

        TypeMirror targetKeyType() {
            return targetKeyType;
        }

        TypeMirror targetValueType() {
            return targetValueType;
        }

        CompiledConversion keyConversion() {
            return keyConversion;
        }

        CompiledConversion valueConversion() {
            return valueConversion;
        }

        TypeMirror workingTargetType() {
            return workingTargetType;
        }

        CreatorResolver.Creation creation() {
            return creation;
        }

        List<StructuralArgument> arguments() {
            return arguments;
        }

        List<StructuralProperty> properties() {
            return properties;
        }

        DynamicObjectPlan dynamicObject() {
            return dynamicObject;
        }


        private static <T> List<T> immutable(
                List<T> values) {

            return values == null
                    ? Collections.<T>emptyList()
                    : Collections.unmodifiableList(
                    new ArrayList<T>(values));
        }
    }


    // -------------------------------------------------------------------------
    // Structural models
    // -------------------------------------------------------------------------

    static final class StructuralArgument {

        private final String name;

        private final NodeAccess source;
        private final TypeMirror targetType;

        private final CompiledConversion conversion;


        StructuralArgument(
                String name,
                NodeAccess source,
                TypeMirror targetType,
                CompiledConversion conversion) {

            this.name = name;
            this.source = source;
            this.targetType = targetType;
            this.conversion = conversion;
        }


        String name() {
            return name;
        }

        NodeAccess source() {
            return source;
        }

        TypeMirror targetType() {
            return targetType;
        }

        CompiledConversion conversion() {
            return conversion;
        }
    }


    static final class StructuralProperty {

        private final String name;

        private final NodeAccess source;
        private final NodeAccess target;

        private final CompiledConversion conversion;


        StructuralProperty(
                String name,
                NodeAccess source,
                NodeAccess target,
                CompiledConversion conversion) {

            this.name = name;
            this.source = source;
            this.target = target;
            this.conversion = conversion;
        }


        String name() {
            return name;
        }

        NodeAccess source() {
            return source;
        }

        NodeAccess target() {
            return target;
        }

        CompiledConversion conversion() {
            return conversion;
        }
    }


    static final class DynamicProperty {

        private final String name;

        private final NodeAccess source;
        private final CompiledConversion conversion;


        DynamicProperty(
                String name,
                NodeAccess source,
                CompiledConversion conversion) {

            this.name = name;
            this.source = source;
            this.conversion = conversion;
        }


        String name() {
            return name;
        }

        NodeAccess source() {
            return source;
        }

        CompiledConversion conversion() {
            return conversion;
        }
    }


    static final class DynamicObjectPlan {

        enum Kind {
            STATIC_PROPERTIES,
            ENTRIES,
            DYNAMIC_ENTRIES,
            RUNTIME_ENTRIES
        }


        private final Kind kind;
        private final NodeKind sourceKind;

        private final TypeMirror targetValueType;

        private final List<DynamicProperty> properties;

        private final CompiledConversion valueConversion;

        private final Set<String> excludedNames;


        DynamicObjectPlan(
                Kind kind,
                NodeKind sourceKind,
                TypeMirror targetValueType,
                List<DynamicProperty> properties,
                CompiledConversion valueConversion,
                Set<String> excludedNames) {

            this.kind = kind;
            this.sourceKind = sourceKind;
            this.targetValueType =
                    targetValueType;

            this.properties =
                    properties == null
                            ? Collections.<DynamicProperty>emptyList()
                            : Collections.unmodifiableList(
                            new ArrayList<DynamicProperty>(
                                    properties));

            this.valueConversion =
                    valueConversion;

            this.excludedNames =
                    excludedNames == null
                            ? Collections.<String>emptySet()
                            : Collections.unmodifiableSet(
                            new LinkedHashSet<String>(
                                    excludedNames));
        }


        Kind kind() {
            return kind;
        }

        NodeKind sourceKind() {
            return sourceKind;
        }

        TypeMirror targetValueType() {
            return targetValueType;
        }

        List<DynamicProperty> properties() {
            return properties;
        }

        CompiledConversion valueConversion() {
            return valueConversion;
        }

        Set<String> excludedNames() {
            return excludedNames;
        }
    }
}
