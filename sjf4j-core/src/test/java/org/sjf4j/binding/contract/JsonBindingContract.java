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
                generator.start();
                generator.writeObjectStart();
                generator.writeName("items");
                generator.writeArrayStart();
                generator.writeIntValue(1);
                generator.writeArraySeparator();
                generator.writeStringValue("x");
                generator.writeArrayEnd();
                generator.writeName("ok", true);
                generator.writeBoolean(true);
                generator.writeObjectEnd();
                generator.end();

            } finally {
                generator.close();
            }
        }
        {
            StreamingParser parser = binding.createParser(new StringReader(output.toString()));
            try {
                parser.start();
                parser.readObjectStart();
                assertEquals("items", parser.nextName());
                parser.readArrayStart();
                assertEquals(1, parser.readIntValue());
                assertEquals("x", parser.readString());
                parser.readArrayEnd();
                assertEquals("ok", parser.nextName());
                assertTrue(parser.readBooleanValue());
                parser.readObjectEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

}
