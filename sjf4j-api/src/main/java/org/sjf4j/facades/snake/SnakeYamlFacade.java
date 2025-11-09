package org.sjf4j.facades.snake;

import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.YamlFacade;
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

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public class SnakeYamlFacade implements YamlFacade {

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
    public Object readNode(@NonNull Reader input, Type type) {
        Object node;
        try {
            Parser parser = new ParserImpl(new StreamReader(input), loaderOptions);
            Event ev;
            if (!((ev = parser.getEvent()) instanceof StreamStartEvent)) throw new IllegalStateException("Malformed YAML");
            if (!((ev = parser.getEvent()) instanceof DocumentStartEvent)) throw new IllegalStateException("Malformed YAML");
            node = SnakeParser.readAny(parser, type);
            if (!((ev = parser.getEvent()) instanceof DocumentEndEvent)) throw new IllegalStateException("Malformed YAML");
            if (!((ev = parser.getEvent()) instanceof StreamEndEvent)) throw new IllegalStateException("Malformed YAML");
        } catch (Exception e) {
            throw new JsonException("Failed to deserialize YAML into JSON-Node: " + e.getMessage(), e);
        }
        return node;
    }

    @Override
    public void writeNode(@NonNull Writer output, Object node) {
        try {
            Emitter emitter = new Emitter(output, dumperOptions);
            emitter.emit(new StreamStartEvent(null, null));
            emitter.emit(new DocumentStartEvent(null, null, false, null, null));
            SnakeEmitter.writeAny(emitter, node);
            emitter.emit(new DocumentEndEvent(null, null, false));
            emitter.emit(new StreamEndEvent(null, null));
        } catch (Exception e) {
            throw new JsonException("Failed to serialize JsonObject to YAML: " + e.getMessage(), e);
        }
    }


}
