package org.sjf4j.testbench.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Write;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for Jackson3 compiled String-member null handling. */
public class CompiledJackson3StringMemberTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static class Nested {
        public String optional;
        public String text;
    }

    public static class Payload {
        public String text;
        public String optional;
        public Nested nested;
        public List<String> tags;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Writer {
        @Write byte[] write(Payload value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Writer {
        @Write byte[] write(Payload value) throws IOException;
    }

    @Test
    void jackson3StaticStringMemberPreservesNullPolicy() throws Exception {
        Payload value = fixture();
        for (boolean includeNulls : new boolean[]{true, false}) {
            JsonNode actual = JSON.readTree(CompiledInstances
                    .create(Jackson3Writer.class, new RuntimeContext(includeNulls))
                    .write(value));
            verifyNullPolicy(actual, includeNulls);

            // Jackson2 is deliberately unchanged: both backends must have
            // equivalent output semantics for the same RuntimeContext.
            JsonNode jackson2 = JSON.readTree(CompiledInstances
                    .create(Jackson2Writer.class, new RuntimeContext(includeNulls))
                    .write(value));
            assertEquals(jackson2, actual);
        }
    }

    @Test
    void jackson3GeneratedMemberHelperIsStatic() {
        Jackson3Writer writer = CompiledInstances.create(Jackson3Writer.class);
        List<Method> helpers = Arrays.stream(writer.getClass().getDeclaredMethods())
                .filter(m -> m.getName().startsWith("_writeStringMember"))
                .toList();
        assertEquals(1, helpers.size());
        assertTrue(Modifier.isStatic(helpers.get(0).getModifiers()));
    }

    private static Payload fixture() {
        Payload result = new Payload();
        result.text = "Hello \"世界\"";
        result.optional = null;
        result.nested = new Nested();
        result.nested.text = "a\\b";
        result.nested.optional = null;
        result.tags = Arrays.asList("first", null, "第三");
        return result;
    }

    private static void verifyNullPolicy(JsonNode node, boolean includeNulls) {
        assertEquals("Hello \"世界\"", node.path("text").asText());
        assertEquals("a\\b", node.path("nested").path("text").asText());
        assertEquals("first", node.path("tags").path(0).asText());
        assertTrue(node.path("tags").path(1).isNull(),
                "Null array elements must not be omitted");
        assertEquals("第三", node.path("tags").path(2).asText());

        if (includeNulls) {
            assertTrue(node.has("optional"));
            assertTrue(node.get("optional").isNull());
            assertTrue(node.path("nested").has("optional"));
        } else {
            assertFalse(node.has("optional"));
            assertFalse(node.path("nested").has("optional"));
        }
    }
}
