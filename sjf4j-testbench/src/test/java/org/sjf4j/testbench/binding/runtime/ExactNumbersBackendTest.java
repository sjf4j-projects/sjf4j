package org.sjf4j.testbench.binding.runtime;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonArray;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExactNumbersBackendTest {
    private static final String DECIMAL = "0.123456789123456789";
    private static final String EXPONENT = "1.25e-400";
    private static final String HUGE = "9223372036854775808";
    private static final String DOCUMENT = "{\"n\":" + DECIMAL
            + ",\"nested\":[" + EXPONENT + ",1,2147483648," + HUGE + ",null]}";

    private static Binder<?, ?>[] backends(NodeConfig config) {
        return new Binder<?, ?>[]{
                new Jackson2Binder(config), new Jackson3Binder(config), new SimpleJsonBinder(config),
                new GsonBinder(config), new JsonpBinder(config), new SnakeBinder(config), new Fastjson2Binder(config)
        };
    }

    private static void assertDecimal(String literal, Object value) {
        BigDecimal actual = assertInstanceOf(BigDecimal.class, value);
        assertEquals(0, new BigDecimal(literal).compareTo(actual), literal);
    }

    private static void checkRaw(Map<?, ?> raw) {
        assertDecimal(DECIMAL, raw.get("n"));
        List<?> nested = (List<?>) raw.get("nested");
        assertDecimal(EXPONENT, nested.get(0));
        assertEquals(1, nested.get(1));
        assertEquals(2147483648L, nested.get(2));
        assertEquals(new BigInteger(HUGE), nested.get(3));
        assertNull(nested.get(4));
    }

    @Test
    void exactGenericReadsAcrossBackendsAndInputKinds() {
        byte[] bytes = DOCUMENT.getBytes(StandardCharsets.UTF_8);
        for (Binder<?, ?> binder : backends(NodeConfig.EXACT_NUMBERS)) {
            assertDecimal(DECIMAL, binder.readNode(DECIMAL, Number.class));
            assertDecimal("1.0", binder.readNode("1.0", Object.class));
            assertDecimal("1.0e+3", binder.readNode("1.0e+3", Number.class));
            assertDecimal("1.0e+400", binder.readNode("1.0e+400", Number.class));
            checkRaw((Map<?, ?>) binder.readNode(DOCUMENT, Map.class));
            checkRaw((Map<?, ?>) binder.readNode(bytes, Object.class));
            checkRaw((Map<?, ?>) binder.readNode(new StringReader(DOCUMENT), Object.class));
            checkRaw((Map<?, ?>) binder.readNode(new ByteArrayInputStream(bytes), Map.class));
            JsonObject object = (JsonObject) binder.readNode(DOCUMENT, JsonObject.class);
            assertDecimal(DECIMAL, object.getNumber("n"));
            JsonArray array = (JsonArray) binder.readNode("[" + DECIMAL + "]", JsonArray.class);
            assertDecimal(DECIMAL, array.get(0, Number.class));
            assertNull(binder.readNode("null", Number.class));
        }
    }

    public static class Mixed {
        public Number number;
        public Object raw;
        public Map<String, Object> map;
        public List<Number> numbers;
        public double primitive;
        public Double boxed;
        public BigDecimal decimal;
        public int count;
    }

    @Test
    void typedAndUntypedFieldsAndSeparateBinderPolicies() throws Exception {
        String json = "{\"number\":" + DECIMAL + ",\"raw\":" + DECIMAL
                + ",\"map\":{\"n\":" + DECIMAL + "},\"numbers\":[" + DECIMAL + "]"
                + ",\"primitive\":1.25,\"boxed\":1.25,\"decimal\":" + DECIMAL + ",\"count\":7}";
        for (Binder<?, ?> binder : backends(NodeConfig.EXACT_NUMBERS)) {
            Mixed value = (Mixed) binder.readNode(json, Mixed.class);
            assertDecimal(DECIMAL, value.number);
            assertDecimal(DECIMAL, value.raw);
            assertDecimal(DECIMAL, value.map.get("n"));
            assertDecimal(DECIMAL, value.numbers.get(0));
            assertEquals(1.25d, value.primitive);
            assertEquals(Double.valueOf(1.25), value.boxed);
            assertDecimal(DECIMAL, value.decimal);
            assertEquals(7, value.count);
            try (StreamingParser parser = binder.createParser(DECIMAL)) {
                parser.start();
                assertDecimal(DECIMAL, parser.readNumberExact());
                parser.end();
            }
        }
        for (Binder<?, ?> binder : backends(NodeConfig.DEFAULT)) {
            assertInstanceOf(Double.class, binder.readNode("1.25", Number.class));
            try (StreamingParser parser = binder.createParser("1.25")) {
                parser.start();
                assertInstanceOf(Double.class, parser.readNumber());
                parser.end();
            }
            assertInstanceOf(Double.class, binder.readNode("1.25", Number.class));
        }
    }

    @Test
    void precisionCanBeSelectedForEachValueWithoutParserState() throws Exception {
        String json = "[1.25," + DECIMAL + "," + DOCUMENT + ",[1.25],1.25,null]";
        for (Binder<?, ?> binder : backends(NodeConfig.DEFAULT)) {
            // Fastjson2 uses a native reader-wide feature instead of a per-value API.
            if (binder instanceof Fastjson2Binder) continue;
            try (StreamingParser parser = binder.createParser(json)) {
                parser.start();
                parser.readArrayStart();
                assertEquals(1.25d, parser.readNumber());
                assertDecimal(DECIMAL, parser.readNumberExact());
                checkRaw(parser.readRawObject(NodeConfig.EXACT_NUMBERS));
                assertEquals(List.of(1.25d), parser.readRawArray(NodeConfig.DEFAULT));
                assertEquals(1.25d, parser.readNumber());
                assertNull(parser.readNumberExact());
                assertTrue(parser.nextIfArrayEnd());
                parser.end();
            }
        }
    }

    @Test
    void simpleExactReadsLongLiteralsWithoutChangingDefaultLimit() {
        String decimal = "0." + "1234567890".repeat(15);
        String integer = "9".repeat(150);
        SimpleJsonBinder exact = new SimpleJsonBinder(NodeConfig.EXACT_NUMBERS);
        assertDecimal(decimal, exact.readNode(decimal, Number.class));
        assertEquals(new BigInteger(integer), exact.readNode(integer, Number.class));
        assertThrows(org.sjf4j.exception.BindingException.class,
                () -> new SimpleJsonBinder().readNode(decimal, Number.class));
    }
}
