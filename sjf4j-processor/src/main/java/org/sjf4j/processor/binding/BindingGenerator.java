package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.code.GeneratedClass;
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

    private final CompilationContext context;
    private final BindingMethodGenerator methodGenerator;
    private final BackendResolver backendResolver;

    public BindingGenerator(CompilationContext context) {
        this.context = context;
        this.methodGenerator = new BindingMethodGenerator(context);
        this.backendResolver = new BackendResolver(context);
    }

    /** Generates a binder implementation for one annotated interface. */
    public void generate(TypeElement type) {
        if (!type.getTypeParameters().isEmpty()) {
            context.error(type, "@CompiledBinder interface cannot declare type parameters");
            return;
        }

        GeneratedClass generated = GeneratedClass.forInterface(context, type);
        BackendSpec backend = backendResolver.resolve(type, generated);

        if (backend == null || !generated.isValid()) return;

        CompiledBinder annotation = type.getAnnotation(CompiledBinder.class);
        boolean includeNulls = annotation.includeNulls();
        boolean exactNumbers = annotation.exactNumbers();

        List<ResolvedMethod> resolvedMethods = context.methods.abstractMethods(type);
        List<BindingPlan> plans = new ArrayList<BindingPlan>(resolvedMethods.size());

        for (ResolvedMethod resolved : resolvedMethods) {
            BindingPlan plan = methodGenerator.analyze(resolved, generated);

            if (plan != null) {
                plans.add(plan);
            }
        }

        if (!generated.isValid()) return;

        configureBackendBinder(generated, backend, exactNumbers);

        BindingNames bindingNames = new BindingNames(generated.names());
        BindingCompiler compiler = new BindingCompiler(
                context, bindingNames, context.metadata, backend.backend());
        List<BindingCompiler.CompiledMethod> compiled =
                new ArrayList<BindingCompiler.CompiledMethod>(plans.size());

        for (BindingPlan plan : plans) {
            BindingCompiler.CompiledMethod method = compiler.compile(plan, generated);

            if (method != null) {
                compiled.add(method);
            }
        }

        if (!generated.isValid()) return;

        BindingEmitter emitter = new BindingEmitter(context, backend, bindingNames, includeNulls, exactNumbers);

        for (BindingCompiler.CompiledMethod method : compiled) {
            emitter.emitMethod(method, generated);
        }

        for (BindingValue helper : compiler.helpers()) {
            emitter.emitHelper(helper, generated);
        }

        emitter.emitEnumFields(generated);
        generated.write();
    }

    /** Fastjson2 fixes the native numeric policy once, at construction. */
    private void configureBackendBinder(GeneratedClass generated, BackendSpec backend,
                                        boolean exactNumbers) {
        boolean fastjson2 = backend.backend() == Backend.FASTJSON2;
        generated.enableBackendBinder(backend.binderType(),
                fastjson2 && exactNumbers ? "org.sjf4j.NodeConfig.EXACT_NUMBERS" : "");
        generated.addField(out -> out.line("private final " + backend.binderType() + " binder;"));
        String policy = fastjson2 ? ".forCompiledRead(" + exactNumbers + ")" : "";
        generated.addConstructorInitializer(out -> out.line("this.binder = "
                + "org.sjf4j.util.Asserts.notNull(backendBinder, \"backendBinder\")" + policy + ";"));
    }
}
