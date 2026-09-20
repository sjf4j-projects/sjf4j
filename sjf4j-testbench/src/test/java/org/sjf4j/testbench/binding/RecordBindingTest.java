package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NamingStrategy;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.exception.BindingException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordBindingTest {
    private final SimpleJsonBinder binding = new SimpleJsonBinder(StreamingContext.EMPTY);

    @Test
    void bindsCanonicalArgumentsRegardlessOfJsonOrder() {
        Ordered record = (Ordered) binding.readNode("{\"active\":true,\"age\":7,\"name\":\"han\"}", Ordered.class);

        assertEquals(new Ordered("han", 7, true), record);
    }

    @Test
    void bindsPrimaryNamesAndAliasesAndRejectsDuplicates() {
        Renamed primary = (Renamed) binding.readNode("{\"age\":6,\"display_name\":\"primary\"}", Renamed.class);
        assertEquals("primary", primary.name());
        assertEquals(6, primary.age());

        Renamed nameAlias = (Renamed) binding.readNode("{\"age\":7,\"name\":\"han\"}", Renamed.class);
        assertEquals("han", nameAlias.name());

        Renamed alias = (Renamed) binding.readNode("{\"age\":8,\"legacy_name\":\"old\"}", Renamed.class);
        assertEquals("old", alias.name());

        assertThrows(BindingException.class, () -> binding.readNode(
                "{\"name\":\"first\",\"display_name\":\"second\",\"age\":9}", Renamed.class));
        assertThrows(BindingException.class, () -> binding.readNode(
                "{\"display_name\":\"first\",\"name\":\"second\",\"age\":9}", Renamed.class));

        Map<?, ?> written = (Map<?, ?>) binding.readNode(binding.writeNodeAsString(alias), Map.class);
        assertEquals("old", written.get("display_name"));
        assertFalse(written.containsKey("name"));
        assertFalse(written.containsKey("legacy_name"));
    }

    @Test
    void appliesMissingAndNullDefaultsAccordingToBindingSemantics() {
        Defaults missing = (Defaults) binding.readNode("{}", Defaults.class);
        assertEquals(0, missing.count());
        assertFalse(missing.enabled());
        assertNull(missing.label());
        assertNull(missing.boxed());

        Defaults nullPrimitives = (Defaults) binding.readNode("{\"count\":null,\"enabled\":null}", Defaults.class);
        assertEquals(0, nullPrimitives.count());
        assertFalse(nullPrimitives.enabled());

        Defaults referenceNull = (Defaults) binding.readNode("{\"label\":null,\"boxed\":null}", Defaults.class);
        assertNull(referenceNull.label());
        assertNull(referenceNull.boxed());
    }

    @Test
    void skipsStructuredUnknownPropertiesBeforeLaterKnownFields() {
        Ordered record = (Ordered) binding.readNode(
                "{\"name\":\"han\",\"unknown\":{\"deep\":[1,{\"x\":false}]},\"also_unknown\":[\"x\",null],\"age\":7,\"active\":true}",
                Ordered.class);

        assertEquals(new Ordered("han", 7, true), record);
    }

    @Test
    void bindsNestedRecordsCollectionsMapsAndArrays() {
        Envelope record = (Envelope) binding.readNode(
                "{\"child\":{\"name\":\"one\"},\"children\":[{\"name\":\"two\"}],\"by_id\":{\"three\":{\"name\":\"three\"}},\"scores\":[1,2,3]}",
                Envelope.class);

        assertEquals(new Child("one"), record.child());
        assertEquals(List.of(new Child("two")), record.children());
        assertEquals(new Child("three"), record.byId().get("three"));
        assertArrayEquals(new int[]{1, 2, 3}, record.scores());
    }

    @Test
    void appliesNamingStrategyAndBindsEnumAndLocalDate() {
        Snake snake = (Snake) binding.readNode("{\"first_name\":\"han\",\"account_id\":7}", Snake.class);
        assertEquals(new Snake("han", 7), snake);
        Map<?, ?> snakeOutput = (Map<?, ?>) binding.readNode(binding.writeNodeAsString(snake), Map.class);
        assertEquals("han", snakeOutput.get("first_name"));
        assertEquals(7, snakeOutput.get("account_id"));

        Typed typed = (Typed) binding.readNode("{\"state\":\"ACTIVE\",\"date\":\"2025-01-02\"}", Typed.class);
        assertEquals(new Typed(State.ACTIVE, LocalDate.of(2025, 1, 2)), typed);
        Map<?, ?> typedOutput = (Map<?, ?>) binding.readNode(binding.writeNodeAsString(typed), Map.class);
        assertEquals("ACTIVE", typedOutput.get("state"));
        assertEquals("2025-01-02", typedOutput.get("date"));
    }

    record Ordered(String name, int age, boolean active) {}
    record Renamed(@NodeProperty(value = "display_name", aliases = {"name", "legacy_name"}) String name, int age) {}
    record Defaults(int count, boolean enabled, String label, Integer boxed) {}
    record Child(String name) {}
    record Envelope(Child child, List<Child> children, @NodeProperty("by_id") Map<String, Child> byId, int[] scores) {}
    @NodeBinding(naming = NamingStrategy.SNAKE_CASE) record Snake(String firstName, int accountId) {}
    enum State { ACTIVE, INACTIVE }
    record Typed(State state, LocalDate date) {}
}
