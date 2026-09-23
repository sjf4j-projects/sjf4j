package org.sjf4j.processor.path;

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
    private final NavigatorMethodGenerator methodGenerator;


    public NavigatorGenerator(ProcessorContext context) {
        this.context = context;
        this.methodGenerator = new NavigatorMethodGenerator(context);
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

        List<ResolvedMethod> methods =
                context.methods.abstractMethods(type);

        if (hasGenericInheritedAbstractMethod(
                type,
                methods)) {
            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(context, type);

        for (ResolvedMethod method : methods) {
            methodGenerator.generate(
                    method.declaration(),
                    generated);
        }

        generated.write();
    }


    private boolean hasGenericInheritedAbstractMethod(
            TypeElement type,
            List<ResolvedMethod> methods) {

        for (ResolvedMethod resolved : methods) {
            ExecutableElement method =
                    resolved.declaration();

            Element owner =
                    method.getEnclosingElement();

            if (!(owner instanceof TypeElement) ||
                    owner.equals(type)) {
                continue;
            }

            TypeElement ownerType =
                    (TypeElement) owner;

            if (ownerType.getTypeParameters().isEmpty()) {
                continue;
            }

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
