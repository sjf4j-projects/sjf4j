package org.sjf4j.processor.path;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.HashSet;
import java.util.Set;

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

        if (hasGenericInheritedAbstractMethod(type)) {
            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(context, type);

        Set<String> inheritedSignatures =
                new HashSet<>();

        for (Element element : context.elements.getAllMembers(type)) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) element;

            if (!method.getModifiers().contains(Modifier.ABSTRACT) ||
                    method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            if (!method.getEnclosingElement().equals(type) &&
                    !inheritedSignatures.add(erasedSignature(method))) {

                continue;
            }

            methodGenerator.generate(method, generated);
        }

        generated.write();
    }


    private String erasedSignature(ExecutableElement method) {
        StringBuilder signature =
                new StringBuilder(method.getSimpleName())
                        .append('(');

        for (VariableElement parameter : method.getParameters()) {
            signature.append(context.typeUtils.erasure(parameter.asType()))
                    .append(';');
        }

        return signature.append(')').toString();
    }


    private boolean hasGenericInheritedAbstractMethod(TypeElement type) {
        for (Element element : context.elements.getAllMembers(type)) {
            if (element.getKind() != ElementKind.METHOD ||
                    element.getEnclosingElement().equals(type) ||
                    !element.getModifiers().contains(Modifier.ABSTRACT) ||
                    element.getModifiers().contains(Modifier.STATIC)) {

                continue;
            }

            TypeElement owner = (TypeElement) element.getEnclosingElement();

            if (owner.getTypeParameters().isEmpty()) {
                continue;
            }

            context.error(
                    element,
                    "@CompiledNavigator cannot inherit abstract method '" +
                            ((ExecutableElement) element).getSimpleName() +
                            "' from generic interface " + owner.getQualifiedName());
            return true;
        }

        return false;
    }
}
