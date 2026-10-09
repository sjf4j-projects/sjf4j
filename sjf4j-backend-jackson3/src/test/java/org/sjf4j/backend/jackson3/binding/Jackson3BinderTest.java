package org.sjf4j.backend.jackson3.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
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
    void isDiscoveredAsTheDefaultJsonBinderProvider() {
        BinderProvider provider = BinderFactory.provider(Format.JSON);

        assertInstanceOf(Jackson3BinderProvider.class, provider);
        assertInstanceOf(Jackson3Binder.class, provider.create(RuntimeContext.EMPTY));
    }

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
        String omittingNulls = new Jackson3Binder(new JsonFactory(), new RuntimeContext(false))
                .writeNodeAsString(value);

        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertTrue(includingNulls.contains("\"related\":{\"first\":{\"active\":false}}"));
        assertTrue(!omittingNulls.contains("\"nullable\""));
    }

    @Test
    void retainsSuppliedContextAndCreatesJacksonReadersAndWriters() throws Exception {
        RuntimeContext context = new RuntimeContext(false);
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
    void closesBinderCreatedGeneratorsWithoutClosingCallerTargets() throws Exception {
        Jackson3Binder binder = new Jackson3Binder(new JsonFactory());
        TrackingWriter writerOutput = new TrackingWriter();
        TrackingOutputStream streamOutput = new TrackingOutputStream();

        try (Jackson3Writer writer = binder.createWriter(writerOutput)) {
            writer.writeNull();
        }
        try (Jackson3Writer writer = binder.createWriter(streamOutput)) {
            writer.writeNull();
        }

        assertFalse(writerOutput.closed);
        assertFalse(streamOutput.closed);
        assertEquals("null", writerOutput.toString());
        assertEquals("null", new String(streamOutput.toByteArray(), StandardCharsets.UTF_8));
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
    void byteAndStreamInputRemainUtf8() throws Exception {
        Jackson3Binder binder = new Jackson3Binder(new JsonFactory());
        byte[] utf8 = "{\"title\":\"héllo\"}".getBytes(StandardCharsets.UTF_8);

        Document fromBytes = (Document) binder.readNode(utf8, Document.class);
        Document fromStream = (Document) binder.readNode(new ByteArrayInputStream(utf8), Document.class);

        assertEquals("héllo", fromBytes.title);
        assertEquals("héllo", fromStream.title);
        assertTrue(new String(binder.writeNodeAsBytes(document()), StandardCharsets.UTF_8).contains("\"id\":7"));
        try (Jackson3Reader reader = binder.createReader("\uFEFFnull".getBytes(StandardCharsets.UTF_16LE))) {
            assertThrows(Exception.class, reader::nextIfNull);
        }
    }

    @Test
    void retainsLargeByteOutputAfterRecyclerReuse() {
        Jackson3Binder binder = new Jackson3Binder(new JsonFactory());
        String title = "x".repeat(100_000);
        Document value = new Document(7, title, new Details(true), Arrays.asList("one"),
                new LinkedHashMap<>(), null);

        byte[] bytes = binder.writeNodeAsBytes(value);
        for (int i = 0; i < 4; i++) {
            binder.writeNodeAsBytes(document());
        }
        Document roundTrip = (Document) binder.readNode(bytes, Document.class);

        assertEquals(title, roundTrip.title);
    }

    @Test
    void wrapsSuppliedJacksonStreamsAndClosesThemWithTheWrapper() throws Exception {
        JsonParser parser = new JsonFactory().createParser(ObjectReadContext.empty(), "null");
        try (Jackson3Reader reader = new Jackson3Binder(new JsonFactory()).createReader(parser)) {
            assertTrue(reader.nextIfNull());
        }
        assertTrue(parser.isClosed());

        TrackingOutputStream output = new TrackingOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(ObjectWriteContext.empty(), output);
        try (Jackson3Writer writer = new Jackson3Binder(new JsonFactory()).createWriter(generator)) {
            writer.writeNull();
        }
        assertTrue(generator.isClosed());
        assertTrue(output.closed);
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

    @Test
    void matchesRecordCreatorArgumentsAndAliasesWithoutFieldMapLookups() {
        CreatorRecord value = (CreatorRecord) new Jackson3Binder(new JsonFactory()).readNode(
                "{\"unknown\":{\"nested\":[1,2]},\"age\":7,\"legacy_name\":\"Ada\"}",
                CreatorRecord.class);

        assertEquals(new CreatorRecord("Ada", 7), value);
        assertThrows(BindingException.class, () -> new Jackson3Binder(new JsonFactory()).readNode(
                "{\"name\":\"first\",\"legacy_name\":\"duplicate\",\"age\":7}",
                CreatorRecord.class));
    }

    record CreatorRecord(
            @org.sjf4j.annotation.node.NodeProperty(value = "name", aliases = "legacy_name") String name,
            int age) {
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

    @Test
    void jojoUsesNativeNameMatcherForDeclaredAndUnknownProperties() {
        Jackson3Binder binder = new Jackson3Binder(new JsonFactory());
        MixedJojo result = (MixedJojo) binder.readNode(
                "{\"unknown_before\":{\"flag\":true},\"legacy_name\":\"Ada\","
                        + "\"id\":17,\"unknown_after\":[1,null]}", MixedJojo.class);

        assertEquals(17, result.id);
        assertEquals("Ada", result.name);
        assertEquals(true, ((Map<?, ?>) result.getNode("unknown_before")).get("flag"));
        assertEquals(2, ((List<?>) result.getNode("unknown_after")).size());
        assertFalse(result.dynamicProperties().containsKey("legacy_name"));
    }

    @Test
    void readsPureDynamicJojoWithEmptyStaticMatcher() {
        PureDynamicJojo jojo = (PureDynamicJojo) new Jackson3Binder(new JsonFactory()).readNode(
                "{\"escaped\\\"key\":1,\"obj\":{\"ok\":true},\"items\":[2,3]}", PureDynamicJojo.class);
        assertEquals(1, ((Number) jojo.getNode("escaped\"key")).intValue());
        assertEquals(true, ((Map<?, ?>) jojo.getNode("obj")).get("ok"));
        assertEquals(2, ((List<?>) jojo.getNode("items")).size());
    }

    static class PureDynamicJojo extends org.sjf4j.JsonObject {
    }

    static class MixedJojo extends org.sjf4j.JsonObject {
        public int id;

        @org.sjf4j.annotation.node.NodeProperty(value = "name", aliases = "legacy_name")
        public String name;
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
        public void close() throws java.io.IOException {
            closed = true;
            super.close();
        }
    }

}
