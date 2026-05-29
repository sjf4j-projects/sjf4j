package org.sjf4j.processor.generate;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.SourceWriter;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GeneratedClass {

    private final ProcessorContext ctx;
    private final TypeElement origin;
    private final String packageName;
    private final String simpleName;
    private final String qualifiedName;
    private final String originName;
    private final List<GeneratedMember> fields = new ArrayList<>();
    private final List<GeneratedMember> methods = new ArrayList<>();
    private final List<GeneratedMember> helpers = new ArrayList<>();

    public GeneratedClass(ProcessorContext ctx, TypeElement origin, String postfix) {
        this.ctx = ctx;
        this.origin = origin;
        PackageElement pkg = ctx.elements.getPackageOf(origin);
        this.packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
        String binaryName = ctx.elements.getBinaryName(origin).toString();
        String packagePrefix = packageName.isEmpty() ? "" : packageName + ".";
        String binarySimpleName = packageName.isEmpty() ? binaryName : binaryName.substring(packagePrefix.length());
        this.simpleName = binarySimpleName + postfix;
        this.qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        this.originName = origin.getQualifiedName().toString();
    }

    public String originName() { return originName; }

    public void addField(GeneratedMember member) { fields.add(member); }

    public void addMethod(GeneratedMember member) { methods.add(member); }

    public void addHelper(GeneratedMember member) { helpers.add(member); }

    public boolean isEmpty() {
        return fields.isEmpty() && methods.isEmpty() && helpers.isEmpty();
    }

    public void emit() {
        try (SourceWriter out = new SourceWriter(ctx, origin, qualifiedName)) {
            out.line("package " + packageName + ";");
            out.line("");
            out.line("public final class " + simpleName + " implements " + originName + " {");
            out.indent();
            out.line("public static final " + originName + " INSTANCE = new " + simpleName + "();");
            out.line("");

            for (GeneratedMember field : fields) field.emit(out);
            if (!fields.isEmpty()) out.line("");

            out.line("public " + simpleName + "() {");
            out.line("}");

            for (GeneratedMember method : methods) method.emit(out);
            for (GeneratedMember helper : helpers) helper.emit(out);

            out.dedent();
            out.line("}");
        } catch (IOException e) {
            ctx.error(origin, "Failed to generate " + qualifiedName + ": " + e.getMessage());
        }
    }
}
