package org.sjf4j.facade.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.TypeReference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
public class SimpleNodeFacadeGenericTest {

    private final NodeFacade nodeFacade = new SimpleNodeFacade();

    public static class UserBody {
        public String name;
        public int age;
    }

    public static class PatchResponse<T> extends JsonObject {
        public int code;
        public String msg;
        public T body;
    }

    public static class PatchResponseWithCreator<T> extends JsonObject {
        public final int code;
        public final String msg;
        public final T body;

        @NodeCreator
        public PatchResponseWithCreator(@NodeProperty("code") int code,
                                        @NodeProperty("msg") String msg,
                                        @NodeProperty("body") T body) {
            this.code = code;
            this.msg = msg;
            this.body = body;
        }
    }

    @Test
    void read_top_level_generic_list_of_pojo() {
        List<JsonObject> node = Arrays.asList(
                new JsonObject("name", "A", "age", 7),
                new JsonObject("name", "B", "age", 8)
        );

        List<UserBody> users = (List<UserBody>) nodeFacade.readNode(
                node,
                new TypeReference<List<UserBody>>() {}.getType(),
                true
        );

        assertEquals(2, users.size());
        assertInstanceOf(UserBody.class, users.get(0));
        assertEquals("A", users.get(0).name);
        assertEquals(8, users.get(1).age);
    }

    @Test
    void read_top_level_generic_map_of_pojo() {
        JsonObject node = new JsonObject(
                "u1", new JsonObject("name", "Han", "age", 18),
                "u2", new JsonObject("name", "Li", "age", 20)
        );

        Map<String, UserBody> users = (Map<String, UserBody>) nodeFacade.readNode(
                node,
                new TypeReference<Map<String, UserBody>>() {}.getType(),
                true
        );

        assertEquals(2, users.size());
        assertInstanceOf(UserBody.class, users.get("u1"));
        assertEquals("Han", users.get("u1").name);
        assertEquals(20, users.get("u2").age);
    }

    @Test
    void read_generic_jojo_body_with_pojo_type_should_bind_to_pojo() {
        JsonObject node = new JsonObject(
                "code", 200,
                "msg", "ok",
                "body", new JsonObject("name", "Han", "age", 18)
        );

        PatchResponse<UserBody> response = (PatchResponse<UserBody>) nodeFacade.readNode(
                node,
                new TypeReference<PatchResponse<UserBody>>() {}.getType(),
                true
        );

        assertEquals(200, response.code);
        assertEquals("ok", response.msg);
        assertInstanceOf(UserBody.class, response.body);
        assertEquals("Han", response.body.name);
        assertEquals(18, response.body.age);
    }

    @Test
    void read_generic_jojo_body_with_list_type_should_bind_to_pojo_list() {
        JsonObject node = new JsonObject(
                "code", 200,
                "msg", "ok",
                "body", Arrays.asList(
                        new JsonObject("name", "A", "age", 7),
                        new JsonObject("name", "B", "age", 8)
                )
        );

        PatchResponse<List<UserBody>> response = (PatchResponse<List<UserBody>>) nodeFacade.readNode(
                node,
                new TypeReference<PatchResponse<List<UserBody>>>() {}.getType(),
                true
        );

        assertEquals(200, response.code);
        assertEquals(2, response.body.size());
        assertInstanceOf(UserBody.class, response.body.get(0));
        assertEquals("A", response.body.get(0).name);
        assertEquals("B", response.body.get(1).name);
        assertEquals(8, response.body.get(1).age);
    }

    @Test
    void read_generic_jojo_body_with_map_type_should_bind_to_map() {
        JsonObject node = new JsonObject(
                "code", 500,
                "msg", "error",
                "body", new JsonObject("retry", 3, "timeout", 30)
        );

        PatchResponse<Map<String, Integer>> response = (PatchResponse<Map<String, Integer>>) nodeFacade.readNode(
                node,
                new TypeReference<PatchResponse<Map<String, Integer>>>() {}.getType(),
                true
        );

        assertEquals(500, response.code);
        assertEquals("error", response.msg);
        assertInstanceOf(Map.class, response.body);
        assertEquals(3, response.body.get("retry"));
        assertEquals(30, response.body.get("timeout"));
    }

    @Test
    void read_generic_jojo_with_node_creator_body_should_bind_to_pojo() {
        JsonObject node = new JsonObject(
                "code", 200,
                "msg", "ok",
                "body", new JsonObject("name", "Han", "age", 18)
        );

        PatchResponseWithCreator<UserBody> response = (PatchResponseWithCreator<UserBody>) nodeFacade.readNode(
                node,
                new TypeReference<PatchResponseWithCreator<UserBody>>() {}.getType(),
                true
        );

        assertEquals(200, response.code);
        assertEquals("ok", response.msg);
        assertInstanceOf(UserBody.class, response.body);
        assertEquals("Han", response.body.name);
        assertEquals(18, response.body.age);
    }
}
