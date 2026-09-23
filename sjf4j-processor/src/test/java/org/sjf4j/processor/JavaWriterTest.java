package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaWriterTest {
    @Test
    void importsInternalAccessForDynamicProperties() throws Exception {
        StringWriter source = new StringWriter();
        JavaWriter writer = new JavaWriter(source, "sample", "Generated");
        writer.line("package sample;");
        writer.line("org.sjf4j.JsonObject value;");
        writer.line("if (org.sjf4j.InternalAccess.dynamicProperties(source) != null) {}");
        writer.close();

        String generated = source.toString();
        assertTrue(generated.contains("import org.sjf4j.JsonObject;"));
        assertTrue(generated.contains("import org.sjf4j.InternalAccess;"));
        assertTrue(generated.contains("JsonObject value;"));
        assertTrue(generated.contains("InternalAccess.dynamicProperties(source)"));
        assertFalse(generated.contains("org.sjf4j.InternalAccess.dynamicProperties(source)"));
    }
}
