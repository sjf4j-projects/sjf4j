package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompiledGsonJsonpRootScalarTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rootScalarsUseNativeRoutingWithoutParameterNameCollisions() throws Exception {
        for (String backend : new String[]{"GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Choice.java", "package fixture; public enum Choice { ALPHA, BETA }");
            sources.put("fixture/Binder.java", "package fixture; import org.sjf4j.annotation.binding.*; "
                    + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                    + "@WriteTo String writeString(String value) throws java.io.IOException; "
                    + "@WriteTo String writeInt(int g) throws java.io.IOException; "
                    + "@WriteTo String writeBoolean(boolean value) throws java.io.IOException; "
                    + "@WriteTo String writeFloat(Float value) throws java.io.IOException; "
                    + "@WriteTo String writeBoxed(Integer item) throws java.io.IOException; "
                    + "@WriteTo String writeNumber(Number value) throws java.io.IOException; "
                    + "@WriteTo String writeDecimal(java.math.BigDecimal value) throws java.io.IOException; "
                    + "@WriteTo String writeCharacter(Character value) throws java.io.IOException; "
                    + "@WriteTo String writeChar(char value) throws java.io.IOException; "
                    + "@WriteTo String writeEnum(Choice value) throws java.io.IOException; "
                    + "@WriteTo String writeRuntime(Object value) throws java.io.IOException; "
                    + "@WriteTo String writeUuid(java.util.UUID value) throws java.io.IOException; "
                    + "@WriteTo byte[] writeIntBytes(int value) throws java.io.IOException; "
                    + "@WriteTo void writeIntStream(int value, java.io.OutputStream out) throws java.io.IOException; "
                    + "@WriteTo void writeIntWriter(int value, java.io.Writer out) throws java.io.IOException; "
                    + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("g2 = writer.nativeWriter();"), backend);
            assertTrue(generated.contains("_writeString(g, value);"), backend);
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Class<?> choice = loader.loadClass("fixture.Choice");
                Object binder = impl.getConstructor().newInstance();
                assertEquals("\"hello\"", impl.getMethod("writeString", String.class).invoke(binder, "hello"), backend);
                assertEquals("null", impl.getMethod("writeString", String.class).invoke(binder, new Object[]{null}), backend);
                assertEquals("7", impl.getMethod("writeInt", int.class).invoke(binder, 7), backend);
                assertEquals("true", impl.getMethod("writeBoolean", boolean.class).invoke(binder, true), backend);
                assertEquals("0.125", impl.getMethod("writeFloat", Float.class).invoke(binder, 0.125f), backend);
                assertEquals("19", impl.getMethod("writeBoxed", Integer.class).invoke(binder, 19), backend);
                assertEquals("null", impl.getMethod("writeFloat", Float.class).invoke(binder, new Object[]{null}), backend);
                assertEquals("12", impl.getMethod("writeNumber", Number.class).invoke(binder, 12), backend);
                assertEquals("1.25", impl.getMethod("writeDecimal", java.math.BigDecimal.class).invoke(binder, new java.math.BigDecimal("1.25")), backend);
                assertEquals("\"x\"", impl.getMethod("writeCharacter", Character.class).invoke(binder, 'x'), backend);
                assertEquals("\"z\"", impl.getMethod("writeChar", char.class).invoke(binder, 'z'), backend);
                assertEquals("\"ALPHA\"", impl.getMethod("writeEnum", choice).invoke(binder, Enum.valueOf((Class) choice, "ALPHA")), backend);
                assertEquals("[1,null,3]", impl.getMethod("writeRuntime", Object.class).invoke(binder, java.util.Arrays.asList(1, null, 3)), backend);
                UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                assertEquals("\"" + uuid + "\"", impl.getMethod("writeUuid", UUID.class).invoke(binder, uuid), backend);
                assertEquals("7", new String((byte[]) impl.getMethod("writeIntBytes", int.class).invoke(binder, 7), StandardCharsets.UTF_8), backend);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                impl.getMethod("writeIntStream", int.class, java.io.OutputStream.class).invoke(binder, 7, stream);
                assertEquals("7", stream.toString(StandardCharsets.UTF_8), backend);
                StringWriter writer = new StringWriter();
                impl.getMethod("writeIntWriter", int.class, java.io.Writer.class).invoke(binder, 7, writer);
                assertEquals("7", writer.toString(), backend);
            }
        }
    }
}
