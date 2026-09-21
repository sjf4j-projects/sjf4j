package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
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

class Jackson2BinderTest {

    @Test
    void readsPojoWithNestedCollectionsAndMaps() {
        String json = "{\"id\":7,\"title\":\"Ada\",\"details\":{\"active\":true},"
                + "\"tags\":[\"one\",\"two\"],\"related\":{\"first\":{\"active\":false}},"
                + "\"nullable\":null,\"unknown\":\"ignored\"}";

        Document value = (Document) new Jackson2Binder(new JsonFactory()).readNode(json, Document.class);

        assertEquals(7, value.id);
        assertEquals("Ada", value.title);
        assertTrue(value.details.active);
        assertEquals(Arrays.asList("one", "two"), value.tags);
        assertEquals(false, value.related.get("first").active);
        assertNull(value.nullable);
    }

    @Test
    void writesPojoWithNestedCollectionsAndMapsAndHonorsNullContext() {
        Document value = document();

        String includingNulls = new Jackson2Binder(new JsonFactory())
                .writeNodeAsString(value);
        String omittingNulls = new Jackson2Binder(new JsonFactory(), new StreamingContext(false))
                .writeNodeAsString(value);

        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertTrue(includingNulls.contains("\"related\":{\"first\":{\"active\":false}}"));
        assertTrue(!omittingNulls.contains("\"nullable\""));
    }

    @Test
    void retainsSuppliedContextAndCreatesJacksonReadersAndWriters() throws Exception {
        StreamingContext context = new StreamingContext(false);
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory(), context);
        StringWriter output = new StringWriter();

        try (Jackson2Reader reader = binder.createReader(new StringReader("null"))) {
            assertInstanceOf(Jackson2Reader.class, reader);
        }
        Jackson2Writer writer = binder.createWriter(output);
        assertInstanceOf(Jackson2Writer.class, writer);
        writer.writeNull();
        writer.close();
        assertEquals("null", output.toString());
    }

    @Test
    void usesSuppliedFactoryConfiguration() {
        JsonFactory factory = new JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS);
        Document value = (Document) new Jackson2Binder(factory)
                .readNode("/* configured parser */ {\"id\":7}", Document.class);

        assertEquals(7, value.id);
    }

    @Test
    void writeNodeFlushesWithoutClosingCallerWriter() {
        TrackingWriter output = new TrackingWriter();

        new Jackson2Binder(new JsonFactory()).writeNode(output, document());

        assertFalse(output.closed);
        assertTrue(output.toString().contains("\"id\":7"));
    }

    @Test
    void usesNativeFactoryOverloadsForStringAndOutput() throws Exception {
        TrackingFactory factory = new TrackingFactory();
        Jackson2Binder binder = new Jackson2Binder(factory);

        try (Jackson2Reader reader = binder.createReader("null")) {
            reader.nextNull();
        }
        assertEquals("string", factory.parserSource);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Jackson2Writer writer = binder.createWriter(output)) {
            writer.writeStringValue("héllo");
        }
        assertEquals(JsonEncoding.UTF8, factory.generatorEncoding);
        assertEquals("\"héllo\"", new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void byteAndStreamInputRemainUtf8() throws Exception {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        byte[] utf8 = "{\"title\":\"héllo\"}".getBytes(StandardCharsets.UTF_8);

        Document fromBytes = (Document) binder.readNode(utf8, Document.class);
        Document fromStream = (Document) binder.readNode(new ByteArrayInputStream(utf8), Document.class);

        assertEquals("héllo", fromBytes.title);
        assertEquals("héllo", fromStream.title);
        try (Jackson2Reader reader = binder.createReader("\uFEFFnull".getBytes(StandardCharsets.UTF_16LE))) {
            assertThrows(Exception.class, reader::nextNull);
        }
    }

    @Test
    void wrapsSuppliedJacksonStreamsAndClosesThemWithTheWrapper() throws Exception {
        JsonParser parser = new JsonFactory().createParser("null");
        try (Jackson2Reader reader = new Jackson2Binder(new JsonFactory()).createReader(parser)) {
            reader.nextNull();
        }
        assertTrue(parser.isClosed());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(output);
        try (Jackson2Writer writer = new Jackson2Binder(new JsonFactory()).createWriter(generator)) {
            writer.writeNull();
        }
        assertTrue(generator.isClosed());
        assertEquals("null", new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNullDependenciesAndIo() {
        assertThrows(NullPointerException.class, () -> new Jackson2Binder((JsonFactory) null));
        assertThrows(NullPointerException.class, () -> new Jackson2Binder(new JsonFactory(), null));

        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        assertThrows(NullPointerException.class, () -> binder.createReader((Reader) null));
        assertThrows(NullPointerException.class, () -> binder.createReader((JsonParser) null));
        assertThrows(NullPointerException.class, () -> binder.createReader((String) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((Writer) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((JsonGenerator) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((OutputStream) null));
    }

    private static Document document() {
        Map<String, Details> related = new LinkedHashMap<>();
        related.put("first", new Details(false));
        return new Document(7, "Ada", new Details(true), Arrays.asList("one", "two"), related, null);
    }

    static class Document {
        public int id;
        public String title;
        public Details details;
        public List<String> tags;
        public Map<String, Details> related;
        public String nullable;

        Document() {
        }

        Document(int id, String title, Details details, List<String> tags, Map<String, Details> related, String nullable) {
            this.id = id;
            this.title = title;
            this.details = details;
            this.tags = tags;
            this.related = related;
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

    static class TrackingWriter extends StringWriter {
        boolean closed;

        @Override

        public void close() {
            closed = true;
        }
    }

    static class TrackingFactory extends JsonFactory {
        String parserSource;
        JsonEncoding generatorEncoding;

        @Override

        public JsonParser createParser(String input) throws java.io.IOException {
            parserSource = "string";
            return super.createParser(input);
        }

        @Override

        public JsonGenerator createGenerator(OutputStream output, JsonEncoding encoding) throws java.io.IOException {
            generatorEncoding = encoding;
            return super.createGenerator(output, encoding);
        }
    }
}
