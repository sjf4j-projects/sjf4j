package org.sjf4j.jdk17.node;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.Nodes;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class NodesTest {

    private final Sjf4j sjf4j = Sjf4j.builder()
            .jsonFacade(new SimpleJsonFacade())
            .build();

    record PlainRecord(String msg, LocalDate date) {}
    static class User {
        public String name;
        public PlainRecord[] records;
    }

    @Test
    public void testToPojo1() {
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        JsonObject jo = sjf4j.fromJson(json, JsonObject.class);
        log.info("jo={}", Nodes.inspect(jo));

        User user1 = Nodes.toPojo(jo, User.class);
        log.info("user1={}", Nodes.inspect(user1));

        JsonObject jo2 = JsonObject.of("name", "yes");
        User user2 = Nodes.toPojo(jo2, User.class);
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("yes", user2.name);
    }

    @Test
    public void testFromNode1() {
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        User user1 = sjf4j.fromJson(json, User.class);
        log.info("user1={}", Nodes.inspect(user1));

        Object node = sjf4j.fromJson(json);
        log.info("node={}", Nodes.inspect(node));
        User user2 = sjf4j.fromNode(node, User.class);
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("2026-02-04", user2.records[0].date.toString());
    }

    @Test
    public void testShadowCopy1() {
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        User user1 = sjf4j.fromJson(json, User.class);

        User user2 = Nodes.copy(user1);
        user1.records[0] = new PlainRecord("m2m", LocalDate.parse("2026-02-05"));
        log.info("user1={}", Nodes.inspect(user1));
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("m2m", user1.records[0].msg);
        assertEquals("m2m", user2.records[0].msg);
    }

    @Test
    public void testDeepCopy1() {
        String json = "{\"name\":\"n\",\"records\":[{\"msg\":\"m\",\"date\":\"2026-02-04\"}]}";
        User user1 = sjf4j.fromJson(json, User.class);

        User user2 = sjf4j.deepNode(user1);
        user1.records[0] = new PlainRecord("m2m", LocalDate.parse("2026-02-05"));
        log.info("user1={}", Nodes.inspect(user1));
        log.info("user2={}", Nodes.inspect(user2));
        assertEquals("m2m", user1.records[0].msg);
        assertEquals("m", user2.records[0].msg);
    }


}
