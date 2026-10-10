package org.sjf4j.testbench.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Record components are implicit properties; compiler-generated methods and
 * unrelated zero-argument methods are not. Kept in testbench (Java 17) because
 * the core artifact is compiled to Java 8 bytecode.
 */
class RecordPropertySerializationTest {

    private static final Jackson2Binder BINDER = new Jackson2Binder();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record PlainRecord(long id, int age, String username, boolean active) {
        @Override
        public String toString() {
            return "record-display";
        }

        @Override
        public int hashCode() {
            return 42;
        }

        public String helper() {
            return "should-not-serialize";
        }

        public int calculated() {
            return 99;
        }
    }

    public record OptInRecord(String name) {
        public String getLabel() {
            return "bean-label";
        }

        @NodeProperty("explicit")
        public String custom() {
            return "annotated-value";
        }

        public String randomMethod() {
            return "not-a-property";
        }
    }

    public record ComponentNamedGet(String getCode) {
    }

    @Test
    void onlyRecordComponentsAreImplicitlySerialized() throws IOException {
        PlainRecord value = new PlainRecord(839201L, 34, "alice.builder", true);
        PojoInfo info = TypeRegistry.requireRegisteredPojoInfo(PlainRecord.class);
        Set<String> names = Arrays.stream(info.readableProperties)
                .map(p -> p.name).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList("id", "age", "username", "active")), names);

        Map<?, ?> json = MAPPER.readValue(BINDER.writeNodeAsString(value), Map.class);
        assertEquals(4, json.size());
        assertEquals(839201, ((Number) json.get("id")).longValue());
        assertEquals(34, ((Number) json.get("age")).intValue());
        assertEquals("alice.builder", json.get("username"));
        assertEquals(true, json.get("active"));
        assertFalse(json.containsKey("hashCode"));
        assertFalse(json.containsKey("toString"));
        assertFalse(json.containsKey("helper"));
        assertFalse(json.containsKey("calculated"));

        PlainRecord roundTrip = (PlainRecord) BINDER.readNode(BINDER.writeNodeAsString(value), PlainRecord.class);
        assertEquals(value.id(), roundTrip.id());
        assertEquals(value.age(), roundTrip.age());
        assertEquals(value.username(), roundTrip.username());
        assertEquals(value.active(), roundTrip.active());
    }

    @Test
    void explicitAndBeanGettersRemainAvailableOnRecords() throws IOException {
        OptInRecord value = new OptInRecord("alice");
        Map<?, ?> json = MAPPER.readValue(BINDER.writeNodeAsString(value), Map.class);
        assertEquals(3, json.size());
        assertEquals("alice", json.get("name"));
        assertEquals("bean-label", json.get("label"));
        assertEquals("annotated-value", json.get("explicit"));
        assertFalse(json.containsKey("randomMethod"));
    }

    @Test
    void componentAccessorNameTakesPrecedenceOverBeanStylePrefix() throws IOException {
        ComponentNamedGet value = new ComponentNamedGet("ABC");
        Map<?, ?> json = MAPPER.readValue(BINDER.writeNodeAsString(value), Map.class);
        assertEquals(1, json.size());
        assertEquals("ABC", json.get("getCode"));
        assertFalse(json.containsKey("code"));
    }
}
