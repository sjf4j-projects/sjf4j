package org.sjf4j.processor;

import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.processor.path.PathGenerator;
import org.sjf4j.processor.mapper.MapperGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Annotation processor entry point for SJF4J compiled path and mapper
 * interfaces.
 *
 * <p>The entry point keeps round handling intentionally small: it validates that
 * method-level annotations are attached to the proper owning interface, then
 * delegates all operation-specific validation and source emission to
 * {@link PathGenerator} or {@link MapperGenerator}.  This separation keeps
 * cross-feature annotation rules centralized without mixing path and mapper code
 * generation logic.</p>
 *
 * <p>The processor returns {@code false} from {@link #process(Set,
 * RoundEnvironment)} so other processors can still observe SJF4J annotations in
 * the same compilation.</p>
 */
@SupportedAnnotationTypes({
        "org.sjf4j.annotation.path.CompiledPath",
        "org.sjf4j.annotation.path.GetByPath",
        "org.sjf4j.annotation.path.PutByPath",
        "org.sjf4j.annotation.path.PutIfParentPresentByPath",
        "org.sjf4j.annotation.path.EnsurePutByPath",
        "org.sjf4j.annotation.path.EnsurePutIfAbsentByPath",
        "org.sjf4j.annotation.path.FindByPath",

        "org.sjf4j.annotation.mapper.CompiledMapper",
        "org.sjf4j.annotation.mapper.Mapping",
        "org.sjf4j.annotation.mapper.Mappings",
        "org.sjf4j.annotation.mapper.MapperOptions",
        "org.sjf4j.annotation.mapper.MappingCreator",
        "org.sjf4j.annotation.mapper.MappingCreators",
        "org.sjf4j.annotation.mapper.MappingIfParentPresent",
        "org.sjf4j.annotation.mapper.EnsureMapping"
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
     * Validates annotation placement and emits implementations for discovered
     * {@code @CompiledPath} and {@code @CompiledMapper} interfaces.
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

    /**
     * Rejects orphaned method annotations early, before feature generators try
     * to interpret their values.  Creator annotations are the only mapper
     * annotations accepted both on the mapper interface and on individual mapper
     * methods; all other operation annotations must live on methods owned by the
     * matching compiled interface kind.
     */
    private void validateAnnotation(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement anno : annotations) {
            String annoName = anno.getQualifiedName().toString();
            if (ANNO_COMPILED_PATH.equals(annoName) || ANNO_COMPILED_MAPPER.equals(annoName)) continue;
            for (Element element : roundEnv.getElementsAnnotatedWith(anno)) {
                if ("org.sjf4j.annotation.mapper.MappingCreator".equals(annoName)
                        || "org.sjf4j.annotation.mapper.MappingCreators".equals(annoName)) {
                    if (element.getKind() == ElementKind.INTERFACE) {
                        continue;
                    }
                    if (element.getKind() == ElementKind.METHOD) {
                        Element owner = element.getEnclosingElement();
                        if (owner.getKind() != ElementKind.INTERFACE || owner.getAnnotation(CompiledMapper.class) == null) {
                            context.error(element, "@" + anno + " method must be declared in an @CompiledMapper interface");
                        } else if (!element.getModifiers().contains(Modifier.ABSTRACT)) {
                            context.error(element, "@" + anno + " method must be declared on an abstract @CompiledMapper method");
                        }
                        continue;
                    }
                    context.error(element, "@" + anno.getSimpleName() + " can be applied only to interfaces or methods");
                    continue;
                }
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
