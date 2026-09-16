package org.sjf4j.binding.simple;

import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.binding.YamlBinder;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Placeholder YAML facade used when SnakeYAML is not available.
 */
public final class SimpleYamlBinder implements YamlBinder<StreamingReader, StreamingWriter> {
    private final StreamingContext streamingContext;

    public SimpleYamlBinder() {
        this(StreamingContext.EMPTY);
    }

    public SimpleYamlBinder(StreamingContext streamingContext) {
        this.streamingContext = streamingContext;
    }

    @Override
    public StreamingContext streamingContext() {
        return streamingContext;
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
