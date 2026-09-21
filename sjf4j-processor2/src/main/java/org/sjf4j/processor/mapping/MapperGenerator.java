package org.sjf4j.processor.mapping;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;


/**
 * Generates implementations for {@code @CompiledMapper} interfaces.
 *
 * <p>This class handles only interface-level generation. Method validation,
 * mapping analysis, conversion and source emission are delegated to
 * {@link MapperMethodGenerator}.</p>
 */
public final class MapperGenerator {

    private final ProcessorContext context;
    private final MapperMethodGenerator methodGenerator;


    public MapperGenerator(ProcessorContext context) {
        this.context = context;
        this.methodGenerator =
                new MapperMethodGenerator(context);
    }


    public void generate(TypeElement type) {
        if (!type.getTypeParameters().isEmpty()) {
            context.error(
                    type,
                    "@CompiledMapper interface cannot declare type parameters");
            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(
                        context,
                        type);

        for (Element element :
                type.getEnclosedElements()) {

            if (element.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) element;

            if (!method.getModifiers()
                    .contains(Modifier.ABSTRACT)) {

                continue;
            }

            methodGenerator.analyze(
                    method,
                    generated);
        }

        generated.write();
    }
}