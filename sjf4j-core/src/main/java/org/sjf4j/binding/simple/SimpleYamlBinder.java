package org.sjf4j.binding.simple;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.StreamingGenerator;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Unavailable YAML binder used when no YAML backend provider is selected.
 */
public final class SimpleYamlBinder extends Binder<StreamingParser, StreamingGenerator> {

    public SimpleYamlBinder(RuntimeContext context) {
        super(context);
    }

    @Override
    public StreamingParser createParser(Reader input) throws IOException {
        throw new BindingException("YAML reading is unavailable: add sjf4j-backend-snake and SnakeYAML");
    }

    @Override
    public StreamingGenerator createGenerator(Writer output) throws IOException {
        throw new BindingException("YAML writing is unavailable: add sjf4j-backend-snake and SnakeYAML");
    }


}
