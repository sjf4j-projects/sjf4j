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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GsonBinderTest {

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

        try (GsonReader reader = binder.createReader("\"héllo\"")) {
            assertEquals("héllo", reader.readString());
        }
        try (GsonReader reader = binder.createReader(json)) {
            assertEquals("héllo", reader.readString());
        }
        try (GsonReader reader = binder.createReader(new ByteArrayInputStream(json))) {
            assertEquals("héllo", reader.readString());
        }
        try (GsonReader reader = binder.createReader(new StringReader("\"héllo\""))) {
            assertEquals("héllo", reader.readString());
        }
        try (GsonReader reader = binder.createReader(new JsonReader(new StringReader("\"héllo\"")))) {
            assertEquals("héllo", reader.readString());
        }

        StringWriter text = new StringWriter();
        try (GsonWriter writer = binder.createWriter(text)) {
            writer.writeStringValue("héllo");
            writer.flush();
        }
        assertEquals("\"héllo\"", text.toString());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GsonWriter writer = binder.createWriter(bytes)) {
            writer.writeStringValue("héllo");
            writer.flush();
        }
        assertEquals("\"héllo\"", new String(bytes.toByteArray(), StandardCharsets.UTF_8));

        StringWriter nativeOutput = new StringWriter();
        try (GsonWriter writer = binder.createWriter(new JsonWriter(nativeOutput))) {
            writer.writeStringValue("héllo");
            writer.flush();
        }
        assertEquals("\"héllo\"", nativeOutput.toString());
    }

    @Test
    void preservesExternalJsonNullsWhenContextOmitsOrdinaryNulls() {
        JsonObject object = new JsonObject();
        object.add("empty", JsonNull.INSTANCE);

        JsonObject output = JsonParser.parseString(
                new GsonBinder(new Gson(), new RuntimeContext(false)).writeNodeAsString(object))
                .getAsJsonObject();

        assertTrue(output.get("empty").isJsonNull());
    }

    @Test
    void rejectsNullDependenciesAndIo() {
        assertThrows(NullPointerException.class, () -> new GsonBinder(null));
        assertThrows(NullPointerException.class, () -> new GsonBinder(new Gson(), null));

        GsonBinder binder = new GsonBinder(new Gson());
        assertThrows(NullPointerException.class, () -> binder.createReader((Reader) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((Writer) null));
        assertThrows(NullPointerException.class, () -> binder.createReader((JsonReader) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((JsonWriter) null));
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
