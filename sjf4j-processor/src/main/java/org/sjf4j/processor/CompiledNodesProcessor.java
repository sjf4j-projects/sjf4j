package org.sjf4j.processor;

import org.sjf4j.processor.generate.CompiledNodesGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes({
        "org.sjf4j.annotation.compiled.CompiledNodes",
        "org.sjf4j.annotation.compiled.CompiledPath"
})
public final class CompiledNodesProcessor extends AbstractProcessor {

    private CompiledNodesGenerator compiledNodesGenerator;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ProcessorContext context = new ProcessorContext(processingEnv);
        this.compiledNodesGenerator = new CompiledNodesGenerator(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        compiledNodesGenerator.validate(annotations, roundEnv);
        compiledNodesGenerator.generate(roundEnv);
        return false;
    }
}
