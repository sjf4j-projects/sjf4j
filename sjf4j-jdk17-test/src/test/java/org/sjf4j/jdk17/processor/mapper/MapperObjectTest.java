package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MapperObjectTest {

    @Test
    public void mapsJsonObjectToRecordWithNestedPojo() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);

        JsonObject customer = new JsonObject();
        customer.put("name", "Ada");
        customer.put("age", Long.valueOf(36));
        JsonObject address = new JsonObject();
        address.put("city", "London");
        JsonObject source = new JsonObject();
        source.put("id", Integer.valueOf(100));
        source.put("status", "ACTIVE");
        source.put("customer", customer);
        source.put("address", address);

        OrderDto dto = mapper.order(source);

        assertEquals(100L, dto.id());
        assertEquals(Status.ACTIVE, dto.status());
        assertEquals(new CustomerDto("Ada", 36), dto.customer());
        assertEquals(new AddressDto("London"), dto.address());
    }

    @Test
    public void mapsMapToRecordWithNestedPojo() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", "Grace");
        customer.put("age", Integer.valueOf(37));
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Arlington");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", Long.valueOf(200));
        source.put("status", "ACTIVE");
        source.put("customer", customer);
        source.put("address", address);

        OrderDto dto = mapper.order(source);

        assertEquals(200L, dto.id());
        assertEquals(Status.ACTIVE, dto.status());
        assertEquals(new CustomerDto("Grace", 37), dto.customer());
        assertEquals(new AddressDto("Arlington"), dto.address());
    }

    @Test
    public void mapsObjectRootWhenRuntimeSourceIsObjectLike() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        Map<String, Object> customer = Map.of("name", "Katherine", "age", Long.valueOf(38));
        Map<String, Object> source = Map.of(
                "id", Integer.valueOf(300),
                "status", "ACTIVE",
                "customer", customer,
                "address", Map.of("city", "Hampton"));

        OrderDto dto = mapper.orderObject(source);

        assertEquals(300L, dto.id());
        assertEquals(Status.ACTIVE, dto.status());
        assertEquals(new CustomerDto("Katherine", 38), dto.customer());
        assertEquals(new AddressDto("Hampton"), dto.address());
    }

    @Test
    public void mapsJsonObjectToMutableBean() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        JsonObject source = new JsonObject();
        source.put("id", Long.valueOf(400));
        source.put("status", "ACTIVE");
        source.put("customer", JsonObject.of("name", "Margaret", "age", Long.valueOf(39)));
        source.put("address", JsonObject.of("city", "New York"));

        MutableOrder dto = mapper.mutable(source);

        assertEquals(400L, dto.id);
        assertEquals(Status.ACTIVE, dto.status);
        assertEquals(new CustomerDto("Margaret", 39), dto.customer);
        assertEquals(new AddressDto("New York"), dto.address);
    }

    @Test
    public void projectsRecordToJsonObjectShallowly() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        CustomerDto customer = new CustomerDto("Ada", 36);
        ProjectionSource source = new ProjectionSource(500L, Status.ACTIVE, customer, null);

        JsonObject object = mapper.project(source);

        assertEquals(500L, object.getNode("id"));
        assertEquals(Status.ACTIVE, object.getNode("status"));
        assertSame(customer, object.getNode("customer"));
        assertEquals(null, object.getNode("missing"));
    }

    @Test
    public void projectsBeanToJsonObjectUsingNodeNames() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        ProjectionBean source = new ProjectionBean();
        source.firstName = "Ada";
        source.setLastName("Lovelace");

        JsonObject object = mapper.projectBean(source);

        assertEquals("Ada", object.getNode("first_name"));
        assertEquals("Lovelace", object.getNode("last_name"));
    }

    @Test
    public void projectsPojoToMapShallowlyUsingNodeNames() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        ProjectionBean source = new ProjectionBean();
        CustomerDto nested = new CustomerDto("Ada", 36);
        source.firstName = "Ada";
        source.setLastName("Lovelace");
        source.customer = nested;

        Map<String, Object> target = mapper.projectBeanMap(source);

        assertEquals("Ada", target.get("first_name"));
        assertEquals("Lovelace", target.get("last_name"));
        assertSame(nested, target.get("customer"));
    }

    @Test
    public void projectsPojoPropertyToJsonObjectTargetProperty() {
        HolderMapper mapper = CompiledNodes.of(HolderMapper.class);
        ProjectionSource source = new ProjectionSource(600L, Status.ACTIVE, new CustomerDto("Grace", 37), "note");

        ProjectionHolder holder = mapper.holder(new ProjectionWrapper(source));

        assertEquals(600L, holder.node.getNode("id"));
        assertSame(source.customer(), holder.node.getNode("customer"));
    }

    @Test
    public void projectsMapToJsonObjectShallowly() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        JsonObject nested = JsonObject.of("city", "London");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", Long.valueOf(700));
        source.put("nested", nested);

        JsonObject object = mapper.projectMap(source);

        assertNotSame(source, object);
        assertEquals(700L, object.getNode("id"));
        assertSame(nested, object.getNode("nested"));
    }

    @Test
    public void projectsJsonObjectToJsonObjectShallowly() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        JsonObject nested = JsonObject.of("name", "Ada");
        JsonObject source = JsonObject.of("id", Long.valueOf(800), "nested", nested);

        JsonObject object = mapper.projectJsonObject(source);

        assertNotSame(source, object);
        assertEquals(800L, object.getNode("id"));
        assertSame(nested, object.getNode("nested"));
    }

    @Test
    public void projectsJsonObjectToTypedMap() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        JsonObject source = JsonObject.of("a", Long.valueOf(1), "b", Integer.valueOf(2));

        Map<String, Integer> target = mapper.projectJsonObjectInts(source);

        assertEquals(1, target.get("a"));
        assertEquals(2, target.get("b"));
    }

    @Test
    public void projectsObjectRuntimeMapToJsonObjectOnly() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        JsonObject nested = JsonObject.of("city", "London");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", Long.valueOf(900));
        source.put("nested", nested);

        JsonObject target = mapper.projectObjectJson(source);

        assertEquals(900L, target.getNode("id"));
        assertSame(nested, target.getNode("nested"));
        assertThrows(BindingException.class, () -> mapper.projectObjectJson(JsonObject.of("id", 1)));
    }

    @Test
    public void projectsObjectRuntimeMapToTypedMapOnly() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("x", Long.valueOf(9));

        Map<String, Integer> target = mapper.projectObjectInts(source);

        assertEquals(9, target.get("x"));
        assertThrows(BindingException.class, () -> mapper.projectObjectInts(JsonObject.of("x", 1)));
    }

    @Test
    public void mapsMapToJojoWithNoArgsAndDynamicExtras() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        CustomerDto nested = new CustomerDto("Grace", 37);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", Integer.valueOf(11));
        source.put("customer", nested);
        source.put("extra", Long.valueOf(99));

        ProjectionJojo target = mapper.jojo(source);

        assertEquals(11L, target.id);
        assertSame(nested, target.customer);
        assertEquals(99L, target.getNode("extra"));
    }

    @Test
    public void mapsMapToCtorJojoWithDynamicExtras() {
        ObjectMapper mapper = CompiledNodes.of(ObjectMapper.class);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", Integer.valueOf(12));
        source.put("unknown", "v");

        CtorJojo target = mapper.ctorJojo(source);

        assertEquals(12L, target.id());
        assertEquals("v", target.getNode("unknown"));
    }

    public enum Status { ACTIVE }

    public record OrderDto(Long id, Status status, CustomerDto customer, AddressDto address) {}

    public record CustomerDto(String name, Integer age) {}

    public record AddressDto(String city) {}

    public record ProjectionSource(Long id, Status status, CustomerDto customer, String missing) {}

    public record ProjectionWrapper(ProjectionSource node) {}

    public record ProjectionHolder(JsonObject node) {}

    public static final class ProjectionBean {
        @com.fasterxml.jackson.annotation.JsonProperty("first_name")
        public String firstName;

        private String lastName;

        public CustomerDto customer;

        @com.alibaba.fastjson2.annotation.JSONField(name = "last_name")
        public String getLastName() { return lastName; }

        public void setLastName(String lastName) { this.lastName = lastName; }
    }

    public static final class MutableOrder {
        public Long id;
        public Status status;
        public CustomerDto customer;
        public AddressDto address;
    }

    public static final class ProjectionJojo extends JsonObject {
        public Long id;
        public CustomerDto customer;

        public ProjectionJojo() {}
    }

    public static final class CtorJojo extends JsonObject {
        private final Long id;

        public CtorJojo(@NodeProperty("id") Long id) { this.id = id; }

        public Long id() { return id; }
    }

    @CompiledMapper
    public interface ObjectMapper {
        OrderDto order(JsonObject source);

        OrderDto order(Map<String, Object> source);

        OrderDto orderObject(Object source);

        MutableOrder mutable(JsonObject source);

        JsonObject project(ProjectionSource source);

        JsonObject projectBean(ProjectionBean source);

        JsonObject projectMap(Map<String, Object> source);

        JsonObject projectJsonObject(JsonObject source);

        Map<String, Object> projectBeanMap(ProjectionBean source);

        Map<String, Integer> projectJsonObjectInts(JsonObject source);

        JsonObject projectObjectJson(Object source);

        Map<String, Integer> projectObjectInts(Object source);

        ProjectionJojo jojo(Map<String, Object> source);

        CtorJojo ctorJojo(Map<String, Object> source);
    }

    @CompiledMapper
    public interface HolderMapper {
        ProjectionHolder holder(ProjectionWrapper source);
    }
}
