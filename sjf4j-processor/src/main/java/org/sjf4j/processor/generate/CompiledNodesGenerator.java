package org.sjf4j.processor.generate;

import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.CompiledPath;
import org.sjf4j.processor.ProcessorContext;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Set;

public final class CompiledNodesGenerator {

    private static final String ANNO_COMPILED_NODES = "org.sjf4j.annotation.compiled.CompiledNodes";

    private final ProcessorContext ctx;
    private final CompiledPathGenerator compiledPathGenerator;

    public CompiledNodesGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
        this.compiledPathGenerator = new CompiledPathGenerator(ctx);
    }

    public void validate(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (ANNO_COMPILED_NODES.equals(annotation.getQualifiedName().toString())) continue;
            String annotationName = "@" + annotation.getSimpleName();
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.METHOD) {
                    ctx.error(element, annotationName + " only supports methods");
                    continue;
                }
                Element owner = element.getEnclosingElement();
                if (owner.getKind() != ElementKind.INTERFACE || owner.getAnnotation(CompiledNodes.class) == null) {
                    ctx.error(element, annotationName + " methods must be declared in an @CompiledNodes interface");
                }
            }
        }
    }

    public void generate(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(CompiledNodes.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                ctx.error(element, "@CompiledNodes only supports interfaces");
                continue;
            }
            generateInterface((TypeElement) element);
        }
    }

    private void generateInterface(TypeElement iface) {
        GeneratedClass target = new GeneratedClass(ctx, iface, "_Impl");

        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            if (method.getAnnotation(CompiledPath.class) != null) compiledPathGenerator.generate(method, target);
        }

        if (!target.isEmpty()) target.emit();
    }
}
