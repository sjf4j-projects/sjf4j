package org.sjf4j.processor;

import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.processor.path.NavigatorGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Test-only processor bridge for compiler integration tests.
 *
 * Exercises navigation generation independently of the production dispatcher.
 */
@SupportedAnnotationTypes("org.sjf4j.annotation.path.CompiledNavigator")
public final class TestNavigatorProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment round) {

        if (round.processingOver()) {
            return false;
        }

        ProcessorContext context = new ProcessorContext(processingEnv);

        for (Element element : round.getElementsAnnotatedWith(CompiledNavigator.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element, "@CompiledNavigator can be applied only to interfaces");
                continue;
            }

            TypeElement owner = (TypeElement) element;
            new NavigatorGenerator(context.forInterface(owner, false)).generate(owner);
        }

        return true;
    }
}
