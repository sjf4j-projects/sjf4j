package org.sjf4j.facade;

import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.TypeReference;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class CodecFacadeAssertions {

    private CodecFacadeAssertions() {}

    @NodeValue
    public static class Ops {
        private final LocalDate localDate;

        Ops(LocalDate localDate) {
            this.localDate = localDate;
        }

        @ValueToRaw
        String encode() {
            return localDate.toString();
        }

        @RawToValue
        static Ops decode(String raw) {
            return new Ops(LocalDate.parse(raw));
        }
    }

    public static class BookField extends JsonObject {
        private int id;
        private String name;

        @NodeProperty("user_name")
        private String userName;
        private double height;
        private transient int transientHeight;
    }

    public static class InstantFieldBook {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant createdAt;
        public Instant updatedAt;
    }

    public static class InstantCreatorBook {
        public final Instant createdAt;
        public final Instant updatedAt;

        @NodeCreator
        public InstantCreatorBook(@NodeProperty(value = "createdAt", valueFormat = "epochMillis") Instant createdAt,
                                  @NodeProperty("updatedAt") Instant updatedAt) {
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    @SuppressWarnings("unchecked")
    public static void assertNodeValue(JsonFacade<?, ?> facade) {
        NodeRegistry.registerTypeInfo(Ops.class);

        String json = "[\"2024-10-01\",\"2025-12-18\"]";
        List<Ops> values = (List<Ops>) facade.readNode(json, new TypeReference<List<Ops>>() {}.getType());
        assertEquals(2, values.size());
        assertEquals("2024-10-01", values.get(0).encode());
        assertEquals("2025-12-18", values.get(1).encode());
        assertEquals(json, facade.writeNodeAsString(values));
    }

    public static void assertNodeField(JsonFacade<?, ?> facade, String readJson, String writeJson) {
        BookField book = (BookField) facade.readNode(readJson, BookField.class);
        assertEquals("han", book.userName);
        assertEquals("han", book.getString("user_name"));
        assertNull(book.getString("userName"));
        assertEquals(0.0, book.height);
        assertEquals(0, book.transientHeight);
        assertEquals(writeJson, facade.writeNodeAsString(book));
    }

    public static void assertValueFormat(JsonFacade<?, ?> facade) {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        long epochMillis = instant.toEpochMilli();
        String json = "{\"createdAt\":" + epochMillis + ",\"updatedAt\":\"" + instant + "\"}";

        InstantFieldBook book = (InstantFieldBook) facade.readNode(json, InstantFieldBook.class);
        assertEquals(instant, book.createdAt);
        assertEquals(instant, book.updatedAt);
        assertEquals(json, facade.writeNodeAsString(book));

        InstantCreatorBook creatorBook = (InstantCreatorBook) facade.readNode(json, InstantCreatorBook.class);
        assertEquals(instant, creatorBook.createdAt);
        assertEquals(instant, creatorBook.updatedAt);
    }

    public static void assertConfiguredInstantValueFormat(JsonFacade<?, ?> facade) {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        long epochMillis = instant.toEpochMilli();
        assertEquals(String.valueOf(epochMillis), facade.writeNodeAsString(instant));
        assertEquals(instant, facade.readNode(String.valueOf(epochMillis), Instant.class));
    }
}
