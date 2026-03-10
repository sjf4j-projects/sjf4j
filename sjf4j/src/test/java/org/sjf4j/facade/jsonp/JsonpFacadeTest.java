package org.sjf4j.facade.jsonp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.node.Nodes;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class JsonpFacadeTest {

    static class Book extends JsonObject {
        int id;
        String name;
    }

    static class User {
        String name;
        List<User> friends;
    }

    @Test
    void testReadWriteAllModes() {
        String json = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        for (StreamingFacade.StreamingMode mode : new StreamingFacade.StreamingMode[]{
                StreamingFacade.StreamingMode.SHARED_IO,
                StreamingFacade.StreamingMode.AUTO
        }) {
            JsonpJsonFacade facade = new JsonpJsonFacade(mode);
            Book book = (Book) facade.readNode(new StringReader(json), Book.class);
            String out = facade.writeNodeAsString(book);
            assertEquals(json, out);
        }
    }

    @Test
    void testReadArrayAndObject() {
        JsonpJsonFacade facade = new JsonpJsonFacade();

        JsonObject jo = (JsonObject) facade.readNode("{\"a\":1,\"b\":true}", JsonObject.class);
        assertEquals(1, jo.getInteger("a"));

        JsonArray ja = (JsonArray) facade.readNode("[1,2,3]", JsonArray.class);
        assertEquals(3, ja.size());
    }

    @Test
    void testSkipNode() {
        JsonpJsonFacade facade = new JsonpJsonFacade();
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

    @Test
    void testWriteNodeToWriter() {
        JsonpJsonFacade facade = new JsonpJsonFacade(StreamingFacade.StreamingMode.AUTO);
        Book book = new Book();
        book.id = 1;
        book.name = "n";
        StringWriter sw = new StringWriter();
        facade.writeNode(sw, book);
        assertEquals("{\"id\":1,\"name\":\"n\"}", sw.toString());
    }
}
