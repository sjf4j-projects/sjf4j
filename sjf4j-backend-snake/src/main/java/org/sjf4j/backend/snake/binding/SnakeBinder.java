package org.sjf4j.backend.snake.binding;

import org.sjf4j.NodeConfig;
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
public final class SnakeBinder extends Binder<SnakeParser, SnakeGenerator> {
    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;

    /** Creates a binder with default SnakeYAML options. */
    public SnakeBinder() {
        this(new LoaderOptions(), new DumperOptions(), NodeConfig.DEFAULT);
    }

    /**
     * Creates a binder with default backend settings and supplied node configuration.
     * @param config the SJF4J node configuration
     */
    public SnakeBinder(NodeConfig config) {
        this(new LoaderOptions(), new DumperOptions(), config);
    }

    /**
     * Creates a binder with supplied SnakeYAML options and runtime settings.
     * @param loaderOptions the parser options
     * @param dumperOptions the emitter options
     * @param config the SJF4J node configuration
     */
    public SnakeBinder(LoaderOptions loaderOptions, DumperOptions dumperOptions,
                       NodeConfig config) {
        super(config);
        this.loaderOptions = Asserts.notNull(loaderOptions, "loaderOptions");
        this.dumperOptions = Asserts.notNull(dumperOptions, "dumperOptions");
    }

    @Override
    public SnakeParser createParser(Reader input) throws IOException {
        return createParser(input, config.exactNumbers);
    }

    @Override
    public SnakeParser createParser(Reader input, boolean exactNumbers) throws IOException {
        Asserts.notNull(input, "input");
        return new SnakeParser(new ParserImpl(new StreamReader(input), loaderOptions), exactNumbers);
    }

    @Override
    public SnakeGenerator createGenerator(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return new SnakeGenerator(output, dumperOptions);
    }

}
