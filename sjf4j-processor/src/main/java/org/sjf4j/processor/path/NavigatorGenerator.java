package org.sjf4j.processor.path;

import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

/**
 * Generates implementations for @CompiledNavigator interfaces.
 *
 * <p>Interface-level orchestration only. Method semantics and path generation
 * are delegated to {@link NavigatorMethodGenerator}.</p>
 */
public final class NavigatorGenerator {

    private final ProcessorContext context;
    private final NavigatorMethodAnalyzer analyzer;
    private final NavigatorCompiler compiler;
    private final NavigatorEmitter emitter;

    public NavigatorGenerator(ProcessorContext context) {
        this.context = context;
        this.analyzer = new NavigatorMethodAnalyzer(context);
        this.compiler = new NavigatorCompiler(context);
        this.emitter = new NavigatorEmitter(context);
    }

    /**
     * Generates one compiled navigator implementation.
     */
    public void generate(TypeElement type) {
        if (!type.getTypeParameters().isEmpty()) {
            context.error(
                    type,
                    "@CompiledNavigator interface cannot declare type parameters");
            return;
        }

        List<ResolvedMethod> methods = context.methods.abstractMethods(type);

        if (hasGenericInheritedAbstractMethod(type, methods)) return;

        GeneratedClass generated = GeneratedClass.forInterface(context, type);

        // Keep the factory constructor contract uniform across compiled interfaces.
        // Navigation currently has no RuntimeContext-dependent operations.
        generated.enableRuntimeContext();

        CompiledNavigator annotation = type.getAnnotation(CompiledNavigator.class);
        boolean allowFallback = annotation != null && annotation.fallback();

        // Analyze the complete interface before compiling any node accesses.
        List<NavigatorPlan> plans = new java.util.ArrayList<>(methods.size());
        for (ResolvedMethod method : methods) {
            NavigatorPlan plan = analyzer.analyze(method, generated, allowFallback);
            if (plan != null) plans.add(plan);
        }
        if (!generated.isValid()) return;

        // Resolve all paths and fallback decisions before emitting source.
        List<NavigatorCompiler.CompiledMethod> compiled = new java.util.ArrayList<>(plans.size());
        for (NavigatorPlan plan : plans) {
            NavigatorCompiler.CompiledMethod method = compiler.compile(plan, generated);
            if (method != null) compiled.add(method);
        }
        if (!generated.isValid()) return;

        for (NavigatorCompiler.CompiledMethod method : compiled) {
            emitter.emit(method, generated);
        }

        generated.write();
    }

    private boolean hasGenericInheritedAbstractMethod(
            TypeElement type,
            List<ResolvedMethod> methods) {

        for (ResolvedMethod resolved : methods) {
            ExecutableElement method = resolved.declaration();
            Element owner = method.getEnclosingElement();

            if (!(owner instanceof TypeElement) ||
                    owner.equals(type)) {
                continue;
            }

            TypeElement ownerType = (TypeElement) owner;

            if (ownerType.getTypeParameters().isEmpty()) continue;

            context.error(
                    method,
                    "@CompiledNavigator cannot inherit abstract method '" +
                            method.getSimpleName() +
                            "' from generic interface " +
                            ownerType.getQualifiedName());
            return true;
        }

        return false;
    }
}
