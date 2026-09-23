package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.NodeKind;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.PathSyntax;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.mapping.CreatorResolver;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Compiles analyzed JDBC mapper methods into direct ResultSet mapping plans.
 */
public final class JdbcCompiler {

    private static final String JDBC_OPTIONS =
            "org.sjf4j.annotation.mapping.jdbc.JdbcMappingOptions";

    /*
     * Compute helpers declare the Java type read directly from ResultSet. Keep
     * that contract explicit instead of relying on driver-specific typed getObject.
     */
    private static final Set<String> COMPUTE_JDBC_TYPES =
            Collections.unmodifiableSet(
                    new HashSet<String>(
                            Arrays.asList(
                                    String.class.getName(),
                                    Boolean.class.getName(),
                                    Byte.class.getName(),
                                    Short.class.getName(),
                                    Integer.class.getName(),
                                    Long.class.getName(),
                                    Float.class.getName(),
                                    Double.class.getName(),
                                    Character.class.getName(),
                                    BigDecimal.class.getName(),
                                    Date.class.getName(),
                                    Time.class.getName(),
                                    Timestamp.class.getName(),
                                    Object.class.getName())));

    private final ProcessorContext context;
    private final TypeSystem types;
    private final TypeElement mapper;
    private final CreatorResolver creators;

    private final TypeMirror stringType;
    private final TypeMirror objectType;
    private final TypeMirror bigDecimalType;
    private final TypeMirror bytesType;
    private final TypeMirror sqlDateType;
    private final TypeMirror sqlTimeType;
    private final TypeMirror sqlTimestampType;
    private final TypeMirror runtimeExceptionType;
    private final TypeMirror errorType;


    public JdbcCompiler(
            ProcessorContext context,
            TypeElement mapper) {

        this.context = context;
        this.types = context.types;
        this.mapper = mapper;

        this.creators =
                new CreatorResolver(context);

        this.stringType =
                requiredType(String.class);

        this.objectType =
                requiredType(Object.class);

        this.bigDecimalType =
                requiredType(BigDecimal.class);

        this.bytesType =
                context.typeUtils.getArrayType(
                        context.typeUtils.getPrimitiveType(
                                TypeKind.BYTE));

        this.sqlDateType =
                requiredType(Date.class);

        this.sqlTimeType =
                requiredType(Time.class);

        this.sqlTimestampType =
                requiredType(Timestamp.class);

        this.runtimeExceptionType =
                requiredType(RuntimeException.class);

        this.errorType =
                requiredType(Error.class);
    }


    public CompiledMethod compile(
            JdbcPlan plan,
            GeneratedClass generated) {

        if (!types.isFullyConcrete(
                plan.rowType())) {

            error(
                    plan.method(),
                    generated,
                    "JDBC row target must be fully concrete: " +
                            plan.rowType());

            return null;
        }

        NodeKind kind =
                types.nodeKind(
                        plan.rowType());

        if (kind == NodeKind.OBJECT_MAP) {
            return compileMap(
                    plan,
                    generated);
        }

        if (kind != NodeKind.OBJECT_POJO &&
                kind != NodeKind.OBJECT_JOJO) {

            error(
                    plan.method(),
                    generated,
                    "Unsupported JDBC row target: " +
                            plan.rowType());

            return null;
        }

        return compileObject(
                plan,
                generated);
    }


    /*
     * --------------------------------------------------------------
     * Map
     * --------------------------------------------------------------
     */

    private CompiledMethod compileMap(
            JdbcPlan plan,
            GeneratedClass generated) {

        /*
         * Runtime column discovery is deliberately limited to the standard
         * Map<String, Object> result shape.
         */
        if (!types.isSameErasure(
                plan.rowType(),
                types.mapType())) {

            error(
                    plan.method(),
                    generated,
                    "JDBC Map row target must be java.util.Map<String, Object>");

            return null;
        }

        TypeMirror keyType =
                types.mapWriteKeyType(
                        plan.rowType());

        TypeMirror valueType =
                types.mapWriteValueType(
                        plan.rowType());

        if (keyType == null ||
                valueType == null ||
                !types.isFullyConcrete(keyType) ||
                !types.isFullyConcrete(valueType)) {

            error(
                    plan.method(),
                    generated,
                    "JDBC Map target must declare fully concrete key and value types");

            return null;
        }

        if (!types.isSameErasure(
                keyType,
                stringType) ||
                !types.isSameErasure(
                        valueType,
                        objectType)) {

            error(
                    plan.method(),
                    generated,
                    "JDBC Map row target must be Map<String, Object>");

            return null;
        }

        if (!plan.rules().isEmpty()) {

            error(
                    plan.method(),
                    generated,
                    "@Mapping is not supported for JDBC Map row targets");

            return null;
        }

        if (presentOnly(
                plan.method())) {

            error(
                    plan.method(),
                    generated,
                    "PRESENT_ONLY column projection is not applicable to JDBC Map targets");

            return null;
        }

        return CompiledMethod.map(
                plan,
                firstResult(
                        plan.method()));
    }


    /*
     * --------------------------------------------------------------
     * POJO / JOJO
     * --------------------------------------------------------------
     */

    private CompiledMethod compileObject(
            JdbcPlan plan,
            GeneratedClass generated) {

        CreatorResolver.Creation creation =
                creators.resolve(
                        mapper,
                        plan.method(),
                        plan.rowType(),
                        generated);

        if (creation == null) {
            return null;
        }

        TypeMirror targetType =
                creation.targetType();

        if (!types.isFullyConcrete(
                targetType)) {

            error(
                    plan.method(),
                    generated,
                    "JDBC target implementation must be fully concrete: " +
                            targetType);

            return null;
        }

        boolean presentOnly =
                presentOnly(
                        plan.method());

        /*
         * PRESENT_ONLY cannot satisfy required constructor arguments because
         * missing columns must be tolerated.
         */
        if (presentOnly &&
                creation.kind() ==
                        CreatorResolver.Creation.Kind.CONSTRUCTOR &&
                !creation.parameterTypes().isEmpty()) {

            error(
                    plan.method(),
                    generated,
                    "PRESENT_ONLY column projection is not supported for constructor-based JDBC targets");

            return null;
        }

        Map<String, JdbcPlan.Rule> rules =
                rulesByTarget(plan);

        Map<String, Property> properties =
                context.properties.resolve(
                        targetType);

        boolean jojo =
                types.nodeKind(targetType) ==
                        NodeKind.OBJECT_JOJO;

        List<ConstructorArgument> constructorArguments =
                new ArrayList<ConstructorArgument>();

        List<Assignment> assignments =
                new ArrayList<Assignment>();

        Set<String> constructorTargets =
                new HashSet<String>();

        Set<String> directPathTargets =
                new HashSet<String>();

        if (!compileConstructorArguments(
                plan,
                creation,
                rules,
                constructorTargets,
                constructorArguments,
                generated)) {

            return null;
        }

        /*
         * Explicit target paths.
         */
        for (JdbcPlan.Rule rule :
                plan.rules()) {

            if (!isPath(
                    rule.target())) {

                continue;
            }

            if (rule.ignore()) {

                error(
                        plan.method(),
                        generated,
                        "JDBC target path mapping cannot use ignore=true");

                return null;
            }

            if (rule.compute()
                    .trim()
                    .length() == 0 &&
                    (rule.source() == null ||
                            rule.source().length() == 0)) {

                error(
                        plan.method(),
                        generated,
                        "JDBC target path mappings require an explicit source column");

                return null;
            }

            Target target =
                    resolveTarget(
                            targetType,
                            rule.target(),
                            generated,
                            plan.method());

            if (target == null) {
                return null;
            }

            if (target.steps().size() == 1) {

                PathSegment first =
                        target.steps()
                                .get(0)
                                .segment();

                if (first instanceof
                        PathSegment.Name) {
                    directPathTargets.add(
                            ((PathSegment.Name) first)
                                    .name);
                }
            }

            Value value =
                    value(
                            rule,
                            target.type(),
                            generated,
                            plan.method());

            if (value == null) {
                return null;
            }

            assignments.add(
                    new Assignment(
                            target,
                            value));
        }

        /*
         * Automatic and explicitly renamed top-level writable properties.
         */
        for (Map.Entry<String, Property> entry :
                properties.entrySet()) {

            String name =
                    entry.getKey();

            Property property =
                    entry.getValue();

            if (!property.writable()) {
                continue;
            }

            /*
             * Constructor owns this value.
             */
            if (constructorTargets.contains(
                    name)) {

                continue;
            }

            JdbcPlan.Rule rule =
                    rules.get(name);

            if (rule == null &&
                    directPathTargets.contains(name)) {
                continue;
            }

            if (rule != null &&
                    rule.ignore()) {

                continue;
            }

            NodeAccess access =
                    context.access.resolveName(
                            targetType,
                            name);

            if (access == null ||
                    access.writeType() == null) {

                error(
                        plan.method(),
                        generated,
                        "JDBC target property is not writable: " +
                                name);

                return null;
            }

            TypeMirror valueType =
                    access.writeType();

            if (!types.isFullyConcrete(
                    valueType)) {

                error(
                        plan.method(),
                        generated,
                        "JDBC target property type must be fully concrete: " +
                                valueType);

                return null;
            }

            Value value =
                    rule == null
                            ? directValue(
                                    name,
                                    valueType,
                                    generated,
                                    plan.method())
                            : value(
                                    rule,
                                    valueType,
                                    generated,
                                    plan.method());

            if (value == null) {
                return null;
            }

            assignments.add(
                    new Assignment(
                            Target.property(
                                    name,
                                    access),
                            value));
        }

        /*
         * Validate explicit simple targets that were not consumed by either a
         * creator argument or a discovered property.
         */
        for (JdbcPlan.Rule rule :
                plan.rules()) {

            if (isPath(
                    rule.target())) {

                continue;
            }

            if (constructorTargets.contains(
                    rule.target())) {

                continue;
            }

            if (properties.containsKey(
                    rule.target())) {

                continue;
            }

            error(
                    plan.method(),
                    generated,
                    "Unknown JDBC mapping target: " +
                            rule.target());

            return null;
        }

        List<String> readColumns =
                readColumns(
                        constructorArguments,
                        assignments);

        return CompiledMethod.object(
                plan,
                creation,
                targetType,
                constructorArguments,
                assignments,
                jojo,
                readColumns,
                jojo
                        ? consumedColumns(
                        plan,
                        readColumns)
                        : Collections.<String>emptyList(),
                presentOnly,
                firstResult(
                        plan.method()));
    }


    /*
     * --------------------------------------------------------------
     * Constructor
     * --------------------------------------------------------------
     */

    private boolean compileConstructorArguments(
            JdbcPlan plan,
            CreatorResolver.Creation creation,
            Map<String, JdbcPlan.Rule> rules,
            Set<String> constructorTargets,
            List<ConstructorArgument> result,
            GeneratedClass generated) {

        if (creation.kind() !=
                CreatorResolver.Creation.Kind.CONSTRUCTOR) {

            return true;
        }

        ExecutableElement constructor =
                creation.constructor();

        List<? extends VariableElement> parameters =
                constructor.getParameters();

        List<? extends TypeMirror> parameterTypes =
                creation.parameterTypes();

        if (parameters.size() !=
                parameterTypes.size()) {

            error(
                    plan.method(),
                    generated,
                    "Cannot resolve JDBC constructor parameter types");

            return false;
        }

        for (int i = 0;
             i < parameters.size();
             i++) {

            VariableElement parameter =
                    parameters.get(i);

            TypeMirror parameterType =
                    parameterTypes.get(i);

            if (!types.isFullyConcrete(
                    parameterType)) {

                error(
                        plan.method(),
                        generated,
                        "JDBC constructor parameter type must be fully concrete: " +
                                parameterType);

                return false;
            }

            String name =
                    context.annotations.propertyName(
                            parameter,
                            parameter.getSimpleName()
                                    .toString());

            JdbcPlan.Rule rule =
                    rules.get(name);

            if (rule != null &&
                    rule.ignore()) {

                error(
                        plan.method(),
                        generated,
                        "Required JDBC constructor argument cannot be ignored: " +
                                name);

                return false;
            }

            Value value =
                    rule == null
                            ? directValue(
                                    name,
                                    parameterType,
                                    generated,
                                    plan.method())
                            : value(
                                    rule,
                                    parameterType,
                                    generated,
                                    plan.method());

            if (value == null) {
                return false;
            }

            constructorTargets.add(
                    name);

            result.add(
                    new ConstructorArgument(
                            name,
                            parameterType,
                            value));
        }

        return true;
    }


    /*
     * --------------------------------------------------------------
     * Column
     * --------------------------------------------------------------
     */

    private Value value(
            JdbcPlan.Rule rule,
            TypeMirror targetType,
            GeneratedClass generated,
            ExecutableElement method) {

        if (rule.compute()
                .trim()
                .length() != 0) {

            return computeValue(
                    rule,
                    targetType,
                    generated,
                    method);
        }

        return directValue(
                sourceName(rule),
                targetType,
                generated,
                method);
    }


    private Value directValue(
            String column,
            TypeMirror targetType,
            GeneratedClass generated,
            ExecutableElement method) {

        ColumnRead read =
                columnRead(
                        column,
                        targetType,
                        generated,
                        method);

        return read == null
                ? null
                : Value.read(read);
    }


    private Value computeValue(
            JdbcPlan.Rule rule,
            TypeMirror targetType,
            GeneratedClass generated,
            ExecutableElement method) {

        if (rule.ignore()) {
            error(
                    method,
                    generated,
                    "JDBC computed mapping cannot use ignore=true");

            return null;
        }

        if (rule.source()
                .trim()
                .length() != 0) {

            error(
                    method,
                    generated,
                    "JDBC computed mapping must use sources instead of source");

            return null;
        }

        String compute =
                rule.compute()
                        .trim();

        if (!compute.startsWith("this::") ||
                compute.length() == 6) {

            error(
                    method,
                    generated,
                    "JDBC compute must use this::defaultMethod: " +
                            compute);

            return null;
        }

        ExecutableElement helper =
                resolveComputeHelper(
                        method,
                        compute.substring(6)
                                .trim(),
                        generated);

        if (helper == null) {
            return null;
        }

        javax.lang.model.type.ExecutableType helperType =
                types.resolveMethodType(
                        mapper.asType(),
                        helper);

        if (helperType == null) {
            error(
                    method,
                    generated,
                    "Cannot resolve JDBC compute helper method: " +
                            helper.getSimpleName());

            return null;
        }

        if (!types.isAssignableBoxedGeneric(
                helperType.getReturnType(),
                targetType)) {

            error(
                    method,
                    generated,
                    "JDBC compute helper '" +
                            helper.getSimpleName() +
                            "' cannot return " +
                            helperType.getReturnType() +
                            " for target type " +
                            targetType);

            return null;
        }

        if (targetType.getKind()
                .isPrimitive() &&
                !helperType.getReturnType()
                        .getKind()
                        .isPrimitive()) {

            error(
                    method,
                    generated,
                    "JDBC compute helper '" +
                            helper.getSimpleName() +
                            "' must return a primitive for primitive target type " +
                            targetType);

            return null;
        }

        for (TypeMirror thrown :
                helperType.getThrownTypes()) {

            if (!types.isAssignable(
                    thrown,
                    runtimeExceptionType) &&
                    !types.isAssignable(
                            thrown,
                            errorType)) {

                error(
                        method,
                        generated,
                        "JDBC compute helper '" +
                                helper.getSimpleName() +
                                "' cannot declare checked exception " +
                                thrown);

                return null;
            }
        }

        List<String> columns =
                new ArrayList<String>();

        if (rule.sources().length != 0) {
            columns.addAll(
                    Arrays.asList(
                            rule.sources()));
        } else {
            for (VariableElement parameter :
                    helper.getParameters()) {
                columns.add(
                        parameter.getSimpleName()
                                .toString());
            }
        }

        List<? extends TypeMirror> parameterTypes =
                helperType.getParameterTypes();

        if (columns.size() != parameterTypes.size()) {
            error(
                    method,
                    generated,
                    "JDBC compute helper '" +
                            helper.getSimpleName() +
                            "' expects " +
                            parameterTypes.size() +
                            " source columns but mapping declares " +
                            columns.size());

            return null;
        }

        List<ColumnRead> reads =
                new ArrayList<ColumnRead>(
                        columns.size());

        for (int i = 0;
             i < columns.size();
             i++) {

            TypeMirror parameterType =
                    parameterTypes.get(i);

            if (!isComputeJdbcType(
                    parameterType)) {

                error(
                        method,
                        generated,
                        "Unsupported JDBC compute parameter type for helper '" +
                                helper.getSimpleName() +
                                "': " +
                                parameterType);

                return null;
            }

            ColumnRead read =
                    columnRead(
                            columns.get(i),
                            parameterType,
                            generated,
                            method);

            if (read == null) {
                return null;
            }

            reads.add(read);
        }

        return Value.compute(
                new ComputedValue(
                        helper.getSimpleName()
                                .toString(),
                        targetType,
                        reads));
    }


    private ExecutableElement resolveComputeHelper(
            ExecutableElement method,
            String name,
            GeneratedClass generated) {

        if (name.length() == 0) {
            error(
                    method,
                    generated,
                    "JDBC compute method reference must use this::defaultMethod");

            return null;
        }

        ExecutableElement result =
                null;

        for (javax.lang.model.element.Element member :
                mapper.getEnclosedElements()) {

            if (member.getKind() !=
                    ElementKind.METHOD ||
                    !member.getSimpleName()
                            .contentEquals(name)) {

                continue;
            }

            ExecutableElement candidate =
                    (ExecutableElement) member;

            if (!candidate.getModifiers()
                    .contains(Modifier.DEFAULT) ||
                    candidate.getReturnType()
                            .getKind() ==
                            TypeKind.VOID ||
                    !candidate.getTypeParameters()
                            .isEmpty()) {

                continue;
            }

            if (result != null) {
                error(
                        method,
                        generated,
                        "JDBC compute helper method '" +
                                name +
                                "' is ambiguous");

                return null;
            }

            result =
                    candidate;
        }

        if (result == null) {
            error(
                    method,
                    generated,
                    "JDBC compute helper method '" +
                            name +
                            "' must be a current-interface default method");
        }

        return result;
    }


    private boolean isComputeJdbcType(
            TypeMirror type) {

        TypeMirror boxed =
                types.boxed(type);

        return COMPUTE_JDBC_TYPES.contains(
                erasureName(boxed)) ||
                sameArray(
                        boxed,
                        bytesType);
    }

    private ColumnRead columnRead(
            String column,
            TypeMirror targetType,
            GeneratedClass generated,
            ExecutableElement method) {

        if (column == null ||
                column.length() == 0) {

            error(
                    method,
                    generated,
                    "JDBC source column cannot be empty");

            return null;
        }

        if (isPath(column)) {

            error(
                    method,
                    generated,
                    "JDBC source must be a column name: " +
                            column);

            return null;
        }

        if (!types.isFullyConcrete(
                targetType)) {

            error(
                    method,
                    generated,
                    "JDBC column target type must be fully concrete: " +
                            targetType);

            return null;
        }

        TypeMirror boxed =
                types.boxed(
                        targetType);

        TypeElement element =
                types.typeElement(
                        boxed);

        /*
         * String.
         */
        if (types.isSameErasure(
                boxed,
                stringType)) {

            return read(
                    column,
                    ReadKind.STRING,
                    targetType);
        }

        /*
         * Primitive / boxed primitive.
         */
        String name =
                erasureName(boxed);

        if ("java.lang.Boolean".equals(name)) {
            return read(column, ReadKind.BOOLEAN, targetType);
        }

        if ("java.lang.Byte".equals(name)) {
            return read(column, ReadKind.BYTE, targetType);
        }

        if ("java.lang.Short".equals(name)) {
            return read(column, ReadKind.SHORT, targetType);
        }

        if ("java.lang.Integer".equals(name)) {
            return read(column, ReadKind.INT, targetType);
        }

        if ("java.lang.Long".equals(name)) {
            return read(column, ReadKind.LONG, targetType);
        }

        if ("java.lang.Float".equals(name)) {
            return read(column, ReadKind.FLOAT, targetType);
        }

        if ("java.lang.Double".equals(name)) {
            return read(column, ReadKind.DOUBLE, targetType);
        }

        if ("java.lang.Character".equals(name)) {
            return read(column, ReadKind.CHARACTER, targetType);
        }

        /*
         * Common JDBC scalar reference types.
         */
        if (types.isSameErasure(
                boxed,
                bigDecimalType)) {

            return read(
                    column,
                    ReadKind.BIG_DECIMAL,
                    targetType);
        }

        if (sameArray(
                boxed,
                bytesType)) {

            return read(
                    column,
                    ReadKind.BYTES,
                    targetType);
        }

        if (types.isSameErasure(
                boxed,
                sqlDateType)) {

            return read(
                    column,
                    ReadKind.DATE,
                    targetType);
        }

        if (types.isSameErasure(
                boxed,
                sqlTimeType)) {

            return read(
                    column,
                    ReadKind.TIME,
                    targetType);
        }

        if (types.isSameErasure(
                boxed,
                sqlTimestampType)) {

            return read(
                    column,
                    ReadKind.TIMESTAMP,
                    targetType);
        }

        /*
         * Enum is deliberately string-based rather than driver-dependent
         * getObject(enum.class).
         */
        if (element != null &&
                element.getKind() ==
                        ElementKind.ENUM) {

            return read(
                    column,
                    ReadKind.ENUM,
                    targetType);
        }

        if (types.isObject(
                boxed)) {

            return read(
                    column,
                    ReadKind.OBJECT,
                    targetType);
        }

        /*
         * @NodeValue uses the SJF4J binding conversion pipeline.
         */
        if (types.isNodeValue(
                boxed)) {

            return read(
                    column,
                    ReadKind.NODE_VALUE,
                    targetType);
        }

        /*
         * Containers are structural targets and are not synthesized from one
         * JDBC column.
         */
        NodeKind kind =
                types.nodeKind(
                        boxed);

        switch (kind) {
            case ARRAY_ARRAY:
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:

            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:

                error(
                        method,
                        generated,
                        "Nested structural JDBC column mapping is not supported: " +
                                targetType);

                return null;

            default:
                /*
                 * JDBC 4.1 typed getObject is used as the generic scalar/custom
                 * type fallback, e.g. UUID, LocalDate and driver-specific types.
                 */
                return read(
                        column,
                        ReadKind.TYPED_OBJECT,
                        targetType);
        }
    }


    private ColumnRead read(
            String column,
            ReadKind kind,
            TypeMirror targetType) {

        return new ColumnRead(
                column,
                kind,
                targetType);
    }


    /*
     * --------------------------------------------------------------
     * Target Path
     * --------------------------------------------------------------
     */

    private Target resolveTarget(
            TypeMirror rootType,
            String expression,
            GeneratedClass generated,
            ExecutableElement method) {

        PathSegment[] segments;

        try {
            segments =
                    expression.startsWith("/")
                            ? PathSyntax.parsePointer(
                            expression)
                            : PathSyntax.parsePath(
                            expression);

        } catch (RuntimeException e) {

            error(
                    method,
                    generated,
                    "Invalid JDBC target path '" +
                            expression +
                            "': " +
                            e.getMessage());

            return null;
        }

        if (segments.length <= 1) {

            error(
                    method,
                    generated,
                    "JDBC target path must contain a child segment: " +
                            expression);

            return null;
        }

        List<TargetStep> steps =
                new ArrayList<TargetStep>(
                        segments.length - 1);

        TypeMirror currentType =
                rootType;

        for (int i = 1;
             i < segments.length;
             i++) {

            PathSegment segment =
                    segments[i];

            boolean last =
                    i ==
                            segments.length - 1;

            NodeAccess access;

            if (segment instanceof
                    PathSegment.Name) {

                access =
                        context.access.resolveName(
                                currentType,
                                ((PathSegment.Name) segment)
                                        .name);

            } else if (segment instanceof
                    PathSegment.Index) {

                int index =
                        ((PathSegment.Index) segment)
                                .index;

                /*
                 * The JDBC target emitter intentionally uses direct static
                 * indexing. Relative negative indexing would require extra
                 * runtime path semantics.
                 */
                if (index < 0) {

                    error(
                            method,
                            generated,
                            "Negative indexes are not supported in JDBC target paths: " +
                                    expression);

                    return null;
                }

                access =
                        context.access.resolveIndex(
                                currentType);

            } else {

                error(
                        method,
                        generated,
                        "JDBC target path supports only property names and indexes: " +
                                expression);

                return null;
            }

            if (access == null) {

                error(
                        method,
                        generated,
                        "Cannot resolve JDBC target path '" +
                                expression +
                                "' at segment " +
                                segment);

                return null;
            }

            if (!supportedTargetAccess(
                    access)) {

                error(
                        method,
                        generated,
                        "Unsupported JDBC target path access '" +
                                access.kind() +
                                "' in " +
                                expression);

                return null;
            }

            if (last) {

                TypeMirror writeType =
                        access.writeType();

                if (writeType == null) {

                    error(
                            method,
                            generated,
                            "Final JDBC target path segment is not writable: " +
                                    expression);

                    return null;
                }

                if (!types.isFullyConcrete(
                        writeType)) {

                    error(
                            method,
                            generated,
                            "JDBC target path type must be fully concrete: " +
                                    writeType);

                    return null;
                }

            } else {

                TypeMirror readType =
                        access.readType();

                if (readType == null) {

                    error(
                            method,
                            generated,
                            "Intermediate JDBC target path segment is not readable: " +
                                    expression);

                    return null;
                }

                if (readType
                        .getKind()
                        .isPrimitive()) {

                    error(
                            method,
                            generated,
                            "JDBC target path cannot traverse primitive value: " +
                                    expression);

                    return null;
                }

                if (!types.isFullyConcrete(
                        readType)) {

                    error(
                            method,
                            generated,
                            "Intermediate JDBC target path type must be fully concrete: " +
                                    readType);

                    return null;
                }

                currentType =
                        readType;
            }

            steps.add(
                    new TargetStep(
                            segment,
                            access));
        }

        return Target.path(
                expression,
                steps);
    }


    private boolean supportedTargetAccess(
            NodeAccess access) {

        switch (access.kind()) {
            case PROPERTY:
            case MAP:
            case JSON_OBJECT:
            case LIST:
            case ARRAY:
            case JSON_ARRAY:
                return true;

            case DYNAMIC:
            default:
                return false;
        }
    }


    /*
     * --------------------------------------------------------------
     * Mapping Rules
     * --------------------------------------------------------------
     */

    private Map<String, JdbcPlan.Rule> rulesByTarget(
            JdbcPlan plan) {

        Map<String, JdbcPlan.Rule> result =
                new HashMap<String, JdbcPlan.Rule>();

        for (JdbcPlan.Rule rule :
                plan.rules()) {

            if (!isPath(
                    rule.target())) {

                result.put(
                        rule.target(),
                        rule);
            }
        }

        return result;
    }


    private List<String> readColumns(
            List<ConstructorArgument> constructorArguments,
            List<Assignment> assignments) {

        Set<String> columns =
                new LinkedHashSet<String>();

        for (ConstructorArgument argument :
                constructorArguments) {
            addReadColumns(
                    columns,
                    argument.value());
        }

        for (Assignment assignment :
                assignments) {
            addReadColumns(
                    columns,
                    assignment.value());
        }

        return new ArrayList<String>(
                columns);
    }


    private List<String> consumedColumns(
            JdbcPlan plan,
            List<String> readColumns) {

        Set<String> columns =
                new LinkedHashSet<String>(
                        readColumns);

        for (JdbcPlan.Rule rule :
                plan.rules()) {
            if (!rule.ignore()) {
                continue;
            }

            if (rule.sources().length != 0) {
                columns.addAll(
                        Arrays.asList(
                                rule.sources()));
            } else {
                columns.add(
                        sourceName(rule));
            }
        }

        return new ArrayList<String>(
                columns);
    }


    private void addReadColumns(
            Set<String> columns,
            Value value) {

        if (value.read() != null) {
            columns.add(
                    value.read()
                            .column());
            return;
        }

        for (ColumnRead read :
                value.compute()
                        .reads()) {
            columns.add(
                    read.column());
        }
    }


    private String sourceName(
            JdbcPlan.Rule rule) {

        String source =
                rule.source();

        return source == null ||
                source.length() == 0
                ? rule.target()
                : source;
    }


    private boolean isPath(
            String target) {

        return target != null &&
                (target.startsWith("$") ||
                        target.startsWith("/"));
    }


    /*
     * --------------------------------------------------------------
     * JDBC Options
     * --------------------------------------------------------------
     */

    private boolean presentOnly(
            ExecutableElement method) {

        return optionEquals(
                method,
                "columnProjection",
                "PRESENT_ONLY");
    }


    private boolean firstResult(
            ExecutableElement method) {

        return optionEquals(
                method,
                "singleResult",
                "FIRST");
    }


    /*
     * Read through AnnotationMirror so this compiler only depends on the
     * JdbcMappingOptions member names, not on nested enum implementation types.
     */
    private boolean optionEquals(
            ExecutableElement method,
            String memberName,
            String expected) {

        for (AnnotationMirror mirror :
                method.getAnnotationMirrors()) {

            if (!JDBC_OPTIONS.equals(
                    mirror.getAnnotationType()
                            .toString())) {

                continue;
            }

            Map<? extends ExecutableElement,
                    ? extends AnnotationValue> values =
                    context.elements
                            .getElementValuesWithDefaults(
                                    mirror);

            for (Map.Entry<? extends ExecutableElement,
                    ? extends AnnotationValue> entry :
                    values.entrySet()) {

                if (!entry.getKey()
                        .getSimpleName()
                        .contentEquals(
                                memberName)) {

                    continue;
                }

                Object value =
                        entry.getValue()
                                .getValue();

                if (value instanceof
                        VariableElement) {

                    return ((VariableElement) value)
                            .getSimpleName()
                            .contentEquals(
                                    expected);
                }

                return expected.equals(
                        String.valueOf(value));
            }
        }

        return false;
    }


    /*
     * --------------------------------------------------------------
     * Types
     * --------------------------------------------------------------
     */

    private TypeMirror requiredType(
            Class<?> type) {

        TypeElement element =
                context.elements
                        .getTypeElement(
                                type.getName());

        if (element == null) {
            throw new IllegalStateException(
                    "Required type is not available: " +
                            type.getName());
        }

        return element.asType();
    }


    private String erasureName(
            TypeMirror type) {

        return context.typeUtils
                .erasure(type)
                .toString();
    }


    private boolean sameArray(
            TypeMirror first,
            TypeMirror second) {

        if (first == null ||
                second == null ||
                first.getKind() != TypeKind.ARRAY ||
                second.getKind() != TypeKind.ARRAY) {

            return false;
        }

        return context.typeUtils
                .isSameType(
                        ((ArrayType) first)
                                .getComponentType(),
                        ((ArrayType) second)
                                .getComponentType());
    }


    private void error(
            javax.lang.model.element.Element element,
            GeneratedClass generated,
            String message) {

        context.error(
                element,
                message);

        generated.invalidate();
    }


    /*
     * --------------------------------------------------------------
     * Compiled Model
     * --------------------------------------------------------------
     */

    public static final class CompiledMethod {

        public enum Kind {
            OBJECT,
            MAP
        }

        private final JdbcPlan plan;
        private final Kind kind;

        private final CreatorResolver.Creation creation;
        private final TypeMirror targetType;

        private final List<ConstructorArgument> constructorArguments;
        private final List<Assignment> assignments;

        private final boolean jojo;
        private final List<String> readColumns;
        private final List<String> consumedColumns;

        private final boolean presentOnly;
        private final boolean firstResult;


        private CompiledMethod(
                JdbcPlan plan,
                Kind kind,
                CreatorResolver.Creation creation,
                TypeMirror targetType,
                List<ConstructorArgument> constructorArguments,
                List<Assignment> assignments,
                boolean jojo,
                List<String> readColumns,
                List<String> consumedColumns,
                boolean presentOnly,
                boolean firstResult) {

            this.plan = plan;
            this.kind = kind;
            this.creation = creation;
            this.targetType = targetType;

            this.constructorArguments =
                    Collections.unmodifiableList(
                            new ArrayList<ConstructorArgument>(
                                    constructorArguments));

            this.assignments =
                    Collections.unmodifiableList(
                            new ArrayList<Assignment>(
                                    assignments));

            this.jojo = jojo;
            this.readColumns =
                    Collections.unmodifiableList(
                            new ArrayList<String>(
                                    readColumns));
            this.consumedColumns =
                    Collections.unmodifiableList(
                            new ArrayList<String>(
                                    consumedColumns));

            this.presentOnly = presentOnly;
            this.firstResult = firstResult;
        }


        static CompiledMethod map(
                JdbcPlan plan,
                boolean firstResult) {

            return new CompiledMethod(
                    plan,
                    Kind.MAP,
                    null,
                    plan.rowType(),
                    Collections.<ConstructorArgument>emptyList(),
                    Collections.<Assignment>emptyList(),
                    false,
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    false,
                    firstResult);
        }


        static CompiledMethod object(
                JdbcPlan plan,
                CreatorResolver.Creation creation,
                TypeMirror targetType,
                List<ConstructorArgument> constructorArguments,
                List<Assignment> assignments,
                boolean jojo,
                List<String> readColumns,
                List<String> consumedColumns,
                boolean presentOnly,
                boolean firstResult) {

            return new CompiledMethod(
                    plan,
                    Kind.OBJECT,
                    creation,
                    targetType,
                    constructorArguments,
                    assignments,
                    jojo,
                    readColumns,
                    consumedColumns,
                    presentOnly,
                    firstResult);
        }


        public JdbcPlan plan() {
            return plan;
        }

        public Kind kind() {
            return kind;
        }

        public CreatorResolver.Creation creation() {
            return creation;
        }

        public TypeMirror targetType() {
            return targetType;
        }

        public List<ConstructorArgument> constructorArguments() {
            return constructorArguments;
        }

        public List<Assignment> assignments() {
            return assignments;
        }


        public boolean jojo() {
            return jojo;
        }


        public List<String> readColumns() {
            return readColumns;
        }


        public List<String> consumedColumns() {
            return consumedColumns;
        }

        public boolean presentOnly() {
            return presentOnly;
        }

        public boolean firstResult() {
            return firstResult;
        }
    }


    public enum ReadKind {
        STRING,

        BOOLEAN,
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,

        CHARACTER,

        BIG_DECIMAL,
        BYTES,

        DATE,
        TIME,
        TIMESTAMP,

        ENUM,

        OBJECT,
        TYPED_OBJECT,

        NODE_VALUE
    }


    public static final class ColumnRead {

        private final String column;
        private final ReadKind kind;
        private final TypeMirror targetType;


        ColumnRead(
                String column,
                ReadKind kind,
                TypeMirror targetType) {

            this.column = column;
            this.kind = kind;
            this.targetType = targetType;
        }


        public String column() {
            return column;
        }

        public ReadKind kind() {
            return kind;
        }

        public TypeMirror targetType() {
            return targetType;
        }

        public boolean primitive() {
            return targetType
                    .getKind()
                    .isPrimitive();
        }
    }


    public static final class ConstructorArgument {

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


        public String name() {
            return name;
        }

        public TypeMirror type() {
            return type;
        }

        public Value value() {
            return value;
        }
    }


    public static final class Assignment {

        private final Target target;
        private final Value value;


        Assignment(
                Target target,
                Value value) {

            this.target = target;
            this.value = value;
        }


        public Target target() {
            return target;
        }

        public Value value() {
            return value;
        }
    }


    public static final class Value {

        private final ColumnRead read;
        private final ComputedValue compute;


        private Value(
                ColumnRead read,
                ComputedValue compute) {

            this.read = read;
            this.compute = compute;
        }


        static Value read(
                ColumnRead read) {

            return new Value(
                    read,
                    null);
        }


        static Value compute(
                ComputedValue compute) {

            return new Value(
                    null,
                    compute);
        }


        public ColumnRead read() {
            return read;
        }


        public ComputedValue compute() {
            return compute;
        }
    }


    public static final class ComputedValue {

        private final String helper;
        private final TypeMirror targetType;
        private final List<ColumnRead> reads;


        ComputedValue(
                String helper,
                TypeMirror targetType,
                List<ColumnRead> reads) {

            this.helper = helper;
            this.targetType = targetType;
            this.reads = Collections.unmodifiableList(
                    new ArrayList<ColumnRead>(
                            reads));
        }


        public String helper() {
            return helper;
        }


        public TypeMirror targetType() {
            return targetType;
        }


        public List<ColumnRead> reads() {
            return reads;
        }
    }


    public static final class TargetStep {

        private final PathSegment segment;
        private final NodeAccess access;


        TargetStep(
                PathSegment segment,
                NodeAccess access) {

            this.segment = segment;
            this.access = access;
        }


        public PathSegment segment() {
            return segment;
        }

        public NodeAccess access() {
            return access;
        }
    }


    public static final class Target {

        public enum Kind {
            PROPERTY,
            PATH
        }

        private final Kind kind;
        private final String expression;
        private final List<TargetStep> steps;


        private Target(
                Kind kind,
                String expression,
                List<TargetStep> steps) {

            this.kind = kind;
            this.expression = expression;

            this.steps =
                    Collections.unmodifiableList(
                            new ArrayList<TargetStep>(
                                    steps));
        }


        static Target property(
                String name,
                NodeAccess access) {

            List<TargetStep> steps =
                    new ArrayList<TargetStep>(1);

            steps.add(
                    new TargetStep(
                            new PathSegment.Name(
                                    null,
                                    name),
                            access));

            return new Target(
                    Kind.PROPERTY,
                    name,
                    steps);
        }


        static Target path(
                String expression,
                List<TargetStep> steps) {

            return new Target(
                    Kind.PATH,
                    expression,
                    steps);
        }


        public Kind kind() {
            return kind;
        }

        public String expression() {
            return expression;
        }

        public List<TargetStep> steps() {
            return steps;
        }

        public TargetStep finalStep() {
            return steps.get(
                    steps.size() - 1);
        }

        public NodeAccess finalAccess() {
            return finalStep()
                    .access();
        }

        public TypeMirror type() {
            return finalAccess()
                    .writeType();
        }
    }
}
