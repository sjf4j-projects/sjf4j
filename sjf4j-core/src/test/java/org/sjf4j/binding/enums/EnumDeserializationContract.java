package org.sjf4j.binding.enums;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.annotation.node.NodeProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Plain Jackson enum defaults, adapted through SJF4J's public binding API. */
public abstract class EnumDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    /** Source: EnumDeserializationTest#testSimple. */
    @Test void testSimple() {
        assertEquals(TestEnum.OK, binding(StreamingContext.EMPTY).readNode("\"OK\"", TestEnum.class));
        assertNull(binding(StreamingContext.EMPTY).readNode("null", TestEnum.class));
    }

    /** Source: EnumDeserializationTest#testComplexEnum. */
    @Test void testComplexEnum() {
        assertEquals(java.util.concurrent.TimeUnit.SECONDS,
                binding(StreamingContext.EMPTY).readNode("\"SECONDS\"", java.util.concurrent.TimeUnit.class));
    }

    /** Source: EnumDeserializationTest#testNumbersToEnums; Jackson defaults numeric input to ordinal. */
    @Test void testNumbersToEnums() {
        assertEquals(TestEnum.RULES, binding(StreamingContext.EMPTY).readNode("1", TestEnum.class));
    }

    /** SJF4J only treats a JSON number token as an enum ordinal. */
    @Test void testQuotedNumberIsNotAnEnumOrdinal() {
        assertThrows(RuntimeException.class,
                () -> binding(StreamingContext.EMPTY).readNode("\"1\"", TestEnum.class));
    }

    /** Source: EnumDeserializationTest#testSimple; unknown text fails by default. */
    @Test void testUnknownEnumTextFailsByDefault() {
        assertThrows(RuntimeException.class,
                () -> binding(StreamingContext.EMPTY).readNode("\"NO-SUCH-VALUE\"", TestEnum.class));
    }

    /** Source: EnumDefaultReadTest#testWithoutCustomFeatures; an out-of-range ordinal fails by default. */
    @Test void testUnknownEnumNumberFailsByDefault() {
        assertThrows(RuntimeException.class,
                () -> binding(StreamingContext.EMPTY).readNode("4343", TestEnum.class));
    }

    /** Source: EnumDeserializationTest#testUnwrappedEnumException; arrays are rejected by Jackson default. */
    @Test void testUnwrappedEnumException() {
        assertThrows(RuntimeException.class,
                () -> binding(StreamingContext.EMPTY).readNode("[\"JACKSON\"]", TestEnum.class));
    }

    /** Source: EnumDeserializationTest#testDoNotAllowUnknownEnumValuesAsMapKeysWhenReadAsNullDisabled. */
    @Disabled("TODO: Map key support needs a cross-path design before enum keys can be bound.")
    @Test void testDoNotAllowUnknownEnumValuesAsMapKeysWhenReadAsNullDisabled() {
        assertThrows(RuntimeException.class, () -> binding(StreamingContext.EMPTY).readNode(
                "{\"map\":{\"NO-SUCH-VALUE\":\"value\"}}", EnumMapHolder.class));
    }

    /** Source: EnumDeserializationTest#testEnumValuesCaseSensitivity. */
    @Disabled("TODO: Map key support needs a cross-path design before enum keys can be bound.")
    @Test void testEnumValuesCaseSensitivity() {
        assertThrows(RuntimeException.class, () -> binding(StreamingContext.EMPTY).readNode(
                "{\"map\":{\"JACkson\":\"value\"}}", EnumMapHolder.class));
    }

    /** Source: EnumMapDeserializationTest#testEnumMaps. */
    @Disabled("TODO: Map key support needs a cross-path design before enum keys can be bound.")
    @Test void testEnumMaps() {
        EnumMap<TestEnum, String> value = read("{\"OK\":\"value\"}", new TypeReference<EnumMap<TestEnum, String>>() {});
        assertEquals("value", value.get(TestEnum.OK));
    }

    /** Retained SJF4J coverage: this checkout has no plain Jackson DEFAULT source for enum map values. */
    @Disabled("TODO: Map key support needs a cross-path design before enum keys can be bound.")
    @Test void testEnumMapValues() {
        Map<TestEnum, TestEnum> value = read("{\"JACKSON\":\"RULES\"}", new TypeReference<Map<TestEnum, TestEnum>>() {});
        assertEquals(Map.of(TestEnum.JACKSON, TestEnum.RULES), value);
    }

    /** Source: EnumDeserializationTest#testEnumsWithJsonValue, structural NodeValue equivalent. */
    @Test void testEnumsWithJsonValue() {
        EnumCode value = (EnumCode) binding(StreamingContext.EMPTY).readNode("\"foo\"", EnumCode.class);
        assertEquals("foo", value.value);
    }

    /** Source: EnumSameName4302Test#testWrappedShouldWork. */
    @Test void testWrappedShouldWork() {
        EnumHolder value = (EnumHolder) binding(StreamingContext.EMPTY).readNode("{\"value\":\"OK\"}", EnumHolder.class);
        assertEquals(TestEnum.OK, value.value);
    }

    /** Source: EnumAliasDeser2352Test#testEnumWithAlias, structural property alias only. */
    @Test void testEnumPropertyAlias() {
        AliasedEnumHolder value = (AliasedEnumHolder) binding(StreamingContext.EMPTY).readNode("{\"legacyValue\":\"OK\"}", AliasedEnumHolder.class);
        assertEquals(TestEnum.OK, value.value);
    }

    /** Source: EnumDeserializationTest#testSimple, array and collection element binding. */
    @Test void testEnumArraysAndCollections() {
        TestEnum[] array = (TestEnum[]) binding(StreamingContext.EMPTY).readNode("[\"JACKSON\",\"OK\"]", TestEnum[].class);
        assertEquals(TestEnum.JACKSON, array[0]);
        assertEquals(List.of(TestEnum.RULES, TestEnum.OK), read("[\"RULES\",\"OK\"]", new TypeReference<List<TestEnum>>() {}));
    }

    @SuppressWarnings("unchecked")
    private <T> T read(String json, TypeReference<T> type) {
        return (T) binding(StreamingContext.EMPTY).readNode(json, type.getType());
    }

    enum TestEnum { JACKSON, RULES, OK }
    static class EnumHolder { public TestEnum value; }
    static class EnumMapHolder { public Map<TestEnum, String> map; }
    static class AliasedEnumHolder { @NodeProperty(value = "value", aliases = "legacyValue") public TestEnum value; }

    @org.sjf4j.annotation.node.NodeValue
    static class EnumCode {
        final String value;
        EnumCode(String value) { this.value = value; }
        @org.sjf4j.annotation.node.RawToValue static EnumCode decode(String raw) { return new EnumCode(raw); }
        @org.sjf4j.annotation.node.ValueToRaw String encode() { return value; }
    }
}
