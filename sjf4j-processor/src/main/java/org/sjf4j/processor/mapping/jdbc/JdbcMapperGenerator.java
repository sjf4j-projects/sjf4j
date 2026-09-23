package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;


/**
 * Generates implementations for @CompiledJdbcMapper interfaces.
 */
public final class JdbcMapperGenerator {

    private final ProcessorContext context;
    private final JdbcMethodGenerator methods;


    public JdbcMapperGenerator(
            ProcessorContext context) {

        this.context = context;
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

        List<ResolvedMethod> abstractMethods =
                context.methods.abstractMethods(
                        mapper);

        List<JdbcPlan> plans =
                new ArrayList<JdbcPlan>(
                        abstractMethods.size());

        for (ResolvedMethod method :
                abstractMethods) {

            JdbcPlan plan =
                    methods.analyze(
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

}
