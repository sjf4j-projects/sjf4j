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
        {
            StreamingGenerator generator = binding.createGenerator(output);
            try {
                generator.startWrite();
                generator.startObject();
                generator.writeName("items");
                generator.startArray();
                generator.writeIntValue(1);
                generator.separateElement();
                generator.writeStringValue("x");
                generator.endArray();
                generator.writeName("ok", true);
                generator.writeBoolean(true);
                generator.endObject();
                generator.endWrite();

            } finally {
                generator.release();
            }
        }
        {
            StreamingParser parser = binding.createParser(new StringReader(output.toString()));
            try {
                parser.startRead();
                parser.startObject();
                assertEquals("items", parser.nextName());
                parser.startArray();
                assertEquals(1, parser.readIntValue());
                assertEquals("x", parser.readString());
                parser.endArray();
                assertEquals("ok", parser.nextName());
                assertTrue(parser.readBooleanValue());
                parser.endObject();
                parser.endRead();

            } finally {
                parser.release();
            }
        }
    }

}
