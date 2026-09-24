package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates implementations for {@code @CompiledBinder} interfaces.
 */
public final class BindingGenerator {

    private final ProcessorContext context;
    private final BindingMethodGenerator methodGenerator;

    public BindingGenerator(ProcessorContext context) {
        this.context = context;
        this.methodGenerator =
                new BindingMethodGenerator(context);
    }

    public void generate(TypeElement type) {
        if (!type.getTypeParameters()
                .isEmpty()) {

            context.error(
                    type,
                    "@CompiledBinder interface cannot declare type parameters");

            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(
                        context,
                        type);

        List<ResolvedMethod> resolvedMethods =
                context.methods.abstractMethods(type);

        List<BindingPlan> plans =
                new ArrayList<BindingPlan>(
                        resolvedMethods.size());

        NameAllocator names =
                new NameAllocator();

        for (ResolvedMethod resolved :
                resolvedMethods) {

            names.reserve(
                    resolved.declaration()
                            .getSimpleName()
                            .toString());

            BindingPlan plan =
                    methodGenerator.analyze(
                            resolved,
                            generated);

            if (plan != null) {
                plans.add(plan);
            }
        }

        if (!generated.isValid()) {
            return;
        }

        BindingCompiler compiler =
                new BindingCompiler(
                        context,
                        names);

        List<BindingCompiler.CompiledMethod> compiled =
                new ArrayList<BindingCompiler.CompiledMethod>(
                        plans.size());

        for (BindingPlan plan : plans) {
            compiled.add(
                    compiler.compile(plan));
        }

        BindingEmitter emitter =
                new BindingEmitter(context);

        for (BindingCompiler.CompiledMethod method :
                compiled) {

            emitter.emitMethod(
                    method,
                    generated);
        }

        for (BindingValue helper :
                compiler.helpers()) {

            emitter.emitHelper(
                    helper,
                    generated);
        }

        generated.write();
    }
}
