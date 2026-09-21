package org.sjf4j.processor.path;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

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

        GeneratedClass generated =
                GeneratedClass.forInterface(context, type);

        for (Element element : type.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) element;

            if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }

            methodGenerator.generate(method, generated);
        }

        generated.write();
    }
}