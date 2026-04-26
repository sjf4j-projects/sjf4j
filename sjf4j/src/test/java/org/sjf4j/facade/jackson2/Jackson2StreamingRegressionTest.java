package org.sjf4j.facade.jackson2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson2StreamingRegressionTest {

    private static final String PERSON_JSON = "{\"id\":123,\"height\":175,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
    private static final String ARRAY_JSON = "{\"id\":123,\"name\":\"han\",\"tags\":[\"a\",{\"score\":2},null]}";

    static class Person extends JsonObject {
        public int id;
        public String name;
        public int height;
    }

    @Test
    void testExclusiveModeReadsJojoWithNestedDynamicValues() {
        Person person = (Person) newFacade(StreamingContext.StreamingMode.EXCLUSIVE_IO).readNode(PERSON_JSON, Person.class);
        assertPerson(person);
    }

    @Test
    void testPluginModuleReadsJojoWithNestedDynamicValues() {
        Person person = (Person) newFacade(StreamingContext.StreamingMode.PLUGIN_MODULE).readNode(PERSON_JSON, Person.class);
        assertPerson(person);
    }

    @Test
    void testAutoModeReadsJojoWithNestedDynamicValues() {
        Person person = (Person) new Jackson2JsonFacade(new ObjectMapper()).readNode(PERSON_JSON, Person.class);
        assertPerson(person);
    }

    @Test
    void testPluginModuleReadsJsonObjectWithNestedObjectAndArray() {
        JsonObject object = (JsonObject) newFacade(StreamingContext.StreamingMode.PLUGIN_MODULE).readNode(ARRAY_JSON, JsonObject.class);
        assertEquals(123, object.getInt("id"));
        assertEquals("han", object.getString("name"));
        assertEquals("a", object.getStringByPath("$.tags[0]"));
        assertEquals(2, object.getIntByPath("$.tags[1].score"));
        assertTrue(object.containsKey("tags"));
        assertNull(object.getByPath("$.tags[2]", Object.class));
    }

    private static Jackson2JsonFacade newFacade(StreamingContext.StreamingMode mode) {
        return new Jackson2JsonFacade(new ObjectMapper(), new StreamingContext(mode));
    }

    private static void assertPerson(Person person) {
        assertEquals(123, person.id);
        assertEquals("han", person.name);
        assertEquals(175, person.height);
        assertEquals("good", person.getStringByPath("$.friends.jack"));
        assertEquals(20, person.getIntByPath("$.friends.rose.age[1]"));
        assertEquals(true, person.getBoolean("sex"));
    }
}
