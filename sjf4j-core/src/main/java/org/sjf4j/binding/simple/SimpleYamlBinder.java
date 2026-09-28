package org.sjf4j.binding.simple;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Placeholder YAML binder used when SnakeYAML is not available.
 */
public final class SimpleYamlBinder extends Binder<StreamingReader, StreamingWriter> {

    public SimpleYamlBinder(RuntimeContext context) {
        super(context);
    }

    @Override
    public StreamingReader createReader(Reader input) throws IOException {
        throw new BindingException("YAML reading is unavailable: no supported YAML library detected (SnakeYAML).");
    }

    @Override
    public StreamingWriter createWriter(Writer output) throws IOException {
        throw new BindingException("YAML writing is unavailable: no supported YAML library detected (SnakeYAML).");
    }


}
