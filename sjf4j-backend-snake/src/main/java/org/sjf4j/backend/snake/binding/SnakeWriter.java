package org.sjf4j.backend.snake.binding;

import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.StreamingWriter;
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
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

public final class SnakeWriter extends StreamingWriter {

    private static final Resolver RESOLVER = new Resolver();
    private static final ImplicitTuple PLAIN_IMPLICIT = new ImplicitTuple(true, false);
    private static final ImplicitTuple STRING_IMPLICIT = new ImplicitTuple(true, true);
    private static final ImplicitTuple QUOTED_STRING_IMPLICIT = new ImplicitTuple(false, true);
    private static final String TAG_STRING = Tag.STR.getValue();

    private final Emitter emitter;

    public SnakeWriter(StreamingBinder<?, ?> binder, Emitter emitter) throws IOException {
        super(binder);
        Objects.requireNonNull(emitter, "emitter");
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
        writeStringValue(name);
    }

    /**
     * Writes string scalar.
     */
    @Override
    public void writeStringValue(String value) throws IOException {
        ImplicitTuple implicit = Tag.STR.equals(RESOLVER.resolve(NodeId.scalar, value, true))
                ? STRING_IMPLICIT
                : QUOTED_STRING_IMPLICIT;
        emitter.emit(new ScalarEvent(null, TAG_STRING, implicit, value, null, null,
                DumperOptions.ScalarStyle.PLAIN));
    }

    @Override
    public void writeNumberValue(Number value) throws IOException {
        writePlain(value.toString());
    }

    @Override
    public void writeLongValue(long value) throws IOException {
        writePlain(Long.toString(value));
    }

    @Override
    public void writeIntValue(int value) throws IOException {
        writePlain(Integer.toString(value));
    }

    @Override
    public void writeShortValue(short value) throws IOException {
        writePlain(Short.toString(value));
    }

    @Override
    public void writeByteValue(byte value) throws IOException {
        writePlain(Byte.toString(value));
    }

    @Override
    public void writeDoubleValue(double value) throws IOException {
        writePlain(Double.toString(value));
    }

    @Override
    public void writeFloatValue(float value) throws IOException {
        writePlain(Float.toString(value));
    }

    @Override
    public void writeBooleanValue(boolean value) throws IOException {
        writePlain(Boolean.toString(value));
    }

    @Override
    public void writeCharValue(char value) throws IOException {
        writeString(Character.toString(value));
    }

    @Override
    public void writeNull() throws IOException {
        writePlain("null");
    }

    /**
     * Flush is no-op for event emitter.
     */
    @Override
    public void flush() throws IOException {
    }

    /**
     * Close is no-op for event emitter.
     */
    @Override
    public void close() throws IOException {
    }


    private void writePlain(String value) throws IOException {
        emitter.emit(new ScalarEvent(null, null, PLAIN_IMPLICIT, value, null, null,
                DumperOptions.ScalarStyle.PLAIN));
    }

}
