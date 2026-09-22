package org.sjf4j.backend.jackson3.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;

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

class Jackson3BinderTest {

    @Test
    void readsPojoWithNestedCollectionsAndMaps() {
        String json = "{\"id\":7,\"title\":\"Ada\",\"details\":{\"active\":true},"
                + "\"tags\":[\"one\",\"two\"],\"related\":{\"first\":{\"active\":false}},"
                + "\"nullable\":null,\"unknown\":\"ignored\"}";

        Document value = (Document) new Jackson3Binder(new JsonFactory()).readNode(json, Document.class);

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

        String includingNulls = new Jackson3Binder(new JsonFactory())
                .writeNodeAsString(value);
        String omittingNulls = new Jackson3Binder(new JsonFactory(), new StreamingContext(false))
                .writeNodeAsString(value);

        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertTrue(includingNulls.contains("\"related\":{\"first\":{\"active\":false}}"));
        assertTrue(!omittingNulls.contains("\"nullable\""));
    }

    @Test
    void retainsSuppliedContextAndCreatesJacksonReadersAndWriters() throws Exception {
        StreamingContext context = new StreamingContext(false);
        Jackson3Binder binder = new Jackson3Binder(new JsonFactory(), context);
        StringWriter output = new StringWriter();

        try (Jackson3Reader reader = binder.createReader(new StringReader("null"))) {
            assertInstanceOf(Jackson3Reader.class, reader);
        }
        Jackson3Writer writer = binder.createWriter(output);
        assertInstanceOf(Jackson3Writer.class, writer);
        writer.writeNull();
        writer.close();
        assertEquals("null", output.toString());
    }

    @Test
    void usesSuppliedFactoryConfiguration() {
        JsonFactory factory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
        Document value = (Document) new Jackson3Binder(factory)
                .readNode("/* configured parser */ {\"id\":7}", Document.class);

        assertEquals(7, value.id);
    }

    @Test
    void writeNodeFlushesWithoutClosingCallerWriter() {
        TrackingWriter output = new TrackingWriter();

        new Jackson3Binder(new JsonFactory()).writeNode(output, document());

        assertFalse(output.closed);
        assertTrue(output.toString().contains("\"id\":7"));
    }

    @Test
    void usesNativeFactoryOverloadsForStringAndOutput() throws Exception {
        TrackingFactory factory = new TrackingFactory();
        Jackson3Binder binder = new Jackson3Binder(factory);

        try (Jackson3Reader reader = binder.createReader("null")) {
            reader.nextNull();
        }
        assertEquals("string", factory.parserSource);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Jackson3Writer writer = binder.createWriter(output)) {
            writer.writeStringValue("héllo");
        }
        assertTrue(factory.generatorOutput);
        assertEquals("\"héllo\"", new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void byteAndStreamInputRemainUtf8() throws Exception {
        Jackson3Binder binder = new Jackson3Binder(new JsonFactory());
        byte[] utf8 = "{\"title\":\"héllo\"}".getBytes(StandardCharsets.UTF_8);

        Document fromBytes = (Document) binder.readNode(utf8, Document.class);
        Document fromStream = (Document) binder.readNode(new ByteArrayInputStream(utf8), Document.class);

        assertEquals("héllo", fromBytes.title);
        assertEquals("héllo", fromStream.title);
        try (Jackson3Reader reader = binder.createReader("\uFEFFnull".getBytes(StandardCharsets.UTF_16LE))) {
            assertThrows(Exception.class, reader::nextNull);
        }
    }

    @Test
    void wrapsSuppliedJacksonStreamsAndClosesThemWithTheWrapper() throws Exception {
        JsonParser parser = new JsonFactory().createParser("null");
        try (Jackson3Reader reader = new Jackson3Binder(new JsonFactory()).createReader(parser)) {
            reader.nextNull();
        }
        assertTrue(parser.isClosed());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(output);
        try (Jackson3Writer writer = new Jackson3Binder(new JsonFactory()).createWriter(generator)) {
            writer.writeNull();
        }
        assertTrue(generator.isClosed());
        assertEquals("null", new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNullDependenciesAndIo() {
        assertThrows(NullPointerException.class, () -> new Jackson3Binder((JsonFactory) null));
        assertThrows(NullPointerException.class, () -> new Jackson3Binder(new JsonFactory(), null));

        Jackson3Binder binder = new Jackson3Binder(new JsonFactory());
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
        boolean generatorOutput;

        TrackingFactory() {
            super(JsonFactory.builder()
                    .disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
                    .build());
        }

        @Override
        public JsonParser createParser(ObjectReadContext context, String input) {
            parserSource = "string";
            return super.createParser(context, input);
        }

        @Override
        public JsonGenerator createGenerator(ObjectWriteContext context, OutputStream output) {
            generatorOutput = true;
            return super.createGenerator(context, output);
        }
    }
}
