package org.sjf4j.backend.jsonp.binding;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.Nodes;
import org.sjf4j.exception.BindingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        String omittingNulls = new JsonpBinder(JsonProvider.provider(), new RuntimeContext(false))
                .writeNodeAsString(value);
        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertFalse(omittingNulls.contains("\"nullable\""));
    }

    @Test
    void readsNativeExternalTreeAndTraversesIt() {
        JsonpBinder binder = new JsonpBinder();
        String json = "{\"items\":[{\"name\":\"Ada\",\"active\":true},null,3],\"empty\":{}}";

        JsonObject object = (JsonObject) binder.readNode(json, JsonObject.class);
        Object first = Nodes.getInArray(Nodes.getInObject(object, "items"), 0);
        assertEquals("Ada", ((JsonString) Nodes.getInObject(first, "name")).getString());
        assertEquals(3, object.getJsonArray("items").size());
        assertSame(JsonValue.NULL, Nodes.getInArray(object.getJsonArray("items"), 1));
        assertEquals(json, binder.writeNodeAsString(object));

        assertEquals(object, binder.readNode(json, JsonStructure.class));
        assertEquals(object, binder.readNode(json, JsonValue.class));
        JsonArray array = (JsonArray) binder.readNode("[true,{\"n\":1}]", JsonArray.class);
        assertEquals(2, array.size());
        assertEquals(1, array.getJsonObject(1).getInt("n"));
        assertEquals(array, binder.readNode("[true,{\"n\":1}]".getBytes(StandardCharsets.UTF_8), JsonArray.class));
        assertEquals("text", ((JsonString) binder.readNode("\"text\"", JsonValue.class)).getString());
        assertNull(binder.readNode("null", JsonValue.class));

        TreeDocument nested = (TreeDocument) binder.readNode(
                "{\"before\":1,\"tree\":{\"items\":[true]},\"values\":[1,2],"
                        + "\"entries\":{\"n\":3},\"elements\":[true,null],\"after\":2}", TreeDocument.class);
        assertEquals(1, nested.before);
        assertEquals(1, nested.tree.getJsonArray("items").size());
        assertEquals(2, nested.values.size());
        assertEquals(3, ((jakarta.json.JsonNumber) nested.entries.get("n")).intValue());
        assertNull(nested.elements.get(1));
        assertEquals(2, nested.after);

        Map<String, Object> mixed = new LinkedHashMap<>();
        mixed.put("tree", object);
        mixed.put("array", array);
        mixed.put("nil", JsonValue.NULL);
        assertEquals("{\"tree\":" + json + ",\"array\":[true,{\"n\":1}],\"nil\":null}",
                new JsonpBinder(JsonProvider.provider(), new RuntimeContext(false)).writeNodeAsString(mixed));
        assertEquals("{\"before\":1,\"tree\":{\"items\":[true]},\"values\":[1,2],"
                        + "\"entries\":{\"n\":3},\"elements\":[true,null],\"after\":2}",
                binder.writeNodeAsString(nested));
        assertEquals("true", binder.writeNodeAsString(JsonValue.TRUE));
        assertEquals("null", binder.writeNodeAsString(JsonValue.NULL));
        String precise = "{\"decimal\":0.123456789012345678901234567890}";
        assertEquals(precise, binder.writeNodeAsString(binder.readNode(precise, JsonValue.class)));

        assertThrows(BindingException.class, () -> binder.readNode("[]", JsonObject.class));
        assertThrows(BindingException.class, () -> binder.readNode("{}", JsonArray.class));
        assertThrows(BindingException.class, () -> binder.readNode("true", JsonStructure.class));
        assertThrows(BindingException.class, () -> binder.readNode("{} []", JsonValue.class));
    }

    @Test
    void supportsIoAndNativeParserGeneratorOverloads() throws Exception {
        JsonpBinder binder = new JsonpBinder(JsonProvider.provider(), new RuntimeContext(false));
        byte[] input = "{\"title\":\"héllo\"}".getBytes(StandardCharsets.UTF_8);

        assertEquals("héllo", ((Document) binder.readNode(input, Document.class)).title);
        assertEquals("héllo", ((Document) binder.readNode(new ByteArrayInputStream(input), Document.class)).title);
        assertThrows(Exception.class, () -> binder.readNode(
                "\uFEFFnull".getBytes(StandardCharsets.UTF_16LE), Object.class));
        try (JsonpReader reader = binder.createReader(new StringReader("null"))) {
            assertInstanceOf(JsonpReader.class, reader);
            assertTrue(reader.nextIfNull());
        }

        JsonParser parser = Json.createParser(new StringReader("null"));
        try (JsonpReader reader = binder.createReader(parser)) {
            assertTrue(reader.nextIfNull());
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
    void readNodeDoesNotCloseCallerOwnedReader() {
        final boolean[] closed = {false};
        StringReader input = new StringReader("{\"id\":7}") {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        Document value = (Document) new JsonpBinder().readNode(input, Document.class);
        assertEquals(7, value.id);
        assertFalse(closed[0]);
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

    static class TreeDocument {
        public int before;
        public JsonObject tree;
        public JsonArray values;
        public Map<String, JsonValue> entries;
        public List<JsonValue> elements;
        public int after;
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
