package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeRegistry;
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

class SnakeParserTest {

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        {
            SnakeParser parser = parser("id: null\n");
            try {
                assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
                assertFalse(parser.nextIfArrayStart());
                parser.readObjectStart();
                assertEquals(StreamingParser.Token.NAME, parser.peekToken());
                assertEquals("id", parser.nextName());
                assertEquals(StreamingParser.Token.NULL, parser.peekToken());
                assertTrue(parser.nextIfNull());
                assertTrue(parser.nextIfObjectEnd());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void matchesNamesThroughTheCoreFallbackAndSkipsStructures() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new NameMatcher(pojoInfo.settableProperties);

        {
            SnakeParser parser = parser("unknown: [1, {nested: true}]\nlegacyName: Ada\nid: 7\n");
            try {
                parser.readObjectStart();
                assertEquals(NameMatcher.UNKNOWN, parser.nextNameMatch(matcher));
                parser.skipNode();
                assertEquals(matcher.fallback("name"), parser.nextNameMatch(matcher));
                assertEquals("Ada", parser.readString());
                assertEquals(matcher.fallback("id"), parser.nextNameMatch(matcher));
                assertEquals(7, parser.readIntValue());
                assertEquals(NameMatcher.OBJECT_END, parser.nextNameMatch(matcher));
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsYamlScalarsWithCoreNumericAndCharacterSemantics() throws Exception {
        String yaml = "[+1, 1_000.5_0, 128, 1.25e2, 123456789012345678901234567890, "
                + "1.20e-3, x, null, '', xy]";
        {
            SnakeParser parser = parser(yaml);
            try {
                parser.readArrayStart();
                assertEquals(1, parser.readIntValue());
                assertEquals(1000.5d, parser.readDoubleValue());
                assertThrows(ArithmeticException.class, parser::readByteValue);
                assertEquals(125d, parser.readDoubleValue());
                assertEquals(new BigInteger("123456789012345678901234567890"), parser.readBigInteger());
                assertEquals(new BigDecimal("0.00120"), parser.readBigDecimal());
                assertEquals('x', parser.readCharValue());
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsBoxedValuesAndNulls() throws Exception {
        {
            SnakeParser parser = parser("[null, 1, null, true, null, x]");
            try {
                parser.readArrayStart();
                assertNull(parser.readInt());
                assertEquals(1L, parser.readLong());
                assertNull(parser.readBoolean());
                assertEquals(true, parser.readBoolean());
                assertNull(parser.readChar());
                assertEquals(Character.valueOf('x'), parser.readChar());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void preservesYamlStringQuotingAndExplicitTags() throws Exception {
        {
            SnakeParser parser = parser("[\"null\", 'true', \"123\", !!str null, !!int \"12\", !!float \"1.5\"]");
            try {
                parser.readArrayStart();
                assertEquals("null", parser.readString());
                assertEquals("true", parser.readString());
                assertEquals("123", parser.readString());
                assertEquals("null", parser.readString());
                assertEquals(12, parser.readIntValue());
                assertEquals(1.5d, parser.readDoubleValue());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsRawNodesInInsertionOrder() throws Exception {
        {
            SnakeParser parser = parser("text: Ada\nnumber: 7\nenabled: true\nempty: null\nnested: {first: one}\nitems: [false, {second: 2}]\n");
            try {
                Map<?, ?> value = (Map<?, ?>) parser.readRaw();
                assertEquals(LinkedHashMap.class, value.getClass());
                assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                        new ArrayList<>(value.keySet()));
                assertEquals("Ada", value.get("text"));
                assertEquals(7, value.get("number"));
                assertEquals(true, value.get("enabled"));
                assertNull(value.get("empty"));
                assertEquals(LinkedHashMap.class, value.get("nested").getClass());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void skipsAndReadsDeeplyNestedNodesWithoutRecursion() throws Exception {
        String nested = nestedContainers(2_000);
        {
            SnakeParser parser = parser("unknown: " + nested + "\nid: 7\n");
            try {
                parser.readObjectStart();
                assertEquals("unknown", parser.nextName());
                parser.skipNode();
                assertEquals("id", parser.nextName());
                assertEquals(7, parser.readIntValue());
                parser.readObjectEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }

        {
            SnakeParser parser = parser(nested);
            try {
                Object value = parser.readRaw();
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
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void rejectsNonJsonYamlConstructsAndMultipleDocuments() throws Exception {
        {
            SnakeParser parser = parser("1: value\n");
            try {
                parser.readObjectStart();
                assertThrows(IOException.class, parser::peekToken);

            } finally {
                parser.close();
            }
        }
        {
            SnakeParser parser = parser("? [complex]\n: value\n");
            try {
                parser.readObjectStart();
                assertThrows(IOException.class, parser::nextName);

            } finally {
                parser.close();
            }
        }
        {
            SnakeParser parser = parser("*value");
            try {
                assertThrows(IOException.class, parser::peekToken);

            } finally {
                parser.close();
            }
        }
        {
            SnakeParser parser = parser("--- 1\n--- 2\n");
            try {
                parser.skipNode();
                assertThrows(IOException.class, parser::end);

            } finally {
                parser.close();
            }
        }
        {
            SnakeParser parser = parser("value: 2024-01-01\n");
            try {
                parser.readObjectStart();
                parser.nextName();
                assertThrows(IOException.class, parser::peekToken);

            } finally {
                parser.close();
            }
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
    void acceptsDuplicateKeysWithLastValueWinning() throws Exception {
        Map<?, ?> result = (Map<?, ?>) new SnakeBinder().readNode("a: 1\na: 2\n", Object.class);
        assertEquals(2, result.get("a"));
    }

    @Test
    void readsAcrossNestedObjectArrayBoundaries() throws Exception {
        {
            SnakeParser parser = parser("{outer: [{inner: 1}, 2], next: ok}");
            try {
                parser.readObjectStart();
                assertEquals("outer", parser.nextName());
                parser.readArrayStart();
                parser.readObjectStart();
                assertEquals("inner", parser.nextName());
                assertEquals(1, parser.readIntValue());
                assertTrue(parser.nextIfObjectEnd());
                assertEquals(2, parser.readIntValue());
                parser.readArrayEnd();
                assertEquals("next", parser.nextName());
                assertEquals("ok", parser.readString());
                parser.readObjectEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void skipsMultipleNestedSubtreesAndScalarValues() throws Exception {
        String yaml = "first: {a: [{b: [1, 2]}, {c: true}], d: null}\n"
                + "keep: 7\n"
                + "second: [{x: {y: []}}, false]\n"
                + "scalar: ignored\n"
                + "tail: done\n";
        {
            SnakeParser parser = parser(yaml);
            try {
                parser.readObjectStart();
                assertEquals("first", parser.nextName());
                parser.skipNode();
                assertEquals("keep", parser.nextName());
                assertEquals(7, parser.readIntValue());
                assertEquals("second", parser.nextName());
                parser.skipNode();
                assertEquals("scalar", parser.nextName());
                parser.skipNode();
                assertEquals("tail", parser.nextName());
                assertEquals("done", parser.readString());
                parser.readObjectEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void rejectsNonStringKeysInsideSkippedMappings() throws Exception {
        {
            SnakeParser parser = parser("skip: {1: value}");
            try {
                parser.readObjectStart();
                assertEquals("skip", parser.nextName());
                assertThrows(IOException.class, parser::skipNode);

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsCommentsWhenParserExposesEvents() throws Exception {
        LoaderOptions options = new LoaderOptions().setProcessComments(true);
        SnakeBinder binder = new SnakeBinder(options, new DumperOptions(), org.sjf4j.RuntimeContext.EMPTY);
        assertEquals(1, ((Map<?, ?>) binder.readNode("# before\na: 1 # after\n", Object.class)).get("a"));
    }

    @Test
    void readsBigDecimalWithoutRoundingAndRejectsOctal() throws Exception {
        {
            SnakeParser parser = parser("[0.12345678901234567890123456789, 1e-10000]");
            try {
                parser.readArrayStart();
                assertEquals(new BigDecimal("0.12345678901234567890123456789"), parser.readBigDecimal());
                assertEquals(new BigDecimal("1e-10000"), parser.readBigDecimal());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
        for (String yaml : new String[]{"010", "0_10", "!!int 010"}) {
            {
                SnakeParser parser = parser(yaml);
                try {
                    assertThrows(IOException.class, parser::readNumber);

                } finally {
                    parser.close();
                }
            }
        }
    }

    private static void assertRejected(String yaml) throws Exception {
        {
            SnakeParser parser = parser(yaml);
            try {
                assertThrows(IOException.class, parser::peekToken);

            } finally {
                parser.close();
            }
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

    private static SnakeParser parser(String yaml) throws IOException {
        return parser(new SnakeBinder(), yaml);
    }

    private static SnakeParser parser(SnakeBinder binder, String yaml) throws IOException {
        SnakeParser parser = binder.createParser(yaml);
        parser.start();
        return parser;
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
