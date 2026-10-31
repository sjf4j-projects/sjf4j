package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verify numeric fast paths in generated Java rather than benchmarking the generator itself. */
class CompiledValidatorNumericOptimizationProcessorTest {

    private static String generated(String schema, String type) throws Exception {
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "boolean valid(" + type + " value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Check.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        return result.generatedSource("fixture/Check_Impl.java");
    }

    private static int count(String source, String text) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(text, at)) >= 0; at += text.length()) count++;
        return count;
    }

    @Test
    void nativeIntegersUsePrimitiveRemainderAndBounds() throws Exception {
        String schema = "{\"type\":\"integer\",\"multipleOf\":3,"
                + "\"minimum\":-15,\"exclusiveMaximum\":15}";
        for (String type : new String[]{"int", "long", "java.lang.Integer", "java.lang.Long",
                "short", "byte", "java.lang.Short", "java.lang.Byte"}) {
            String output = generated(schema, type);
            assertTrue(output.contains("value % 3L != 0"), type + "\n" + output);
            assertTrue(output.contains("value < -15L"), type + "\n" + output);
            assertTrue(output.contains("value >= 15L"), type + "\n" + output);
            assertFalse(output.contains("normalizeDecimal("), type + "\n" + output);
            assertFalse(output.contains("isSemanticInteger("), type + "\n" + output);
            assertFalse(output.contains("new BigDecimal("), type + "\n" + output);
        }
    }

    @Test
    void divisorOneAndBoundConstantsAreFolded() throws Exception {
        String output = generated("{\"type\":\"integer\",\"multipleOf\":1,"
                + "\"minimum\":0,\"maximum\":1}", "int");
        assertFalse(output.contains(" % "), output);
        assertFalse(output.contains(".remainder("), output);
        assertTrue(output.contains("value < 0L"), output);
        assertTrue(output.contains("value > 1L"), output);
    }

    @Test
    void fractionalBoundsAndMultipleOfUseReusedStaticDecimals() throws Exception {
        String schema = "{\"type\":\"number\",\"minimum\":1.25,"
                + "\"exclusiveMaximum\":1.25,\"multipleOf\":0.25}";
        String output = generated(schema, "java.math.BigDecimal");
        assertEquals(2, count(output, "private static final BigDecimal"), output);
        assertEquals(1, count(output, "new BigDecimal(\"1.25\")"), output);
        assertTrue(output.contains(".remainder("), output);
        assertTrue(output.contains("Numbers.compare("), output);
    }

    @Test
    void fractionalBoundsOnNativeIntsStayExact() throws Exception {
        String output = generated("{\"type\":\"integer\",\"minimum\":1.5,"
                + "\"exclusiveMaximum\":3.25}", "int");
        assertEquals(2, count(output, "private static final BigDecimal"), output);
        assertTrue(output.contains("Numbers.compare("), output);
        assertFalse(output.contains("value < 1L"), output);
    }

    @Test
    void numericEnumAndConstConstantsAreStatic() throws Exception {
        String output = generated("{\"type\":\"number\",\"enum\":[2.75,3.25],"
                + "\"const\":2.75}", "java.math.BigDecimal");
        assertEquals(2, count(output, "private static final BigDecimal"), output);
        assertEquals(1, count(output, "new BigDecimal(\"2.75\")"), output);
        assertEquals(1, count(output, "new BigDecimal(\"3.25\")"), output);
        assertFalse(output.contains(".isValid("), output);
    }

    @Test
    void integerDivisorBeyondLongStillUsesExactDecimal() throws Exception {
        String output = generated("{\"type\":\"integer\","
                + "\"multipleOf\":18446744073709551616}", "long");
        assertTrue(output.contains(".remainder("), output);
        assertFalse(output.contains(" % "), output);
        assertEquals(1, count(output, "private static final BigDecimal"), output);
    }
}
