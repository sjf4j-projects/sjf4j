package org.sjf4j.facades.snakeyaml;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.snake.SnakeYamlFacade;
import org.sjf4j.util.TypeReference;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.io.StringWriter;

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

        JsonObject jo2 = snake.readObject(new StringReader(ya1));
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
        JsonObject jo1 = JsonObject.fromJson(json1);
        SnakeYamlFacade snake = new SnakeYamlFacade();
        StringWriter sw = new StringWriter();
        snake.writeNode(sw, jo1);
        String ya1 = sw.toString();
        log.info("ya1: \n{}", ya1);

        Person p1 = snake.readObject(new StringReader(ya1), Person.class);
        log.info("p1={}", p1);
        assertEquals(123, p1.getId());
        assertEquals("han", p1.getName());
        assertEquals(175, p1.getHeight());
        assertEquals("han", p1.getStringByPath("$.name"));
        assertEquals(20, p1.getIntegerByPath("$.friends.rose.age[1]"));

        Person p2 = snake.readObject(new StringReader(ya1), new TypeReference<Person>(){});
        log.info("p2={}", p2);
        assertEquals(p1, p2);
    }


}
