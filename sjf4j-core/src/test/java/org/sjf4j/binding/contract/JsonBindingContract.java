package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.StreamingGenerator;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Portable streaming-binding behavior. Subclasses opt parser bindings into this contract. */
public abstract class JsonBindingContract {

    protected abstract Binder<?, ?> binding(RuntimeContext context);

    @Test
    void readerAndWriterExposeStructuralJson() throws Exception {
        Binder<?, ?> binding = binding(RuntimeContext.EMPTY);
        StringWriter output = new StringWriter();
        StreamingGenerator writer = binding.createGenerator(output);
try {
            writer.startWrite();
            writer.startObject();
            writer.writeName("items");
            writer.startArray();
            writer.writeIntValue(1);
            writer.separateElement();
            writer.writeStringValue("x");
            writer.endArray();
            writer.writeName("ok", true);
            writer.writeBoolean(true);
            writer.endObject();
            writer.endWrite();
        
} finally { writer.release(); }
        StreamingParser reader = binding.createParser(new StringReader(output.toString()));
try {
            reader.startRead();
            reader.startObject();
            assertEquals("items", reader.nextName());
            reader.startArray();
            assertEquals(1, reader.readIntValue());
            assertEquals("x", reader.readString());
            reader.endArray();
            assertEquals("ok", reader.nextName());
            assertTrue(reader.readBooleanValue());
            reader.endObject();
            reader.endRead();
        
} finally { reader.release(); }
    }

}
