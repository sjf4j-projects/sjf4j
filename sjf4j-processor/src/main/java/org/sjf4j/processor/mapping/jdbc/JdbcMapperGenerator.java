package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Generates implementations for @CompiledJdbcMapper interfaces.
 */
public final class JdbcMapperGenerator {

    private final ProcessorContext context;
    private final TypeSystem types;
    private final JdbcMethodGenerator methods;


    public JdbcMapperGenerator(
            ProcessorContext context) {

        this.context = context;
        this.types = context.types;
        this.methods =
                new JdbcMethodGenerator(
                        context);
    }


    public void generate(
            TypeElement mapper) {

        /*
         * The generated implementation itself is non-generic. Generic base
         * mapper methods are supported when specialization through this mapper
         * resolves them to fully concrete types.
         */
        if (!mapper.getTypeParameters()
                .isEmpty()) {

            context.error(
                    mapper,
                    "@CompiledJdbcMapper interface must not declare type parameters");

            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(
                        context,
                        mapper);

        List<ExecutableElement> abstractMethods =
                effectiveAbstractMethods(
                        mapper);

        List<JdbcPlan> plans =
                new ArrayList<JdbcPlan>(
                        abstractMethods.size());

        for (ExecutableElement method :
                abstractMethods) {

            JdbcPlan plan =
                    methods.analyze(
                            mapper,
                            method,
                            generated);

            if (plan != null) {
                plans.add(plan);
            }
        }

        if (!generated.isValid()) {
            return;
        }

        JdbcCompiler compiler =
                new JdbcCompiler(
                        context,
                        mapper);

        List<JdbcCompiler.CompiledMethod> compiled =
                new ArrayList<JdbcCompiler.CompiledMethod>(
                        plans.size());

        for (JdbcPlan plan :
                plans) {

            JdbcCompiler.CompiledMethod method =
                    compiler.compile(
                            plan,
                            generated);

            if (method != null) {
                compiled.add(method);
            }
        }

        if (!generated.isValid()) {
            return;
        }

        JdbcEmitter emitter =
                new JdbcEmitter(
                        context);

        for (JdbcCompiler.CompiledMethod method :
                compiled) {

            emitter.emit(
                    method,
                    generated);
        }

        if (generated.isValid()) {
            generated.write();
        }
    }


    // -------------------------------------------------------------------------
    // Effective methods
    // -------------------------------------------------------------------------

    /**
     * Returns the effective abstract instance methods visible from the mapper.
     *
     * <p>Inherited generic methods are retained here. Their parameter and return
     * types are specialized later through TypeSystem.resolveMethodType().</p>
     */
    private List<ExecutableElement> effectiveAbstractMethods(
            TypeElement mapper) {

        Map<String, ExecutableElement> methods =
                new LinkedHashMap<String, ExecutableElement>();

        for (Element member :
                context.elements
                        .getAllMembers(
                                mapper)) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (!method.getModifiers()
                    .contains(
                            Modifier.ABSTRACT)) {

                continue;
            }

            if (method.getModifiers()
                    .contains(
                            Modifier.STATIC)) {

                continue;
            }

            /*
             * Private interface methods are not implementation contracts.
             * This modifier does not exist in Java 8 source, but checking it
             * remains harmless when processing newer source levels.
             */
            if (method.getModifiers()
                    .contains(
                            Modifier.PRIVATE)) {

                continue;
            }

            ExecutableType resolved =
                    types.resolveMethodType(
                            mapper.asType(),
                            method);

            if (resolved == null) {
                /*
                 * JdbcMethodGenerator will produce the proper diagnostic if
                 * this survives method selection. Keep a declaration-based key
                 * only to avoid duplicate diagnostics.
                 */
                String key =
                        declarationKey(
                                method);

                if (!methods.containsKey(
                        key)) {

                    methods.put(
                            key,
                            method);
                }

                continue;
            }

            String key =
                    resolvedKey(
                            method,
                            resolved);

            ExecutableElement existing =
                    methods.get(key);

            if (existing == null) {

                methods.put(
                        key,
                        method);

                continue;
            }

            methods.put(
                    key,
                    moreSpecific(
                            mapper,
                            existing,
                            method));
        }

        List<ExecutableElement> result =
                new ArrayList<ExecutableElement>(
                        methods.values());

        /*
         * Elements.getAllMembers() ordering is not part of the generated-code
         * contract. Sort by the effective signature for deterministic output.
         */
        Collections.sort(
                result,
                new Comparator<ExecutableElement>() {
                    @Override
                    public int compare(
                            ExecutableElement first,
                            ExecutableElement second) {

                        ExecutableType firstType =
                                types.resolveMethodType(
                                        mapper.asType(),
                                        first);

                        ExecutableType secondType =
                                types.resolveMethodType(
                                        mapper.asType(),
                                        second);

                        String firstKey =
                                firstType == null
                                        ? declarationKey(first)
                                        : resolvedKey(
                                        first,
                                        firstType);

                        String secondKey =
                                secondType == null
                                        ? declarationKey(second)
                                        : resolvedKey(
                                        second,
                                        secondType);

                        return firstKey.compareTo(
                                secondKey);
                    }
                });

        return result;
    }


    /**
     * Chooses the overriding/more-specific declaration for one effective Java
     * signature.
     */
    private ExecutableElement moreSpecific(
            TypeElement mapper,
            ExecutableElement first,
            ExecutableElement second) {

        if (context.elements.overrides(
                second,
                first,
                mapper)) {

            return second;
        }

        if (context.elements.overrides(
                first,
                second,
                mapper)) {

            return first;
        }

        /*
         * Diamond inheritance can expose equivalent methods without either
         * declaration directly overriding the other. Prefer the declaration
         * with the more specific resolved return type when possible.
         */
        ExecutableType firstType =
                types.resolveMethodType(
                        mapper.asType(),
                        first);

        ExecutableType secondType =
                types.resolveMethodType(
                        mapper.asType(),
                        second);

        if (firstType != null &&
                secondType != null) {

            TypeMirror firstReturn =
                    firstType.getReturnType();

            TypeMirror secondReturn =
                    secondType.getReturnType();

            boolean secondToFirst =
                    context.typeUtils.isAssignable(
                            secondReturn,
                            firstReturn);

            boolean firstToSecond =
                    context.typeUtils.isAssignable(
                            firstReturn,
                            secondReturn);

            if (secondToFirst &&
                    !firstToSecond) {

                return second;
            }

            if (firstToSecond &&
                    !secondToFirst) {

                return first;
            }
        }

        /*
         * Equivalent inherited declarations. Keeping the first is sufficient;
         * the Java compiler itself reports incompatible inheritance.
         */
        return first;
    }


    // -------------------------------------------------------------------------
    // Signature keys
    // -------------------------------------------------------------------------

    private String resolvedKey(
            ExecutableElement method,
            ExecutableType type) {

        StringBuilder key =
                new StringBuilder();

        key.append(
                method.getSimpleName());

        key.append('(');

        List<? extends TypeMirror> parameters =
                type.getParameterTypes();

        for (int i = 0;
             i < parameters.size();
             i++) {

            if (i != 0) {
                key.append(',');
            }

            key.append(
                    context.typeUtils
                            .erasure(
                                    parameters.get(i)));
        }

        key.append(')');

        return key.toString();
    }


    private String declarationKey(
            ExecutableElement method) {

        StringBuilder key =
                new StringBuilder();

        key.append(
                method.getSimpleName());

        key.append('(');

        for (int i = 0;
             i < method.getParameters()
                     .size();
             i++) {

            if (i != 0) {
                key.append(',');
            }

            key.append(
                    context.typeUtils
                            .erasure(
                                    method.getParameters()
                                            .get(i)
                                            .asType()));
        }

        key.append(')');

        return key.toString();
    }
}
