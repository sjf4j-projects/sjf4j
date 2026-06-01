package org.sjf4j.processor;

import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.exception.JsonException;
import org.sjf4j.processor.path.PathGenerator;
import org.sjf4j.processor.mapper.MapperGenerator;

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
        "org.sjf4j.annotation.path.EnsurePutIfAbsentByPath",

        "org.sjf4j.annotation.mapper.CompiledMapper",
        "org.sjf4j.annotation.mapper.Mapping",
        "org.sjf4j.annotation.mapper.Mappings"
})
public final class Sjf4jProcessor extends AbstractProcessor {

    private static final String ANNO_COMPILED_PATH = CompiledPath.class.getName();
    private static final String ANNO_COMPILED_MAPPER = CompiledMapper.class.getName();

    private ProcessorContext context;
    private PathGenerator pathGenerator;
    private MapperGenerator mapperGenerator;

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
        this.mapperGenerator = new MapperGenerator(context);
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
                context.error(element, "@CompiledPath can be applied only to interfaces");
            } else {
                pathGenerator.generate((TypeElement) element);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(CompiledMapper.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element, "@CompiledMapper can be applied only to interfaces");
            } else {
                mapperGenerator.generate((TypeElement) element);
            }
        }
        return false;
    }

    private void validateAnnotation(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement anno : annotations) {
            String annoName = anno.getQualifiedName().toString();
            if (ANNO_COMPILED_PATH.equals(annoName) || ANNO_COMPILED_MAPPER.equals(annoName)) continue;
            for (Element element : roundEnv.getElementsAnnotatedWith(anno)) {
                if (element.getKind() != ElementKind.METHOD) {
                    context.error(element, "@" + anno.getSimpleName() + " can be applied only to methods");
                } else {
                    Element owner = element.getEnclosingElement();
                    if (annoName.startsWith("org.sjf4j.annotation.mapper.")) {
                        if (owner.getKind() != ElementKind.INTERFACE || owner.getAnnotation(CompiledMapper.class) == null) {
                            context.error(element, "@" + anno + " method must be declared in an @CompiledMapper interface");
                        }
                    } else if (annoName.startsWith("org.sjf4j.annotation.path.")) {
                        if (owner.getKind() != ElementKind.INTERFACE || owner.getAnnotation(CompiledPath.class) == null) {
                            context.error(element, "@" + anno + " method must be declared in an @CompiledPath interface");
                        }
                    } else {
                        context.error(element, "Unrecognized annotation " + annoName);
                    }
                }
            }
        }
    }

}
