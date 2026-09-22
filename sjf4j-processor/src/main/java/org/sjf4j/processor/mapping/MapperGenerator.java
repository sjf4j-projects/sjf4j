package org.sjf4j.processor.mapping;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Generates implementations for {@code @CompiledMapper} interfaces.
 *
 * <p>Generation is deliberately split into analysis, semantic compilation,
 * conversion preparation and source emission. No semantic resolution is
 * performed while the generated source is being written.</p>
 */
public final class MapperGenerator {

    private final ProcessorContext context;
    private final MapperMethodGenerator methodGenerator;


    public MapperGenerator(ProcessorContext context) {
        this.context = context;
        this.methodGenerator =
                new MapperMethodGenerator(context);
    }


    /**
     * Generates one compiled mapper implementation.
     */
    public void generate(TypeElement type) {
        if (!type.getTypeParameters()
                .isEmpty()) {

            context.error(
                    type,
                    "@CompiledMapper interface cannot declare type parameters");

            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(
                        context,
                        type);

        /*
         * --------------------------------------------------------------
         * 1. Analyze methods
         * --------------------------------------------------------------
         */
        List<MappingPlan> plans =
                analyze(
                        type,
                        generated);

        if (!generated.isValid()) {
            return;
        }

        /*
         * --------------------------------------------------------------
         * 2. Compile mapping semantics
         * --------------------------------------------------------------
         */
        MappingCompiler compiler =
                new MappingCompiler(
                        context,
                        type,
                        plans);

        if (!compiler.validate(
                generated)) {

            return;
        }

        List<MappingCompiler.CompiledMethod> methods =
                new ArrayList<MappingCompiler.CompiledMethod>(
                        plans.size());

        for (MappingPlan plan :
                plans) {

            MappingCompiler.CompiledMethod method =
                    compiler.compile(
                            plan,
                            generated);

            if (method != null) {
                methods.add(method);
            }
        }

        if (!generated.isValid()) {
            return;
        }

        /*
         * --------------------------------------------------------------
         * 3. Compile recursive conversions
         * --------------------------------------------------------------
         */
        ConversionEmitter conversionEmitter =
                new ConversionEmitter(
                        context,
                        type,
                        plans);

        if (!conversionEmitter.prepare(
                methods,
                generated)) {

            return;
        }

        if (!generated.isValid()) {
            return;
        }

        /*
         * --------------------------------------------------------------
         * 4. Emit methods
         * --------------------------------------------------------------
         */
        MappingEmitter emitter =
                new MappingEmitter(
                        context,
                        conversionEmitter);

        for (MappingCompiler.CompiledMethod method :
                methods) {

            emitter.emit(
                    method,
                    generated);
        }

        /*
         * --------------------------------------------------------------
         * 5. Write source
         * --------------------------------------------------------------
         */
        generated.write();
    }


    // -------------------------------------------------------------------------
    // Analyze
    // -------------------------------------------------------------------------

    private List<MappingPlan> analyze(
            TypeElement type,
            GeneratedClass generated) {

        List<ExecutableElement> methods =
                abstractMethods(type);

        List<MappingPlan> plans =
                new ArrayList<MappingPlan>(
                        methods.size());

        for (ExecutableElement method :
                methods) {

            if (hasGenericOwner(
                    type,
                    method)) {

                context.error(
                        method,
                        "Inherited mapper methods from generic interfaces are not supported");

                generated.invalidate();
                continue;
            }

            MappingPlan plan =
                    methodGenerator.analyze(
                            method,
                            generated);

            if (plan != null) {
                plans.add(plan);
            }
        }

        return plans;
    }


    // -------------------------------------------------------------------------
    // Abstract mapper methods
    // -------------------------------------------------------------------------

    /**
     * Returns effective abstract mapper methods including inherited methods.
     *
     * <p>Methods are de-duplicated by erased Java signature so an overridden
     * parent method is generated only once.</p>
     */
    private List<ExecutableElement> abstractMethods(
            TypeElement type) {

        Map<String, ExecutableElement> methods =
                new LinkedHashMap<String, ExecutableElement>();

        for (Element member :
                context.elements
                        .getAllMembers(type)) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (!method.getModifiers()
                    .contains(Modifier.ABSTRACT)) {

                continue;
            }

            if (method.getModifiers()
                    .contains(Modifier.STATIC) ||
                    method.getModifiers()
                            .contains(Modifier.PRIVATE)) {

                continue;
            }

            String key =
                    signatureKey(method);

            ExecutableElement previous =
                    methods.get(key);

            if (previous == null ||
                    prefer(
                            type,
                            method,
                            previous)) {

                methods.put(
                        key,
                        method);
            }
        }

        return new ArrayList<ExecutableElement>(
                methods.values());
    }


    /**
     * Chooses the more specific declaration when the same erased signature is
     * inherited through multiple mapper interfaces.
     */
    private boolean prefer(
            TypeElement mapper,
            ExecutableElement candidate,
            ExecutableElement current) {

        try {
            if (context.elements
                    .overrides(
                            candidate,
                            current,
                            mapper)) {

                return true;
            }

            if (context.elements
                    .overrides(
                            current,
                            candidate,
                            mapper)) {

                return false;
            }

        } catch (IllegalArgumentException ignored) {
            /*
             * Some compiler implementations are conservative for inherited
             * interface members. Return-type specificity below is sufficient
             * for our de-duplication fallback.
             */
        }

        TypeMirror candidateReturn =
                candidate.getReturnType();

        TypeMirror currentReturn =
                current.getReturnType();

        if (candidateReturn.getKind()
                .isPrimitive() ||
                currentReturn.getKind()
                        .isPrimitive()) {

            return false;
        }

        return context.typeUtils
                .isSubtype(
                        candidateReturn,
                        currentReturn);
    }


    /**
     * Creates a Java signature key ignoring return type.
     */
    private String signatureKey(
            ExecutableElement method) {

        StringBuilder key =
                new StringBuilder();

        key.append(
                        method.getSimpleName())
                .append('(');

        for (VariableElement parameter :
                method.getParameters()) {

            key.append(
                            context.typeUtils
                                    .erasure(
                                            parameter.asType()))
                    .append(';');
        }

        key.append(')');

        return key.toString();
    }


    // -------------------------------------------------------------------------
    // Generic inherited interfaces
    // -------------------------------------------------------------------------

    /**
     * The current mapping model stores source variables directly as
     * {@link VariableElement}s. Generic inherited members would require
     * resolving every member through {@code Types.asMemberOf}; reject that
     * shape explicitly rather than silently compiling unresolved type variables.
     */
    private boolean hasGenericOwner(
            TypeElement mapper,
            ExecutableElement method) {

        Element owner =
                method.getEnclosingElement();

        if (!(owner instanceof
                TypeElement)) {

            return false;
        }

        TypeElement ownerType =
                (TypeElement) owner;

        if (ownerType.equals(mapper)) {
            return false;
        }

        return !ownerType
                .getTypeParameters()
                .isEmpty();
    }
}