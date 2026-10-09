package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.exception.BindingException;

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
import java.util.UUID;

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
    void bindsCurrentAndParentOneOfFields() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());

        CurrentOneOf current = (CurrentOneOf) binder.readNode(
                "{\"pet\":{\"kind\":\"dog\",\"barks\":true}}", CurrentOneOf.class);
        ParentOneOf parent = (ParentOneOf) binder.readNode(
                "{\"pet\":{\"barks\":true},\"kind\":\"dog\"}", ParentOneOf.class);

        assertTrue(assertInstanceOf(Dog.class, current.pet).barks);
        assertTrue(assertInstanceOf(Dog.class, parent.pet).barks);
    }

    @Test
    void rejectsDuplicateCreatorProperties() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());

        assertThrows(BindingException.class, () -> binder.readNode(
                "{\"name\":\"first\",\"name\":\"second\"}", CreatorValue.class));
    }

    @Test
    void rejectsMismatchedJsonContainerShapes() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());

        assertThrows(BindingException.class, () -> binder.readNode("[]", JsonObject.class));
        assertThrows(BindingException.class, () -> binder.readNode("{}", JsonArray.class));
    }

    @Test
    void writesPojoWithNestedCollectionsAndMapsAndHonorsNullContext() {
        Document value = document();

        String includingNulls = new Jackson2Binder(new JsonFactory())
                .writeNodeAsString(value);
        String omittingNulls = new Jackson2Binder(new JsonFactory(), new RuntimeContext(false))
                .writeNodeAsString(value);

        assertTrue(includingNulls.contains("\"nullable\":null"));
        assertTrue(includingNulls.contains("\"related\":{\"first\":{\"active\":false}}"));
        assertTrue(!omittingNulls.contains("\"nullable\""));
    }

    @Test
    void writesLargeUtf8NodeAsBytes() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        Document value = document();
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 3_000; i++) {
            title.append("é💡");
        }
        value.title = title.toString();

        byte[] bytes = binder.writeNodeAsBytes(value);
        Document roundTripped = (Document) binder.readNode(bytes, Document.class);

        assertTrue(bytes.length > 8_000);
        assertEquals(value.title, roundTripped.title);
    }

    @Test
    void retainsSuppliedContextAndCreatesJacksonReadersAndWriters() throws Exception {
        RuntimeContext context = new RuntimeContext(false);
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
            assertTrue(reader.nextIfNull());
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
            assertThrows(Exception.class, reader::nextIfNull);
        }
    }

    @Test
    void wrapsSuppliedJacksonStreamsAndClosesThemWithTheWrapper() throws Exception {
        JsonParser parser = new JsonFactory().createParser("null");
        try (Jackson2Reader reader = new Jackson2Binder(new JsonFactory()).createReader(parser)) {
            assertTrue(reader.nextIfNull());
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

    @Test
    void streamsJojoDeclaredAndDynamicMembersWithoutIntermediateObjectConversion() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        String json = "{\"extraBefore\":{\"nested\":[1,true]},\"display_name\":\"Ada\","
                + "\"id\":7,\"child\":{\"active\":true},\"extraAfter\":null}";

        MixedJojo jojo = (MixedJojo) binder.readNode(json, MixedJojo.class);

        assertEquals("Ada", jojo.name);
        assertEquals(7, jojo.id);
        assertTrue(jojo.child.active);
        Map<?, ?> nested = assertInstanceOf(Map.class, jojo.getNode("extraBefore"));
        assertEquals(1, ((Number) ((List<?>) nested.get("nested")).get(0)).intValue());
        assertEquals(true, ((List<?>) nested.get("nested")).get(1));
        assertTrue(jojo.containsKey("extraAfter"));
        assertNull(jojo.getNode("extraAfter"));
        assertEquals(2, jojo.dynamicProperties().size());

        // A nested read must leave the parent's parser cursor on the next member.
        MixedJojo second = (MixedJojo) binder.readNode(
                "{\"child\":{\"active\":false},\"id\":8,\"extra\":[1,2]}", MixedJojo.class);
        assertEquals(8, second.id);
        assertFalse(second.child.active);
        assertEquals(2, ((List<?>) second.getNode("extra")).size());
    }

    @Test
    void jojoStreamingHonorsDisabledDynamicReadsAndNullRoot() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        StaticOnlyJojo result = (StaticOnlyJojo) binder.readNode(
                "{\"unknown\":{\"nested\":[1,2]},\"id\":42}", StaticOnlyJojo.class);

        assertEquals(42, result.id);
        assertTrue(result.dynamicProperties().isEmpty());
        assertNull(binder.readNode("null", MixedJojo.class));
    }

    @Test
    void jojoMatcherResolvesAliasesAndPreservesUnknownNames() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        MixedJojo jojo = (MixedJojo) binder.readNode(
                "{\"legacy_name\":\"Alias\",\"unknown\":7,\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\"}",
                MixedJojo.class);

        assertEquals("Alias", jojo.name);
        assertEquals(7, ((Number) jojo.getNode("unknown")).intValue());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), jojo.uuid);
        assertFalse(jojo.dynamicProperties().containsKey("legacy_name"));
    }

    @Test
    void jojoReadOnlyDeclaredNameDoesNotBecomeDynamic() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        ReadOnlyJojo jojo = (ReadOnlyJojo) binder.readNode(
                "{\"fixed\":\"attempted\",\"extra\":123}", ReadOnlyJojo.class);

        assertEquals("preset", jojo.getNode("fixed"));
        assertFalse(jojo.dynamicProperties().containsKey("fixed"));
        assertEquals(123, ((Number) jojo.getNode("extra")).intValue());
    }

    @Test
    void jojoCreatorRetainsDynamicMembersBeforeConstruction() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        CreatedJojo jojo = (CreatedJojo) binder.readNode(
                "{\"before\":1,\"name\":\"Ada\",\"after\":[2,3]}", CreatedJojo.class);

        assertEquals("Ada", jojo.getName());
        assertEquals(1, ((Number) jojo.getNode("before")).intValue());
        assertEquals(2, ((List<?>) jojo.getNode("after")).size());
    }

    @Test
    void jojoCurrentAndParentOneOfStaySupported() {
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        CurrentJojo current = (CurrentJojo) binder.readNode(
                "{\"extra\":9,\"pet\":{\"kind\":\"dog\",\"barks\":true}}", CurrentJojo.class);
        assertTrue(assertInstanceOf(Dog.class, current.pet).barks);
        assertEquals(9, ((Number) current.getNode("extra")).intValue());

        ParentJojo after = (ParentJojo) binder.readNode(
                "{\"pet\":{\"barks\":true},\"extra\":[1],\"kind\":\"dog\"}",
                ParentJojo.class);
        assertTrue(assertInstanceOf(Dog.class, after.pet).barks);
        assertEquals(1, ((List<?>) after.getNode("extra")).size());

        ParentJojo before = (ParentJojo) binder.readNode(
                "{\"kind\":\"dog\",\"pet\":{\"barks\":true}}",
                ParentJojo.class);
        assertTrue(assertInstanceOf(Dog.class, before.pet).barks);
    }

    static class ReadOnlyJojo extends JsonObject {
        public String getFixed() {
            return "preset";
        }
    }

    static class CreatedJojo extends JsonObject {
        private final String name;

        @NodeCreator
        CreatedJojo(@NodeProperty("name") String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static class CurrentJojo extends JsonObject {
        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")}, key = "kind")
        public Animal pet;
    }

    static class ParentJojo extends JsonObject {
        public String kind;

        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")},
                key = "kind", scope = OneOf.Scope.PARENT)
        public Animal pet;
    }

    static class MixedJojo extends JsonObject {
        public int id;

        @NodeProperty(value = "display_name", aliases = {"legacy_name"})
        public String name;

        public Details child;
        public UUID uuid;
    }

    @NodeObject(readDynamic = false)
    static class StaticOnlyJojo extends JsonObject {
        public int id;
    }

    @Test
    void matchesRecordCreatorArgumentsAndAliasesWithoutFieldMapLookups() {
        CreatorRecord value = (CreatorRecord) new Jackson2Binder(new JsonFactory()).readNode(
                "{\"unknown\":{\"nested\":[1,2]},\"age\":7,\"legacy_name\":\"Ada\"}",
                CreatorRecord.class);

        assertEquals(new CreatorRecord("Ada", 7), value);
        assertThrows(BindingException.class, () -> new Jackson2Binder(new JsonFactory()).readNode(
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

    static class CurrentOneOf {
        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")}, key = "kind")
        public Animal pet;
    }

    static class ParentOneOf {
        public String kind;

        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")},
                key = "kind", scope = OneOf.Scope.PARENT)
        public Animal pet;
    }

    static class Animal {
    }

    static class Dog extends Animal {
        public boolean barks;
    }

    static class CreatorValue {
        final String name;

        @NodeCreator
        CreatorValue(@NodeProperty("name") String name) {
            this.name = name;
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
