package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeReaderTest {

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        try (SnakeReader reader = reader("id: null\n")) {
            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfArrayStart());
            reader.startObject();
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfObjectEnd());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void matchesNamesThroughTheCoreFallbackAndSkipsStructures() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new NameMatcher(pojoInfo.writableProperties);

        try (SnakeReader reader = reader("unknown: [1, {nested: true}]\nlegacyName: Ada\nid: 7\n")) {
            reader.startObject();
            assertEquals(NameMatcher.UNKNOWN, reader.nextNameMatch(matcher));
            reader.skipNode();
            assertEquals(matcher.fallback("name"), reader.nextNameMatch(matcher));
            assertEquals("Ada", reader.readString());
            assertEquals(matcher.fallback("id"), reader.nextNameMatch(matcher));
            assertEquals(7, reader.readIntValue());
            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endDocument();
        }
    }

    @Test
    void readsYamlScalarsWithCoreNumericAndCharacterSemantics() throws Exception {
        String yaml = "[+1, 1_000.5_0, 128, 1.25e2, 123456789012345678901234567890, "
                + "1.20e-3, x, null, '', xy]";
        try (SnakeReader reader = reader(yaml)) {
            reader.startArray();
            assertEquals(1, reader.readIntValue());
            assertEquals(1000.5d, reader.readDoubleValue());
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertEquals(125d, reader.readDoubleValue());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.readBigInteger());
            assertEquals(new BigDecimal("0.0012"), reader.readBigDecimal());
            assertEquals('x', reader.readCharValue());
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void readsBoxedValuesAndNulls() throws Exception {
        try (SnakeReader reader = reader("[null, 1, null, true, null, x]")) {
            reader.startArray();
            assertNull(reader.readInt());
            assertEquals(1L, reader.readLong());
            assertNull(reader.readBoolean());
            assertEquals(true, reader.readBoolean());
            assertNull(reader.readChar());
            assertEquals(Character.valueOf('x'), reader.readChar());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void preservesYamlStringQuotingAndExplicitTags() throws Exception {
        try (SnakeReader reader = reader("[\"null\", 'true', \"123\", !!str null, !!int \"12\", !!float \"1.5\"]")) {
            reader.startArray();
            assertEquals("null", reader.readString());
            assertEquals("true", reader.readString());
            assertEquals("123", reader.readString());
            assertEquals("null", reader.readString());
            assertEquals(12, reader.readIntValue());
            assertEquals(1.5d, reader.readDoubleValue());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void readsRawNodesInInsertionOrder() throws Exception {
        try (SnakeReader reader = reader("text: Ada\nnumber: 7\nenabled: true\nempty: null\nnested: {first: one}\nitems: [false, {second: 2}]\n")) {
            Map<?, ?> value = (Map<?, ?>) reader.readRawNode();
            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                    new ArrayList<>(value.keySet()));
            assertEquals("Ada", value.get("text"));
            assertEquals(7, value.get("number"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void skipsAndReadsDeeplyNestedNodesWithoutRecursion() throws Exception {
        String nested = nestedContainers(2_000);
        LoaderOptions options = new LoaderOptions();
        options.setNestingDepthLimit(2_100);
        SnakeBinder binder = new SnakeBinder(options, new DumperOptions(), org.sjf4j.RuntimeContext.EMPTY);
        try (SnakeReader reader = reader(binder, "unknown: " + nested + "\nid: 7\n")) {
            reader.startObject();
            assertEquals("unknown", reader.nextName());
            reader.skipNode();
            assertEquals("id", reader.nextName());
            assertEquals(7, reader.readIntValue());
            reader.endObject();
            reader.endDocument();
        }

        try (SnakeReader reader = reader(binder, nested)) {
            Object value = reader.readRawNode();
            for (int i = 0; i < 2_000; i++) {
                if ((i & 1) == 0) {
                    assertEquals(ArrayList.class, value.getClass());
                    value = ((List<?>) value).get(0);
                } else {
                    assertEquals(LinkedHashMap.class, value.getClass());
                    value = ((Map<?, ?>) value).get("value");
                }
            }
            assertEquals(0, value);
            reader.endDocument();
        }
    }

    @Test
    void rejectsNonJsonYamlConstructsAndMultipleDocuments() throws Exception {
        try (SnakeReader reader = reader("1: value\n")) {
            reader.startObject();
            assertThrows(IOException.class, reader::peekToken);
        }
        try (SnakeReader reader = reader("? [complex]\n: value\n")) {
            reader.startObject();
            assertThrows(IOException.class, reader::nextName);
        }
        try (SnakeReader reader = reader("*value")) {
            assertThrows(IOException.class, reader::peekToken);
        }
        try (SnakeReader reader = reader("--- 1\n--- 2\n")) {
            reader.skipNode();
            assertThrows(IOException.class, reader::endDocument);
        }
        try (SnakeReader reader = reader("value: 2024-01-01\n")) {
            reader.startObject();
            reader.nextName();
            assertThrows(IOException.class, reader::peekToken);
        }
    }

    @Test
    void rejectsNonJsonCollectionTagsAndAnchors() throws Exception {
        assertRejected("!!set {one: null}");
        assertRejected("!!omap [{one: 1}]");

        assertRejected("&value text");
        assertRejected("&value {name: Ada}");
        assertRejected("&value [one]");
    }

    @Test
    void honorsDepthLimitForNormalAndSkippedContainers() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setNestingDepthLimit(2);
        SnakeBinder binder = new SnakeBinder(options, new DumperOptions(), org.sjf4j.RuntimeContext.EMPTY);
        assertThrows(IOException.class, () -> binder.readNode("[[[1]]]", Object.class));
        try (SnakeReader reader = reader(binder, "key: [[[1]]]")) {
            reader.startObject();
            reader.nextName();
            assertThrows(IOException.class, reader::skipNode);
        }
    }

    @Test
    void rejectsDuplicateKeysWhenConfiguredIncludingSkippedObjects() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        SnakeBinder binder = new SnakeBinder(options, new DumperOptions(), org.sjf4j.RuntimeContext.EMPTY);
        assertThrows(RuntimeException.class, () -> binder.readNode("a: 1\\na: 2\\n", Object.class));
        try (SnakeReader reader = reader(binder, "key: {a: 1, a: 2}")) {
            reader.startObject();
            reader.nextName();
            assertThrows(IOException.class, reader::skipNode);
        }
    }

    @Test
    void readsCommentsWhenParserExposesEvents() throws Exception {
        LoaderOptions options = new LoaderOptions().setProcessComments(true);
        SnakeBinder binder = new SnakeBinder(options, new DumperOptions(), org.sjf4j.RuntimeContext.EMPTY);
        assertEquals(1, ((Map<?, ?>) binder.readNode("# before\\na: 1 # after\\n", Object.class)).get("a"));
    }

    @Test
    void readsBigDecimalWithoutRoundingAndRejectsOctal() throws Exception {
        try (SnakeReader reader = reader("[0.12345678901234567890123456789, 1e-10000]")) {
            reader.startArray();
            assertEquals(new BigDecimal("0.12345678901234567890123456789"), reader.readBigDecimal());
            assertEquals(new BigDecimal("1e-10000"), reader.readBigDecimal());
            reader.endArray();
            reader.endDocument();
        }
        for (String yaml : new String[]{"010", "0_10", "!!int 010"}) {
            try (SnakeReader reader = reader(yaml)) {
                assertThrows(IOException.class, reader::readNumber);
            }
        }
    }

    private static void assertRejected(String yaml) throws Exception {
        try (SnakeReader reader = reader(yaml)) {
            assertThrows(IOException.class, reader::peekToken);
        }
    }

    private static String nestedContainers(int depth) {
        StringBuilder yaml = new StringBuilder(depth * 7 + 1);
        for (int i = 0; i < depth; i++) {
            if ((i & 1) == 0) {
                yaml.append('[');
            } else {
                yaml.append("{value: ");
            }
        }
        yaml.append('0');
        for (int i = depth - 1; i >= 0; i--) {
            yaml.append((i & 1) == 0 ? ']' : '}');
        }
        return yaml.toString();
    }

    private static SnakeReader reader(String yaml) throws IOException {
        return reader(new SnakeBinder(), yaml);
    }

    private static SnakeReader reader(SnakeBinder binder, String yaml) throws IOException {
        SnakeReader reader = binder.createReader(yaml);
        reader.startDocument();
        return reader;
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
