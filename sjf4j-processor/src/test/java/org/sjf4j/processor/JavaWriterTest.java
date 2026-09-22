package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaWriterTest {
    @Test
    void doesNotImportUnderscorePrefixedMemberAccess() throws Exception {
        StringWriter source = new StringWriter();
        JavaWriter writer = new JavaWriter(source, "sample", "Generated");
        writer.line("package sample;");
        writer.line("org.sjf4j.JsonObject value;");
        writer.line("if (source._dynamicMap() != null) {}");
        writer.close();

        String generated = source.toString();
        assertTrue(generated.contains("import org.sjf4j.JsonObject;"));
        assertTrue(generated.contains("JsonObject value;"));
        assertTrue(generated.contains("source._dynamicMap()"));
        assertFalse(generated.contains("import source._dynamicMap;"));
    }
}
