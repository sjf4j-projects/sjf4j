package org.sjf4j.facade.simple;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Decode;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @NodeValue
    public static class Ops {
        private final LocalDate localDate;

        public Ops(LocalDate localDate) {
            this.localDate = localDate;
        }
        @Encode
        public String encode() {
            return localDate.toString();
        }

        @Decode
        public static Ops decode(String raw) {
            return new Ops(LocalDate.parse(raw));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNodeValue1() {
        SimpleJsonFacade facade = new SimpleJsonFacade();
        NodeRegistry.registerValueCodec(Ops.class);

        String json1 = "[\"2024-10-01\",\"2025-12-18\"]";
        List<Ops> list = (List<Ops>) facade.readNode(json1, new TypeReference<List<Ops>>() {}.getType());
        log.info("list={}", list);

        StringWriter sw = new StringWriter();
        facade.writeNode(sw, list);
        String json2 = sw.toString();
        log.info("json2={}", json2);
        assertEquals(json1, json2);
    }


    static class User {
        String name;
        List<User> friends;
        Map<String, Object> ext;
    }

    @Test
    public void testPojoUnknownKey() {
        String json = "{\"active\": true }";
        SimpleJsonFacade facade = new SimpleJsonFacade();
        assertDoesNotThrow(() -> facade.readNode(json, User.class));
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
        final String name;
        final int age;

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

}
