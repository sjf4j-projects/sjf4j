package org.sjf4j.processor.generate;

import org.sjf4j.annotation.compiled.GetByPath;
import org.sjf4j.annotation.compiled.PutByPath;
import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public final class NodesGenerator {

    private static final String COMPILED_NODES_POSTFIX = "_Impl";

    private final ProcessorContext ctx;
    private final PathGenerator pathGenerator;

    public NodesGenerator(ProcessorContext ctx) {
        this.ctx = ctx;
        this.pathGenerator = new PathGenerator(ctx);
    }

    public void generate(TypeElement iface) {
        GeneratedClass target = new GeneratedClass(ctx, iface, COMPILED_NODES_POSTFIX);

        for (Element member : iface.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) member;
            String generatedAnno = null;

            GetByPath get = method.getAnnotation(GetByPath.class);
            if (get != null) {
                generatedAnno = "@GetByPath";
                pathGenerator.generateGet(method, target, get.value());
            }

            PutByPath put = method.getAnnotation(PutByPath.class);
            if (put != null) {
                if (generatedAnno != null) {
                    ctx.error(method, "Path operation annotations cannot be used together: " +
                            generatedAnno + " and @PutByPath");
                    return;
                } else {
                    generatedAnno = "@PutByPath";
                    pathGenerator.generatePut(method, target, put.value());
                }
            }

            if (generatedAnno == null && !method.getModifiers().contains(Modifier.DEFAULT) &&
                    !method.getModifiers().contains(Modifier.STATIC)) {
                ctx.error(method, "@CompiledNodes abstract methods must be annotated, for example @GetByPath");
            }

        }

        target.emit();
    }

}
