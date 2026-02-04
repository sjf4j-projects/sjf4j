package org.sjf4j.jdk17.node;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.node.Nodes;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class NodesTest {

    record PlainRecord(String msg, LocalDate date) {}
    static class User {
        private String name;
        private PlainRecord[] records;
    }

    @Test
    public void testToPojo1() {
        Sjf4jConfig.useSimpleJsonAsGlobal();
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        JsonObject jo = Sjf4j.fromJson(json, JsonObject.class);
        log.info("jo={}", Nodes.inspect(jo));

        assertThrows(JsonException.class, () -> Nodes.toPojo(jo, User.class),
                "Type mismatch: cannot convert java.lang.String to java.time.LocalDate");

        JsonObject jo2 = new JsonObject("name", "yes");
        User user2 = Nodes.toPojo(jo2, User.class);
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("yes", user2.name);
    }

    @Test
    public void testFromNode1() {
        Sjf4jConfig.useSimpleJsonAsGlobal();
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        User user1 = Sjf4j.fromJson(json, User.class);
        log.info("user1={}", Nodes.inspect(user1));

        Object node = Sjf4j.fromJson(json);
        log.info("node={}", Nodes.inspect(node));
        User user2 = Sjf4j.fromNode(node, User.class);
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("2026-02-04", user2.records[0].date.toString());
    }

    @Test
    public void testShadowCopy1() {
        Sjf4jConfig.useSimpleJsonAsGlobal();
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        User user1 = Sjf4j.fromJson(json, User.class);

        User user2 = Nodes.copy(user1);
        user1.records[0] = new PlainRecord("m2m", LocalDate.parse("2026-02-05"));
        log.info("user1={}", Nodes.inspect(user1));
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("m2m", user1.records[0].msg);
        assertEquals("m2m", user2.records[0].msg);
    }

    @Test
    public void testDeepCopy1() {
        Sjf4jConfig.useSimpleJsonAsGlobal();
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        User user1 = Sjf4j.fromJson(json, User.class);

        User user2 = Sjf4j.deepNode(user1);
        user1.records[0] = new PlainRecord("m2m", LocalDate.parse("2026-02-05"));
        log.info("user1={}", Nodes.inspect(user1));
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("m2m", user1.records[0].msg);
        assertEquals("m", user2.records[0].msg);
    }


}
