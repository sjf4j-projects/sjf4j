package org.sjf4j.binding.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.binding.PropertiesBinding;
import org.sjf4j.exception.BindingException;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimplePropertiesBindingTest {
    private final PropertiesBinding binding = new SimplePropertiesBinding();

    @Test
    void roundTripsSupportedNestedObjectsAndArraysAsStrings() {
        JsonObject source = JsonObject.of("text", "hello", "number", 12, "flag", true,
                "nested", JsonObject.of("letter", 'x'),
                "items", JsonArray.of("zero", 1, JsonObject.of("name", "two")));
        Properties properties = new Properties();

        binding.writeNode(properties, source);
        JsonObject result = binding.readNode(properties);

        assertEquals("hello", result.getString("text"));
        assertEquals("12", result.getString("number"));
        assertEquals("true", result.getString("flag"));
        assertEquals("x", result.getJsonObject("nested").getString("letter"));
        JsonArray items = result.getJsonArray("items");
        assertEquals("zero", items.getString(0));
        assertEquals("1", items.getString(1));
        assertEquals("two", items.getJsonObject(2).getString("name"));
    }

    @Test
    void roundTripsSpecialKeysUsingRootedJsonPathExpressions() {
        JsonObject source = JsonObject.of("a.b", "dot", "with space", "space",
                "a[b]", "bracket", "quote'key", "quote");
        Properties properties = new Properties();

        binding.writeNode(properties, source);

        assertEquals("dot", properties.getProperty("['a.b']"));
        assertEquals("space", properties.getProperty("['with space']"));
        assertEquals("bracket", properties.getProperty("['a[b]']"));
        assertEquals("quote", properties.getProperty("['quote\\'key']"));
        assertEquals("dot", binding.readNode(properties).getString("a.b"));
        assertEquals("quote", binding.readNode(properties).getString("quote'key"));
    }

    @Test
    void readsElevenElementArraysInNumericalOrderRegardlessOfPropertyOrder() {
        Properties properties = new Properties();
        for (int i = 10; i >= 0; i--) properties.setProperty("items[" + i + "]", "v" + i);

        JsonArray items = binding.readNode(properties).getJsonArray("items");

        assertEquals(11, items.size());
        for (int i = 0; i < 11; i++) assertEquals("v" + i, items.getString(i));
    }

    @Test
    void omitsNullObjectPropertiesAndEmptyContainers() {
        JsonObject source = JsonObject.of("present", "yes", "nullValue", null,
                "emptyObject", new JsonObject(), "emptyArray", new JsonArray());
        Properties properties = new Properties();

        binding.writeNode(properties, source);

        assertEquals("yes", properties.getProperty("present"));
        assertFalse(properties.containsKey("nullValue"));
        assertFalse(properties.containsKey("emptyObject"));
        assertFalse(properties.containsKey("emptyArray"));
        JsonObject result = binding.readNode(properties);
        assertEquals("yes", result.getString("present"));
        assertFalse(result.containsKey("nullValue"));
        assertFalse(result.containsKey("emptyObject"));
        assertFalse(result.containsKey("emptyArray"));
    }

    @Test
    void rejectsNonObjectRootsAndNullArrayElements() {
        Properties properties = new Properties();

        assertThrows(BindingException.class, () -> binding.writeNode(properties, null));
        assertThrows(BindingException.class, () -> binding.writeNode(properties, JsonArray.of("x")));
        assertThrows(BindingException.class, () -> binding.writeNode(properties,
                JsonObject.of("items", JsonArray.of("x", null))));

        properties.setProperty("[0]", "root-array");
        assertThrows(BindingException.class, () -> binding.readNode(properties));
    }

    @Test
    void rejectsArrayElementsThatEmitNoScalarLeaf() {
        Properties properties = new Properties();

        assertThrows(BindingException.class, () -> binding.writeNode(properties,
                JsonObject.of("items", JsonArray.of(new JsonObject()))));
        assertThrows(BindingException.class, () -> binding.writeNode(properties,
                JsonObject.of("items", JsonArray.of(JsonObject.of("omitted", null)))));
        assertThrows(BindingException.class, () -> binding.writeNode(properties,
                JsonObject.of("items", JsonArray.of(new JsonArray()))));
    }

    @Test
    void rejectsSparseAndConflictingPropertyPaths() {
        Properties sparse = new Properties();
        sparse.setProperty("items[1]", "one");
        BindingException sparseError = assertThrows(BindingException.class, () -> binding.readNode(sparse));
        assertEquals("sparse array Properties path 'items[1]'", sparseError.getMessage());

        Properties conflict = new Properties();
        conflict.setProperty("a", "value");
        conflict.setProperty("a.b", "child");
        BindingException conflictError = assertThrows(BindingException.class, () -> binding.readNode(conflict));
        assertEquals("scalar/container path collision at Properties path 'a.b'", conflictError.getMessage());
    }

    @Test
    void replacesWrittenTopLevelPropertiesWithoutClearingUnrelatedProperties() {
        Properties properties = new Properties();
        properties.setProperty("existing", "keep");
        properties.setProperty("old", "still-here");

        binding.writeNode(properties, JsonObject.of("existing", "replace", "new", 1));

        assertEquals("replace", properties.getProperty("existing"));
        assertEquals("1", properties.getProperty("new"));
        assertEquals("still-here", properties.getProperty("old"));
    }

    @Test
    void replacesStaleDescendantsAndArrayProperties() {
        Properties properties = new Properties();
        properties.setProperty("config.old", "old");
        properties.setProperty("config.items[0]", "zero");
        properties.setProperty("config.items[1]", "one");
        properties.setProperty("other.value", "keep");

        binding.writeNode(properties, JsonObject.of("config", JsonObject.of("new", "value")));

        assertEquals("value", properties.getProperty("config.new"));
        assertFalse(properties.containsKey("config.old"));
        assertFalse(properties.containsKey("config.items[0]"));
        assertFalse(properties.containsKey("config.items[1]"));
        assertEquals("keep", properties.getProperty("other.value"));
    }

    @Test
    void nullAndEmptyTopLevelValuesRemoveTheirStaleProperties() {
        Properties properties = new Properties();
        properties.setProperty("nullValue.child", "old");
        properties.setProperty("emptyObject.child", "old");
        properties.setProperty("emptyArray[0]", "old");
        properties.setProperty("unrelated", "keep");

        binding.writeNode(properties, JsonObject.of("nullValue", null,
                "emptyObject", new JsonObject(), "emptyArray", new JsonArray()));

        assertFalse(properties.containsKey("nullValue.child"));
        assertFalse(properties.containsKey("emptyObject.child"));
        assertFalse(properties.containsKey("emptyArray[0]"));
        assertEquals("keep", properties.getProperty("unrelated"));
    }

    @Test
    void replacesSpecialQuotedTopLevelNames() {
        Properties properties = new Properties();
        properties.setProperty("['a.b'].old", "old");
        properties.setProperty("a.b", "unrelated");

        binding.writeNode(properties, JsonObject.of("a.b", "new"));

        assertFalse(properties.containsKey("['a.b'].old"));
        assertEquals("new", properties.getProperty("['a.b']"));
        assertEquals("unrelated", properties.getProperty("a.b"));
    }

    @Test
    void leavesPropertiesUnchangedWhenFlatteningFails() {
        Properties properties = new Properties();
        properties.setProperty("good", "old");
        properties.setProperty("bad[0]", "old");
        properties.setProperty("unrelated", "keep");

        assertThrows(BindingException.class, () -> binding.writeNode(properties,
                JsonObject.of("good", "new", "bad", JsonArray.of("valid", null))));

        assertEquals("old", properties.getProperty("good"));
        assertEquals("old", properties.getProperty("bad[0]"));
        assertEquals("keep", properties.getProperty("unrelated"));
    }
}
