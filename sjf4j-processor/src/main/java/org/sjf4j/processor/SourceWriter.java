package org.sjf4j.processor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

public final class SourceWriter {

    public interface Body {
        void write(SourceWriter out, GeneratedSource source);
    }

    public static final class GeneratedSource {
        public final String packageName;
        public final String originName;
        public final String simpleName;
        public final String qualifiedName;

        private GeneratedSource(String packageName, String originName, String simpleName, String qualifiedName) {
            this.packageName = packageName;
            this.originName = originName;
            this.simpleName = simpleName;
            this.qualifiedName = qualifiedName;
        }
    }

    public static void write(ProcessorContext ctx, TypeElement origin, String postfix, Body body) {
        PackageElement pkg = ctx.elements.getPackageOf(origin);
        String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
        String simpleName = origin.getSimpleName() + postfix;
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        GeneratedSource source = new GeneratedSource(packageName, origin.getQualifiedName().toString(), simpleName, qualifiedName);

        try {
            JavaFileObject file = ctx.filer.createSourceFile(qualifiedName, origin);
            Writer writer = file.openWriter();
            try {
                SourceWriter out = new SourceWriter(writer);
                if (!packageName.isEmpty()) {
                    out.line("package " + packageName + ";");
                    out.line("");
                }
                body.write(out, source);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            ctx.error(origin, "Failed to generate " + qualifiedName + ": " + e.getMessage());
        }
    }

    private final Writer writer;
    private int indent;

    public SourceWriter(Writer writer) {
        this.writer = writer;
    }

    public void indent() { indent++; }

    public void dedent() { indent--; }

    public void line(String line) {
        try {
            for (int i = 0; i < indent; i++) writer.write("    ");
            writer.write(line);
            writer.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
