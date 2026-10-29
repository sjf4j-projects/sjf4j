package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.binding.Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercise the generated unstructured Map fallback against runtime on every
 * backend, including UTF-8 and nested untyped containers.
 */
class CompiledMapReadFallbackTest {

    private static final String JSON =
            "{\"items\":[1,null,{\"enabled\":true}],\"missing\":null,"
                    + "\"unicode\":\"你好\",\"nested\":{\"count\":42,\"value\":2.5}}";

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Reader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Reader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Reader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonReader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpReader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleReader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @FunctionalInterface
    interface StringReader {
        Map<String, Object> read(String json) throws IOException;
    }

    @FunctionalInterface
    interface BytesReader {
        Map<String, Object> read(byte[] json) throws IOException;
    }

    @Test
    void compiledMapsMatchRuntimeMaps() throws Exception {
        Fastjson2Reader fastjson2 = CompiledInstances.create(Fastjson2Reader.class);
        verify("fastjson2", new Fastjson2Binder(), fastjson2::read, fastjson2::readBytes);

        Jackson2Reader jackson2 = CompiledInstances.create(Jackson2Reader.class);
        verify("jackson2", new Jackson2Binder(), jackson2::read, jackson2::readBytes);

        Jackson3Reader jackson3 = CompiledInstances.create(Jackson3Reader.class);
        verify("jackson3", new Jackson3Binder(), jackson3::read, jackson3::readBytes);

        GsonReader gson = CompiledInstances.create(GsonReader.class);
        verify("gson", new GsonBinder(), gson::read, gson::readBytes);

        JsonpReader jsonp = CompiledInstances.create(JsonpReader.class);
        verify("jsonp", new JsonpBinder(), jsonp::read, jsonp::readBytes);

        SimpleReader simple = CompiledInstances.create(SimpleReader.class);
        verify("simple", new SimpleJsonBinder(), simple::read, simple::readBytes);
    }

    @SuppressWarnings("unchecked")
    private static void verify(String label, Binder<?, ?> runtime,
                               StringReader readString, BytesReader readBytes) throws Exception {
        Map<String, Object> expected = (Map<String, Object>) runtime.readNode(JSON, Map.class);
        Map<String, Object> actual = readString.read(JSON);
        Map<String, Object> utf8 = readBytes.read(JSON.getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, actual, label + " String");
        assertEquals(expected, utf8, label + " UTF-8");
        assertEquals(expected.keySet(), actual.keySet(), label + " field order");
        assertTrue(actual.containsKey("missing"), label);
        assertNull(actual.get("missing"), label);
        assertNull(readString.read("null"), label + " null String");
        assertNull(readBytes.read("null".getBytes(StandardCharsets.UTF_8)), label + " null UTF-8");
    }
}
