package org.sjf4j.backend.fastjson2.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StreamingUuidCodecTest {
    private static final String UUID_TEXT = "123e4567-e89b-12d3-a456-426614174000";
    private static final UUID VALUE = UUID.fromString(UUID_TEXT);
    private static final String QUOTED = "\"" + UUID_TEXT + "\"";

    public static class Holder {
        public UUID id;
        public UUID optional;
    }

    @Test
    void streamsRootUuidInBothEncodingsAndPreservesNull() {
        Fastjson2Binder binder = new Fastjson2Binder();
        assertEquals(VALUE, binder.readNode(QUOTED, UUID.class));
        assertEquals(VALUE, binder.readNode(QUOTED.getBytes(StandardCharsets.UTF_8), UUID.class));
        assertEquals(QUOTED, binder.writeNodeAsString(VALUE));
        assertEquals(QUOTED, new String(binder.writeNodeAsBytes(VALUE), StandardCharsets.UTF_8));
        assertNull(binder.readNode("null", UUID.class));
        assertEquals("null", binder.writeNodeAsString(null));
    }

    @Test
    void streamsUuidPojoPropertyAndHonorsNullOmission() {
        Fastjson2Binder binder = new Fastjson2Binder();
        Holder holder = (Holder) binder.readNode(
                "{\"id\":" + QUOTED + ",\"optional\":null}", Holder.class);
        assertEquals(VALUE, holder.id);
        assertNull(holder.optional);

        String json = binder.writeNodeAsString(holder);
        assertTrue(json.contains("\"id\":" + QUOTED), json);
        assertTrue(json.contains("\"optional\":null"), json);

        Fastjson2Binder omitNulls = new Fastjson2Binder(
                com.alibaba.fastjson2.JSONFactory.createReadContext(),
                com.alibaba.fastjson2.JSONFactory.createWriteContext(), new RuntimeContext(false));
        assertFalse(omitNulls.writeNodeAsString(holder).contains("\"optional\""));
    }

    @Test
    void standardUuidFormattingIsBackendIndependent() {
        try (com.alibaba.fastjson2.JSONWriter direct = com.alibaba.fastjson2.JSONWriter.ofUTF8()) {
            direct.writeUUID(VALUE);
            assertEquals(QUOTED, new String(direct.getBytes(), StandardCharsets.UTF_8));
        }
    }
}
