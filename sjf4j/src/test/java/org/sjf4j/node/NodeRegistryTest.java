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
import org.sjf4j.annotation.convertible.Convert;
import org.sjf4j.annotation.convertible.Convertible;
import org.sjf4j.annotation.convertible.Copy;
import org.sjf4j.annotation.convertible.Unconvert;
import org.sjf4j.util.TypeReference;

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



    @Convertible
    public static class BigDay {
        private final LocalDate localDate;
        public BigDay(LocalDate localDate) {
            this.localDate = localDate;
        }

        @Convert
        public String convert() {
            return localDate.toString();
        }

        @Unconvert
        public static BigDay unconvert(String raw) {
            return new BigDay(LocalDate.parse(raw));
        }

        @Copy
        public BigDay copy() {
            return new BigDay(localDate);
        }
    }


    @Test
    public void testConvertible1() {
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.registerConvertible(BigDay.class);
        log.info("ci={}", ci);
        assertNotNull(ci);

        LocalDate now = LocalDate.now();
        BigDay day = new BigDay(now);
        Object raw = ci.convert(day);
        log.info("raw={}", raw);
        assertEquals(now.toString(), raw);

        BigDay day2 = (BigDay) ci.unconvert(raw);
        log.info("day2={}", day2);
        assertEquals(day.localDate, day2.localDate);

        BigDay big = Sjf4j.fromJson("\"2026-01-01\"", BigDay.class);
        log.info("big={}", big);
        assertEquals("\"2026-01-01\"", Sjf4j.toJson(big));
    }


    @Test
    public void testConvertible2() {
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.registerConvertible(new NodeConverter<LocalDate, String>() {
            @Override
            public String convert(LocalDate node) {
                return node.toString();
            }

            @Override
            public LocalDate unconvert(String raw) {
                return LocalDate.parse(raw);
            }

            @Override
            public Class<LocalDate> getNodeClass() {
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
        String raw = (String) ci.convert(now);
        log.info("raw={} type={}", raw, raw.getClass());
        assertEquals(now.toString(), raw);

        LocalDate now2 = (LocalDate) ci.unconvert(raw);
        log.info("now2={}", now2);
        assertEquals(now, now2);
    }


}
