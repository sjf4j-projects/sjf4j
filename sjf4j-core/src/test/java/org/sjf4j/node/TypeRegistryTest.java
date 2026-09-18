package org.sjf4j.node;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
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
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.fixture.JsonObjectPersonFixture;

import java.time.LocalDate;
import java.util.ArrayList;
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
public class TypeRegistryTest {

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
        private List<JsonObjectPersonFixture.Person> friends;
    }

    public static class ParentSameKey {
        public String key;
    }

    public static class ChildSameKey extends ParentSameKey {
        public int key;
    }

    @Test
    public void testRegisterPojo1() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(Person.class);
        log.info("pi={}", pi);
        assertNotNull(pi);
        assertEquals(4, pi.propertyCount);
        assertNotNull(pi.properties.get("name").getterHandle);
        assertNotNull(pi.properties.get("name").setterHandle);
        assertEquals(int.class, pi.properties.get("age").type);
        assertEquals(JsonObject.class, pi.properties.get("info").type);
        assertEquals(new TypeReference<List<JsonObjectPersonFixture.Person>>(){}.getType(),
                pi.properties.get("friends").type);
    }

    @Test
    public void testInheritedFieldSameKeyChildWins() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(ChildSameKey.class);
        assertNotNull(pi.properties.get("key"));
        assertEquals(int.class, pi.properties.get("key").type);

        ChildSameKey pojo = Sjf4j.global().fromJson("{\"key\":123}", ChildSameKey.class);
        assertEquals(123, pojo.key);
    }

    @Test
    public void testContainerFactoryFallback() {
        assertThrows(JsonException.class, () -> TypeRegistry.newMapContainer(Collections.singletonMap("a", 1).getClass(), 0, false));
        Map<String, Object> map = TypeRegistry.newMapContainer(Collections.singletonMap("a", 1).getClass(), 0, true);
        assertTrue(map.isEmpty());

        assertThrows(JsonException.class, () -> TypeRegistry.newListContainer(Arrays.asList("x").getClass(), 3, false));
        List<Object> list = TypeRegistry.newListContainer(Arrays.asList("x").getClass(), 3, true);
        assertTrue(list.isEmpty());

        assertThrows(JsonException.class, () -> TypeRegistry.newSetContainer(Collections.singleton("z").getClass(), 0, false));
        Set<Object> set = TypeRegistry.newSetContainer(Collections.singleton("z").getClass(), 0, true);
        assertTrue(set.isEmpty());
    }

    @Test
    public void testSizedDefaultContainers() {
        Map<String, Integer> map = TypeRegistry.newMapContainer(Map.class, 13, false);
        List<Integer> list = TypeRegistry.newListContainer(List.class, 13, false);
        List<Integer> arrayList = TypeRegistry.newListContainer(ArrayList.class, 13, false);
        Set<Integer> set = TypeRegistry.newSetContainer(Set.class, 13, false);
        for (int i = 0; i < 13; i++) {
            map.put(Integer.toString(i), i);
            list.add(i);
            arrayList.add(i);
            set.add(i);
        }
        assertEquals(13, map.size());
        assertEquals(13, list.size());
        assertEquals(13, arrayList.size());
        assertEquals(13, set.size());

        assertTrue(TypeRegistry.newListContainer(List.class, 0, false).isEmpty());
    }

    @Test
    public void testInvoke1() {
        Person p1 = new Person();
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(Person.class);
        FieldInfo fi = pi.properties.get("name");

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

    public static class CodecDay {
        protected final LocalDate localDate;

        public CodecDay(LocalDate localDate) {
            this.localDate = localDate;
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
        ValueCodecInfo vci = TypeRegistry.registerTypeInfo(BigDay.class).valueCodecInfo;
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
        ValueCodecInfo vci = TypeRegistry.registerValueCodec(new ValueCodec<CodecDay, String>() {
            @Override
            public String valueToRaw(CodecDay node) {
                return node.localDate.toString();
            }

            @Override
            public CodecDay rawToValue(String raw) {
                return new CodecDay(LocalDate.parse(raw));
            }

            @Override
            public Class<CodecDay> valueClass() {
                return CodecDay.class;
            }

            @Override
            public Class<String> rawClass() {
                return String.class;
            }
        });
        log.info("vci={}", vci);
        assertNotNull(vci);

        CodecDay now = new CodecDay(LocalDate.now());
        String raw = (String) vci.valueToRaw(now);
        log.info("raw={} type={}", raw, raw.getClass());
        assertEquals(now.localDate.toString(), raw);

        CodecDay now2 = (CodecDay) vci.rawToValue(raw);
        log.info("now2={}", now2);
        assertEquals(now.localDate, now2.localDate);
    }

    @Test
    public void testRegisterValueCodecDuplicateFails() {
        assertThrows(JsonException.class, () -> TypeRegistry.registerValueCodec(new ValueCodec<LocalDate, String>() {
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
        }));
    }

    /// Creator

    @Test
    public void testCreatorPojo() {
        Sjf4j sjf4j = Sjf4j.builder().jsonFacadeProvider(JsonpJsonFacade.provider()).build();
        String json = "{\"name\":\"Alice\",\"age\":18}";
        CreatorPojo pojo = sjf4j.fromJson(json, CreatorPojo.class);
        assertEquals("Alice", pojo.getName());
        assertEquals(18, pojo.getAge());
    }

    @Test
    public void testCreatorPojoMissingParamName() {
        String json = "{\"name\":\"Alice\"}";

        Fastjson2JsonFacade fastjson2 = new Fastjson2JsonFacade(new JSONReader.Feature[0], new JSONWriter.Feature[0],
                new StreamingContext(StreamingContext.StreamingMode.PLUGIN_MODULE));
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
