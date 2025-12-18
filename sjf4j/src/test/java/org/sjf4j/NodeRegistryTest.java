package org.sjf4j;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.convertible.Convert;
import org.sjf4j.annotation.convertible.NodeConvertible;
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



    @NodeConvertible
    public static class Ops {
        private final LocalDate localDate;

        public Ops(LocalDate localDate) {
            this.localDate = localDate;
        }
        @Convert
        public String convert() {
            return localDate.toString();
        }

        @Unconvert
        public static Ops unconvert(Object raw) {
            NodeType nt = NodeType.of(raw);
            if (nt == NodeType.VALUE_STRING) {
                return new Ops(LocalDate.parse((String) raw));
            }
            throw new RuntimeException("Wrong raw type");
        }
    }


    @Test
    public void testConvertible1() {
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.registerConvertible(Ops.class);
        log.info("ci={}", ci);
        assertNotNull(ci);

        LocalDate now = LocalDate.now();
        Ops ops = new Ops(now);
        Object raw = ci.convert(ops);
        log.info("raw={}", raw);
        assertEquals(now.toString(), raw);

        Ops ops2 = (Ops) ci.unconvert(raw);
        log.info("ops2={}", ops2);
        assertEquals(ops.localDate, ops2.localDate);
    }


}
