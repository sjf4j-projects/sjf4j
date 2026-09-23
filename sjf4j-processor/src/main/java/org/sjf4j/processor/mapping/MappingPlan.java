package org.sjf4j.processor.mapping;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * Immutable compile-time description of one mapper method.
 */
final class MappingPlan {

    enum Kind {
        CREATE,
        UPDATE
    }


    enum WriteMode {
        STRICT,
        IF_PARENT_PRESENT,
        ENSURE
    }


    private final ExecutableElement method;
    private final ExecutableType methodType;
    private final Kind kind;

    private final TypeMirror targetType;
    private final VariableElement targetParameter;

    private final List<VariableElement> sources;
    private final List<TypeMirror> sourceTypes;
    private final List<Rule> rules;

    private final AnnotationMirror options;


    MappingPlan(
            ExecutableElement method,
            ExecutableType methodType,
            Kind kind,
            TypeMirror targetType,
            VariableElement targetParameter,
            List<VariableElement> sources,
            List<TypeMirror> sourceTypes,
            List<Rule> rules,
            AnnotationMirror options) {

        this.method =
                Objects.requireNonNull(
                        method,
                        "method");

        this.methodType =
                Objects.requireNonNull(
                        methodType,
                        "methodType");

        this.kind =
                Objects.requireNonNull(
                        kind,
                        "kind");

        this.targetType =
                Objects.requireNonNull(
                        targetType,
                        "targetType");

        this.targetParameter =
                targetParameter;

        this.sources =
                Collections.unmodifiableList(
                        new ArrayList<VariableElement>(
                                sources));

        this.sourceTypes =
                Collections.unmodifiableList(
                        new ArrayList<TypeMirror>(
                                sourceTypes));

        if (this.sources.size() !=
                this.sourceTypes.size()) {
            throw new IllegalArgumentException(
                    "sources and sourceTypes must have the same size");
        }

        this.rules =
                Collections.unmodifiableList(
                        new ArrayList<Rule>(
                                rules));

        this.options =
                options;
    }


    ExecutableElement method() {
        return method;
    }

    ExecutableType methodType() {
        return methodType;
    }

    Kind kind() {
        return kind;
    }

    boolean create() {
        return kind == Kind.CREATE;
    }

    boolean update() {
        return kind == Kind.UPDATE;
    }

    TypeMirror targetType() {
        return targetType;
    }

    VariableElement targetParameter() {
        return targetParameter;
    }

    List<VariableElement> sources() {
        return sources;
    }

    List<TypeMirror> sourceTypes() {
        return sourceTypes;
    }

    TypeMirror sourceType(VariableElement source) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).equals(source)) {
                return sourceTypes.get(i);
            }
        }

        throw new IllegalArgumentException(
                "unknown source parameter: " + source);
    }

    VariableElement primarySource() {
        return sources.get(0);
    }

    TypeMirror primarySourceType() {
        return sourceTypes.get(0);
    }

    List<Rule> rules() {
        return rules;
    }

    AnnotationMirror options() {
        return options;
    }


    static final class Rule {

        private final AnnotationMirror annotation;
        private final WriteMode mode;

        private final String target;

        private final String source;
        private final List<String> sources;

        private final String compute;
        private final String nestedMapper;

        private final boolean ignore;

        private final String arrayPolicy;
        private final String objectPolicy;

        private final boolean arrayExplicit;
        private final boolean objectExplicit;


        Rule(
                AnnotationMirror annotation,
                WriteMode mode,
                String target,
                String source,
                List<String> sources,
                String compute,
                String nestedMapper,
                boolean ignore,
                String arrayPolicy,
                String objectPolicy,
                boolean arrayExplicit,
                boolean objectExplicit) {

            this.annotation =
                    Objects.requireNonNull(
                            annotation,
                            "annotation");

            this.mode =
                    Objects.requireNonNull(
                            mode,
                            "mode");

            this.target =
                    Objects.requireNonNull(
                            target,
                            "target");

            this.source =
                    source == null
                            ? ""
                            : source;

            this.sources =
                    Collections.unmodifiableList(
                            new ArrayList<String>(
                                    sources));

            this.compute =
                    compute == null
                            ? ""
                            : compute;

            this.nestedMapper =
                    nestedMapper == null
                            ? ""
                            : nestedMapper;

            this.ignore = ignore;

            this.arrayPolicy = arrayPolicy;
            this.objectPolicy = objectPolicy;

            this.arrayExplicit = arrayExplicit;
            this.objectExplicit = objectExplicit;
        }


        AnnotationMirror annotation() {
            return annotation;
        }

        WriteMode mode() {
            return mode;
        }

        String target() {
            return target;
        }

        String source() {
            return source;
        }

        List<String> sources() {
            return sources;
        }

        String compute() {
            return compute;
        }

        String nestedMapper() {
            return nestedMapper;
        }

        boolean ignore() {
            return ignore;
        }

        String arrayPolicy() {
            return arrayPolicy;
        }

        String objectPolicy() {
            return objectPolicy;
        }

        boolean arrayExplicit() {
            return arrayExplicit;
        }

        boolean objectExplicit() {
            return objectExplicit;
        }

        boolean targetPath() {
            return !target.isEmpty() &&
                    (target.charAt(0) == '$' ||
                            target.charAt(0) == '/');
        }

        boolean computed() {
            return !compute.isEmpty();
        }

        boolean explicitSource() {
            return !source.isEmpty();
        }
    }
}