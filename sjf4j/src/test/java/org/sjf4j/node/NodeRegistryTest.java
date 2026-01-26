package org.sjf4j.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JojoTest;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Copy;
import org.sjf4j.annotation.node.Decode;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


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

    @Test
    public void testRegisterPojo1() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(Person.class);
        log.info("pi={}", pi);
        assertNotNull(pi);
        assertEquals(4, pi.getFields().size());
        assertNotNull(pi.getFields().get("name").getGetter());
        assertNotNull(pi.getFields().get("name").getSetter());
        assertEquals(int.class, pi.getFields().get("age").getType());
        assertEquals(JsonObject.class, pi.getFields().get("info").getType());
        assertEquals(new TypeReference<List<JojoTest.Person>>(){}.getType(),
                pi.getFields().get("friends").getType());
    }

    @Test
    public void testIsPojo1() {
        assertTrue(NodeRegistry.isPojo(Role.class));
    }

    @Test
    public void testInvoke1() {
        Person p1 = new Person();
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(Person.class);
        NodeRegistry.FieldInfo fi = pi.getFields().get("name");

        fi.invokeSetter(p1, "hahaha");
        String name1 = (String) fi.invokeGetter(p1);
    }



    @NodeValue
    public static class Day {
        protected final LocalDate localDate;
        public Day(LocalDate localDate) {
            this.localDate = localDate;
        }

        @Encode
        public String encode() {
            return localDate.toString();
        }

        @Decode
        public static Day decode(String raw) {
            return new Day(LocalDate.parse(raw));
        }

        @Copy
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


    @Test
    public void testNodeValue1() {
        NodeRegistry.ValueCodecInfo ci = NodeRegistry.registerValueCodec(BigDay.class);
        log.info("ci={}", ci);
        assertNotNull(ci);

        LocalDate now = LocalDate.now();
        BigDay day = new BigDay(now);
        Object raw = ci.encode(day);
        log.info("raw={}", raw);
        assertEquals(now.toString(), raw);

        BigDay day2 = (BigDay) ci.decode(raw);
        log.info("day2={}", day2);
        assertEquals(day.localDate, day2.localDate);

        BigDay big = Sjf4j.fromJson("\"2026-01-01\"", BigDay.class);
        log.info("big={}", big);
        assertEquals("\"2026-01-01\"", Sjf4j.toJsonString(big));
    }


    @Test
    public void testNodeValue2() {
        NodeRegistry.ValueCodecInfo ci = NodeRegistry.registerValueCodec(new ValueCodec<LocalDate, String>() {
            @Override
            public String encode(LocalDate node) {
                return node.toString();
            }

            @Override
            public LocalDate decode(String raw) {
                return LocalDate.parse(raw);
            }

            @Override
            public Class<LocalDate> getValueClass() {
                return LocalDate.class;
            }

            @Override
            public Class<String> getRawClass() {
                return String.class;
            }
        });
        log.info("ci={}", ci);
        assertNotNull(ci);

        LocalDate now = LocalDate.now();
        String raw = (String) ci.encode(now);
        log.info("raw={} type={}", raw, raw.getClass());
        assertEquals(now.toString(), raw);

        LocalDate now2 = (LocalDate) ci.decode(raw);
        log.info("now2={}", now2);
        assertEquals(now, now2);
    }


}
