package org.sjf4j.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class StreamingIOTest {

    @Getter
    @Setter
    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext = new LinkedHashMap<>();
    }

    // Define a JOJO `User2`
    @Getter @Setter
    static class UserJojo extends JsonObject {
        String name;
        List<UserJojo> friends;
    }


    @Test
    void testSkipNode1() {
        Sjf4jConfig.useStreamingSharedIOAsGlobal();
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
        User pojo = Sjf4j.fromJson(json, User.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("Jack", pojo.name);
    }


    @Test
    public void testExceptionWithPathSegment1() {
        Sjf4jConfig.useStreamingSharedIOAsGlobal();
//        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());
        JacksonJsonFacade facade = new JacksonJsonFacade(new ObjectMapper());
        String json = "{\n" +
                "  \"name\": \"Alice\",\n" +
                "  \"friends\": [\n" +
                "    {\"name\": \"Bill\", \"active\": true },\n" +
                "    {\n" +
                "      \"name\": \"Cindy\",\n" +
                "      \"friends\": [\n" +
                "        {\"name\": false},\n" +
                "        {\"id\": 5, \"info\": \"blabla\"}\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"age\": 18\n" +
                "}\n";

        Throwable cause = assertThrows(JsonException.class, () -> {
            Object node = facade.readNode(json, UserJojo.class);
            log.info("node={}", node);
        });
        BindingException inner = null;
        while (cause != null) {
            if (cause instanceof BindingException) {
                inner = (BindingException) cause;
                break;
            }
            cause = cause.getCause();
        }
        assertNotNull(inner, "JsonBindingException not found in cause chain");
        log.info("inner:", inner);
        assertTrue(inner.getMessage().contains("/@UserJojo{*friends/[1/@UserJojo{*friends/[0/@UserJojo{*name"));
    }

    @Test
    public void testExceptionWithPathSegment2() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());

        UserJojo user1 = new UserJojo();
        user1.name = "Alice";
        user1.put("throws_key", new Object());
        UserJojo user2 = new UserJojo();
        user2.name = "Bill";
        user2.friends = new ArrayList<>();
        user2.friends.add(user1);

        Throwable cause = assertThrows(JsonException.class, () -> facade.writeNodeAsString(user1));
        BindingException inner = null;
        while (cause != null) {
            if (cause instanceof BindingException) {
                inner = (BindingException) cause;
                break;
            }
            cause = cause.getCause();
        }
        assertNotNull(inner, "JsonBindingException not found in cause chain");
        assertTrue(inner.getMessage().contains("/@UserJojo{throws_key"));
    }


}
