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
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real generated binder coverage for both the standard Jackson 3 wrapper and
 * the optional fully-native POJO read path. The CI compiles and runs this
 * suite with sjf4j.jackson3.nativePojo=false and true independently.
 */
public class CompiledJackson3NameMatchTest {

    public static class User {
        public int id;
        @NodeProperty(aliases = {"legacyName", "旧名"})
        public String name;
        public boolean enabled;
        public Integer optionalCount;
        public Boolean optionalFlag;
        public List<String> tags;
        public List<Detail> details;
    }

    public static class Detail {
        public String label;
        public Integer rank;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Reader {
        @Read User fromString(String json) throws IOException;
        @Read User fromUtf8(byte[] json) throws IOException;
        @Read User fromReader(java.io.Reader json) throws IOException;
        @Read User fromInputStream(InputStream json) throws IOException;
    }

    @Test
    void handlesExpectedMismatchedAliasedAndUnknownNamesOnAllInputs() throws Exception {
        Reader binder = CompiledInstances.create(Reader.class);
        String[] documents = {
                "{\"id\":7,\"name\":\"Ada\",\"enabled\":true}",
                "{\"enabled\":true,\"name\":\"Ada\",\"id\":7}",
                "{\"id\":7,\"unknown\":{\"items\":[1,{\"nested\":true}]},"
                        + "\"legacyName\":\"Ada\",\"enabled\":true}",
                "{\"enabled\":true,\"旧名\":\"Ada\",\"id\":7}",
                "{\"legacyName\":\"outdated\",\"name\":\"Ada\",\"id\":7,\"enabled\":true}"
        };
        for (String json : documents) {
            assertUser(binder.fromString(json));
            assertUser(binder.fromUtf8(json.getBytes(StandardCharsets.UTF_8)));
            assertUser(binder.fromReader(new StringReader(json)));
            assertUser(binder.fromInputStream(new ByteArrayInputStream(
                    json.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    void handlesNestedListsBoxedNullsAndUnknownValues() throws Exception {
        Reader binder = CompiledInstances.create(Reader.class);
        String json = "{\"unused\":[{\"deep\":{\"array\":[1,2,3]}}],"
                + "\"旧名\":\"雪\",\"id\":9,\"enabled\":false,\"optionalCount\":null,"
                + "\"optionalFlag\":null,\"tags\":[null,\"🌿\"],"
                + "\"details\":[null,{\"label\":\"first\",\"rank\":3},"
                + "{\"label\":null,\"rank\":null}]}";
        for (User user : new User[]{
                binder.fromString(json),
                binder.fromUtf8(json.getBytes(StandardCharsets.UTF_8)),
                binder.fromReader(new StringReader(json)),
                binder.fromInputStream(new ByteArrayInputStream(
                        json.getBytes(StandardCharsets.UTF_8)))}) {
            assertEquals(9, user.id);
            assertEquals("雪", user.name);
            assertFalse(user.enabled);
            assertNull(user.optionalCount);
            assertNull(user.optionalFlag);
            assertEquals(2, user.tags.size());
            assertNull(user.tags.get(0));
            assertEquals("🌿", user.tags.get(1));
            assertEquals(3, user.details.size());
            assertNull(user.details.get(0));
            assertEquals("first", user.details.get(1).label);
            assertEquals(3, user.details.get(1).rank);
            assertNull(user.details.get(2).label);
            assertNull(user.details.get(2).rank);
        }

        assertNull(binder.fromString("null"));
        assertNull(binder.fromUtf8("null".getBytes(StandardCharsets.UTF_8)));
        User empty = binder.fromString("{\"tags\":[],\"details\":null}");
        assertTrue(empty.tags.isEmpty());
        assertNull(empty.details);
    }

    @Test
    void rejectsInvalidTokenTypesAndIncompleteContainers() {
        Reader binder = CompiledInstances.create(Reader.class);
        String[] invalid = {
                "[]",
                "{\"id\":null}",
                "{\"id\":\"7\"}",
                "{\"enabled\":1}",
                "{\"name\":false}",
                "{\"tags\":{}}",
                "{\"tags\":[3]}",
                "{\"details\":[{\"rank\":\"not-a-number\"}]}",
                "{\"unknown\":[}",
                "{\"id\":7",
                "{\"id\":7} true"
        };
        for (String json : invalid) {
            assertThrows(Exception.class, () -> binder.fromString(json), json);
            assertThrows(Exception.class,
                    () -> binder.fromUtf8(json.getBytes(StandardCharsets.UTF_8)), json);
        }
    }

    private static void assertUser(User user) {
        assertEquals(7, user.id);
        assertEquals("Ada", user.name);
        assertTrue(user.enabled);
    }
}
