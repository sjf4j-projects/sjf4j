package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.exception.BindingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleJsonBindingTest {

    @Test
    void usesDefaultAndCustomStreamingContexts() {
        assertSame(StreamingContext.EMPTY, new SimpleJsonBinder().streamingContext());

        StreamingContext context = new StreamingContext(false);
        assertSame(context, new SimpleJsonBinder(context).streamingContext());
        assertThrows(NullPointerException.class, () -> new SimpleJsonBinder(null));
    }

    @Test
    void createsConcreteReadersAndWritersForAllFactoryForms() throws Exception {
        SimpleJsonBinder binding = new SimpleJsonBinder();

        try (SimpleJsonReader reader = binding.createReader(new StringReader("null"))) {
            assertInstanceOf(SimpleJsonReader.class, reader);
        }
        try (SimpleJsonReader reader = binding.createReader("null")) {
            assertInstanceOf(SimpleJsonReader.class, reader);
        }
        try (SimpleJsonReader reader = binding.createReader("null".getBytes(StandardCharsets.UTF_8))) {
            assertInstanceOf(SimpleJsonReader.class, reader);
        }
        try (SimpleJsonReader reader = binding.createReader(
                new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8)))) {
            assertInstanceOf(SimpleJsonReader.class, reader);
        }

        try (SimpleJsonWriter writer = binding.createWriter(new StringWriter())) {
            assertInstanceOf(SimpleJsonWriter.class, writer);
        }
        try (SimpleJsonWriter writer = binding.createWriter(new ByteArrayOutputStream())) {
            assertInstanceOf(SimpleJsonWriter.class, writer);
        }
    }

    @Test
    void readsAndWritesNestedNodesThroughConvenienceMethods() {
        SimpleJsonBinder binding = new SimpleJsonBinder();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("greeting", "héllo");
        source.put("items", List.of(1, JsonObject.of("nested", JsonArray.of(true, "x"))));

        String json = binding.writeNodeAsString(source);
        assertEquals("{\"greeting\":\"héllo\",\"items\":[1,{\"nested\":[true,\"x\"]}]}", json);
        assertEquals(json, new String(binding.writeNodeAsBytes(source), StandardCharsets.UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        binding.writeNode(output, source);
        assertEquals(json, output.toString(StandardCharsets.UTF_8));

        JsonObject node = (JsonObject) binding.readNode(json, JsonObject.class);
        assertEquals("héllo", node.getString("greeting"));
        assertEquals(1L, node.getJsonArray("items").getLong(0));
        assertEquals("x", node.getJsonArray("items").getJsonObject(1)
                .getJsonArray("nested").getString(1));

        Map<?, ?> fromBytes = assertInstanceOf(Map.class, binding.readNode(
                json.getBytes(StandardCharsets.UTF_8), Map.class));
        assertEquals("héllo", fromBytes.get("greeting"));
        Map<?, ?> fromStream = assertInstanceOf(Map.class, binding.readNode(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Map.class));
        assertEquals("héllo", fromStream.get("greeting"));
    }

    @Test
    void handlesRootValuesAndRejectsTrailingDocuments() {
        SimpleJsonBinder binding = new SimpleJsonBinder();

        assertEquals("null", binding.writeNodeAsString(null));
        assertEquals("true", binding.writeNodeAsString(true));
        assertEquals("\"a\\nb\"", binding.writeNodeAsString("a\nb"));
        assertEquals("[1,2]", binding.writeNodeAsString(new int[]{1, 2}));
        assertEquals("[]", binding.writeNodeAsString(List.of()));
        assertEquals("{}", binding.writeNodeAsString(Map.of()));

        assertEquals(12, binding.readNode("12", Object.class));
        assertEquals("a\nb", binding.readNode("\"a\\nb\"", Object.class));
        assertEquals(Arrays.asList(1, null), binding.readNode("[1,null]", Object.class));

        for (String json : new String[]{"null null", "{}x", "[1] 0"}) {
            assertThrows(BindingException.class, () -> binding.readNode(json, Object.class), json);
            assertThrows(BindingException.class,
                    () -> binding.readNode(json.getBytes(StandardCharsets.UTF_8), Object.class), json);
            assertThrows(BindingException.class, () -> binding.readNode(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Object.class), json);
        }
    }

    @Test
    void readsStringsWhoseEscapesCrossTheInternalBufferBoundary() {
        SimpleJsonBinder binding = new SimpleJsonBinder();
        String prefix = "a".repeat(8190);
        String json = '"' + prefix + "\\n\"";

        assertEquals(prefix + '\n', binding.readNode(json, String.class));
    }

    @Test
    void honorsConfiguredIncludeNulls() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("present", 1);
        source.put("missing", null);

        assertEquals("{\"present\":1,\"missing\":null}", new SimpleJsonBinder().writeNodeAsString(source));
        assertEquals("{\"present\":1}", new SimpleJsonBinder(new StreamingContext(false)).writeNodeAsString(source));
    }

    @Test
    void wrapsMalformedInputAndNonFiniteOutputFailures() {
        SimpleJsonBinder binding = new SimpleJsonBinder();

        BindingException readFailure = assertThrows(BindingException.class,
                () -> binding.readNode("{\"value\":}", Map.class));
        assertInstanceOf(BindingException.class, readFailure.getCause());

        BindingException writeFailure = assertThrows(BindingException.class,
                () -> binding.writeNodeAsString(Double.NaN));
        assertInstanceOf(BindingException.class, writeFailure.getCause());
        assertInstanceOf(IOException.class, rootCause(writeFailure));
    }

    private static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error;
    }
}
