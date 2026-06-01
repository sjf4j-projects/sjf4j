package org.sjf4j.processor;

import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.processor.path.PathGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Annotation processor entry point for SJF4J compiled-node interfaces.
 *
 * <p>It validates the placement of compiled path annotations and delegates
 * source generation for each {@code @CompiledPath} interface.</p>
 */
@SupportedAnnotationTypes({
        "org.sjf4j.annotation.path.CompiledPath",
        "org.sjf4j.annotation.path.GetByPath",
        "org.sjf4j.annotation.path.PutByPath",
        "org.sjf4j.annotation.path.PutIfParentPresentByPath",
        "org.sjf4j.annotation.path.EnsurePutByPath",
        "org.sjf4j.annotation.path.EnsurePutIfAbsentByPath"
})
public final class Sjf4jProcessor extends AbstractProcessor {

    private static final String ANNO_COMPILED_NODES = CompiledPath.class.getName();

    private ProcessorContext context;
    private PathGenerator pathGenerator;

    /**
     * Uses the newest source level supported by the current compiler.
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Initializes shared processor state and generators for this compiler run.
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.context = new ProcessorContext(processingEnv);
        this.pathGenerator = new PathGenerator(context);
    }

    /**
     * Validates compiled-path annotations and emits implementations for
     * discovered {@code @CompiledPath} interfaces.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        validateAnnotation(annotations, roundEnv);
        for (Element element : roundEnv.getElementsAnnotatedWith(CompiledPath.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element, "@CompiledPath only supports interfaces");
            } else {
                pathGenerator.generate((TypeElement) element);
            }
        }
        return false;
    }

    private void validateAnnotation(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement anno : annotations) {
            if (ANNO_COMPILED_NODES.equals(anno.getQualifiedName().toString())) continue;
            for (Element element : roundEnv.getElementsAnnotatedWith(anno)) {
                if (element.getKind() != ElementKind.METHOD) {
                    context.error(element, "@" + anno.getSimpleName() + " only supports methods");
                } else {
                    Element owner = element.getEnclosingElement();
                    if (owner.getKind() != ElementKind.INTERFACE || owner.getAnnotation(CompiledPath.class) == null) {
                        context.error(element,
                                "@" + anno + " methods must be declared in an @CompiledPath interface");
                    }
                }
            }
        }
    }

}
