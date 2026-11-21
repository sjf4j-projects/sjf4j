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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compiles under standard, native-only, and mixed-native Jackson3 codegen.
 * The mixed mode must never consume a value token twice when handing a field
 * back to the ordinary Compiled reader.
 */
public class CompiledJackson3HybridTest {

    public enum Status { NEW, ACTIVE }

    public static class Detail {
        public String label;
        public int count;
    }

    public static class Order {
        public int id;
        @NodeProperty(aliases = {"旧名"})
        public String name;
        public BigDecimal amount;
        public Status status;
        public short shortNumber;
        public List<Integer> counts;
        public Map<String, String> attributes;
        public List<Detail> details;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface HybridReader {
        @Read Order fromUtf8(byte[] input) throws IOException;
        @Read Order fromString(String input) throws IOException;
        @Read Order fromReader(java.io.Reader input) throws IOException;
        @Read Order fromInputStream(InputStream input) throws IOException;
    }

    private final HybridReader binder = CompiledInstances.create(HybridReader.class);

    @Test
    void mixedFieldsAndNestedContainersAcrossInputTypes() throws Exception {
        String[] documents = {
                "{\"id\":7,\"name\":\"Ada\",\"amount\":12.50,\"status\":\"ACTIVE\","
                        + "\"shortNumber\":12,\"counts\":[1,null,3],"
                        + "\"attributes\":{\"x\":\"y\"},"
                        + "\"details\":[{\"label\":\"child\",\"count\":2}],"
                        + "\"ignored\":{\"sub\":[true,{\"deep\":null}]}}",
                "{\"ignored\":[{\"nested\":1}],\"details\":[{\"count\":2,\"label\":\"child\"}],"
                        + "\"attributes\":{\"x\":\"y\"},\"counts\":[1,null,3],"
                        + "\"status\":\"ACTIVE\",\"amount\":12.50,\"shortNumber\":12,"
                        + "\"旧名\":\"Ada\",\"id\":7}"
        };
        for (String json : documents) {
            byte[] utf8 = json.getBytes(StandardCharsets.UTF_8);
            check(binder.fromUtf8(utf8));
            check(binder.fromString(json));
            check(binder.fromReader(new StringReader(json)));
            check(binder.fromInputStream(new ByteArrayInputStream(utf8)));
        }
    }

    @Test
    void nullValuesAndAfterFallbackNativeFields() throws Exception {
        String json = "{\"amount\":null,\"status\":null,\"counts\":null,"
                + "\"shortNumber\":0,\"attributes\":null,\"id\":9,\"name\":\"after\","
                + "\"details\":[null,{\"label\":null,\"count\":0}]}";
        Order result = binder.fromUtf8(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(9, result.id);
        assertEquals("after", result.name);
        assertNull(result.amount);
        assertNull(result.status);
        assertNull(result.counts);
        assertNull(result.attributes);
        assertEquals(2, result.details.size());
        assertNull(result.details.get(0));
        assertNull(result.details.get(1).label);
        assertNull(binder.fromUtf8("null".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void invalidValuesAndTruncatedInputMustStillFail() {
        for (String json : new String[]{
                "{\"id\":1,\"amount\":{}}",
                "{\"id\":1,\"status\":\"NOT_A_STATUS\"}",
                "{\"id\":1,\"shortNumber\":32768}",
                "{\"id\":1,\"counts\":[1,{\"x\":3}]}",
                "{\"id\":1,\"attributes\":[1]}",
                "{\"id\":1,\"details\":[{\"label\":false}]}",
                "{\"id\":1,\"amount\":1.00",
                "{\"id\":1} false"
        }) {
            assertThrows(Exception.class,
                    () -> binder.fromUtf8(json.getBytes(StandardCharsets.UTF_8)), json);
            assertThrows(Exception.class, () -> binder.fromString(json), json);
        }
    }

    private static void check(Order result) {
        assertNotNull(result);
        assertEquals(7, result.id);
        assertEquals("Ada", result.name);
        assertEquals(0, new BigDecimal("12.50").compareTo(result.amount));
        assertEquals(Status.ACTIVE, result.status);
        assertEquals(12, result.shortNumber);
        assertEquals(java.util.Arrays.asList(1, null, 3), result.counts);
        assertEquals("y", result.attributes.get("x"));
        assertEquals(1, result.details.size());
        assertEquals("child", result.details.get(0).label);
        assertEquals(2, result.details.get(0).count);
    }
}
