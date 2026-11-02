package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** A codec's declared scalar type is not a guarantee of a non-null raw value. */
class NullableValueCodecTest {

    @NodeValue
    public static final class Text {
        final String raw;
        public Text(String raw) { this.raw = raw; }
        @ValueToRaw public String encode() { return raw; }
        @RawToValue public static Text decode(String raw) { return new Text(raw); }
    }

    @NodeValue
    public static final class Numeric {
        final Long raw;
        public Numeric(Long raw) { this.raw = raw; }
        @ValueToRaw public Long encode() { return raw; }
        @RawToValue public static Numeric decode(Long raw) { return new Numeric(raw); }
    }

    @NodeValue
    public static final class Logical {
        final Boolean raw;
        public Logical(Boolean raw) { this.raw = raw; }
        @ValueToRaw public Boolean encode() { return raw; }
        @RawToValue public static Logical decode(Boolean raw) { return new Logical(raw); }
    }

    @Test
    void scalarCodecNullsKeepDeclaredTypesWithoutCrashingHashOrEquals() {
        Object[] nullable = {new Text(null), new Numeric(null), new Logical(null)};
        Object[] withValues = {new Text("yes"), new Numeric(4L), new Logical(true)};
        Object[] raw = {"yes", 4, true};
        JsonType[] types = {JsonType.STRING, JsonType.NUMBER, JsonType.BOOLEAN};

        for (int i = 0; i < nullable.length; i++) {
            Object n = nullable[i];
            assertEquals(types[i], JsonType.of(n));
            assertEquals(0, Nodes.hash(n));
            assertTrue(Nodes.equals(n, n));
            assertFalse(Nodes.equals(n, null));
            assertFalse(Nodes.equals(null, n));
            assertFalse(Nodes.equals(n, withValues[i]));
            assertFalse(Nodes.equals(withValues[i], n));

            // A second codec instance with a null encoding is value-equal,
            // and its hash must agree with that equality.
            Object other = i == 0 ? new Text(null) : i == 1 ? new Numeric(null) : new Logical(null);
            assertTrue(Nodes.equals(n, other));
            assertEquals(Nodes.hash(n), Nodes.hash(other));

            assertTrue(Nodes.equals(withValues[i], raw[i]));
            assertEquals(Nodes.hash(withValues[i]), Nodes.hash(raw[i]));
        }
        assertFalse(Nodes.equals(nullable[0], nullable[1]));
        assertFalse(Nodes.equals(nullable[1], nullable[2]));
    }

    @Test
    void scalarComparisonsAndTruthinessHandleNullCodecResults() {
        Object[] nullable = {new Text(null), new Numeric(null)};
        Object[] populated = {new Text("b"), new Numeric(3L)};

        for (int i = 0; i < nullable.length; i++) {
            Object a = nullable[i], b = populated[i];
            assertFalse(FilterExpr.gt(a, b));
            assertFalse(FilterExpr.ge(a, b));
            assertFalse(FilterExpr.lt(a, b));
            assertFalse(FilterExpr.le(a, b));
            assertFalse(FilterExpr.gt(b, a));
            assertFalse(FilterExpr.ge(b, a));
            assertFalse(FilterExpr.lt(b, a));
            assertFalse(FilterExpr.le(b, a));
            assertFalse(FilterExpr.truth(a));
            assertTrue(FilterExpr.truth(b));
        }
        assertFalse(FilterExpr.truth(new Logical(null)));
        assertTrue(FilterExpr.truth(new Logical(true)));
        assertFalse(FilterExpr.truth(new Logical(false)));
        assertFalse(FilterExpr.truth(new Text("")));
        assertFalse(FilterExpr.truth(new Numeric(0L)));
        assertTrue(FilterExpr.gt(new Text("b"), "a"));
        assertTrue(FilterExpr.ge(new Numeric(3L), 3));
    }

    @Test
    void matchAndBuiltInStringFunctionsHandleNullCodecResults() {
        Text nullable = new Text(null);
        Pattern pattern = Pattern.compile("hello");

        assertFalse(FilterExpr.match(nullable, pattern));
        assertFalse(FilterExpr.match(Arrays.asList(nullable, new Text("other")), pattern));
        assertTrue(FilterExpr.match(Arrays.asList(nullable, new Text("hello")), pattern));

        assertNull(FunctionRegistry.invoke("length", nullable, new Object[0]));
        assertEquals(5, FunctionRegistry.invoke("length", new Text("hello"), new Object[0]));
        assertEquals(false, FunctionRegistry.invoke("match", nullable, new Object[]{"hello"}));
        assertEquals(false, FunctionRegistry.invoke("search", nullable, new Object[]{"hello"}));
        assertEquals(true, FunctionRegistry.invoke("match", new Text("hello"), new Object[]{"hello"}));
        assertEquals(true, FunctionRegistry.invoke("search", new Text("hello"), new Object[]{"ell"}));
    }
}
