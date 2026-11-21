package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.node.NodeProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Both wrapper and opt-in native Jackson3 must preserve scalar and enum
 * semantics, including nested containers and input-source selection.
 */
public class CompiledJackson3NativeScalarsTest {

    public enum State { NEW, ACTIVE, PAUSED }

    public static class Detail {
        public byte severity;
        public BigDecimal ratio;
        public State state;
    }

    public static class Order {
        public byte small;
        public Byte boxedSmall;
        public short medium;
        public Short boxedMedium;
        public BigInteger bigInteger;
        @NodeProperty(aliases = {"legacyAmount"})
        public BigDecimal amount;
        public Number number;
        public char initial;
        public Character optionalInitial;
        public State state;
        public List<BigDecimal> amounts;
        public List<State> states;
        public Detail detail;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Reader {
        @Read Order fromUtf8(byte[] input) throws IOException;
        @Read Order fromString(String input) throws IOException;
        @Read Order fromReader(java.io.Reader input) throws IOException;
        @Read Order fromInputStream(InputStream input) throws IOException;
    }

    private static final String VALID =
            "{\"small\":127,\"boxedSmall\":-128,\"medium\":32767,"
            + "\"boxedMedium\":-32768,\"bigInteger\":123456789012345678901234567890,"
            + "\"legacyAmount\":1234567.125,\"number\":19.5,"
            + "\"initial\":\"Z\",\"optionalInitial\":null,\"state\":\"ACTIVE\","
            + "\"amounts\":[null,1.50,-2.25],\"states\":[\"NEW\",2,null],"
            + "\"detail\":{\"ratio\":3.75,\"severity\":9,\"state\":1},"
            + "\"unknown\":{\"items\":[1,{\"nested\":true}]}}";

    @Test
    void readsNativeScalarsAndEnumsAcrossAllInputs() throws Exception {
        Reader reader = CompiledInstances.create(Reader.class);
        byte[] bytes = VALID.getBytes(StandardCharsets.UTF_8);
        for (Order value : new Order[]{
                reader.fromUtf8(bytes),
                reader.fromString(VALID),
                reader.fromReader(new StringReader(VALID)),
                reader.fromInputStream(new ByteArrayInputStream(bytes))}) {
            assertEquals((byte) 127, value.small);
            assertEquals(Byte.valueOf((byte) -128), value.boxedSmall);
            assertEquals((short) 32767, value.medium);
            assertEquals(Short.valueOf((short) -32768), value.boxedMedium);
            assertEquals(new BigInteger("123456789012345678901234567890"), value.bigInteger);
            assertEquals(new BigDecimal("1234567.125"), value.amount);
            assertEquals(19.5, value.number.doubleValue(), 0.0);
            assertEquals('Z', value.initial);
            assertNull(value.optionalInitial);
            assertEquals(State.ACTIVE, value.state);
            assertEquals(3, value.amounts.size());
            assertNull(value.amounts.get(0));
            assertEquals(new BigDecimal("1.50"), value.amounts.get(1));
            assertEquals(new BigDecimal("-2.25"), value.amounts.get(2));
            assertEquals(State.NEW, value.states.get(0));
            assertEquals(State.PAUSED, value.states.get(1));
            assertNull(value.states.get(2));
            assertEquals((byte) 9, value.detail.severity);
            assertEquals(new BigDecimal("3.75"), value.detail.ratio);
            assertEquals(State.ACTIVE, value.detail.state);
        }
    }

    @Test
    void preservesNullAndMalformedValueHandling() throws Exception {
        Reader reader = CompiledInstances.create(Reader.class);
        Order empty = reader.fromUtf8(
                "{\"boxedSmall\":null,\"boxedMedium\":null,\"bigInteger\":null,"
                + "\"amount\":null,\"number\":null,\"optionalInitial\":null,"
                + "\"state\":null,\"amounts\":null,\"states\":null,\"detail\":null}"
                .getBytes(StandardCharsets.UTF_8));
        assertNull(empty.boxedSmall);
        assertNull(empty.boxedMedium);
        assertNull(empty.bigInteger);
        assertNull(empty.amount);
        assertNull(empty.number);
        assertNull(empty.optionalInitial);
        assertNull(empty.state);
        assertNull(empty.amounts);
        assertNull(empty.states);
        assertNull(empty.detail);
        assertNull(reader.fromUtf8("null".getBytes(StandardCharsets.UTF_8)));

        String[] invalid = {
                "{\"small\":128}",
                "{\"small\":-129}",
                "{\"medium\":32768}",
                "{\"boxedMedium\":-32769}",
                "{\"small\":null}",
                "{\"amount\":\"abc\"}",
                "{\"state\":\"UNRECOGNIZED\"}",
                "{\"state\":3}",
                "{\"state\":-1}",
                "{\"state\":false}",
                "{\"initial\":\"AB\"}",
                "{\"initial\":null}",
                "{\"optionalInitial\":12}",
                "{\"amounts\":[{\"invalid\":true}]}",
                "{\"detail\":{\"severity\":255}}"
        };
        for (String json : invalid) {
            assertThrows(Exception.class, () -> reader.fromUtf8(
                    json.getBytes(StandardCharsets.UTF_8)), json);
            assertThrows(Exception.class, () -> reader.fromString(json), json);
        }
    }
}
