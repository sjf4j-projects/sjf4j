package org.sjf4j.processor;

import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.processor.schema.SchemaGenerator;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.processor.binding.BindingGenerator;
import org.sjf4j.processor.mapping.MapperGenerator;
import org.sjf4j.processor.mapping.jdbc.JdbcMapperGenerator;
import org.sjf4j.processor.path.NavigatorGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Annotation processor entry point for compiled navigation, binding, object
 * mapping, and JDBC mapping. It validates annotation placement and delegates
 * each annotated interface to its feature generator. Diagnostics are attached
 * to the offending interface or method through {@link ProcessorContext}.
 */
public final class CodegenProcessor extends AbstractProcessor {

    private ProcessorContext context;
    private AnnotationValidator annotationValidator;


    /**
     * Lists both root and method-level annotations so invalid element kinds
     * and non-abstract methods can be diagnosed independently of generation.
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return AnnotationValidator.supportedAnnotationTypes();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.context = new ProcessorContext(processingEnv);
        this.annotationValidator = new AnnotationValidator(context);
    }

    /** Processes each discovered interface; other processors may see the same annotations. */
    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        if (roundEnv.processingOver()) {
            if (!roundEnv.errorRaised()) context.writeCompiledIndex();
            return false;
        }

        annotationValidator.validate(annotations, roundEnv);

        // Discovery precedes generation within each annotation processing round.
        context.discovery.collect(roundEnv);

        processInterfaces(roundEnv, CompiledNavigator.class, scope -> new NavigatorGenerator(scope).generate(scope.owner));
        processInterfaces(roundEnv, CompiledMapper.class, this::generateMapper);
        processInterfaces(roundEnv, CompiledJdbcMapper.class, this::generateJdbcMapper);
        processInterfaces(roundEnv, CompiledBinder.class, scope -> new BindingGenerator(scope).generate(scope.owner));
        processInterfaces(roundEnv, CompiledValidator.class, scope -> new SchemaGenerator(scope).generate(scope.owner));

        return false;
    }

    private void generateMapper(CompilationContext scope) {
        TypeElement type = scope.owner;
        if (type.getAnnotation(CompiledJdbcMapper.class) != null) {
            context.error(type, "An interface cannot be annotated with both @CompiledMapper and @CompiledJdbcMapper");
            return;
        }

        new MapperGenerator(scope).generate(type);
    }

    private void generateJdbcMapper(CompilationContext scope) {
        TypeElement type = scope.owner;
        /*
         * The CompiledMapper pass reports the conflict once.
         */
        if (type.getAnnotation(CompiledMapper.class) != null) return;

        new JdbcMapperGenerator(scope).generate(type);
    }

    private <A extends Annotation> void processInterfaces(
            RoundEnvironment roundEnv,
            Class<A> annotationType,
            Consumer<CompilationContext> generator) {

        for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                context.error(element,
                        "@" + annotationType.getSimpleName() + " can be applied only to interfaces");
                continue;
            }
            TypeElement owner = (TypeElement) element;
            generator.accept(context.forInterface(owner, annotationType != CompiledNavigator.class));
        }
    }
}
