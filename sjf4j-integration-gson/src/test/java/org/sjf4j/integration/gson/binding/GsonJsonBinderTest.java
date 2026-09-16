package org.sjf4j.integration.gson.binding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;

import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GsonJsonBinderTest {

    @Test
    void readsPojoLikeNativeGsonAndIgnoresUnknownProperties() {
        String json = "{\"id\":7,\"title\":\"Ada\",\"details\":{\"active\":true,\"note\":\"nested\"},"
                + "\"tags\":[\"one\",\"two\"],\"nullable\":null,\"unknown\":\"ignored\"}";
        Gson gson = new Gson();

        Document nativeValue = gson.fromJson(json, Document.class);
        Document binderValue = (Document) new GsonJsonBinder(gson).readNode(json, Document.class);

        assertDocumentEquals(nativeValue, binderValue);
        assertEquals(null, binderValue.nullable);
    }

    @Test
    void writesPojoLikeNativeGsonWithEquivalentSettings() {
        Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        Document value = document();

        JsonObject nativeJson = JsonParser.parseString(gson.toJson(value)).getAsJsonObject();
        String binderOutput = new GsonJsonBinder(gson).writeNodeAsString(value);
        JsonObject binderJson = JsonParser.parseString(binderOutput).getAsJsonObject();

        assertEquals(nativeJson, binderJson);
        assertTrue(binderOutput.contains("<Ada & Bob>"));
        assertEquals("<Ada & Bob>", binderJson.get("title").getAsString());
        assertTrue(binderJson.get("nullable").isJsonNull());
    }

    @Test
    void documentsIntentionalDefaultNullPolicyDifference() {
        Document value = document();
        Gson gson = new Gson();

        JsonObject nativeJson = JsonParser.parseString(gson.toJson(value)).getAsJsonObject();
        JsonObject binderJson = JsonParser.parseString(new GsonJsonBinder(gson).writeNodeAsString(value)).getAsJsonObject();
        JsonObject omitNullsJson = JsonParser.parseString(
                new GsonJsonBinder(gson, new StreamingContext(false)).writeNodeAsString(value)).getAsJsonObject();

        assertFalse(nativeJson.has("nullable"));
        assertTrue(binderJson.get("nullable").isJsonNull());
        assertFalse(omitNullsJson.has("nullable"));
    }

    @Test
    void rejectsNullDependenciesAndIo() {
        assertThrows(NullPointerException.class, () -> new GsonJsonBinder(null));
        assertThrows(NullPointerException.class, () -> new GsonJsonBinder(new Gson(), null));

        GsonJsonBinder binder = new GsonJsonBinder(new Gson());
        assertThrows(NullPointerException.class, () -> binder.createReader((Reader) null));
        assertThrows(NullPointerException.class, () -> binder.createWriter((Writer) null));
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
