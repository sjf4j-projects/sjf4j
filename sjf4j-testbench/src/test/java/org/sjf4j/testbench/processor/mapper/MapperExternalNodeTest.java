package org.sjf4j.testbench.processor.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.NullValuePolicy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CompiledMapper mapping from Jackson 2's backend-native JsonNode representations. */
public class MapperExternalNodeTest {

    @Test
    public void mapsJacksonJsonNodeToRecordIncludingNestedRecord() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);
        ObjectNode address = JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1");
        JsonNode source = JsonNodeFactory.instance.objectNode()
                .put("name", "Ada").put("age", 36).put("active", true).set("address", address);

        JacksonNodeDto dto = mapper.fromJackson(source);

        assertEquals("Ada", dto.name());
        assertEquals(36, dto.age());
        assertEquals(true, dto.active());
        assertEquals("London", dto.address().city());
        assertEquals("NW1", dto.address().zip());
    }

    @Test
    public void mapsJacksonArrayNodesToJavaArraysAndCollections() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);
        JsonNode numbers = JsonNodeFactory.instance.arrayNode().add(1).add(2);

        assertEquals(List.of(1, 2), List.of(mapper.jacksonArray(numbers)));
        assertEquals(List.of(1, 2), mapper.jacksonList(numbers));
        assertEquals(Set.of(1, 2), mapper.jacksonSet(numbers));

        JsonNode source = JsonNodeFactory.instance.objectNode().set("addresses",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1")));
        assertEquals("London", mapper.jacksonNestedArray(source).addresses().get(0).city());
    }

    @Test
    public void mapsFacadeIndexedPathAndCachedPojoChildReads() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);
        JsonNode item = JsonNodeFactory.instance.objectNode().put("name", "Ada");
        assertEquals("Ada", mapper.jacksonFirst(JsonNodeFactory.instance.arrayNode().add(item)).name());
        assertEquals("Ada", mapper.jacksonLast(JsonNodeFactory.instance.arrayNode().add(item)).name());
        assertNull(mapper.jacksonOutOfRange(JsonNodeFactory.instance.arrayNode().add(item)).name());

        JsonNode address = JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1");
        JacksonTwiceDto twice = mapper.jacksonTwice(JsonNodeFactory.instance.objectNode().set("address", address));
        assertEquals("London", twice.home().city());
        assertEquals("London", twice.work().city());
    }

    @Test
    public void mapsFacadeNullNodesAsJavaNull() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);
        ObjectNode source = JsonNodeFactory.instance.objectNode();
        source.set("array", JsonNodeFactory.instance.arrayNode().addNull().add(2));

        JacksonNullDto dto = mapper.jacksonNulls(source);

        assertNull(dto.name());
        assertNull(dto.age());
        assertNull(dto.address());
        assertNull(dto.numbers());
        assertNull(dto.array()[0]);
        assertEquals(2, dto.array()[1]);

        JacksonIgnoreDto ignored = mapper.jacksonIgnoreNulls(source);
        assertEquals("default", ignored.name);
        assertEquals(7, ignored.age);
        assertEquals(List.of(1), ignored.numbers);
    }

    @Test
    public void mapsJacksonObjectNodeToTypedMap() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);

        assertEquals(Map.of("one", 1, "two", 2), mapper.jacksonMap(
                JsonNodeFactory.instance.objectNode().put("one", 1).put("two", 2)));
        assertEquals("London", mapper.jacksonAddressMap(JsonNodeFactory.instance.objectNode().set("home",
                JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1"))).get("home").city());
    }

    @Test
    public void mapsJacksonNestedObjectNodeToTypedMapProperty() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);
        JsonNode source = JsonNodeFactory.instance.objectNode().set("addresses",
                JsonNodeFactory.instance.objectNode().set("home",
                        JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1")));

        assertEquals("London", mapper.jacksonAddressBook(source).addresses().get("home").city());
    }

    @Test
    public void mapsFacadeChildWithExplicitJacksonNodeConverter() {
        ExternalNodeMapper mapper = CompiledInstances.create(ExternalNodeMapper.class);
        JsonNode source = JsonNodeFactory.instance.objectNode().set("address",
                JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1"));

        assertEquals("London", mapper.jacksonExplicitAddress(source).address().city());
    }

    public record Address(String city, String zip) {}

    public record NameOnly(String name) {}

    public record JacksonNodeDto(String name, Integer age, Boolean active, Address address) {}
    public record JacksonArrayDto(List<Address> addresses) {}
    public record JacksonTwiceDto(Address home, Address work) {}
    public record JacksonAddressBook(Map<String, Address> addresses) {}
    public record JacksonExplicitAddressDto(Address address) {}
    public record JacksonNullDto(String name, Integer age, Address address, List<Integer> numbers, Integer[] array) {}
    public static final class JacksonIgnoreDto {
        public String name = "default";
        public Integer age = 7;
        public List<Integer> numbers = List.of(1);
    }
    @CompiledMapper
    public interface ExternalNodeMapper {
        JacksonNodeDto fromJackson(JsonNode source);

        Integer[] jacksonArray(JsonNode source);

        List<Integer> jacksonList(JsonNode source);

        Set<Integer> jacksonSet(JsonNode source);

        JacksonArrayDto jacksonNestedArray(JsonNode source);

        @Mapping(target = "name", source = "$[0].name")
        NameOnly jacksonFirst(JsonNode source);

        @Mapping(target = "name", source = "$[-1].name")
        NameOnly jacksonLast(JsonNode source);

        @Mapping(target = "name", source = "$[3].name")
        NameOnly jacksonOutOfRange(JsonNode source);

        @Mapping(target = "home", source = "address")
        @Mapping(target = "work", source = "address")
        JacksonTwiceDto jacksonTwice(JsonNode source);

        JacksonNullDto jacksonNulls(JsonNode source);

        @MappingOptions(nulls = NullValuePolicy.IGNORE)
        JacksonIgnoreDto jacksonIgnoreNulls(JsonNode source);

        Map<String, Integer> jacksonMap(JsonNode source);

        Map<String, Address> jacksonAddressMap(JsonNode source);

        JacksonAddressBook jacksonAddressBook(JsonNode source);

        @MappingOptions(methods = {"this::mapJacksonAddress"})
        @Mapping(target = "address", source = "address")
        JacksonExplicitAddressDto jacksonExplicitAddress(JsonNode source);

        default Address mapJacksonAddress(JsonNode source) {
            return new Address(source.get("city").asText(), source.get("zip").asText());
        }

    }
}
