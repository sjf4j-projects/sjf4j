package org.sjf4j.backend.jsonp.binding;

import jakarta.json.Json;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonpBinderTest {

    @Test
    void readsAndWritesPojoAndHonorsNullContext() {
        String json = "{\"id\":7,\"title\":\"Ada\",\"tags\":[\"one\",\"two\"],"
                + "\"nullable\":null,\"unknown\":{\"discard\":true}}";
        JsonpBinder binder = new JsonpBinder();

        Document value = (Document) binder.readNode(json, Document.class);
        assertEquals(7, value.id);
        assertEquals("Ada", value.title);
        assertEquals(Arrays.asList("one", "two"), value.tags);
        assertNull(value.nullable);

        String includingNulls = binder.writeNodeAsString(value);
        String omittingNulls = new JsonpBinder(JsonProvider.provider(), new StreamingContext(false))
                .writeNodeAsString(value);
        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertFalse(omittingNulls.contains("\"nullable\""));
    }

    @Test
    void supportsIoAndNativeParserGeneratorOverloads() throws Exception {
        JsonpBinder binder = new JsonpBinder(JsonProvider.provider(), new StreamingContext(false));
        byte[] input = "{\"title\":\"héllo\"}".getBytes(StandardCharsets.UTF_8);

        assertEquals("héllo", ((Document) binder.readNode(input, Document.class)).title);
        assertEquals("héllo", ((Document) binder.readNode(new ByteArrayInputStream(input), Document.class)).title);
        assertThrows(Exception.class, () -> binder.readNode(
                "\uFEFFnull".getBytes(StandardCharsets.UTF_16LE), Object.class));
        try (JsonpReader reader = binder.createReader(new StringReader("null"))) {
            assertInstanceOf(JsonpReader.class, reader);
            reader.nextNull();
        }

        JsonParser parser = Json.createParser(new StringReader("null"));
        try (JsonpReader reader = binder.createReader(parser)) {
            reader.nextNull();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonpWriter writer = binder.createWriter(output)) {
            writer.writeStringValue("héllo");
        }
        assertEquals("\"héllo\"", new String(output.toByteArray(), StandardCharsets.UTF_8));

        StringWriter nativeOutput = new StringWriter();
        JsonGenerator generator = Json.createGenerator(nativeOutput);
        try (JsonpWriter writer = binder.createWriter(generator)) {
            writer.writeNull();
        }
        assertEquals("null", nativeOutput.toString());
    }

    @Test
    void writeNodeFlushesButDoesNotCloseCallerWriter() {
        TrackingWriter output = new TrackingWriter();

        new JsonpBinder().writeNode(output, new Document(7, "Ada", null, null));

        assertFalse(output.closed);
        assertTrue(output.toString().contains("\"id\":7"));

        TrackingOutputStream bytes = new TrackingOutputStream();
        new JsonpBinder().writeNode(bytes, "value");
        assertFalse(bytes.closed);
        assertEquals("\"value\"", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNullDependenciesAndIo() {
        assertThrows(NullPointerException.class, () -> new JsonpBinder((JsonProvider) null));
        assertThrows(NullPointerException.class, () -> new JsonpBinder(JsonProvider.provider(), null));

        JsonpBinder binder = new JsonpBinder();
        assertThrows(NullPointerException.class, () -> binder.createReader((Reader) null));
        assertThrows(NullPointerException.class, () -> binder.createReader((String) null));
        assertThrows(NullPointerException.class, () -> binder.createReader((byte[]) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((Writer) null));
    }

    static class Document {
        public int id;
        public String title;
        public List<String> tags;
        public String nullable;

        Document() {
        }

        Document(int id, String title, List<String> tags, String nullable) {
            this.id = id;
            this.title = title;
            this.tags = tags;
            this.nullable = nullable;
        }
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
        public void close() {
            closed = true;
        }
    }
}
