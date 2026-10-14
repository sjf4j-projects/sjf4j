package org.sjf4j.backend.snake.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * YAML binder backed by SnakeYAML's event parser and emitter. Input is
 * interpreted as a JSON-semantic object graph: aliases, anchors, and
 * non-JSON-compatible tags are rejected by the reader.
 */
public final class SnakeBinder extends Binder<SnakeReader, SnakeWriter> {
    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;

    /** Creates a binder with default SnakeYAML options. */
    public SnakeBinder() {
        this(new LoaderOptions(), new DumperOptions(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with supplied SnakeYAML options and runtime settings.
     * @param loaderOptions the parser options
     * @param dumperOptions the emitter options
     * @param context the SJF4J runtime settings
     */
    public SnakeBinder(LoaderOptions loaderOptions, DumperOptions dumperOptions,
                       RuntimeContext context) {
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
        return new SnakeWriter(output, dumperOptions);
    }

}
