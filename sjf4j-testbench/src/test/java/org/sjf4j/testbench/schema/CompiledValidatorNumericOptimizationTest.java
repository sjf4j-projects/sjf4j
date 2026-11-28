package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime parity for native integer arithmetic and shared compiled numeric constants. */
class CompiledValidatorNumericOptimizationTest {
    private static final String INTEGER = """
            {"type":"integer","minimum":-15,"exclusiveMaximum":15,"multipleOf":3}
            """;
    private static final String INCREMENT = """
            {"type":"integer","multipleOf":1,"minimum":-1,"maximum":1}
            """;
    private static final String FRACTIONAL_BOUND = """
            {"type":"integer","minimum":1.5,"exclusiveMaximum":3.25}
            """;
    private static final String FRACTIONAL_NUMBER = """
            {"type":"number","minimum":1.25,"exclusiveMaximum":5.5,"multipleOf":0.25}
            """;
    private static final String DECIMAL_LITERAL = """
            {"type":"number","enum":[2.75,3.25],"const":2.75}
            """;
    private static final String HUGE_MULTIPLE = """
            {"type":"integer","multipleOf":18446744073709551616}
            """;

    private static final String THREE = "{\"type\":\"number\",\"multipleOf\":3}";
    private static final String TENTH = "{\"type\":\"number\",\"multipleOf\":0.1}";

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(INTEGER) boolean primitiveInt(int value);
        @ValidJsonSchema(INTEGER) boolean primitiveLong(long value);
        @ValidJsonSchema(INTEGER) boolean wrappedInt(Integer value);
        @ValidJsonSchema(INTEGER) boolean wrappedLong(Long value);
        @ValidJsonSchema(INTEGER) boolean primitiveByte(byte value);
        @ValidJsonSchema(INTEGER) boolean primitiveShort(short value);
        @ValidJsonSchema(INCREMENT) boolean increment(int value);
        @ValidJsonSchema(FRACTIONAL_BOUND) boolean fractionalInteger(int value);
        @ValidJsonSchema(FRACTIONAL_NUMBER) boolean fractionalNumber(BigDecimal value);
        @ValidJsonSchema(DECIMAL_LITERAL) boolean literal(BigDecimal value);
        @ValidJsonSchema(HUGE_MULTIPLE) boolean hugeDivisor(long value);
        @ValidJsonSchema(HUGE_MULTIPLE) boolean hugeBigInteger(BigInteger value);
        @ValidJsonSchema(THREE) boolean floatingDouble(double value);
        @ValidJsonSchema(THREE) boolean floatingFloat(float value);
        @ValidJsonSchema(THREE) boolean dynamicMultiple(Object value);
        @ValidJsonSchema(TENTH) boolean decimalDouble(double value);
        @ValidJsonSchema(TENTH) boolean decimalFloat(float value);
    }

    @Test
    void integralPrimitiveAndWrapperBoundsMatchRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan ref = JsonSchema.fromJson(INTEGER).formulate();
        for (int n : new int[]{-128,-18,-15,-14,-12,-3,-1,0,1,3,6,12,14,15,18,127}) {
            boolean expected = ref.isValid(n);
            assertEquals(expected, direct.primitiveInt(n), "int " + n);
            assertEquals(expected, direct.primitiveLong(n), "long " + n);
            assertEquals(expected, direct.wrappedInt(n), "Integer " + n);
            assertEquals(expected, direct.wrappedLong((long)n), "Long " + n);
            assertEquals(expected, direct.primitiveShort((short)n), "short " + n);
            assertEquals(expected, direct.primitiveByte((byte)n), "byte " + n);
        }
        for (long n : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, -1000000000000L,
                1000000000000L, -21L, 21L}) {
            assertEquals(ref.isValid(n), direct.primitiveLong(n), "long " + n);
            assertEquals(ref.isValid(n), direct.wrappedLong(n), "Long " + n);
        }
    }

    @Test
    void divisorOneAndFractionalBoundariesRemainEquivalent() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan increment = JsonSchema.fromJson(INCREMENT).formulate();
        SchemaPlan fractional = JsonSchema.fromJson(FRACTIONAL_BOUND).formulate();
        for (int n = -20; n <= 20; n++) {
            assertEquals(increment.isValid(n), direct.increment(n), "increment " + n);
            assertEquals(fractional.isValid(n), direct.fractionalInteger(n), "fractional " + n);
        }
        assertTrue(direct.fractionalInteger(2));
        assertTrue(direct.fractionalInteger(3));
        assertFalse(direct.fractionalInteger(1));
        assertFalse(direct.fractionalInteger(4));
    }

    @Test
    void decimalConstantsAreSharedWithoutChangingNumericSemantics() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan fractional = JsonSchema.fromJson(FRACTIONAL_NUMBER).formulate();
        SchemaPlan literals = JsonSchema.fromJson(DECIMAL_LITERAL).formulate();
        for (String text : new String[]{"0", "1", "1.25", "1.50", "2.75",
                "3.25", "3.5", "5.25", "5.5", "6", "-1"}) {
            BigDecimal n = new BigDecimal(text);
            assertEquals(fractional.isValid(n), direct.fractionalNumber(n), text);
            assertEquals(literals.isValid(n), direct.literal(n), text);
        }
        assertTrue(direct.literal(new BigDecimal("2.750")));
        assertFalse(direct.literal(new BigDecimal("3.25")));
    }

    @Test
    void floatingMultiplesMatchRuntimeAndExactDecimalRemainder() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan ref = JsonSchema.fromJson(THREE).formulate();
        BigDecimal divisor = BigDecimal.valueOf(3);
        for (double n : new double[]{-12, -12.5, -0.0, 0.3, 6, Math.nextDown(6.0),
                Math.nextDown(0x1.0p53), 0x1.0p53, Math.nextUp(0x1.0p53),
                1e20, 3e20, Double.MIN_VALUE}) {
            boolean expected = new BigDecimal(Double.toString(n)).remainder(divisor).signum() == 0;
            assertEquals(expected, ref.isValid(n), "runtime double " + n);
            assertEquals(expected, direct.floatingDouble(n), "compiled double " + n);
            assertEquals(expected, direct.dynamicMultiple(n), "dynamic double " + n);
        }
        for (float n : new float[]{-12, -12.5f, -0.0f, 0.3f, 6, Math.nextUp(6.0f),
                Math.nextDown(0x1.0p24f), 0x1.0p24f, Math.nextUp(0x1.0p24f),
                1000000064f, Float.MIN_VALUE}) {
            boolean expected = new BigDecimal(Float.toString(n)).remainder(divisor).signum() == 0;
            assertEquals(expected, ref.isValid(n), "runtime float " + n);
            assertEquals(expected, direct.floatingFloat(n), "compiled float " + n);
            assertEquals(expected, direct.dynamicMultiple(n), "dynamic float " + n);
        }
        assertTrue(direct.decimalDouble(0.3d));
        assertTrue(direct.decimalFloat(0.3f));
        assertFalse(direct.decimalDouble(0.31d));
        assertFalse(direct.decimalFloat(0.31f));
    }

    @Test
    void outOfRangeDivisorFallsBackToExactDecimal() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan ref = JsonSchema.fromJson(HUGE_MULTIPLE).formulate();
        for (long n : new long[]{Long.MIN_VALUE, -1, 0, 1, 2, Long.MAX_VALUE}) {
            assertEquals(ref.isValid(n), direct.hugeDivisor(n), "long " + n);
        }
        BigInteger big = BigInteger.ONE.shiftLeft(64);
        for (BigInteger n : new BigInteger[]{big, big.subtract(BigInteger.ONE),
                big.add(BigInteger.ONE), big.negate(), big.multiply(BigInteger.TWO)}) {
            assertEquals(ref.isValid(n), direct.hugeBigInteger(n), n.toString());
        }
    }
}
