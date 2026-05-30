package org.sjf4j.processor;

import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.processor.generate.NodesGenerator;

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
 * source generation for each {@code @CompiledNodes} interface.</p>
 */
@SupportedAnnotationTypes({
        "org.sjf4j.annotation.compiled.CompiledNodes",
        "org.sjf4j.annotation.compiled.GetByPath",
        "org.sjf4j.annotation.compiled.PutByPath"
})
public final class Sjf4jProcessor extends AbstractProcessor {

    private static final String ANNO_COMPILED_NODES = CompiledNodes.class.getName();

    private ProcessorContext context;
    private NodesGenerator nodesGenerator;

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
        this.nodesGenerator = new NodesGenerator(context);
    }

    /**
     * Validates compiled-path annotations and emits implementations for
     * discovered {@code @CompiledNodes} interfaces.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        validateAnnotation(annotations, roundEnv);
        for (Element element : roundEnv.getElementsAnnotatedWith(CompiledNodes.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element, "@CompiledNodes only supports interfaces");
            } else {
                nodesGenerator.generate((TypeElement) element);
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
                    if (owner.getKind() != ElementKind.INTERFACE || owner.getAnnotation(CompiledNodes.class) == null) {
                        context.error(element,
                                "@" + anno + " methods must be declared in an @CompiledNodes interface");
                    }
                }
            }
        }
    }

}
