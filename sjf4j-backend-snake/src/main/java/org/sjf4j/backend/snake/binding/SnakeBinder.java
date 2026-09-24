package org.sjf4j.backend.snake.binding;

import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.YamlBinder;
import org.sjf4j.util.Asserts;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/** YAML binder backed by SnakeYAML's event parser and emitter. */
public final class SnakeBinder extends YamlBinder<SnakeReader, SnakeWriter> {
    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;

    public SnakeBinder() {
        this(new LoaderOptions(), new DumperOptions(), StreamingContext.EMPTY);
    }

    public SnakeBinder(LoaderOptions loaderOptions, DumperOptions dumperOptions,
                       StreamingContext context) {
        super(context);
        this.loaderOptions = Asserts.notNull(loaderOptions, "loaderOptions");
        this.dumperOptions = Asserts.notNull(dumperOptions, "dumperOptions");
    }

    @Override
    public SnakeReader createReader(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        return new SnakeReader(new ParserImpl(new StreamReader(input), loaderOptions));
    }

    @Override
    public SnakeWriter createWriter(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return new SnakeWriter(this, new Emitter(output, dumperOptions));
    }

}
