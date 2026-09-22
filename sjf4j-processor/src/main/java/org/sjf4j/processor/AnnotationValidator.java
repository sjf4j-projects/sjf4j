package org.sjf4j.processor;

import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.EnsureMapping;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingCreator;
import org.sjf4j.annotation.mapping.MappingCreators;
import org.sjf4j.annotation.mapping.MappingIfParentPresent;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.Mappings;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapping.jdbc.JdbcMappingOptions;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.EnsurePutByPath;
import org.sjf4j.annotation.path.EnsurePutIfAbsentByPath;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.path.PutByPath;
import org.sjf4j.annotation.path.PutIfParentPresentByPath;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;


/**
 * Validates annotation ownership rules shared by compiled SJF4J features.
 */
final class AnnotationValidator {

    private static final Set<String> ROOT_ANNOTATIONS =
            names(
                    CompiledNavigator.class,
                    CompiledMapper.class,
                    CompiledJdbcMapper.class);

    private static final Set<String> PATH_METHOD_ANNOTATIONS =
            names(
                    GetByPath.class,
                    PutByPath.class,
                    PutIfParentPresentByPath.class,
                    EnsurePutByPath.class,
                    EnsurePutIfAbsentByPath.class,
                    FindByPath.class);

    private static final Set<String> MAPPING_METHOD_ANNOTATIONS =
            names(
                    Mapping.class,
                    Mappings.class,
                    MappingIfParentPresent.class,
                    EnsureMapping.class);

    private static final Set<String> CREATOR_ANNOTATIONS =
            names(
                    MappingCreator.class,
                    MappingCreators.class);

    private static final Set<String> SUPPORTED_ANNOTATIONS =
            supportedAnnotations();


    private final ProcessorContext context;


    AnnotationValidator(
            ProcessorContext context) {

        this.context = context;
    }


    static Set<String> supportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }


    void validate(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        for (TypeElement annotation :
                annotations) {

            String annotationName =
                    annotation
                            .getQualifiedName()
                            .toString();

            if (ROOT_ANNOTATIONS.contains(
                    annotationName)) {
                continue;
            }

            for (Element element :
                    roundEnv.getElementsAnnotatedWith(
                            annotation)) {

                validate(
                        annotationName,
                        annotation.getSimpleName()
                                .toString(),
                        element);
            }
        }
    }


    private void validate(
            String annotationName,
            String annotationSimpleName,
            Element element) {

        if (PATH_METHOD_ANNOTATIONS.contains(
                annotationName)) {

            validateAbstractMethod(
                    element,
                    annotationSimpleName,
                    Owner.NAVIGATOR);

            return;
        }

        if (MAPPING_METHOD_ANNOTATIONS.contains(
                annotationName)) {

            validateAbstractMethod(
                    element,
                    annotationSimpleName,
                    Owner.MAPPER);

            return;
        }

        if (MappingOptions.class
                .getName()
                .equals(annotationName)) {

            validateMappingOptions(
                    element);

            return;
        }

        if (JdbcMappingOptions.class
                .getName()
                .equals(annotationName)) {

            validateJdbcMappingOptions(
                    element);

            return;
        }

        if (CREATOR_ANNOTATIONS.contains(
                annotationName)) {

            validateCreator(
                    element,
                    annotationSimpleName);
        }
    }


    // -------------------------------------------------------------------------
    // Path / mapping operations
    // -------------------------------------------------------------------------

    private void validateAbstractMethod(
            Element element,
            String annotation,
            Owner owner) {

        if (element.getKind() !=
                ElementKind.METHOD) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " can be applied only to methods");

            return;
        }

        if (!element.getModifiers()
                .contains(Modifier.ABSTRACT)) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " can be applied only to abstract methods");

            return;
        }

        Element enclosing =
                element.getEnclosingElement();

        if (enclosing.getKind() !=
                ElementKind.INTERFACE) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " method must be declared in a compiled interface");

            return;
        }

        TypeElement type =
                (TypeElement) enclosing;

        switch (owner) {
            case NAVIGATOR:
                if (type.getAnnotation(
                        CompiledNavigator.class) == null) {

                    context.error(
                            element,
                            "@" +
                                    annotation +
                                    " method must be declared in an " +
                                    "@CompiledNavigator interface");
                }
                return;

            case MAPPER:
                if (!isMapper(type)) {
                    context.error(
                            element,
                            "@" +
                                    annotation +
                                    " method must be declared in an " +
                                    "@CompiledMapper or @CompiledJdbcMapper interface");
                }
                return;

            default:
                throw new AssertionError(owner);
        }
    }


    // -------------------------------------------------------------------------
    // Options
    // -------------------------------------------------------------------------

    private void validateMappingOptions(
            Element element) {

        TypeElement owner =
                abstractMethodOwner(
                        element,
                        "MappingOptions");

        if (owner == null) {
            return;
        }

        if (owner.getAnnotation(
                CompiledMapper.class) != null) {
            return;
        }

        if (owner.getAnnotation(
                CompiledJdbcMapper.class) != null) {

            context.error(
                    element,
                    "@MappingOptions is not supported on " +
                            "@CompiledJdbcMapper methods; " +
                            "use @JdbcMappingOptions");

            return;
        }

        context.error(
                element,
                "@MappingOptions method must be declared in an " +
                        "@CompiledMapper interface");
    }


    private void validateJdbcMappingOptions(
            Element element) {

        TypeElement owner =
                abstractMethodOwner(
                        element,
                        "JdbcMappingOptions");

        if (owner == null) {
            return;
        }

        if (owner.getAnnotation(
                CompiledJdbcMapper.class) == null) {

            context.error(
                    element,
                    "@JdbcMappingOptions method must be declared in an " +
                            "@CompiledJdbcMapper interface");
        }
    }


    // -------------------------------------------------------------------------
    // Creator
    // -------------------------------------------------------------------------

    private void validateCreator(
            Element element,
            String annotation) {

        if (element.getKind() ==
                ElementKind.INTERFACE) {

            if (!isMapper(
                    (TypeElement) element)) {

                context.error(
                        element,
                        "@" +
                                annotation +
                                " can be applied only to " +
                                "@CompiledMapper or @CompiledJdbcMapper interfaces");
            }

            return;
        }

        if (element.getKind() !=
                ElementKind.METHOD) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " can be applied only to mapper interfaces " +
                            "or abstract mapper methods");

            return;
        }

        if (!element.getModifiers()
                .contains(Modifier.ABSTRACT)) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " can be applied only to abstract mapper methods");

            return;
        }

        Element enclosing =
                element.getEnclosingElement();

        if (enclosing.getKind() !=
                ElementKind.INTERFACE ||
                !isMapper(
                        (TypeElement) enclosing)) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " method must be declared in an " +
                            "@CompiledMapper or @CompiledJdbcMapper interface");
        }
    }


    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TypeElement abstractMethodOwner(
            Element element,
            String annotation) {

        if (element.getKind() !=
                ElementKind.METHOD) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " can be applied only to methods");

            return null;
        }

        if (!element.getModifiers()
                .contains(Modifier.ABSTRACT)) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " can be applied only to abstract methods");

            return null;
        }

        Element owner =
                element.getEnclosingElement();

        if (!(owner instanceof TypeElement) ||
                owner.getKind() !=
                        ElementKind.INTERFACE) {

            context.error(
                    element,
                    "@" +
                            annotation +
                            " method must be declared in an interface");

            return null;
        }

        return (TypeElement) owner;
    }


    private boolean isMapper(
            TypeElement type) {

        return type.getAnnotation(
                CompiledMapper.class) != null
                || type.getAnnotation(
                CompiledJdbcMapper.class) != null;
    }


    @SafeVarargs
    private static Set<String> names(
            Class<? extends Annotation>... annotations) {

        Set<String> result =
                new LinkedHashSet<>();

        for (Class<? extends Annotation> annotation :
                annotations) {

            result.add(
                    annotation.getName());
        }

        return Collections.unmodifiableSet(result);
    }


    private static Set<String> supportedAnnotations() {
        Set<String> result =
                new LinkedHashSet<>();

        result.addAll(ROOT_ANNOTATIONS);
        result.addAll(PATH_METHOD_ANNOTATIONS);
        result.addAll(MAPPING_METHOD_ANNOTATIONS);
        result.addAll(CREATOR_ANNOTATIONS);

        result.add(
                MappingOptions.class.getName());

        result.add(
                JdbcMappingOptions.class.getName());

        return Collections.unmodifiableSet(result);
    }


    private enum Owner {
        NAVIGATOR,
        MAPPER
    }



}