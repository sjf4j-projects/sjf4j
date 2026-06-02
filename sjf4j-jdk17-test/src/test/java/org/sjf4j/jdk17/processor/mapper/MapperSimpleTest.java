package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.JsonObject;
import org.sjf4j.compiled.CompiledRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperSimpleTest {

    @Test
    public void mapsToNoArgsBeanWithRenameIgnoreAndInlineCompute() {
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);
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
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);
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
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);

        UserDto dto = mapper.withHelper(new Person("Ada", "Lovelace", 36));

        assertEquals("Ada/Lovelace", dto.getFullName());
    }

    @Test
    public void mapsSameNamePropertiesWithoutMappingAnnotations() {
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);

        SameDto dto = mapper.sameNames(new Person("Ada", "Lovelace", 36));

        assertEquals("Ada", dto.first());
        assertEquals("Lovelace", dto.last());
        assertEquals(36, dto.age());
    }

    @Test
    public void mapsJsonPathAndJsonPointerSources() {
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);
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
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);

        NameOnly dto = mapper.dottedKey(Map.of("profile.name", "literal-key"));

        assertEquals("literal-key", dto.name());
    }

    @Test
    public void mapsNullablePathSourceToReferenceTarget() {
        UserMapper mapper = CompiledRegistry.of(UserMapper.class);

        AgeDto dto = mapper.age(new AgeSource(new AgeBox(42)));

        assertEquals(42, dto.age);
    }

    @Test
    public void mapsFromMultipleSourceParameters() {
        MultiMapper mapper = CompiledRegistry.of(MultiMapper.class);
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
        MultiMapper mapper = CompiledRegistry.of(MultiMapper.class);
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
        MultiMapper mapper = CompiledRegistry.of(MultiMapper.class);
        Address address = new Address("London", "NW1");

        MultiCtor ctor = mapper.toCtor(null, address);

        assertNull(ctor.name());
        assertEquals("London", ctor.city());
        assertNull(ctor.profileName());
        assertNull(mapper.toCtor(null, null));
    }

    @Test
    public void mapsPrimitiveConstructorValueOnlyThroughExplicitCompute() {
        MultiMapper mapper = CompiledRegistry.of(MultiMapper.class);
        Address address = new Address("London", "NW1");

        assertEquals(7, mapper.toAgeRecord(new Score(7), address).age());
        assertEquals(0, mapper.toAgeRecord(null, address).age());
        assertNull(mapper.toAgeRecord(null, null));
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

    public static final class AgeDto {
        public Integer age;

        public AgeDto() {}
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
