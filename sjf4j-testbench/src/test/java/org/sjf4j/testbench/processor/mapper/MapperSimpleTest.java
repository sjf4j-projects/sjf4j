package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.JsonObject;
import org.sjf4j.compiled.CompiledNodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperSimpleTest {

    @Test
    public void mapsToNoArgsBeanWithRenameIgnoreAndInlineCompute() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
        Person person = new Person("Ada", "Lovelace", 36);

        UserDto dto = mapper.toDto(person);

        assertEquals("Ada", dto.getFirst());
        assertEquals("Lovelace", dto.getSurname());
        assertEquals("Ada Lovelace", dto.getFullName());
        assertEquals(0, dto.age);
        assertNull(mapper.toDto(null));
    }

    @Test
    public void mapsToRecordAndConstructorTargets() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
        Person person = new Person("Ada", "Lovelace", 36);

        NameRecord record = mapper.toRecord(person);
        assertEquals("Ada", record.first());
        assertEquals("Lovelace", record.surname());

        NameCtor ctor = mapper.toCtor(person);
        assertEquals("Ada", ctor.first());
        assertEquals("Lovelace", ctor.surname());
    }

    @Test
    public void mapsWithLocalHelperCompute() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        UserDto dto = mapper.withHelper(new Person("Ada", "Lovelace", 36));

        assertEquals("Ada/Lovelace", dto.getFullName());
    }

    @Test
    public void mapsSameNamePropertiesWithoutMappingAnnotations() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        SameDto dto = mapper.sameNames(new Person("Ada", "Lovelace", 36));

        assertEquals("Ada", dto.first());
        assertEquals("Lovelace", dto.last());
        assertEquals(36, dto.age());
    }

    @Test
    public void mapsJsonPathAndJsonPointerSources() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
        JsonObject object = new JsonObject();
        object.put("name", "JsonObjectName");

        NestedDto dto = mapper.nested(new NestedSource(
                new Profile("Ada"),
                List.of(new Tag("list-tag")),
                new String[]{"array-tag"},
                Map.of("nick", "map-name"),
                object));

        assertEquals("Ada", dto.name());
        assertEquals("list-tag", dto.firstTag());
        assertEquals("array-tag", dto.arrayTag());
        assertEquals("map-name", dto.nick());
        assertEquals("JsonObjectName", dto.nodeName());
        assertEquals("Ada:map-name", dto.combined());

        NestedDto missing = mapper.nested(new NestedSource(null, List.of(), new String[0], Map.of(), new JsonObject()));
        assertNull(missing.name());
        assertNull(missing.firstTag());
        assertNull(missing.arrayTag());
        assertNull(missing.nick());
        assertNull(missing.nodeName());
        assertEquals("null:null", missing.combined());
    }

    @Test
    public void treatsDottedMapKeyAsPlainPropertyName() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        NameOnly dto = mapper.dottedKey(Map.of("profile.name", "literal-key"));

        assertEquals("literal-key", dto.name());
    }

    @Test
    public void mapsNullablePathSourceToReferenceTarget() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        AgeDto dto = mapper.age(new AgeSource(new AgeBox(42)));

        assertEquals(42, dto.age);
    }

    @Test
    public void createMapperSetsNullPathValuesByDefault() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        ReferenceDefaultsDto dto = mapper.setNulls(new DefaultsSource(null, null, null));

        assertNull(dto.name);
        assertNull(dto.tags);
        assertNull(dto.nestedAge);
    }

    @Test
    public void groupedSetPathSourcesAssignNullsForMissingParents() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        GroupedDefaultsDto dto = mapper.groupedSet(new GroupedSource(null));
        assertNull(dto.first);
        assertNull(dto.last);

        GroupedDefaultsDto nestedMissing = mapper.groupedSet(new GroupedSource(new GroupedProfile(null)));
        assertNull(nestedMissing.first);
        assertNull(nestedMissing.last);
    }

    @Test
    public void groupedIgnorePathSourcesSkipMissingParentsAndNullLeaves() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        GroupedDefaultsDto missingParent = mapper.groupedIgnore(new GroupedSource(null));
        assertEquals("default-first", missingParent.first);
        assertEquals("default-last", missingParent.last);

        GroupedDefaultsDto nullLeaf = mapper.groupedIgnore(new GroupedSource(new GroupedProfile(new GroupedName(null, "Lovelace"))));
        assertEquals("default-first", nullLeaf.first);
        assertEquals("Lovelace", nullLeaf.last);
    }

    @Test
    public void mapsFromMultipleSourceParameters() {
        MultiMapper mapper = CompiledNodes.instanceOf(MultiMapper.class);
        Customer customer = new Customer("Ada", new Profile("Countess"));
        Address address = new Address("London", "NW1");

        MultiDto dto = mapper.toDto(customer, address);

        assertEquals("Ada", dto.name);
        assertEquals("London", dto.city);
        assertEquals("NW1", dto.zip);
        assertEquals("Countess", dto.profileName);
        assertEquals("Countess", dto.pointerName);
        assertEquals("Ada@London", dto.label);
        assertEquals("Countess#London", dto.pathLabel);

        MultiDto partial = mapper.toDto(null, address);
        assertNull(partial.name);
        assertEquals("London", partial.city);
        assertEquals("NW1", partial.zip);
        assertNull(partial.profileName);
        assertEquals("null#London", partial.pathLabel);
        assertNull(mapper.toDto(null, null));
    }

    @Test
    public void mapsFromMultipleSourceParametersToRecordTarget() {
        MultiMapper mapper = CompiledNodes.instanceOf(MultiMapper.class);
        Customer customer = new Customer("Ada", new Profile("Countess"));
        Address address = new Address("London", "NW1");

        MultiRecord record = mapper.toRecord(customer, address);

        assertEquals("Ada", record.name());
        assertEquals("London", record.city());
        assertEquals("Countess", record.profileName());

        MultiRecord partial = mapper.toRecord(null, address);
        assertNull(partial.name());
        assertEquals("London", partial.city());
        assertNull(partial.profileName());
        assertNull(mapper.toRecord(null, null));
    }

    @Test
    public void mapsFromMultipleSourceParametersToConstructorTarget() {
        MultiMapper mapper = CompiledNodes.instanceOf(MultiMapper.class);
        Address address = new Address("London", "NW1");

        MultiCtor ctor = mapper.toCtor(null, address);

        assertNull(ctor.name());
        assertEquals("London", ctor.city());
        assertNull(ctor.profileName());
        assertNull(mapper.toCtor(null, null));
    }

    @Test
    public void mapsPrimitiveConstructorValueOnlyThroughExplicitCompute() {
        MultiMapper mapper = CompiledNodes.instanceOf(MultiMapper.class);
        Address address = new Address("London", "NW1");

        assertEquals(7, mapper.toAgeRecord(new Score(7), address).age());
        assertEquals(0, mapper.toAgeRecord(null, address).age());
        assertNull(mapper.toAgeRecord(null, null));
    }

    @Test
    public void thirdPartyPropertyNamesDriveRecordTargetAutoMapping() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        ThirdPartyRecord record = mapper.thirdPartyNames(Map.of(
                "first_name", "Ada",
                "last_name", "Lovelace"));

        assertEquals("Ada", record.firstName());
        assertEquals("Lovelace", record.lastName());
    }

    @Test
    public void mapsJacksonJsonNodeToRecordIncludingNestedRecord() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
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
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
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
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
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
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
        ObjectNode source = JsonNodeFactory.instance.objectNode();
        source.putNull("name");
        source.putNull("age");
        source.set("address", JsonNodeFactory.instance.nullNode());
        source.set("numbers", JsonNodeFactory.instance.nullNode());
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
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);

        assertEquals(Map.of("one", 1, "two", 2), mapper.jacksonMap(
                JsonNodeFactory.instance.objectNode().put("one", 1).put("two", 2)));
        assertEquals("London", mapper.jacksonAddressMap(JsonNodeFactory.instance.objectNode().set("home",
                JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1"))).get("home").city());
    }

    @Test
    public void mapsJacksonNestedObjectNodeToTypedMapProperty() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
        JsonNode source = JsonNodeFactory.instance.objectNode().set("addresses",
                JsonNodeFactory.instance.objectNode().set("home",
                        JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1")));

        assertEquals("London", mapper.jacksonAddressBook(source).addresses().get("home").city());
    }

    @Test
    public void mapsFacadeChildWithExplicitJacksonNodeConverter() {
        UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
        JsonNode source = JsonNodeFactory.instance.objectNode().set("address",
                JsonNodeFactory.instance.objectNode().put("city", "London").put("zip", "NW1"));

        assertEquals("London", mapper.jacksonExplicitAddress(source).address().city());
    }

    public record Person(String first, String last, int age) {}

    public record NameRecord(String first, String surname) {}

    public record SameDto(String first, String last, int age) {}

    public record Profile(String name) {}

    public record Customer(String name, Profile profile) {}

    public record Address(String city, String zip) {}

    public record Tag(String label) {}

    public record NestedSource(Profile profile, List<Tag> tags, String[] aliases, Map<String, String> names, JsonObject node) {}

    public record NestedDto(String name, String firstTag, String arrayTag, String nick, Object nodeName, String combined) {}

    public record NameOnly(String name) {}

    public record MultiRecord(String name, String city, String profileName) {}

    public record MultiAgeRecord(int age) {}

    public record Score(Integer age) {}

    public record AgeBox(Integer age) {}

    public record AgeSource(AgeBox box) {}

    public record DefaultsSource(String name, List<String> tags, AgeBox box) {}

    public record GroupedSource(GroupedProfile profile) {}

    public record GroupedProfile(GroupedName name) {}

    public record GroupedName(String first, String last) {}

    public record ThirdPartyRecord(@com.fasterxml.jackson.annotation.JsonProperty("first_name") String firstName,
                                   @com.alibaba.fastjson2.annotation.JSONField(name = "last_name") String lastName) {}
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
    public static final class AgeDto {
        public Integer age;

        public AgeDto() {}
    }

    public static final class ReferenceDefaultsDto {
        public String name = "default-name";
        public List<String> tags = List.of("default-tag");
        public Integer nestedAge = 9;

        public ReferenceDefaultsDto() {}

        public void setName(String name) { this.name = name; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public void setNestedAge(Integer nestedAge) { this.nestedAge = nestedAge; }
    }

    public static final class GroupedDefaultsDto {
        public String first = "default-first";
        public String last = "default-last";

        public GroupedDefaultsDto() {}
    }

    public static final class MultiCtor {
        private final String name;
        private final String city;
        private final String profileName;

        public MultiCtor(String name, String city, String profileName) {
            this.name = name;
            this.city = city;
            this.profileName = profileName;
        }

        public String name() { return name; }
        public String city() { return city; }
        public String profileName() { return profileName; }
    }

    public static final class NameCtor {
        private final String first;
        private final String surname;

        public NameCtor(String first, String surname) {
            this.first = first;
            this.surname = surname;
        }

        public String first() { return first; }
        public String surname() { return surname; }
    }

    public static final class UserDto {
        private String first;
        private String surname;
        private String fullName;
        public int age;

        public UserDto() {}

        public String getFirst() { return first; }
        public void setFirst(String first) { this.first = first; }

        public String getSurname() { return surname; }
        public void setSurname(String surname) { this.surname = surname; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }

    public static final class MultiDto {
        public String name;
        public String city;
        public String zip;
        public String profileName;
        public String pointerName;
        public String label;
        public String pathLabel;
    }

    @CompiledMapper
    public interface UserMapper {
        @Mapping(target = "surname", source = "last")
        @Mapping(target = "fullName", sources = {"first", "last"}, compute = "(a, b) -> a + \" \" + b")
        @Mapping(target = "age", ignore = true)
        UserDto toDto(Person person);

        @Mapping(target = "surname", source = "last")
        NameRecord toRecord(Person person);

        @Mapping(target = "surname", source = "last")
        NameCtor toCtor(Person person);

        @Mapping(target = "surname", source = "last")
        @Mapping(target = "fullName", compute = "this::join")
        @Mapping(target = "age", ignore = true)
        UserDto withHelper(Person person);

        SameDto sameNames(Person person);

        @Mapping(target = "name", source = "$.profile.name")
        @Mapping(target = "firstTag", source = "/tags/0/label")
        @Mapping(target = "arrayTag", source = "$.aliases[0]")
        @Mapping(target = "nick", source = "/names/nick")
        @Mapping(target = "nodeName", source = "$.node.name")
        @Mapping(target = "combined", sources = {"$.profile.name", "/names/nick"}, compute = "(a, b) -> a + \":\" + b")
        NestedDto nested(NestedSource source);

        @Mapping(target = "name", source = "profile.name")
        NameOnly dottedKey(Map<String, String> source);

        @Mapping(target = "age", source = "$.box.age")
        AgeDto age(AgeSource source);

        @Mapping(target = "nestedAge", source = "$.box.age")
        ReferenceDefaultsDto setNulls(DefaultsSource source);

        @Mapping(target = "first", source = "$.profile.name.first")
        @Mapping(target = "last", source = "/profile/name/last")
        GroupedDefaultsDto groupedSet(GroupedSource source);

        @MapperOptions(nulls = NullValuePolicy.IGNORE)
        @Mapping(target = "first", source = "$.profile.name.first")
        @Mapping(target = "last", source = "/profile/name/last")
        GroupedDefaultsDto groupedIgnore(GroupedSource source);

        ThirdPartyRecord thirdPartyNames(Map<String, String> source);

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

        @MapperOptions(nulls = NullValuePolicy.IGNORE)
        JacksonIgnoreDto jacksonIgnoreNulls(JsonNode source);

        Map<String, Integer> jacksonMap(JsonNode source);

        Map<String, Address> jacksonAddressMap(JsonNode source);

        JacksonAddressBook jacksonAddressBook(JsonNode source);

        @MapperOptions(using = {"mapJacksonAddress"})
        @Mapping(target = "address", source = "address")
        JacksonExplicitAddressDto jacksonExplicitAddress(JsonNode source);

        default Address mapJacksonAddress(JsonNode source) {
            return new Address(source.get("city").asText(), source.get("zip").asText());
        }

        default String join(String first, String last) {
            return first + "/" + last;
        }
    }

    @CompiledMapper
    public interface MultiMapper {
        @Mapping(target = "name", source = "customer:name")
        @Mapping(target = "city", source = "address:city")
        @Mapping(target = "zip", source = "address:zip")
        @Mapping(target = "profileName", source = "customer:$.profile.name")
        @Mapping(target = "pointerName", source = "customer:/profile/name")
        @Mapping(target = "label", sources = {"customer:name", "address:city"}, compute = "(name, city) -> name + \"@\" + city")
        @Mapping(target = "pathLabel", sources = {"customer:$.profile.name", "address:city"}, compute = "(profile, city) -> profile + \"#\" + city")
        MultiDto toDto(Customer customer, Address address);

        @Mapping(target = "name", source = "customer:name")
        @Mapping(target = "city", source = "address:city")
        @Mapping(target = "profileName", source = "customer:$.profile.name")
        MultiRecord toRecord(Customer customer, Address address);

        @Mapping(target = "name", source = "customer:name")
        @Mapping(target = "city", source = "address:city")
        @Mapping(target = "profileName", source = "customer:$.profile.name")
        MultiCtor toCtor(Customer customer, Address address);

        @Mapping(target = "age", sources = {"score:age"}, compute = "(age) -> age == null ? 0 : age")
        MultiAgeRecord toAgeRecord(Score score, Address address);
    }
}
