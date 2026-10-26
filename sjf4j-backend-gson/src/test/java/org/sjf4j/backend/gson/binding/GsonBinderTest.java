package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GsonBinderTest {


    @Test
    void doesNotCloseCallerOwnedIo() {
        GsonBinder binder = new GsonBinder();

        AtomicBoolean readerClosed = new AtomicBoolean();
        StringReader input = new StringReader("\"hello\"") {
            @Override
            public void close() {
                readerClosed.set(true);
            }
        };

        assertEquals("hello", binder.readNode(input, String.class));
        assertFalse(readerClosed.get());

        AtomicBoolean writerClosed = new AtomicBoolean();
        StringWriter output = new StringWriter() {
            @Override
            public void close() {
                writerClosed.set(true);
            }
        };

        binder.writeNode(output, "hello");

        assertEquals("\"hello\"", output.toString());
        assertFalse(writerClosed.get());
    }

    @Test
    void externalJsonWriterEnablesExplicitNullSerialization()
            throws Exception {

        GsonBinder binder = new GsonBinder();
        StringWriter output = new StringWriter();
        JsonWriter nativeWriter = new JsonWriter(output);
        nativeWriter.setSerializeNulls(false);

        {
            GsonGenerator writer = binder.createGenerator(nativeWriter);
            try {
                assertTrue(nativeWriter.getSerializeNulls());

                writer.startObject();
                writer.writeName("value");
                writer.writeNull();
                writer.endObject();
                writer.endWrite();

            } finally {
                writer.release();
            }
        }

        assertEquals("{\"value\":null}", output.toString());
    }

    @Test
    void readsPojoLikeNativeGsonAndIgnoresUnknownProperties() {
        String json = "{\"id\":7,\"title\":\"Ada\",\"details\":{\"active\":true,\"note\":\"nested\"},"
                + "\"tags\":[\"one\",\"two\"],\"nullable\":null,\"unknown\":\"ignored\"}";
        Gson gson = new Gson();

        Document nativeValue = gson.fromJson(json, Document.class);
        Document binderValue = (Document) new GsonBinder(gson).readNode(json, Document.class);

        assertDocumentEquals(nativeValue, binderValue);
        assertEquals(null, binderValue.nullable);
    }

    @Test
    void writesPojoLikeNativeGsonWithEquivalentSettings() {
        Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        Document value = document();

        JsonObject nativeJson = JsonParser.parseString(gson.toJson(value)).getAsJsonObject();
        String binderOutput = new GsonBinder(gson).writeNodeAsString(value);
        JsonObject binderJson = JsonParser.parseString(binderOutput).getAsJsonObject();

        assertEquals(nativeJson, binderJson);
        assertTrue(binderOutput.contains("<Ada & Bob>"));
        assertEquals("<Ada & Bob>", binderJson.get("title").getAsString());
        assertTrue(binderJson.get("nullable").isJsonNull());
    }

    @Test
    void runtimeContextControlsNullSerialization() {
        Document value = document();
        Gson gson = new Gson();

        JsonObject nativeJson = JsonParser.parseString(gson.toJson(value)).getAsJsonObject();
        JsonObject binderJson = JsonParser.parseString(new GsonBinder(gson).writeNodeAsString(value)).getAsJsonObject();
        JsonObject omitNullsJson = JsonParser.parseString(
                new GsonBinder(gson, new RuntimeContext(false)).writeNodeAsString(value)).getAsJsonObject();

        assertFalse(nativeJson.has("nullable"));
        assertTrue(binderJson.get("nullable").isJsonNull());
        assertFalse(omitNullsJson.has("nullable"));
    }

    @Test
    void createsReadersAndWritersForAllSupportedInputsAndOutputs() throws Exception {
        GsonBinder binder = new GsonBinder();
        byte[] json = "\"héllo\"".getBytes(StandardCharsets.UTF_8);

        {
            GsonParser reader = binder.createParser("\"héllo\"");
            try {
                assertEquals("héllo", reader.readString());

            } finally {
                reader.release();
            }
        }
        {
            GsonParser reader = binder.createParser(json);
            try {
                assertEquals("héllo", reader.readString());

            } finally {
                reader.release();
            }
        }
        {
            GsonParser reader = binder.createParser(new ByteArrayInputStream(json));
            try {
                assertEquals("héllo", reader.readString());

            } finally {
                reader.release();
            }
        }
        {
            GsonParser reader = binder.createParser(new StringReader("\"héllo\""));
            try {
                assertEquals("héllo", reader.readString());

            } finally {
                reader.release();
            }
        }
        {
            GsonParser reader = binder.createParser(new JsonReader(new StringReader("\"héllo\"")));
            try {
                assertEquals("héllo", reader.readString());
            } finally {
                reader.release();
            }
        }

        StringWriter text = new StringWriter();
        {
            GsonGenerator writer = binder.createGenerator(text);
            try {
                writer.writeStringValue("héllo");
                writer.endWrite();

            } finally {
                writer.release();
            }
        }
        assertEquals("\"héllo\"", text.toString());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        {
            GsonGenerator writer = binder.createGenerator(bytes);
            try {
                writer.writeStringValue("héllo");
                writer.endWrite();

            } finally {
                writer.release();
            }
        }
        assertEquals("\"héllo\"", new String(bytes.toByteArray(), StandardCharsets.UTF_8));

        StringWriter nativeOutput = new StringWriter();
        {
            GsonGenerator writer = binder.createGenerator(new JsonWriter(nativeOutput));
            try {
                writer.writeStringValue("héllo");
                writer.endWrite();
            } finally {
                writer.release();
            }
        }
        assertEquals("\"héllo\"", nativeOutput.toString());
    }

    @Test
    void includeNullsControlsNativeJsonNullObjectProperties() {
        JsonObject object = new JsonObject();
        object.add("empty", JsonNull.INSTANCE);

        GsonBinder withoutNulls = new GsonBinder(new Gson(), new RuntimeContext(false));
        GsonBinder withNulls = new GsonBinder(new Gson(), RuntimeContext.EMPTY);

        assertFalse(JsonParser.parseString(withoutNulls.writeNodeAsString(object))
                .getAsJsonObject().has("empty"));
        assertTrue(JsonParser.parseString(withNulls.writeNodeAsString(object))
                .getAsJsonObject().get("empty").isJsonNull());
    }

    @Test
    void omitsNativeAndJavaNullObjectPropertiesButPreservesNullArrayElements() {
        JsonObject nativeTree = new JsonObject();
        nativeTree.add("explicit", JsonNull.INSTANCE);
        JsonObject child = new JsonObject();
        child.add("explicit", JsonNull.INSTANCE);
        nativeTree.add("child", child);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        array.add(JsonNull.INSTANCE);
        array.add("x");
        nativeTree.add("items", array);

        java.util.Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
        wrapper.put("ordinary", null);
        wrapper.put("native", nativeTree);
        GsonBinder binder = new GsonBinder(new Gson(), new RuntimeContext(false));
        assertEquals(
                "{\"native\":{\"child\":{},\"items\":[null,\"x\"]}}",
                binder.writeNodeAsString(wrapper));
    }

    @Test
    void rejectsNullDependenciesAndIo() {
        assertThrows(NullPointerException.class, () -> new GsonBinder(new Gson(), null));

        GsonBinder binder = new GsonBinder(new Gson());
        assertThrows(NullPointerException.class, () -> binder.createParser((Reader) null));
        assertThrows(NullPointerException.class, () -> binder.createGenerator((Writer) null));
        assertThrows(NullPointerException.class, () -> binder.createParser((JsonReader) null));
        assertThrows(NullPointerException.class, () -> binder.createGenerator((JsonWriter) null));
    }

    private static Document document() {
        return new Document(7, "<Ada & Bob>", new Details(true, "nested"), Arrays.asList("one", "two"), null);
    }

    private static void assertDocumentEquals(Document expected, Document actual) {
        assertEquals(expected.id, actual.id);
        assertEquals(expected.title, actual.title);
        assertEquals(expected.details.active, actual.details.active);
        assertEquals(expected.details.note, actual.details.note);
        assertEquals(expected.tags, actual.tags);
        assertEquals(expected.nullable, actual.nullable);
    }

    static class Document {
        public int id;
        public String title;
        public Details details;
        public List<String> tags;
        public String nullable;

        Document() {
        }

        Document(int id, String title, Details details, List<String> tags, String nullable) {
            this.id = id;
            this.title = title;
            this.details = details;
            this.tags = tags;
            this.nullable = nullable;
        }
    }

    static class Details {
        public boolean active;
        public String note;

        Details() {
        }

        Details(boolean active, String note) {
            this.active = active;
            this.note = note;
        }
    }
}
