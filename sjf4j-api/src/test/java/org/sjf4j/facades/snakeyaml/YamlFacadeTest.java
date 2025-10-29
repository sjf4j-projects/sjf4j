package org.sjf4j.facades.snakeyaml;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.facades.snake.SnakeYamlFacade;
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
        snake.writeObject(sw, jo1);
        String ya1 = sw.toString();
        log.info("ya1: \n{}", ya1);

        JsonObject jo2 = snake.readObject(new StringReader(ya1));
        assertEquals(jo1, jo2);
    }

}
