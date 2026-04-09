package org.sjf4j.node;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.models.JojoTest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;


@Slf4j
public class NodeRegistryTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Role {
        private String name;
        private float percentage;
    }

    @Getter
    @Setter
    public static class Person extends JsonObject {
        private String name;
        private int age;
        private JsonObject info;
        private List<JojoTest.Person> friends;
    }

    public static class ParentSameKey {
        public String key;
    }

    public static class ChildSameKey extends ParentSameKey {
        public int key;
    }

    @Test
    public void testRegisterPojo1() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(Person.class);
        log.info("pi={}", pi);
        assertNotNull(pi);
        assertEquals(4, pi.fieldCount);
        assertNotNull(pi.fields.get("name").getter);
        assertNotNull(pi.fields.get("name").setter);
        assertEquals(int.class, pi.fields.get("age").type);
        assertEquals(JsonObject.class, pi.fields.get("info").type);
        assertEquals(new TypeReference<List<JojoTest.Person>>(){}.getType(),
                pi.fields.get("friends").type);
    }

    @Test
    public void testInheritedFieldSameKeyChildWins() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(ChildSameKey.class);
        assertNotNull(pi.fields.get("key"));
        assertEquals(int.class, pi.fields.get("key").type);

        ChildSameKey pojo = Sjf4j.global().fromJson("{\"key\":123}", ChildSameKey.class);
        assertEquals(123, pojo.key);
    }

    @Test
    public void testContainerFactoryFallback() {
        assertThrows(JsonException.class, () -> NodeRegistry.newMapContainer(Collections.singletonMap("a", 1).getClass(), false));
        Map<String, Object> map = NodeRegistry.newMapContainer(Collections.singletonMap("a", 1).getClass(), true);
        assertTrue(map.isEmpty());

        assertThrows(JsonException.class, () -> NodeRegistry.newListContainer(Arrays.asList("x").getClass(), false));
        List<Object> list = NodeRegistry.newListContainer(Arrays.asList("x").getClass(), true);
        assertTrue(list.isEmpty());

        assertThrows(JsonException.class, () -> NodeRegistry.newSetContainer(Collections.singleton("z").getClass(), false));
        Set<Object> set = NodeRegistry.newSetContainer(Collections.singleton("z").getClass(), true);
        assertTrue(set.isEmpty());
    }

    @Test
    public void testInvoke1() {
        Person p1 = new Person();
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(Person.class);
        NodeRegistry.FieldInfo fi = pi.fields.get("name");

        fi.invokeSetter(p1, "hahaha");
        String name1 = (String) fi.invokeGetter(p1);
    }



    @NodeValue
    public static class Day {
        protected final LocalDate localDate;
        public Day(LocalDate localDate) {
            this.localDate = localDate;
        }

        @ValueToRaw
        public String encode() {
            return localDate.toString();
        }

        @RawToValue
        public static Day decode(String raw) {
            return new Day(LocalDate.parse(raw));
        }

        @ValueCopy
        public Day copy() {
            return new Day(localDate);
        }
    }

    public static class BigDay extends Day {
        public BigDay(LocalDate localDate) {super(localDate);}

        public static BigDay decode(String raw) { return new BigDay(LocalDate.parse(raw));}

        public BigDay copy() {
            return new BigDay(localDate);
        }
    }

    public static class CreatorPojo {
        private final String name;
        private final int age;

//        @NodeCreator
        public CreatorPojo(@NodeProperty("name") String name,
                           @NodeProperty("age") int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    public static class CreatorPojoNoMatch {
        private final String name;

        @NodeCreator
        public CreatorPojoNoMatch(String name) {
            this.name = name;
        }
    }

    public static class JacksonCreatorPojo {
        private final String name;
        private final int age;

        @JsonCreator
        public JacksonCreatorPojo(@JsonProperty("name") String name,
                                  @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    public static class JacksonAliasPojo {
        private final String name;

        @JsonCreator
        public JacksonAliasPojo(@JsonProperty("name")
                                @JsonAlias({"n", "nick"}) String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class NodeAliasPojo {
        private final String name;

        @NodeCreator
        public NodeAliasPojo(@NodeProperty(value = "name", aliases = {"n", "nick"}) String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    @Test
    public void testNodeValue1() {
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerTypeInfo(BigDay.class).valueCodecInfo;
        log.info("vci={}", vci);
        assertNotNull(vci);

        LocalDate now = LocalDate.now();
        BigDay day = new BigDay(now);
        Object raw = vci.valueToRaw(day);
        log.info("raw={}", raw);
        assertEquals(now.toString(), raw);

        BigDay day2 = (BigDay) vci.rawToValue(raw);
        log.info("day2={}", day2);
        assertEquals(day.localDate, day2.localDate);

        BigDay big = Sjf4j.global().fromJson("\"2026-01-01\"", BigDay.class);
        log.info("big={}", big);
        assertEquals("\"2026-01-01\"", Sjf4j.global().toJsonString(big));
    }


    @Test
    public void testNodeValue2() {
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.overrideValueCodec(new ValueCodec<LocalDate, String>() {
            @Override
            public String valueToRaw(LocalDate node) {
                return node.toString();
            }

            @Override
            public LocalDate rawToValue(String raw) {
                return LocalDate.parse(raw);
            }

            @Override
            public Class<LocalDate> valueClass() {
                return LocalDate.class;
            }

            @Override
            public Class<String> rawClass() {
                return String.class;
            }
        });
        log.info("vci={}", vci);
        assertNotNull(vci);

        LocalDate now = LocalDate.now();
        String raw = (String) vci.valueToRaw(now);
        log.info("raw={} type={}", raw, raw.getClass());
        assertEquals(now.toString(), raw);

        LocalDate now2 = (LocalDate) vci.rawToValue(raw);
        log.info("now2={}", now2);
        assertEquals(now, now2);
    }

    /// Creator

    @Test
    public void testCreatorPojo() {
        Sjf4j sjf4j = Sjf4j.builder().jsonFacade(new JsonpJsonFacade()).build();
        String json = "{\"name\":\"Alice\",\"age\":18}";
        CreatorPojo pojo = sjf4j.fromJson(json, CreatorPojo.class);
        assertEquals("Alice", pojo.getName());
        assertEquals(18, pojo.getAge());
    }

    @Test
    public void testCreatorPojoMissingParamName() {
        String json = "{\"name\":\"Alice\"}";

        Fastjson2JsonFacade fastjson2 = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        CreatorPojoNoMatch obj1 = (CreatorPojoNoMatch) fastjson2.readNode(json, CreatorPojoNoMatch.class);
        log.info("obj1={}", Nodes.inspect(obj1));
        log.info("obj1.name={}", obj1.name);

        Jackson2JsonFacade jackson2 = new Jackson2JsonFacade();
        assertThrows(JsonException.class, () -> jackson2.readNode(json, CreatorPojoNoMatch.class));
    }

    @Test
    public void testJackson2CreatorPojo() {
        String json = "{\"name\":\"Bob\",\"age\":20}";
        JacksonCreatorPojo pojo = Sjf4j.global().fromJson(json, JacksonCreatorPojo.class);
        assertEquals("Bob", pojo.getName());
        assertEquals(20, pojo.getAge());
    }

    @Test
    public void testJackson2AliasPojo() {
        String json = "{\"n\":\"Alice\"}";
        JacksonAliasPojo pojo = Sjf4j.global().fromJson(json, JacksonAliasPojo.class);
        assertEquals("Alice", pojo.getName());
    }

    @Test
    public void testNodeAliasPojo() {
        String json = "{\"nick\":\"Alice\"}";
        NodeAliasPojo pojo = Sjf4j.global().fromJson(json, NodeAliasPojo.class);
        assertEquals("Alice", pojo.getName());
    }

}
