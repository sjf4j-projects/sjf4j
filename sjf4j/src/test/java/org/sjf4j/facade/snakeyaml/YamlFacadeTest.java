package org.sjf4j.facade.snakeyaml;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.facade.snake.SnakeYamlFacade;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class YamlFacadeTest {

    @Test
    public void testOne() {
        Yaml yaml = new Yaml();
        yaml.load("");
    }

    @Test
    public void testSerDe1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        SnakeYamlFacade snake = new SnakeYamlFacade();
        StringWriter sw = new StringWriter();
        snake.writeNode(sw, jo1);
        String ya1 = sw.toString();
        log.info("ya1: \n{}", ya1);

        JsonObject jo2 = (JsonObject) snake.readNode(new StringReader(ya1), JsonObject.class);
        log.info("jo1.hash={}, jo2.hash={}", jo1.hashCode(), jo2.hashCode());
        assertEquals(jo1, jo2);
    }


    @Getter @Setter
    static class Person extends JsonObject {
        private int id;
        private String name;
        private int height;
    }

    @Test
    public void testJojo1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Person px = Sjf4j.fromJson(json1, Person.class);
        log.info("px={}", px);

        JsonObject jo1 = JsonObject.fromJson(json1);
        SnakeYamlFacade snake = new SnakeYamlFacade();
        StringWriter sw = new StringWriter();
        snake.writeNode(sw, jo1);
        String ya1 = sw.toString();
        log.info("ya1: \n{}", ya1);

        Person p1 = (Person) snake.readNode(new StringReader(ya1), Person.class);
        log.info("p1={}", p1);
        assertEquals(123, p1.getId());
        assertEquals("han", p1.getName());
        assertEquals(175, p1.getHeight());
        assertEquals("han", p1.getStringByPath("$.name"));
        assertEquals(20, p1.getIntegerByPath("$.friends.rose.age[1]"));

        Person p2 = (Person) snake.readNode(new StringReader(ya1), new TypeReference<Person>(){}.getType());
        log.info("p2={}", p2);
        assertEquals(p1, p2);
    }


    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext;
    }

    @Test
    void testSkipNode1() {
        SnakeYamlFacade facade = new SnakeYamlFacade();
        String json = "{\n" +
                "  \"id\": 7,\n" +
                "  \"skipObj\": {\n" +
                "    \"x\": [true, false, null, {\"deep\": \"v, }\"}],\n" +
                "    \"y\": 2\n" +
                "  },\n" +
                "  \"skipArr\": [1,2,{\"a\":[3,4]}],\n" +
                "  \"skipStr\": \"wa,w[]{}a\",\n" +
                "  \"skipNumber\": -334455,\n" +
                "  \"skipBoolean\": false,\n" +
                "  \"skipNull\": null,\n" +
                "  \"name\": \"Jack\"\n" +
                "}";
        User pojo = (User) facade.readNode(json, User.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("Jack", pojo.name);
    }

}
