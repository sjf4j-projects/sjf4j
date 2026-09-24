package org.sjf4j.processor.mapping;

import org.sjf4j.exception.NodeException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Analyzes one abstract method declared by an {@code @CompiledMapper}.
 *
 * <p>This class handles method shape and mapping-annotation semantics only.
 * Target creation, converter selection, structural mapping and source emission
 * are handled after all mapper methods have been analyzed.</p>
 */
public final class MapperMethodGenerator {

    private static final String MAPPING =
            "org.sjf4j.annotation.mapping.Mapping";

    private static final String MAPPINGS =
            "org.sjf4j.annotation.mapping.Mappings";

    private static final String MAPPING_OPTIONS =
            "org.sjf4j.annotation.mapping.MappingOptions";

    private static final String MAPPING_IF_PARENT_PRESENT =
            "org.sjf4j.annotation.mapping.MappingIfParentPresent";

    private static final String ENSURE_MAPPING =
            "org.sjf4j.annotation.mapping.EnsureMapping";


    private final ProcessorContext context;


    public MapperMethodGenerator(ProcessorContext context) {
        this.context = context;
    }


    /**
     * Analyzes one mapper method.
     *
     * @return resolved method plan, or {@code null} after a diagnostic
     */
    public MappingPlan analyze(
            ResolvedMethod resolvedMethod,
            GeneratedClass generated) {

        ExecutableElement method =
                resolvedMethod.declaration();

        ExecutableType methodType =
                resolvedMethod.type();

        if (!method.getTypeParameters().isEmpty()) {
            error(
                    method,
                    generated,
                    "mapper method cannot declare type parameters");

            return null;
        }

        List<? extends VariableElement> parameters =
                method.getParameters();

        List<? extends TypeMirror> parameterTypes =
                methodType.getParameterTypes();

        if (parameters.isEmpty()) {
            error(
                    method,
                    generated,
                    "mapper method must declare at least one source parameter");

            return null;
        }

        MappingPlan.Kind kind;
        TypeMirror targetType;
        VariableElement targetParameter;
        List<VariableElement> sources =
                new ArrayList<VariableElement>();

        List<TypeMirror> sourceTypes =
                new ArrayList<TypeMirror>();

        if (methodType.getReturnType().getKind() ==
                TypeKind.VOID) {

            kind =
                    MappingPlan.Kind.UPDATE;

            if (parameters.size() < 2) {
                error(
                        method,
                        generated,
                        "update mapper method must declare target parameter followed by at least one source parameter");

                return null;
            }

            targetParameter =
                    parameters.get(0);

            targetType =
                    parameterTypes.get(0);

            if (targetType.getKind().isPrimitive()) {
                error(
                        method,
                        generated,
                        "update mapper target cannot be primitive");

                return null;
            }

            for (int i = 1;
                 i < parameters.size();
                 i++) {

                sources.add(
                        parameters.get(i));

                sourceTypes.add(
                        parameterTypes.get(i));
            }

        } else {
            kind =
                    MappingPlan.Kind.CREATE;

            targetParameter = null;

            targetType =
                    methodType.getReturnType();

            sources.addAll(
                    parameters);

            sourceTypes.addAll(
                    parameterTypes);
        }

        List<MappingPlan.Rule> rules =
                resolveRules(
                        method,
                        kind,
                        generated);

        if (rules == null) {
            return null;
        }

        AnnotationMirror options =
                findAnnotation(
                        method,
                        MAPPING_OPTIONS);

        return new MappingPlan(
                method,
                methodType,
                kind,
                targetType,
                targetParameter,
                sources,
                sourceTypes,
                rules,
                options);
    }


    /*
     * --------------------------------------------------------------
     * Mapping Rules
     * --------------------------------------------------------------
     */

    private List<MappingPlan.Rule> resolveRules(
            ExecutableElement method,
            MappingPlan.Kind methodKind,
            GeneratedClass generated) {

        List<MappingPlan.Rule> rules =
                new ArrayList<MappingPlan.Rule>();

        for (AnnotationMirror mirror :
                method.getAnnotationMirrors()) {

            String name =
                    annotationName(
                            mirror);

            if (MAPPING.equals(name)) {
                MappingPlan.Rule rule =
                        rule(
                                mirror,
                                MappingPlan.WriteMode.STRICT);

                if (!validateRule(
                        method,
                        methodKind,
                        rule,
                        generated)) {

                    return null;
                }

                rules.add(rule);
                continue;
            }

            if (MAPPINGS.equals(name)) {
                for (AnnotationMirror nested :
                        annotationArray(
                                mirror,
                                "value")) {

                    MappingPlan.Rule rule =
                            rule(
                                    nested,
                                    MappingPlan.WriteMode.STRICT);

                    if (!validateRule(
                            method,
                            methodKind,
                            rule,
                            generated)) {

                        return null;
                    }

                    rules.add(rule);
                }

                continue;
            }

            if (MAPPING_IF_PARENT_PRESENT.equals(name)) {
                MappingPlan.Rule rule =
                        rule(
                                mirror,
                                MappingPlan.WriteMode.IF_PARENT_PRESENT);

                if (!validateRule(
                        method,
                        methodKind,
                        rule,
                        generated)) {

                    return null;
                }

                rules.add(rule);
                continue;
            }

            if (ENSURE_MAPPING.equals(name)) {
                MappingPlan.Rule rule =
                        rule(
                                mirror,
                                MappingPlan.WriteMode.ENSURE);

                if (!validateRule(
                        method,
                        methodKind,
                        rule,
                        generated)) {

                    return null;
                }

                rules.add(rule);
            }
        }

        if (!validateDuplicateTargets(
                method,
                rules,
                generated)) {

            return null;
        }

        return rules;
    }


    private MappingPlan.Rule rule(
            AnnotationMirror annotation,
            MappingPlan.WriteMode mode) {

        String target =
                stringValue(
                        annotation,
                        "target");

        String source =
                stringValue(
                        annotation,
                        "source");

        List<String> sources =
                stringArrayValue(
                        annotation,
                        "sources");

        String compute =
                stringValue(
                        annotation,
                        "compute");

        String nestedMapper =
                stringValue(
                        annotation,
                        "nestedMapper");

        boolean ignore =
                booleanValue(
                        annotation,
                        "ignore",
                        false);

        String arrayPolicy =
                enumValue(
                        annotation,
                        "array");

        String objectPolicy =
                enumValue(
                        annotation,
                        "object");

        boolean arrayExplicit =
                hasExplicitValue(
                        annotation,
                        "array");

        boolean objectExplicit =
                hasExplicitValue(
                        annotation,
                        "object");

        return new MappingPlan.Rule(
                annotation,
                mode,
                target,
                source,
                sources,
                compute,
                nestedMapper,
                ignore,
                arrayPolicy,
                objectPolicy,
                arrayExplicit,
                objectExplicit);
    }


    private boolean validateRule(
            ExecutableElement method,
            MappingPlan.Kind methodKind,
            MappingPlan.Rule rule,
            GeneratedClass generated) {

        if (rule.target().isEmpty()) {
            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " target must not be empty");

            return false;
        }

        if (!rule.source().isEmpty() &&
                !rule.sources().isEmpty()) {

            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " cannot declare both source and sources");

            return false;
        }

        if (!rule.sources().isEmpty() &&
                rule.compute().isEmpty()) {

            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " sources may be used only with compute");

            return false;
        }

        if (!rule.compute().isEmpty() &&
                !rule.nestedMapper().isEmpty()) {

            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " cannot declare both compute and nestedMapper");

            return false;
        }

        for (String source :
                rule.sources()) {

            if (source == null ||
                    source.trim().isEmpty()) {

                error(
                        method,
                        generated,
                        annotationSimpleName(rule.annotation()) +
                                " sources must not contain empty values");

                return false;
            }
        }

        boolean targetPath =
                isPath(
                        rule.target());

        if (rule.ignore()) {
            if (rule.mode() !=
                    MappingPlan.WriteMode.STRICT) {

                error(
                        method,
                        generated,
                        annotationSimpleName(rule.annotation()) +
                                " does not support ignore");

                return false;
            }

            if (targetPath) {
                error(
                        method,
                        generated,
                        "@Mapping(ignore = true) does not support target paths");

                return false;
            }

            if (!rule.sources().isEmpty() ||
                    !rule.compute().isEmpty() ||
                    !rule.nestedMapper().isEmpty()) {

                error(
                        method,
                        generated,
                        "@Mapping(ignore = true) cannot declare sources, compute or nestedMapper");

                return false;
            }
        }

        if (rule.mode() !=
                MappingPlan.WriteMode.STRICT) {

            if (!targetPath) {
                error(
                        method,
                        generated,
                        annotationSimpleName(rule.annotation()) +
                                " target must be a JSONPath or JSON Pointer");

                return false;
            }
        }

        if (targetPath &&
                (rule.arrayExplicit() ||
                        rule.objectExplicit())) {

            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " target-path mapping does not support array or object update policy");

            return false;
        }

        if (methodKind ==
                MappingPlan.Kind.CREATE) {

            if (rule.arrayExplicit()) {
                error(
                        method,
                        generated,
                        "@Mapping.array is supported only on update mapper methods");

                return false;
            }

            if (rule.objectExplicit()) {
                error(
                        method,
                        generated,
                        "@Mapping.object is supported only on update mapper methods");

                return false;
            }
        }

        if (targetPath &&
                !validateTargetPath(
                        method,
                        rule,
                        generated)) {

            return false;
        }

        return true;
    }


    private boolean validateTargetPath(
            ExecutableElement method,
            MappingPlan.Rule rule,
            GeneratedClass generated) {

        JsonPath path;

        try {
            path =
                    JsonPath.parse(
                            rule.target());

        } catch (NodeException e) {
            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " has invalid target path '" +
                            rule.target() +
                            "': " +
                            e.getMessage());

            return false;
        }

        PathSegment[] segments =
                path.segments();

        if (segments.length <= 1) {
            error(
                    method,
                    generated,
                    annotationSimpleName(rule.annotation()) +
                            " target path must contain a non-root child");

            return false;
        }

        if (rule.mode() ==
                MappingPlan.WriteMode.IF_PARENT_PRESENT) {

            for (int i = 1;
                 i < segments.length;
                 i++) {

                PathSegment segment =
                        segments[i];

                if (!(segment instanceof PathSegment.Name) &&
                        !(segment instanceof PathSegment.Index)) {

                    error(
                            method,
                            generated,
                            "@MappingIfParentPresent target path supports only name and index segments");

                    return false;
                }
            }
        }

        if (rule.mode() ==
                MappingPlan.WriteMode.ENSURE) {

            for (int i = 1;
                 i < segments.length;
                 i++) {

                if (!(segments[i] instanceof
                        PathSegment.Name)) {

                    error(
                            method,
                            generated,
                            "@EnsureMapping target path supports only name segments");

                    return false;
                }
            }
        }

        return true;
    }


    private boolean validateDuplicateTargets(
            ExecutableElement method,
            List<MappingPlan.Rule> rules,
            GeneratedClass generated) {

        Set<String> targets =
                new HashSet<String>();

        for (MappingPlan.Rule rule :
                rules) {

            if (!targets.add(
                    rule.target())) {

                error(
                        method,
                        generated,
                        "duplicate mapping target '" +
                                rule.target() +
                                "'");

                return false;
            }
        }

        return true;
    }


    /*
     * --------------------------------------------------------------
     * Annotation Helpers
     * --------------------------------------------------------------
     */

    private AnnotationMirror findAnnotation(
            Element element,
            String annotationName) {

        for (AnnotationMirror mirror :
                element.getAnnotationMirrors()) {

            if (annotationName.equals(
                    annotationName(mirror))) {

                return mirror;
            }
        }

        return null;
    }


    private String annotationName(
            AnnotationMirror mirror) {

        Element element =
                mirror.getAnnotationType()
                        .asElement();

        if (!(element instanceof TypeElement)) {
            return "";
        }

        return ((TypeElement) element)
                .getQualifiedName()
                .toString();
    }


    private String annotationSimpleName(
            AnnotationMirror mirror) {

        Element element =
                mirror.getAnnotationType()
                        .asElement();

        return element == null
                ? "@Mapping"
                : "@" +
                element.getSimpleName();
    }


    private AnnotationValue value(
            AnnotationMirror mirror,
            String name) {

        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                context.elements
                        .getElementValuesWithDefaults(
                                mirror);

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
            AnnotationMirror mirror,
            String name) {

        for (ExecutableElement member :
                mirror.getElementValues()
                        .keySet()) {

            if (member.getSimpleName()
                    .contentEquals(name)) {

                return true;
            }
        }

        return false;
    }


    private String stringValue(
            AnnotationMirror mirror,
            String name) {

        AnnotationValue value =
                value(
                        mirror,
                        name);

        if (value == null) {
            return "";
        }

        Object raw =
                value.getValue();

        return raw instanceof String
                ? ((String) raw).trim()
                : "";
    }


    private boolean booleanValue(
            AnnotationMirror mirror,
            String name,
            boolean defaultValue) {

        AnnotationValue value =
                value(
                        mirror,
                        name);

        if (value == null) {
            return defaultValue;
        }

        Object raw =
                value.getValue();

        return raw instanceof Boolean
                ? (Boolean) raw
                : defaultValue;
    }


    @SuppressWarnings("unchecked")
    private List<String> stringArrayValue(
            AnnotationMirror mirror,
            String name) {

        AnnotationValue value =
                value(
                        mirror,
                        name);

        if (value == null) {
            return new ArrayList<String>();
        }

        Object raw =
                value.getValue();

        if (!(raw instanceof List)) {
            return new ArrayList<String>();
        }

        List<String> result =
                new ArrayList<String>();

        for (AnnotationValue item :
                (List<? extends AnnotationValue>) raw) {

            Object itemValue =
                    item.getValue();

            if (itemValue instanceof String) {
                result.add(
                        ((String) itemValue)
                                .trim());
            }
        }

        return result;
    }


    @SuppressWarnings("unchecked")
    private List<AnnotationMirror> annotationArray(
            AnnotationMirror mirror,
            String name) {

        AnnotationValue value =
                value(
                        mirror,
                        name);

        if (value == null) {
            return new ArrayList<AnnotationMirror>();
        }

        Object raw =
                value.getValue();

        if (!(raw instanceof List)) {
            return new ArrayList<AnnotationMirror>();
        }

        List<AnnotationMirror> result =
                new ArrayList<AnnotationMirror>();

        for (AnnotationValue item :
                (List<? extends AnnotationValue>) raw) {

            Object itemValue =
                    item.getValue();

            if (itemValue instanceof AnnotationMirror) {
                result.add(
                        (AnnotationMirror) itemValue);
            }
        }

        return result;
    }


    private String enumValue(
            AnnotationMirror mirror,
            String name) {

        AnnotationValue value =
                value(
                        mirror,
                        name);

        if (value == null) {
            return null;
        }

        Object raw =
                value.getValue();

        if (raw instanceof VariableElement) {
            return ((VariableElement) raw)
                    .getSimpleName()
                    .toString();
        }

        return raw == null
                ? null
                : raw.toString();
    }


    /*
     * --------------------------------------------------------------
     * Path
     * --------------------------------------------------------------
     */

    private boolean isPath(String value) {
        return value != null &&
                !value.isEmpty() &&
                (value.charAt(0) == '$' ||
                        value.charAt(0) == '/');
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
}
