package org.sjf4j.processor.generate;

import org.sjf4j.annotation.compiled.CompiledPath;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public final class CompiledNodesGenerator {

    private static final String COMPILED_NODES_POSTFIX = "_Impl";

    private final ProcessorContext ctx;
    private final CompiledPathGenerator compiledPathGenerator;

    public CompiledNodesGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
        this.compiledPathGenerator = new CompiledPathGenerator(ctx);
    }

    public void generate(TypeElement iface) {
        GeneratedClass target = new GeneratedClass(ctx, iface, COMPILED_NODES_POSTFIX);

        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            if (method.getAnnotation(CompiledPath.class) != null) {
                compiledPathGenerator.generate(method, target);
            }
        }

        target.emit();
    }

}
