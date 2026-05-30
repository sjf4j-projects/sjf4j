package org.sjf4j.processor.generate;

import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates source members for one generated implementation class.
 *
 * <p>The class owns naming, file creation, and member emission order while the
 * path-specific generators only contribute fields, methods, and helpers.</p>
 */
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

    /**
     * Creates a generated class model for the given origin type and name postfix.
     */
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

    /**
     * Returns the fully qualified source interface name implemented by this class.
     */
    public String originName() { return originName; }

    /**
     * Adds a field emitter to the generated class body.
     */
    public void addField(GeneratedMember member) { fields.add(member); }

    /**
     * Adds a method emitter to the generated class body.
     */
    public void addMethod(GeneratedMember member) { methods.add(member); }

    /**
     * Adds a helper-member emitter after generated methods.
     */
    public void addHelper(GeneratedMember member) { helpers.add(member); }

    /**
     * Returns true when no members have been contributed.
     */
    public boolean isEmpty() {
        return fields.isEmpty() && methods.isEmpty() && helpers.isEmpty();
    }

    /**
     * Writes the generated Java source file through the annotation-processing filer.
     */
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
