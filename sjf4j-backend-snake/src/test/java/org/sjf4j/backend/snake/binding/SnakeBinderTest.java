package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.TypeReference;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeBinderTest {

    private static final String PERSON_YAML = ""
            + "id: 123\n"
            + "height: 175\n"
            + "name: han\n"
            + "friends:\n"
            + "  jack: good\n"
            + "  rose:\n"
            + "    age: [18, 20]\n"
            + "sex: true\n";

    private static final String UNKNOWN_NESTED_YAML = ""
            + "skipObject:\n"
            + "  value: [true, false, null, {deep: value}]\n"
            + "skipArray: [1, 2, {nested: [3, 4]}]\n"
            + "name: Jack\n";

    @Test
    void roundTripsJsonObject() {
        SnakeBinder binder = new SnakeBinder();
        JsonObject source = JsonObject.of(
                "id", 123,
                "name", "han",
                "friends", Map.of("jack", "good", "ages", Arrays.asList(18, 20)),
                "active", true
        );

        JsonObject target = (JsonObject) binder.readNode(
                binder.writeNodeAsString(source), JsonObject.class);

        assertEquals(source, target);
    }

    @Test
    void roundTripsPojo() {
        SnakeBinder binder = new SnakeBinder();
        Person source = new Person(7, "Ada", Arrays.asList("math", "logic"));

        Person target = (Person) binder.readNode(binder.writeNodeAsString(source), Person.class);

        assertEquals(source.id, target.id);
        assertEquals(source.name, target.name);
        assertEquals(source.tags, target.tags);
    }

    @Test
    void readsJsonObjectSubclassThroughReaderAndTypeReferences() {
        SnakeBinder binder = new SnakeBinder();

        PersonObject direct = (PersonObject) binder.readNode(new StringReader(PERSON_YAML), PersonObject.class);
        assertPerson(direct);

        PersonObject person = (PersonObject) binder.readNode(
                PERSON_YAML, new TypeReference<PersonObject>() {}.getType());
        assertPerson(person);

        @SuppressWarnings("unchecked")
        List<PersonObject> people = (List<PersonObject>) binder.readNode(
                "- " + PERSON_YAML.replace("\n", "\n  "),
                new TypeReference<List<PersonObject>>() {}.getType());
        assertEquals(1, people.size());
        assertPerson(people.get(0));
    }

    @Test
    void ignoresUnknownNestedFieldsForPojo() {
        NameOnlyUser user = (NameOnlyUser) new SnakeBinder().readNode(
                UNKNOWN_NESTED_YAML, NameOnlyUser.class);

        assertEquals("Jack", user.name);
    }

    private static void assertPerson(PersonObject person) {
        assertEquals(123, person.id);
        assertEquals("han", person.name);
        assertEquals(175, person.height);
        assertEquals("good", person.getStringByPath("$.friends.jack"));
        assertEquals(20, person.getIntByPath("$.friends.rose.age[1]"));
        assertTrue(person.getBoolean("sex"));
        assertNull(person.getNode("missing"));
    }

    public static class Person {
        public int id;
        public String name;
        public List<String> tags;

        public Person() {
        }

        Person(int id, String name, List<String> tags) {
            this.id = id;
            this.name = name;
            this.tags = tags;
        }
    }

    public static class PersonObject extends JsonObject {
        public int id;
        public String name;
        public int height;
    }

    public static class NameOnlyUser {
        public String name;
    }
}
