package org.sjf4j.facade.snake;

import org.sjf4j.facade.FacadeWriter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;

import java.io.IOException;

public class SnakeWriter implements FacadeWriter {

    private final Emitter emitter;

    public SnakeWriter(Emitter emitter) throws IOException {
        if (emitter == null) throw new IllegalArgumentException("Emitter must not be null");
        this.emitter = emitter;
    }


    @Override
    public void startDocument() throws IOException {
        emitter.emit(new StreamStartEvent(null, null));
        emitter.emit(new DocumentStartEvent(null, null, false, null, null));
    }

    @Override
    public void endDocument() throws IOException {
        emitter.emit(new DocumentEndEvent(null, null, false));
        emitter.emit(new StreamEndEvent(null, null));
    }

    @Override
    public void startObject() throws IOException {
        emitter.emit(new MappingStartEvent(null, null, true, null, null,
                DumperOptions.FlowStyle.BLOCK));
    }

    @Override
    public void endObject() throws IOException {
        emitter.emit(new MappingEndEvent(null, null));
    }

    @Override
    public void startArray() throws IOException {
        emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                DumperOptions.FlowStyle.BLOCK));
    }

    @Override
    public void endArray() throws IOException {
        emitter.emit(new SequenceEndEvent(null, null));
    }

    @Override
    public void writeName(String name) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                name, null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    @Override
    public void writeString(String value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value, null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    @Override
    public void writeNumber(Number value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    @Override
    public void writeBoolean(Boolean value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    @Override
    public void writeNull() throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                "null", null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    @Override
    public void flush() throws IOException {
        // nothing
    }
}
