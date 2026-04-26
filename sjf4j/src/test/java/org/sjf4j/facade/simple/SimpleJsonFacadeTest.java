package org.sjf4j.facade.simple;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.CodecFacadeAssertions;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;
import org.sjf4j.node.ValueFormatMapping;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SimpleJsonFacadeTest {

    public static class Book extends JsonObject {
        private int id;
        private String name;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOne() {
        SimpleJsonFacade facade = new SimpleJsonFacade();

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";

        JsonObject jo1 = (JsonObject) facade.readNode(json1, JsonObject.class);
        log.info("jo1={}", jo1.inspect());

        Map<String, Object> map = (Map<String, Object>) facade.readNode(json1, Map.class);
        log.info("map={}", map);

        Book book = (Book) facade.readNode(new StringReader(json1), Book.class);
        log.info("book={}", book);

        String json2 = "[1,2,3]";
        JsonArray ja1 = (JsonArray) facade.readNode(json2, JsonArray.class);
        log.info("ja1={}", ja1);

        Object ja2 = facade.readNode(new StringReader(json2), List.class);
        log.info("ja2={}, type={}", ja2, ja2.getClass());

        Object ja3 = facade.readNode(new StringReader(json2), int[].class);
        log.info("ja3={}, type={}", ja3, ja3.getClass());
    }


    @Test
    public void testWrite1() {
        SimpleJsonFacade facade = new SimpleJsonFacade();

        String json1 = "{\"id\":123,\"name\":\"han\",\"height\":175.3,\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Book jo1 = (Book) facade.readNode(new StringReader(json1), Book.class);

        StringWriter output;
        output = new StringWriter();
        facade.writeNode(output, jo1);
        String json2 = output.toString();
        assertEquals(json1, json2);

    }

    @Test
    public void testNodeValue1() {
        CodecFacadeAssertions.assertNodeValue(new SimpleJsonFacade());
    }


    static class User {
        public String name;
        public List<User> friends;
        public Map<String, Object> ext;
    }

    @NodeBinding(readDynamic = false)
    static class ReadDisabledBook extends JsonObject {
        public int id;
        public String name;
    }

    @NodeBinding(writeDynamic = false)
    static class WriteDisabledBook extends JsonObject {
        public int id;
        public String name;
    }

    static class WriteOnlyPojo {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @NodeBinding(writeDynamic = false)
    static class WriteOnlyJojo extends JsonObject {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @Test
    public void testPojoUnknownKey() {
        String json = "{\"active\": true }";
        SimpleJsonFacade facade = new SimpleJsonFacade();
        User user = (User) assertDoesNotThrow(() -> facade.readNode(json, User.class));
        assertNotNull(user);
        assertNull(user.name);
        assertNull(user.friends);
        assertNull(user.ext);
    }

    @Test
    void jojo_read_dynamic_can_be_disabled() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        ReadDisabledBook book = (ReadDisabledBook) facade.readNode("{\"id\":1,\"name\":\"a\",\"extra\":2}",
                ReadDisabledBook.class);
        assertNull(book.get("extra"));
        book.put("runtime", 3);
        assertEquals("{\"id\":1,\"name\":\"a\",\"runtime\":3}", facade.writeNodeAsString(book));
    }

    @Test
    void jojo_write_dynamic_can_be_disabled() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        WriteDisabledBook book = (WriteDisabledBook) facade.readNode("{\"id\":1,\"name\":\"a\",\"extra\":2}",
                WriteDisabledBook.class);
        assertEquals(2, book.getInt("extra"));
        book.put("runtime", 3);
        assertEquals("{\"id\":1,\"name\":\"a\"}", facade.writeNodeAsString(book));
    }

    @Test
    void write_only_members_are_not_serialized() {
        SimpleJsonFacade facade = new SimpleJsonFacade();

        WriteOnlyPojo pojo = new WriteOnlyPojo();
        pojo.setName("han");
        pojo.setSecret("hidden");
        assertEquals("{\"name\":\"han\"}", facade.writeNodeAsString(pojo));

        WriteOnlyJojo jojo = new WriteOnlyJojo();
        jojo.setName("han");
        jojo.setSecret("hidden");
        jojo.put("runtime", 1);
        assertEquals("{\"name\":\"han\"}", facade.writeNodeAsString(jojo));
    }


    /// With Args Creator

    static class Ctor2PlusField {
        final String name;
        final int age;
        String city;

        @NodeCreator
        public Ctor2PlusField(@NodeProperty("name") String name,
                              @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        public void setCity(String city) { this.city = city; }
    }

    @Test
    void ctor_with_extra_field_should_fill_by_setter() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        String json = "{\"name\":\"a\",\"age\":1,\"city\":\"sh\"}";
        Ctor2PlusField pojo = (Ctor2PlusField) facade.readNode(json, Ctor2PlusField.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("a", pojo.name);
    }

    static class CtorAlias {
        public final String name;
        public final int age;

        @JsonCreator
        public CtorAlias(@NodeProperty(value = "name", aliases = {"n"}) String name,
                         @NodeProperty("age") @JsonAlias("old") int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void ctor_with_alias_param_should_bind() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        String json = "{\"n\":\"a\",\"age\":2}";
        CtorAlias pojo = (CtorAlias) facade.readNode(json, CtorAlias.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("a", pojo.name);

        String json2 = facade.writeNodeAsString(pojo);
        log.info("json2={}", json2);
        assertEquals("{\"name\":\"a\",\"age\":2}", json2);
    }

    static class CtorMissingRef {
        final String name;
        final Integer age;

        @NodeCreator
        public CtorMissingRef(@JSONField(name = "name") String name,
                              @JsonProperty("age") Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void ctor_missing_reference_param_should_default_null() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        String json = "{\"name\":\"a\"}";
        CtorMissingRef pojo = (CtorMissingRef) facade.readNode(json, CtorMissingRef.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("a", pojo.name);
        assertNull(pojo.age);
    }

    static class CtorMissingPrimitive {
        final String name;
        final int age;

        @JSONCreator
        public CtorMissingPrimitive(@NodeProperty("name") String name,
                                    @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void ctor_missing_primitive_param_should_default_zero() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        String json = "{\"name\":\"a\"}";
        CtorMissingPrimitive pojo = (CtorMissingPrimitive) facade.readNode(json, CtorMissingPrimitive.class);
        log.info("pojo={}", Nodes.inspect(pojo));
        assertEquals("a", pojo.name);
        assertEquals(0, pojo.age);
    }

    static class GetterOnlyPojo {
        private String name = "seed";

        public String getName() {
            return name;
        }
    }

    static class SetterOnlyPojo {
        private String name;

        public void setName(String name) {
            this.name = name;
        }

        public String rawName() {
            return name;
        }
    }

    @Test
    void bean_only_should_not_write_private_field_without_getter() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        SetterOnlyPojo pojo = (SetterOnlyPojo) facade.readNode("{\"name\":\"a\"}", SetterOnlyPojo.class);
        assertEquals("a", pojo.rawName());
        assertEquals("{}", facade.writeNodeAsString(pojo));
    }

    @Test
    void bean_only_should_not_read_private_field_without_setter() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        GetterOnlyPojo pojo = (GetterOnlyPojo) facade.readNode("{\"name\":\"a\"}", GetterOnlyPojo.class);
        assertEquals("seed", pojo.getName());
        assertEquals("{\"name\":\"seed\"}", facade.writeNodeAsString(pojo));
    }

    @Test
    void testSkipNode1() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
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
    public void testThrow1() {
        SimpleJsonFacade facade = new SimpleJsonFacade();

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20---]}},\"sex\":true}";

        BindingException error = assertThrows(BindingException.class, () -> facade.readNode(json1, JsonObject.class));
        assertTrue(error.getCause().getMessage().contains("'$.friends.rose.age[1]'"));
    }

    @Test
    public void testValueFormat() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        CodecFacadeAssertions.assertValueFormat(facade);

        StreamingContext context = new StreamingContext(ValueFormatMapping.of(Collections.singletonMap(Instant.class, "epochMillis")));
        SimpleJsonFacade configured = new SimpleJsonFacade(context);
        CodecFacadeAssertions.assertConfiguredInstantValueFormat(configured);
    }


}
