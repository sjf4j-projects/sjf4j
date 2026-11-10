package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.NumericPolicy;

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
 * range-checked numeric and strict dynamic-value conversion as the oracle.
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
    public void fractionalDecimalUsesRangeCheckedNumberTruncation() {
        assertEquals(1, mapper.toInteger(new NumericSource(new BigDecimal("1.25"))).value());
        assertEquals(-1, mapper.toInteger(new NumericSource(new BigDecimal("-1.75"))).value());
    }

    @Test
    public void exactNumericAllowsIntegralDecimal() {
        assertEquals(42, mapper.toIntegerExact(new NumericSource(new BigDecimal("42.0"))).value());
        assertEquals(-128, mapper.toIntegerExact(new NumericSource(new BigDecimal("-128.000"))).value());
    }

    @Test
    public void exactNumericRejectsFractionalDecimal() {
        assertThrows(ArithmeticException.class,
                () -> mapper.toIntegerExact(new NumericSource(new BigDecimal("1.25"))));
        assertThrows(ArithmeticException.class,
                () -> mapper.toIntegerExact(new NumericSource(new BigDecimal("-1.75"))));
    }

    @Test
    public void exactNumericStillRejectsOutOfRange() {
        assertThrows(ArithmeticException.class,
                () -> mapper.toIntegerExact(new NumericSource(new BigDecimal("2147483648"))));
    }

    @Test
    public void exactNumericAppliesToDynamicNumber() {
        assertEquals(42, mapper.dynamicIntegerExact(new DynamicSource(new BigDecimal("42.0"))).value());
        assertThrows(ArithmeticException.class,
                () -> mapper.dynamicIntegerExact(new DynamicSource(new BigDecimal("1.25"))));
        assertThrows(RuntimeException.class,
                () -> mapper.dynamicIntegerExact(new DynamicSource("42")));
    }

    @Test
    public void exactNumericAppliesToContainerElements() {
        assertEquals(List.of(42), mapper.integerListExact(List.of(new BigDecimal("42.0"))));
        assertThrows(ArithmeticException.class,
                () -> mapper.integerListExact(List.of(new BigDecimal("42.25"))));

        assertEquals(Map.of("a", 42), mapper.integerMapExact(Map.of("a", new BigDecimal("42.0"))));
        assertThrows(ArithmeticException.class,
                () -> mapper.integerMapExact(Map.of("a", new BigDecimal("42.25"))));
    }

    @Test
    public void nestedExactAndLossyPoliciesStayIndependent() {
        NestedNumericSource source = new NestedNumericSource(new DeepNumericSource(new BigDecimal("1.25")));
        assertEquals(1, mapper.nestedLossy(source).nested().value());
        assertThrows(ArithmeticException.class, () -> mapper.nestedExact(source));
        assertEquals(1, mapper.nestedLossy(source).nested().value());
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
    public record DeepNumericSource(Number value) {}
    public record DeepNumericTarget(Integer value) {}
    public record NestedNumericSource(DeepNumericSource nested) {}
    public record NestedNumericTarget(DeepNumericTarget nested) {}
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
        @MappingOptions(numeric = NumericPolicy.ALLOW_PRECISION_LOSS)
        IntegerTarget toInteger(NumericSource source);
        NestedNumericTarget nestedLossy(NestedNumericSource source);
        @MappingOptions(numeric = NumericPolicy.EXACT)
        NestedNumericTarget nestedExact(NestedNumericSource source);
        @MappingOptions(numeric = NumericPolicy.EXACT)
        IntegerTarget toIntegerExact(NumericSource source);
        ByteTarget toByte(NumericSource source);
        IntegerTarget dynamicInteger(DynamicSource source);
        @MappingOptions(numeric = NumericPolicy.EXACT)
        IntegerTarget dynamicIntegerExact(DynamicSource source);
        BooleanTarget dynamicBoolean(DynamicSource source);
        EnumTarget enumValue(TextSource source);
        CharTarget character(TextSource source);
        List<Integer> integerList(List<Object> source);
        @MappingOptions(numeric = NumericPolicy.EXACT)
        List<Integer> integerListExact(List<Object> source);
        Map<String, Integer> integerMap(Map<String, Object> source);
        @MappingOptions(numeric = NumericPolicy.EXACT)
        Map<String, Integer> integerMapExact(Map<String, Object> source);
    }
}
