package org.sjf4j.testbench;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.simple.SimpleJsonFacade;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecordTest {

    @Test
    // Structural source: jackson-databind/src/test/java/tools/jackson/databind/deser/PropertyAliasTest.java#testAliasOnRecordUpdateWithIgnoredGetter
    // Limited structural adaptation: this tests record-component aliases only, not record update or ignored-getter semantics.
    public void testAliasOnRecordUpdateWithIgnoredGetter() {
        Sjf4j sjf4j = Sjf4j.builder().jsonFacadeProvider(SimpleJsonFacade.provider()).build();

        Person person = sjf4j.fromJson("{\"legacyName\":\"han\",\"age\":7}", Person.class);

        assertEquals("han", person.name());
        assertEquals(7, person.age());
        Map<?, ?> written = sjf4j.fromJson(sjf4j.toJsonString(person), Map.class);
        assertEquals("han", written.get("name"));
        assertFalse(written.containsKey("legacyName"));
    }

    public record Person(@NodeProperty(value = "name", aliases = "legacyName") String name, int age) {}

    // Direct source: creators/CreatorNullPrimitivesTest#testRecordAbsentPrimitivesShouldDefault.
    @Test
    public void creatorRecordAbsentPrimitivesUseJavaDefaults() {
        Sjf4j sjf4j = Sjf4j.builder().jsonFacadeProvider(SimpleJsonFacade.provider()).build();
        PrimitiveRecord record = sjf4j.fromJson("{\"second\":42,\"enabled\":true}", PrimitiveRecord.class);
        assertEquals(0, record.first()); assertEquals(42, record.second()); assertEquals(true, record.enabled()); assertFalse(record.visible());
    }

    public record PrimitiveRecord(int first, int second, boolean enabled, boolean visible) {}
}
