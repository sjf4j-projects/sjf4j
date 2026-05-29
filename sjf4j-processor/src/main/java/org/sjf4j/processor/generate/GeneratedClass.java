package org.sjf4j.processor.generate;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

public final class GeneratedClass {

    private final ProcessorContext ctx;
    private final TypeElement origin;
    private final String postfix;
    private final List<GeneratedMember> fields = new ArrayList<>();
    private final List<GeneratedMember> methods = new ArrayList<>();
    private final List<GeneratedMember> helpers = new ArrayList<>();

    public GeneratedClass(ProcessorContext ctx, TypeElement origin, String postfix) {
        this.ctx = ctx;
        this.origin = origin;
        this.postfix = postfix;
    }

    public void addField(GeneratedMember member) {
        fields.add(member);
    }

    public void addMethod(GeneratedMember member) {
        methods.add(member);
    }

    public void addHelper(GeneratedMember member) {
        helpers.add(member);
    }

    public boolean isEmpty() {
        return fields.isEmpty() && methods.isEmpty() && helpers.isEmpty();
    }

    public void emit() {
        SourceWriter.write(ctx, origin, postfix, (out, source) -> {
            out.line("public final class " + source.simpleName + " implements " + source.originName + " {");
            out.indent();
            out.line("public static final " + source.originName + " INSTANCE = new " + source.simpleName + "();");
            out.line("");

            for (GeneratedMember field : fields) field.emit(out);
            if (!fields.isEmpty()) out.line("");

            out.line("public " + source.simpleName + "() {");
            out.line("}");

            for (GeneratedMember method : methods) method.emit(out);
            for (GeneratedMember helper : helpers) helper.emit(out);

            out.dedent();
            out.line("}");
        });
    }
}
