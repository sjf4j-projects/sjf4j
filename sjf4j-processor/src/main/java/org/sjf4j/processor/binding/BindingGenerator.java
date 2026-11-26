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
        boolean binderIncludeNulls = annotation.includeNulls();
        boolean binderExactNumbers = annotation.exactNumbers();

        List<ResolvedMethod> resolvedMethods = context.methods.abstractMethods(type);
        List<BindingPlan> plans = new ArrayList<BindingPlan>(resolvedMethods.size());

        for (ResolvedMethod resolved : resolvedMethods) {
            BindingPlan plan = methodGenerator.analyze(resolved, generated, binderIncludeNulls, binderExactNumbers);

            if (plan != null) {
                plans.add(plan);
            }
        }

        if (!generated.isValid()) return;

        String exactReadBinder = configureBackendBinder(generated, backend, plans);

        BindingCompiler compiler = new BindingCompiler(
                context, generated.names(), context.metadata, backend.backend());
        List<BindingCompiler.CompiledMethod> compiled =
                new ArrayList<BindingCompiler.CompiledMethod>(plans.size());

        for (BindingPlan plan : plans) {
            BindingCompiler.CompiledMethod method = compiler.compile(plan, generated);

            if (method != null) {
                compiled.add(method);
            }
        }

        if (!generated.isValid()) return;

        BindingEmitter emitter = new BindingEmitter(context, backend,
                new BindingNames(generated.names()), exactReadBinder);

        for (BindingCompiler.CompiledMethod method : compiled) {
            emitter.emitMethod(method, generated);
        }

        for (BindingValue helper : compiler.helpers()) {
            emitter.emitHelper(helper, generated);
        }

        emitter.emitEnumFields(generated);
        generated.write();
    }

    /**
     * Fastjson2 fixes its numeric mode in the native reader context. Select
     * the reader factory once in the constructor, and only add a second
     * binder field when both read policies are actually used.
     */
    private String configureBackendBinder(GeneratedClass generated, BackendSpec backend,
                                          List<BindingPlan> plans) {
        boolean ordinary = false;
        boolean exact = false;
        boolean fastjson2 = backend.backend() == Backend.FASTJSON2;
        if (fastjson2) {
            for (BindingPlan plan : plans) {
                if (plan.direction() != BindingPlan.Direction.READ_FROM) continue;
                if (plan.exactNumbers()) exact = true;
                else ordinary = true;
            }
        }
        boolean exactOnly = exact && !ordinary;
        generated.enableBackendBinder(backend.binderType(),
                exactOnly ? "org.sjf4j.NodeConfig.EXACT_NUMBERS" : "");
        generated.addField(out -> out.line("private final " + backend.binderType() + " binder;"));
        String policy = ordinary || exact ? ".forCompiledRead(" + exactOnly + ")" : "";
        generated.addConstructorInitializer(out -> out.line("this.binder = "
                + "org.sjf4j.util.Asserts.notNull(backendBinder, \"backendBinder\")" + policy + ";"));
        if (!exact || !ordinary) return "binder";

        String exactBinder = generated.names().field("exactBinder");
        generated.addField(out -> out.line("private final " + backend.binderType() + " " + exactBinder + ";"));
        generated.addConstructorInitializer(out -> out.line("this." + exactBinder
                + " = backendBinder.forCompiledRead(true);"));
        return exactBinder;
    }
}
