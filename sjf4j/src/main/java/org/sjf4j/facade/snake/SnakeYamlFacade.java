package org.sjf4j.facade.snake;

import org.sjf4j.facade.YamlFacade;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.parser.Parser;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * SnakeYAML-based YAML facade.
 */
public class SnakeYamlFacade implements YamlFacade<SnakeReader, SnakeWriter> {

    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;

    /**
     * Creates SnakeYAML facade with default loader/dumper options.
     */
    public SnakeYamlFacade() {
        this.loaderOptions = new LoaderOptions();
        this.dumperOptions = new DumperOptions();
    }

    /**
     * Creates SnakeYAML facade with custom loader/dumper options.
     */
    public SnakeYamlFacade(LoaderOptions loaderOptions, DumperOptions dumperOptions) {
        this.loaderOptions = loaderOptions;
        this.dumperOptions = dumperOptions;
    }

    /**
     * Creates an event-based YAML reader.
     */
    @Override
    public SnakeReader createReader(Reader input) {
        Parser parser = new ParserImpl(new StreamReader(input), loaderOptions);
        return new SnakeReader(parser);
    }

    /**
     * Creates an event-based YAML writer.
     */
    @Override
    public SnakeWriter createWriter(Writer output) throws IOException {
        Emitter emitter = new Emitter(output, dumperOptions);
        return new SnakeWriter(emitter);
    }


    /// API

//    @Override
//    public Object readNode(@NonNull Reader input, Type type) {
//        try {
//            Parser parser = new ParserImpl(new StreamReader(input), loaderOptions);
//            SnakeStreamingUtil.startDocument(parser);
//            Object node = SnakeStreamingUtil.readNode(parser, type);
//            SnakeStreamingUtil.endDocument(parser);
//            return node;
//        } catch (IOException e) {
//            throw new JsonException("Failed to read YAML streaming into node of type '" + type + "'", e);
//        }
//    }
//
//    @Override
//    public void writeNode(@NonNull Writer output, Object node) {
//        try {
//            Emitter emitter = new Emitter(output, dumperOptions);
//            SnakeStreamingUtil.startDocument(emitter);
//            SnakeStreamingUtil.writeNode(emitter, node);
//            SnakeStreamingUtil.endDocument(emitter);
//        } catch (IOException e) {
//            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to YAML streaming", e);
//        }
//    }


}
