package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.sjf4j.InternalAccess;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
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
    void readsRawNodesWithFastjson2NativeContainers() throws Exception {
        Fastjson2Parser parser = new Fastjson2Parser(JSONReader.of(
                "{\"text\":\"Ada\",\"number\":7,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}"));
        try {

            assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
            Map<?, ?> value = (Map<?, ?>) parser.readRaw();

            assertEquals(com.alibaba.fastjson2.JSONObject.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals("Ada", value.get("text"));
            assertEquals(7, value.get("number"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(com.alibaba.fastjson2.JSONObject.class, value.get("nested").getClass());
            assertEquals(com.alibaba.fastjson2.JSONArray.class, value.get("items").getClass());
            assertEquals(com.alibaba.fastjson2.JSONObject.class, ((List<?>) value.get("items")).get(1).getClass());
            assertEquals(StreamingParser.Token.EOF, parser.peekToken());
            parser.end();
        } finally {
            parser.close();
        }
    }

    @Test
    void typedRawContainerEntryPointsPreservePeekAndNullSemantics() throws Exception {
        Fastjson2Parser parser = new Fastjson2Parser(JSONReader.of(
                "[{\"id\":7},[null,{\"active\":true}],null,null]"));
        try {
            parser.readArrayStart();
            assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
            Map<String, Object> object = parser.readRawObject();
            assertEquals(com.alibaba.fastjson2.JSONObject.class, object.getClass());
            assertEquals(7, object.get("id"));
            assertEquals(StreamingParser.Token.ARRAY_START, parser.peekToken());
            List<Object> array = parser.readRawArray();
            assertEquals(com.alibaba.fastjson2.JSONArray.class, array.getClass());
            assertEquals(com.alibaba.fastjson2.JSONObject.class, array.get(1).getClass());
            assertNull(parser.readRawObject());
            assertNull(parser.readRawArray());
            parser.readArrayEnd();
            parser.end();
        } finally {
            parser.close();
        }
        Fastjson2Parser mismatch = new Fastjson2Parser(JSONReader.of("[]"));
        try {
            assertThrows(BindingException.class, mismatch::readRawObject);
        } finally {
            mismatch.close();
        }
    }

    @Test
    void jsonNodesAdoptNativeRawContainersWithoutCopy() {
        Fastjson2Binder binder = new Fastjson2Binder();
        JsonObject object = (JsonObject) binder.readNode(
                "{\"id\":7,\"nested\":[1,{\"ok\":true}]}", JsonObject.class);
        JsonArray array = (JsonArray) binder.readNode(
                "[1,{\"name\":\"Ada\"}]", JsonArray.class);

        assertEquals(com.alibaba.fastjson2.JSONObject.class,
                InternalAccess.dynamicMembers(object).getClass());
        assertEquals(com.alibaba.fastjson2.JSONArray.class,
                InternalAccess.dynamicElements(array).getClass());
        assertEquals(7, object.get("id"));
        assertEquals(2, array.size());
    }

    @Test
    void nativeRawContainersMatchTheSjf4jObjectGraph() throws Exception {
        String json = "{\"small\":7,\"large\":9876543210,\"fraction\":98.75,"
                + "\"nested\":{\"enabled\":true,\"values\":[null,1,{\"name\":\"Ada\"}]}}";
        Fastjson2Binder binder = new Fastjson2Binder();
        Map<?, ?> expected = (Map<?, ?>) binder.readNode(json, Map.class);

        // Backend-native JSONObject/JSONArray are legitimate Map/List nodes.
        // Their concrete class is deliberately NOT forced to match SJF4J's
        // LinkedHashMap/ArrayList representation.
        JSONReader.Context nativeContext =
                JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
        try (JSONReader nativeReader = JSONReader.of(json, nativeContext)) {
            Map<?, ?> nativeObject = nativeReader.readObject();
            assertEquals(expected, nativeObject);
            assertEquals(com.alibaba.fastjson2.JSONObject.class, nativeObject.getClass());
            Map<?, ?> nested = (Map<?, ?>) nativeObject.get("nested");
            assertEquals(com.alibaba.fastjson2.JSONObject.class, nested.getClass());
            assertEquals(com.alibaba.fastjson2.JSONArray.class, nested.get("values").getClass());
            assertTrue(nativeReader.isEnd());
        }

        JSONReader.Context context = JSONFactory.createReadContext();
        context.config(JSONReader.Feature.UseDoubleForDecimals);
        context.setObjectSupplier(LinkedHashMap::new);
        context.setArraySupplier(ArrayList::new);
        try (JSONReader nativeReader = JSONReader.of(json, context)) {
            Map<?, ?> actual = nativeReader.readObject();

            assertTrue(nativeReader.isEnd());
            assertEquals(expected, actual);
            assertEquals(LinkedHashMap.class, actual.getClass());
            assertEquals(LinkedHashMap.class, actual.get("nested").getClass());
            Map<?, ?> nested = (Map<?, ?>) actual.get("nested");
            assertEquals(ArrayList.class, nested.get("values").getClass());
            assertEquals(LinkedHashMap.class, ((List<?>) nested.get("values")).get(2).getClass());
            assertEquals(expected.get("fraction").getClass(), actual.get("fraction").getClass());
            assertEquals(expected.get("large").getClass(), actual.get("large").getClass());
        }
    }

    @Test
    void rawNodeReportsEofUsingStreamingTokenSemantics() {
        Fastjson2Parser parser = new Fastjson2Parser(JSONReader.of(""));
        try {
            BindingException error = assertThrows(BindingException.class, parser::readRaw);
            assertEquals("unexpected token 'EOF'", error.getMessage());
        } finally {
            parser.close();
        }
    }

    @Test
    void rawNodeConsumesOneValueAndRefreshesPeekState() throws Exception {
        Fastjson2Parser parser = new Fastjson2Parser(JSONReader.of("[{\"id\":7},\"next\"]"));
        try {
            parser.readArrayStart();
            assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
            assertEquals(7, ((Map<?, ?>) parser.readRaw()).get("id"));
            assertEquals(StreamingParser.Token.STRING, parser.peekToken());
            assertEquals("next", parser.readString());
            parser.readArrayEnd();
        } finally {
            parser.close();
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
    void writesCompiledNamesAndFallsBackForEscapedOrSingleQuotedNames() {
        assertEquals("{\"value\":1}", new Fastjson2Binder().writeNodeAsString(new NamedValue()));
        assertEquals("{\"a\\\"b\":1}", new Fastjson2Binder().writeNodeAsString(new EscapedNameValue()));

        Fastjson2Binder singleQuotes = new Fastjson2Binder(JSONFactory.createReadContext(),
                JSONFactory.createWriteContext(JSONWriter.Feature.UseSingleQuotes));
        assertEquals("{'value':1}", singleQuotes.writeNodeAsString(new NamedValue()));

        Fastjson2Binder unquoted = new Fastjson2Binder(JSONFactory.createReadContext(),
                JSONFactory.createWriteContext(JSONWriter.Feature.UnquoteFieldName));
        assertEquals("{value:1}", unquoted.writeNodeAsString(new NamedValue()));
    }

    @Test
    void cachedPackedNamesMatchOrdinaryNamesInBothEncodings() throws Exception {
        List<String> names = new ArrayList<>();
        for (int size = 1; size <= 17; size++) {
            char[] letters = new char[size];
            Arrays.fill(letters, 'a');
            names.add(new String(letters));
        }
        names.addAll(Arrays.asList("", "a\"b", "a\\b", "名字", "é", "line\nfeed"));

        for (String name : names) {
            Fastjson2CompiledName compiled = new Fastjson2CompiledName(name);
            boolean eligible = name.length() >= 2 && name.length() <= 16
                    && name.chars().allMatch(ch -> ch >= 32 && ch <= 126
                            && ch != '"' && ch != '\\');
            assertEquals(eligible ? name.length() : 0, compiled.rawNameLength, name);
            for (boolean utf8 : new boolean[]{false, true}) {
                assertEquals(writePreparedName(name, utf8, false),
                        writePreparedName(name, utf8, true), name);
            }
        }
    }

    @Test
    void cachedPackedNamesRespectNonRawWriterFeatures() throws Exception {
        for (JSONWriter.Feature feature : Arrays.asList(
                JSONWriter.Feature.UseSingleQuotes,
                JSONWriter.Feature.UnquoteFieldName,
                JSONWriter.Feature.BrowserSecure,
                JSONWriter.Feature.EscapeNoneAscii)) {
            for (boolean utf8 : new boolean[]{false, true}) {
                assertEquals(writePreparedName("property", utf8, false, feature),
                        writePreparedName("property", utf8, true, feature), feature.name());
            }
        }
    }

    private static String writePreparedName(String name, boolean utf8, boolean prepared,
            JSONWriter.Feature... features) throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder(
                JSONFactory.createReadContext(), JSONFactory.createWriteContext(features));
        {
            Fastjson2Generator generator = utf8 ? binder.createGeneratorUtf8() : binder.createGenerator();
            try {
                generator.writeObjectStart();
                if (prepared) generator.writeName(generator.createCompiledName(name));
                else generator.writeName(name);
                generator.writeString("héllo");
                // Also exercise the second-property separator after the cached name.
                if (prepared) generator.writeName(generator.createCompiledName("tail"));
                else generator.writeName("tail");
                generator.writeNull();
                generator.writeObjectEnd();
                return utf8 ? new String(generator.toBytes(), StandardCharsets.UTF_8) : generator.toString();

            } finally {
                generator.close();
            }
        }
    }

    @Test
    void nativeWritersShareRawNameFeatureContract() {
        Fastjson2Binder plain = new Fastjson2Binder();
        try (JSONWriter generator = plain.createNativeWriter()) {
            assertTrue(Fastjson2Binder.supportsRawUtf16Names(generator));
            generator.startObject();
            generator.writeName("text");
            generator.writeColon();
            generator.writeString("héllo");
            generator.endObject();
            assertEquals("{\"text\":\"héllo\"}", generator.toString());
        }
        try (JSONWriter generator = plain.createNativeWriterUtf8()) {
            assertTrue(Fastjson2Binder.supportsRawUtf8Names(generator));
            generator.startObject();
            generator.writeName("text");
            generator.writeColon();
            generator.writeString("héllo");
            generator.endObject();
            assertEquals("{\"text\":\"héllo\"}",
                    new String(generator.getBytes(), StandardCharsets.UTF_8));
        }

        for (JSONWriter.Feature feature : Arrays.asList(
                JSONWriter.Feature.UseSingleQuotes,
                JSONWriter.Feature.UnquoteFieldName,
                JSONWriter.Feature.BrowserSecure,
                JSONWriter.Feature.EscapeNoneAscii)) {
            Fastjson2Binder configured = new Fastjson2Binder(
                    JSONFactory.createReadContext(),
                    JSONFactory.createWriteContext(feature));
            try (JSONWriter generator = configured.createNativeWriter()) {
                assertFalse(Fastjson2Binder.supportsRawUtf16Names(generator), feature.name());
            }
            try (JSONWriter generator = configured.createNativeWriterUtf8()) {
                assertFalse(Fastjson2Binder.supportsRawUtf8Names(generator), feature.name());
            }
        }
    }

    @Test
    void createsNativeReadersWritersAndFlushesToSuppliedOutputs() throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        StringWriter text = new StringWriter();

        {
            Fastjson2Parser parser = binder.createParser(new StringReader("null"));
            try {
                assertInstanceOf(Fastjson2Parser.class, parser);
                assertTrue(parser.nextIfNull());

            } finally {
                parser.close();
            }
        }
        Fastjson2Generator generator = binder.createGenerator(text);
        generator.writeStringValue("héllo");
        generator.end();
        assertEquals("", text.toString());
        generator.close();
        assertEquals("\"héllo\"", text.toString());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        {
            Fastjson2Generator byteWriter = binder.createGenerator(bytes);
            try {
                byteWriter.writeStringValue("héllo");
                byteWriter.end();

            } finally {
                byteWriter.close();
            }
        }
        assertEquals("\"héllo\"", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void wrapsSuppliedNativeStreamsAndRejectsNullDependencies() throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        Fastjson2Parser parser = binder.createParser(JSONReader.of("null"));
        try {
            assertTrue(parser.nextIfNull());
        } finally {
            parser.close();
        }
        assertThrows(NullPointerException.class, () -> new Fastjson2Binder(null, JSONFactory.createWriteContext()));
        assertThrows(NullPointerException.class, () -> new Fastjson2Binder(JSONFactory.createReadContext(), null));
        assertThrows(NullPointerException.class, () -> binder.createParser((StringReader) null));
        assertThrows(NullPointerException.class, () -> binder.createGenerator((StringWriter) null));
        assertThrows(NullPointerException.class, () -> binder.createParser((JSONReader) null));
        assertThrows(NullPointerException.class, () -> binder.createGenerator((JSONWriter) null));
    }

    @Test
    void closingWriterDoesNotCloseCallerOwnedOutput() throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        TrackingWriter text = new TrackingWriter();
        TrackingOutputStream bytes = new TrackingOutputStream();

        {
            Fastjson2Generator generator = binder.createGenerator(text);
            try {
                generator.start();
                generator.writeNull();
                generator.end();
            } finally {
                generator.close();
            }
        }
        {
            Fastjson2Generator generator = binder.createGenerator(bytes);
            try {
                generator.start();
                generator.writeNull();
                generator.end();
            } finally {
                generator.close();
            }
        }

        assertFalse(text.closed);
        assertFalse(bytes.closed);
        assertEquals("null", text.toString());
        assertEquals("null", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void jojoUsesHashedMatcherForDeclaredAndUnknownProperties() {
        Fastjson2Binder binder = new Fastjson2Binder();
        MixedJojo result = (MixedJojo) binder.readNode(
                "{\"unknown_before\":{\"flag\":true},\"legacy_name\":\"Ada\","
                        + "\"id\":17,\"unknown_after\":[1,null]}", MixedJojo.class);

        assertEquals(17, result.id);
        assertEquals("Ada", result.name);
        assertEquals(true, ((Map<?, ?>) result.getNode("unknown_before")).get("flag"));
        assertEquals(2, ((List<?>) result.getNode("unknown_after")).size());
        assertFalse(result.dynamicMembers().containsKey("legacy_name"));
    }

    @Test
    void readsPureDynamicJojoWithEmptyStaticMatcher() {
        PureDynamicJojo jojo = (PureDynamicJojo) new Fastjson2Binder().readNode(
                "{\"escaped\\\"key\":1,\"obj\":{\"ok\":true},\"items\":[2,3]}", PureDynamicJojo.class);
        assertEquals(1, ((Number) jojo.getNode("escaped\"key")).intValue());
        assertEquals(true, ((Map<?, ?>) jojo.getNode("obj")).get("ok"));
        assertEquals(2, ((List<?>) jojo.getNode("items")).size());
    }

    static class PureDynamicJojo extends org.sjf4j.JsonObject {
    }

    static class MixedJojo extends org.sjf4j.JsonObject {
        public int id;

        @NodeProperty(value = "name", aliases = "legacy_name")
        public String name;
    }


    @Test
    void parserReleasePreservesCallerOwnedReaderAndInputStream() {
        Fastjson2Binder binder = new Fastjson2Binder();
        boolean[] readerClosed = {false};
        StringReader source = new StringReader("{\"id\":7}") {
            @Override
            public void close() {
                readerClosed[0] = true;
                super.close();
            }
        };
        assertEquals(7, ((Map<?, ?>) binder.readNode(source, Map.class)).get("id"));
        assertFalse(readerClosed[0]);

        boolean[] streamClosed = {false};
        ByteArrayInputStream stream = new ByteArrayInputStream(
                "{\"id\":7}".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                streamClosed[0] = true;
                super.close();
            }
        };
        assertEquals(7, ((Map<?, ?>) binder.readNode(stream, Map.class)).get("id"));
        assertFalse(streamClosed[0]);
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

    static class NamedValue {
        public int value = 1;
    }

    static class EscapedNameValue {
        @NodeProperty("a\"b")
        public int value = 1;
    }
}
