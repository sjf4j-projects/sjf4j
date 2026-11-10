package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Conversion boundaries inspired by MapStruct's conversion tests, with SJF4J's
 * strict numeric and dynamic-value conversion behavior as the oracle.
 */
public class MapperConversionEdgeTest {

    private final EdgeMapper mapper = CompiledInstances.create(EdgeMapper.class);

    @Test
    public void exactBigDecimalConvertsToInteger() {
        assertEquals(42, mapper.toInteger(new NumericSource(new BigDecimal("42.0"))).value());
    }

    @Test
    public void narrowingToIntRejectsOverflow() {
        assertThrows(RuntimeException.class,
                () -> mapper.toInteger(new NumericSource((long) Integer.MAX_VALUE + 1)));
    }

    @Test
    public void narrowingToIntRejectsFractionalDecimal() {
        assertThrows(RuntimeException.class,
                () -> mapper.toInteger(new NumericSource(new BigDecimal("1.25"))));
    }

    @Test
    public void narrowingToByteAcceptsLowerBoundary() {
        assertEquals((byte) -128, mapper.toByte(new NumericSource(BigInteger.valueOf(-128))).value());
    }

    @Test
    public void narrowingToByteRejectsUnderflow() {
        assertThrows(RuntimeException.class,
                () -> mapper.toByte(new NumericSource(BigInteger.valueOf(-129))));
    }

    @Test
    public void dynamicStringIsNotCoercedToInteger() {
        assertThrows(RuntimeException.class,
                () -> mapper.dynamicInteger(new DynamicSource("42")));
    }

    @Test
    public void dynamicBooleanIsNotCoercedToInteger() {
        assertThrows(RuntimeException.class,
                () -> mapper.dynamicInteger(new DynamicSource(Boolean.TRUE)));
    }

    @Test
    public void dynamicStringIsNotCoercedToBoolean() {
        assertThrows(RuntimeException.class,
                () -> mapper.dynamicBoolean(new DynamicSource("true")));
    }

    @Test
    public void unknownEnumLiteralIsRejected() {
        assertThrows(RuntimeException.class,
                () -> mapper.enumValue(new TextSource("UNKNOWN")));
    }

    @Test
    public void enumConversionIsCaseSensitive() {
        assertEquals(State.ACTIVE, mapper.enumValue(new TextSource("ACTIVE")).value());
        assertThrows(RuntimeException.class,
                () -> mapper.enumValue(new TextSource("active")));
    }

    @Test
    public void emptyStringCannotProduceCharacter() {
        assertThrows(RuntimeException.class,
                () -> mapper.character(new TextSource("")));
    }

    @Test
    public void invalidNestedListValueFailsWithoutMutatingSource() {
        List<Object> source = new ArrayList<>();
        source.add(1L);
        source.add("not-a-number");
        assertThrows(RuntimeException.class, () -> mapper.integerList(source));
        assertEquals(List.of(1L, "not-a-number"), source);
    }

    @Test
    public void invalidMapValueFailsWithoutMutatingSource() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("good", 1L);
        source.put("bad", "not-a-number");
        assertThrows(RuntimeException.class, () -> mapper.integerMap(source));
        assertEquals(1L, source.get("good"));
        assertEquals("not-a-number", source.get("bad"));
    }

    public record NumericSource(Number value) {}
    public record DynamicSource(Object value) {}
    public record TextSource(String value) {}
    public record IntegerTarget(Integer value) {}
    public record ByteTarget(Byte value) {}
    public record BooleanTarget(Boolean value) {}
    public record EnumTarget(State value) {}
    public record CharTarget(Character value) {}
    public enum State { ACTIVE, INACTIVE }

    @CompiledMapper
    public interface EdgeMapper {
        IntegerTarget toInteger(NumericSource source);
        ByteTarget toByte(NumericSource source);
        IntegerTarget dynamicInteger(DynamicSource source);
        BooleanTarget dynamicBoolean(DynamicSource source);
        EnumTarget enumValue(TextSource source);
        CharTarget character(TextSource source);
        List<Integer> integerList(List<Object> source);
        Map<String, Integer> integerMap(Map<String, Object> source);
    }
}
