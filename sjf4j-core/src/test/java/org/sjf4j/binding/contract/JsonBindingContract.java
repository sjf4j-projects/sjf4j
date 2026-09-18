package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Portable streaming-binding behavior. Subclasses opt parser bindings into this contract. */
public abstract class JsonBindingContract {

    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    @Test
    void readerAndWriterExposeStructuralJson() throws Exception {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        StringWriter output = new StringWriter();
        try (StreamingWriter writer = binding.createWriter(output)) {
            writer.startDocument();
            writer.startObject();
            writer.writeName("items");
            writer.startArray();
            writer.writeIntValue(1);
            writer.separateElement();
            writer.writeStringValue("x");
            writer.endArray();
            writer.separateProperty();
            writer.writeName("ok");
            writer.writeBoolean(true);
            writer.endObject();
            writer.endDocument();
            writer.flush();
        }
        try (StreamingReader reader = binding.createReader(new StringReader(output.toString()))) {
            reader.startDocument();
            reader.startObject();
            assertEquals("items", reader.nextName());
            reader.startArray();
            assertEquals(1, reader.nextIntValue());
            assertEquals("x", reader.nextString());
            reader.endArray();
            assertEquals("ok", reader.nextName());
            assertTrue(reader.nextBooleanValue());
            reader.endObject();
            reader.endDocument();
        }
    }

}
