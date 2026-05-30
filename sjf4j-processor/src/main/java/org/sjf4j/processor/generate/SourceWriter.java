package org.sjf4j.processor.generate;

import org.sjf4j.processor.ProcessorContext;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

/**
 * Minimal indentation-aware writer for generated Java source files.
 */
public final class SourceWriter implements Closeable {

    private final Writer writer;
    private int indent;

    /**
     * Opens a generated source file for the supplied qualified class name.
     */
    public SourceWriter(ProcessorContext ctx, Element origin, String qualifiedName) throws IOException {
        JavaFileObject file = ctx.filer.createSourceFile(qualifiedName, origin);
        this.writer = file.openWriter();
    }

    /**
     * Increases indentation for subsequent lines.
     */
    public void indent() { indent++; }

    /**
     * Decreases indentation for subsequent lines.
     */
    public void dedent() { indent--; }

    /**
     * Writes one source line using the current indentation level.
     */
    public void line(String line) {
        try {
            for (int i = 0; i < indent; i++) writer.write("    ");
            writer.write(line);
            writer.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Closes the underlying source writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }


}
