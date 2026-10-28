package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.NodeProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real generated binder coverage for the Reader-based prepared-name fast path
 * and the unchanged UTF-8 binary matcher.
 */
public class CompiledJackson3NameMatchTest {

    public static class User {
        public int id;
        @NodeProperty(aliases = "legacyName")
        public String name;
        public boolean enabled;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Reader {
        @ReadFrom User fromString(String json) throws IOException;
        @ReadFrom User fromUtf8(byte[] json) throws IOException;
    }

    @Test
    void handlesExpectedMismatchedAliasedAndUnknownNamesOnBothInputs() throws Exception {
        Reader binder = CompiledInstances.create(Reader.class);
        String[] documents = {
                "{\"id\":7,\"name\":\"Ada\",\"enabled\":true}",
                "{\"enabled\":true,\"name\":\"Ada\",\"id\":7}",
                "{\"id\":7,\"unknown\":{\"items\":[1,2]},\"legacyName\":\"Ada\",\"enabled\":true}"
        };
        for (String json : documents) {
            assertUser(binder.fromString(json));
            assertUser(binder.fromUtf8(json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static void assertUser(User user) {
        assertEquals(7, user.id);
        assertEquals("Ada", user.name);
        assertTrue(user.enabled);
    }
}
