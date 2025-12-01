package org.sjf4j.facades.snake;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.FacadeReader;
import org.sjf4j.facades.FacadeWriter;
import org.sjf4j.facades.YamlFacade;
import org.sjf4j.facades.gson.GsonReader;
import org.sjf4j.facades.gson.GsonWriter;
import org.sjf4j.facades.jackson.JacksonStreamingUtil;
import org.sjf4j.util.StreamingUtil;
import org.sjf4j.util.TypeReference;
import org.sjf4j.util.TypeUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import org.yaml.snakeyaml.parser.Parser;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class SnakeYamlFacade implements YamlFacade<SnakeReader, SnakeWriter> {

    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;

    public SnakeYamlFacade() {
        this.loaderOptions = new LoaderOptions();
        this.dumperOptions = new DumperOptions();
    }

    public SnakeYamlFacade(LoaderOptions loaderOptions, DumperOptions dumperOptions) {
        this.loaderOptions = loaderOptions;
        this.dumperOptions = dumperOptions;
    }

    @Override
    public SnakeReader createReader(Reader input) {
        Parser parser = new ParserImpl(new StreamReader(input), loaderOptions);
        return new SnakeReader(parser);
    }

    @Override
    public SnakeWriter createWriter(Writer output) throws IOException {
        Emitter emitter = new Emitter(output, dumperOptions);
        return new SnakeWriter(emitter);
    }


    /// API

    @Override
    public Object readNode(@NonNull Reader input, Type type) {
        try {
            Parser parser = new ParserImpl(new StreamReader(input), loaderOptions);
            SnakeStreamingUtil.startDocument(parser);
            Object node = SnakeStreamingUtil.readNode(parser, type);
            SnakeStreamingUtil.endDocument(parser);
            return node;
        } catch (IOException e) {
            throw new JsonException("Failed to read YAML streaming into node of type '" + type + "'", e);
        }
    }

    @Override
    public void writeNode(@NonNull Writer output, Object node) {
        try {
            Emitter emitter = new Emitter(output, dumperOptions);
            SnakeStreamingUtil.startDocument(emitter);
            SnakeStreamingUtil.writeNode(emitter, node);
            SnakeStreamingUtil.endDocument(emitter);
        } catch (IOException e) {
            throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) + "' to YAML streaming", e);
        }
    }


}
