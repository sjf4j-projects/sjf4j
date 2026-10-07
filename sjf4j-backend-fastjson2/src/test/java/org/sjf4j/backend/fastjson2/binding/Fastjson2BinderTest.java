package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fastjson2BinderTest {

    @Test
    void readsRawNodesWithSjf4jCollectionSemantics() throws Exception {
        try (Fastjson2Reader reader = new Fastjson2Reader(JSONReader.of(
                "{\"text\":\"Ada\",\"number\":7,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}"))) {

            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            Map<?, ?> value = (Map<?, ?>) reader.readRawNode();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals("Ada", value.get("text"));
            assertEquals(7, value.get("number"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(LinkedHashMap.class, ((List<?>) value.get("items")).get(1).getClass());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void rawNodeReportsEofUsingStreamingTokenSemantics() {
        try (Fastjson2Reader reader = new Fastjson2Reader(JSONReader.of(""))) {
            BindingException error = assertThrows(BindingException.class, reader::readRawNode);
            assertEquals("unexpected token 'EOF'", error.getMessage());
        }
    }

    @Test
    void rawNodeConsumesOneValueAndRefreshesPeekState() throws Exception {
        try (Fastjson2Reader reader = new Fastjson2Reader(JSONReader.of("[{\"id\":7},\"next\"]"))) {
            reader.startArray();
            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            assertEquals(7, ((Map<?, ?>) reader.readRawNode()).get("id"));
            assertEquals(StreamingReader.Token.STRING, reader.peekToken());
            assertEquals("next", reader.readString());
            reader.endArray();
        }
    }

    @Test
    void readsPojoWithNestedCollectionsAndIgnoresUnknownProperties() {
        String json = "{\"id\":7,\"title\":\"Ada\",\"details\":{\"active\":true},"
                + "\"tags\":[\"one\",\"two\"],\"nullable\":null,\"unknown\":\"ignored\"}";

        Document value = (Document) new Fastjson2Binder().readNode(json, Document.class);

        assertEquals(7, value.id);
        assertEquals("Ada", value.title);
        assertTrue(value.details.active);
        assertEquals(Arrays.asList("one", "two"), value.tags);
        assertNull(value.nullable);
    }

    @Test
    void writesPojoAndHonorsNullContext() {
        Document value = new Document(7, "Ada", new Details(true), Arrays.asList("one", "two"), null);

        String includingNulls = new Fastjson2Binder().writeNodeAsString(value);
        String omittingNulls = new Fastjson2Binder(JSONFactory.createReadContext(),
                JSONFactory.createWriteContext(), new RuntimeContext(false)).writeNodeAsString(value);

        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertFalse(omittingNulls.contains("\"nullable\""));
    }

    @Test
    void writesSeparatorsForMultipleElementsAndProperties() {
        Fastjson2Binder binder = new Fastjson2Binder();
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        assertEquals("[1,2]", binder.writeNodeAsString(Arrays.asList(1, 2)));
        assertEquals("{\"a\":1,\"b\":2}", binder.writeNodeAsString(map));
    }

    @Test
    void createsNativeReadersWritersAndFlushesToSuppliedOutputs() throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        StringWriter text = new StringWriter();

        try (Fastjson2Reader reader = binder.createReader(new StringReader("null"))) {
            assertInstanceOf(Fastjson2Reader.class, reader);
            assertTrue(reader.nextIfNull());
        }
        Fastjson2Writer writer = binder.createWriter(text);
        writer.writeStringValue("héllo");
        writer.close();
        assertEquals("\"héllo\"", text.toString());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Fastjson2Writer byteWriter = binder.createWriter(bytes)) {
            byteWriter.writeStringValue("héllo");
            byteWriter.flush();
        }
        assertEquals("\"héllo\"", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void wrapsSuppliedNativeStreamsAndRejectsNullDependencies() throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        try (Fastjson2Reader reader = binder.createReader(JSONReader.of("null"))) {
            assertTrue(reader.nextIfNull());
        }
        assertThrows(NullPointerException.class, () -> new Fastjson2Binder(null, JSONFactory.createWriteContext()));
        assertThrows(NullPointerException.class, () -> new Fastjson2Binder(JSONFactory.createReadContext(), null));
        assertThrows(NullPointerException.class, () -> binder.createReader((StringReader) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((StringWriter) null));
        assertThrows(NullPointerException.class, () -> binder.createReader((JSONReader) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((JSONWriter) null));
    }

    @Test
    void closingWriterDoesNotCloseCallerOwnedOutput() throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        TrackingWriter text = new TrackingWriter();
        TrackingOutputStream bytes = new TrackingOutputStream();

        try (Fastjson2Writer writer = binder.createWriter(text)) {
            writer.writeNull();
        }
        try (Fastjson2Writer writer = binder.createWriter(bytes)) {
            writer.writeNull();
        }

        assertFalse(text.closed);
        assertFalse(bytes.closed);
        assertEquals("null", text.toString());
        assertEquals("null", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    static class TrackingWriter extends StringWriter {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    static class TrackingOutputStream extends ByteArrayOutputStream {
        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    static class Document {
        public int id;
        public String title;
        public Details details;
        public List<String> tags;
        public String nullable;

        Document() {
        }

        Document(int id, String title, Details details, List<String> tags, String nullable) {
            this.id = id;
            this.title = title;
            this.details = details;
            this.tags = tags;
            this.nullable = nullable;
        }
    }

    static class Details {
        public boolean active;

        Details() {
        }

        Details(boolean active) {
            this.active = active;
        }
    }
}
