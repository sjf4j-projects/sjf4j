package org.sjf4j.facade.snakeyaml;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.snake.SnakeYamlFacade;
import org.sjf4j.node.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlFacadeTest {

    private static final String PERSON_JSON = "{\"id\":123,\"height\":175,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

    private static final String PERSON_YAML = ""
            + "id: 123\n"
            + "height: 175\n"
            + "name: han\n"
            + "friends:\n"
            + "  jack: good\n"
            + "  rose:\n"
            + "    age:\n"
            + "      - 18\n"
            + "      - 20\n"
            + "sex: true\n";

    private static final String SKIP_YAML = ""
            + "skipObj:\n"
            + "  x:\n"
            + "    - true\n"
            + "    - false\n"
            + "    - null\n"
            + "    - deep: v\n"
            + "  y: 2\n"
            + "skipArr:\n"
            + "  - 1\n"
            + "  - 2\n"
            + "  - a:\n"
            + "      - 3\n"
            + "      - 4\n"
            + "name: Jack\n";

    static class Person extends JsonObject {
        public int id;
        public String name;
        public int height;
    }

    static class NameOnlyUser {
        public String name;
    }

    @Test
    void testRoundTripJsonObjectThroughYaml() {
        SnakeYamlFacade facade = new SnakeYamlFacade();
        JsonObject source = JsonObject.fromJson(PERSON_JSON);

        StringWriter out = new StringWriter();
        facade.writeNode(out, source);

        JsonObject target = (JsonObject) facade.readNode(new StringReader(out.toString()), JsonObject.class);
        assertEquals(source, target);
    }

    @Test
    void testReadJojoFromYamlKeepsDynamicFields() {
        SnakeYamlFacade facade = new SnakeYamlFacade();
        assertPerson((Person) facade.readNode(new StringReader(PERSON_YAML), Person.class));
    }

    @Test
    void testReadJojoFromYamlByTypeReferenceKeepsDynamicFields() {
        SnakeYamlFacade facade = new SnakeYamlFacade();
        Person person = (Person) facade.readNode(new StringReader(PERSON_YAML), new TypeReference<Person>() {}.getType());
        assertPerson(person);
    }

    @Test
    void testSkipUnknownNestedNodes() {
        SnakeYamlFacade facade = new SnakeYamlFacade();
        NameOnlyUser user = (NameOnlyUser) facade.readNode(SKIP_YAML, NameOnlyUser.class);
        assertEquals("Jack", user.name);
    }

    private static void assertPerson(Person person) {
        assertEquals(123, person.id);
        assertEquals("han", person.name);
        assertEquals(175, person.height);
        assertEquals("han", person.getStringByPath("$.name"));
        assertEquals("good", person.getStringByPath("$.friends.jack"));
        assertEquals(20, person.getIntByPath("$.friends.rose.age[1]"));
        assertEquals(true, person.getBoolean("sex"));
    }
}
