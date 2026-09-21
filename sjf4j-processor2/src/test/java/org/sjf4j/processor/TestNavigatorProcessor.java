package org.sjf4j.processor;

import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.processor.path.NavigatorGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Test-only processor bridge for compiler integration tests.
 *
 * <p>The production processor is intentionally absent while the processor
 * refactor is in progress. This bridge exercises the refactored context and
 * navigator generator without making either available from main sources.</p>
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
        NavigatorGenerator generator = new NavigatorGenerator(context);

        for (Element element : round.getElementsAnnotatedWith(CompiledNavigator.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element, "@CompiledNavigator can be applied only to interfaces");
                continue;
            }

            TypeElement type = (TypeElement) element;
            if (hasInheritedAbstractMethod(type, context)) {
                continue;
            }

            generator.generate(type);
        }

        return true;
    }

    private boolean hasInheritedAbstractMethod(
            TypeElement type,
            ProcessorContext context) {

        for (Element member : processingEnv.getElementUtils().getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD
                    || member.getEnclosingElement().equals(type)
                    || !member.getModifiers().contains(Modifier.ABSTRACT)
                    || member.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            context.error(
                    member,
                    "@CompiledNavigator cannot inherit abstract instance method '"
                            + ((ExecutableElement) member).getSimpleName()
                            + "' from " + member.getEnclosingElement());
            return true;
        }

        return false;
    }
}
