package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StringBuilderWriterTest {

    @Test
    void writesAndAppendsToBuilder() {
        StringBuilderWriter writer = new StringBuilderWriter();

        writer.write('a');
        writer.write(new char[] {'x', 'b', 'c'}, 1, 2);
        writer.write("def");
        writer.write("ghij", 1, 2);
        writer.append('|').append("tail", 0, 2);

        assertEquals("abcdefhi|ta", writer.toString());
    }

    @Test
    void exposesAndResetsSuppliedBuilder() {
        StringBuilder builder = new StringBuilder("prefix");
        StringBuilderWriter writer = new StringBuilderWriter(builder);

        assertSame(builder, writer.getBuilder());
        writer.reset();
        writer.write("value");
        writer.flush();
        writer.close();

        assertEquals("value", builder.toString());
    }
}
