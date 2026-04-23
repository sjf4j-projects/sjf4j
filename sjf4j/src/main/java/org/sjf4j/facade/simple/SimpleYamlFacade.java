package org.sjf4j.facade.simple;

import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.FacadeProvider;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.facade.StreamingWriter;
import org.sjf4j.facade.YamlFacade;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Placeholder YAML facade used when SnakeYAML is not available.
 */
public class SimpleYamlFacade implements YamlFacade<StreamingReader, StreamingWriter> {
    private final StreamingContext streamingContext;

    public SimpleYamlFacade() {
        this(StreamingContext.EMPTY);
    }

    public SimpleYamlFacade(StreamingContext streamingContext) {
        this.streamingContext = streamingContext;
    }

    public static FacadeProvider<YamlFacade<?, ?>> provider() {
        return SimpleYamlFacade::new;
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
