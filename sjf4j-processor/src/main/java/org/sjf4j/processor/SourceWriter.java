package org.sjf4j.processor;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

public final class SourceWriter implements Closeable {

    private final Writer writer;
    private int indent;

    public SourceWriter(ProcessorContext ctx, Element origin, String qualifiedName) throws IOException {
        JavaFileObject file = ctx.filer.createSourceFile(qualifiedName, origin);
        this.writer = file.openWriter();
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

    @Override
    public void close() throws IOException {
        writer.close();
    }


}
