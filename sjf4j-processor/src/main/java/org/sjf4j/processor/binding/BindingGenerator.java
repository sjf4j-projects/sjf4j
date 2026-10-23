package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates implementations for {@code @CompiledBinder} interfaces. The
 * backend is resolved once per interface; method plans are checked before
 * shared read/write helpers and generated source are emitted.
 */
public final class BindingGenerator {

    private final ProcessorContext context;
    private final BindingMethodGenerator methodGenerator;
    private final BackendResolver backendResolver;

    public BindingGenerator(ProcessorContext context) {
        this.context = context;
        this.methodGenerator =
                new BindingMethodGenerator(context);
        this.backendResolver =
                new BackendResolver(context);
    }

    /** Generates a binder implementation for one annotated interface. */
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

        BackendSpec backend =
                backendResolver.resolve(
                        type,
                        generated);

        if (backend == null ||
                !generated.isValid()) {
            return;
        }

        generated.enableRuntimeContext();
        // Backend configuration is a runtime I/O concern; structural binding
        // decisions remain owned by the annotation processor.
        String backendConfigType = null;
        String mapperConvenience = null;
        switch (backend.backend()) {
            case JACKSON2:
                backendConfigType = "com.fasterxml.jackson.core.JsonFactory";
                mapperConvenience = "com.fasterxml.jackson.databind.ObjectMapper";
                break;
            case JACKSON3:
                backendConfigType = "tools.jackson.core.json.JsonFactory";
                break;
            case GSON:
                backendConfigType = "com.google.gson.Gson";
                break;
            default:
                break;
        }
        if (backendConfigType != null) {
            generated.enableBackendConstructor(
                    backendConfigType, backend.binderType(), mapperConvenience);
        }
        generated.addField(out ->
                out.line("private final " + backend.binderType() + " binder;"));
        generated.addContextInitializer(out ->
                out.line("this.binder = new " + backend.binderType() + "(context);"));

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
                        names,
                        context.metadata(type),
                        backend.backend());

        List<BindingCompiler.CompiledMethod> compiled =
                new ArrayList<BindingCompiler.CompiledMethod>(
                        plans.size());

        for (BindingPlan plan : plans) {
            BindingCompiler.CompiledMethod method =
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

        BindingEmitter emitter =
                new BindingEmitter(
                        context,
                        backend);

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
