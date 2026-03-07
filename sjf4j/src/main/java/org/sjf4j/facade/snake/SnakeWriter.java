package org.sjf4j.facade.snake;

import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingWriter;
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

/**
 * Streaming writer backed by SnakeYAML emitter events.
 */
public class SnakeWriter implements StreamingWriter {

    private final Emitter emitter;

    /**
     * Creates writer adapter from SnakeYAML emitter.
     */
    public SnakeWriter(Emitter emitter) throws IOException {
        if (emitter == null) throw new JsonException("emitter is null");
        this.emitter = emitter;
    }


    /**
     * Starts YAML stream and document.
     */
    @Override
    public void startDocument() throws IOException {
        emitter.emit(new StreamStartEvent(null, null));
        emitter.emit(new DocumentStartEvent(null, null, false, null, null));
    }

    /**
     * Ends YAML document and stream.
     */
    @Override
    public void endDocument() throws IOException {
        emitter.emit(new DocumentEndEvent(null, null, false));
        emitter.emit(new StreamEndEvent(null, null));
    }

    /**
     * Starts object scope as mapping event.
     */
    @Override
    public void startObject() throws IOException {
        emitter.emit(new MappingStartEvent(null, null, true, null, null,
                DumperOptions.FlowStyle.BLOCK));
    }

    /**
     * Ends object scope.
     */
    @Override
    public void endObject() throws IOException {
        emitter.emit(new MappingEndEvent(null, null));
    }

    /**
     * Starts array scope as sequence event.
     */
    @Override
    public void startArray() throws IOException {
        emitter.emit(new SequenceStartEvent(null, null, true, null, null,
                DumperOptions.FlowStyle.BLOCK));
    }

    /**
     * Ends array scope.
     */
    @Override
    public void endArray() throws IOException {
        emitter.emit(new SequenceEndEvent(null, null));
    }

    /**
     * Writes object field name scalar.
     */
    @Override
    public void writeName(String name) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                name, null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    /**
     * Writes string scalar.
     */
    @Override
    public void writeString(String value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value, null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    /**
     * Writes number scalar.
     */
    @Override
    public void writeNumber(Number value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    /**
     * Writes boolean scalar.
     */
    @Override
    public void writeBoolean(Boolean value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                value.toString(), null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    /**
     * Writes null scalar.
     */
    @Override
    public void writeNull() throws IOException {
        emitter.emit(new ScalarEvent(null, null, new ImplicitTuple(true, false),
                "null", null, null, DumperOptions.ScalarStyle.PLAIN));
    }

    /**
     * Flush is no-op for event emitter.
     */
    @Override
    public void flush() throws IOException {
        // nothing
    }

    /**
     * Close is no-op for event emitter.
     */
    @Override
    public void close() throws IOException {
        // Nothing
    }
}
