package org.sjf4j.jdk17.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.compiled.CompiledRegistry;
import org.sjf4j.exception.JsonException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GetByPathTest {

    @Test
    public void getsDeepRecordListMapAndJsonPaths() {
        GetNodes nodes = CompiledRegistry.of(GetNodes.class);
        Account account = account();

        assertEquals("last@example.com", nodes.getLastMemberEmail(account));
        assertEquals("Binjiang", nodes.getHomeDistrict(account));
        assertEquals("owner-name", nodes.getMetadataOwnerName(account));
        assertEquals("array-value", nodes.getJsonArrayValue(JsonArray.of("array-value")));
    }

    @Test
    public void missingReferencePathReturnsNull() {
        GetNodes nodes = CompiledRegistry.of(GetNodes.class);

        assertNull(nodes.getLastMemberEmail(null));
        assertNull(nodes.getLastMemberEmail(new Account(null)));
        assertNull(nodes.getLastMemberEmail(new Account(new Profile(new Organization(List.of(), Map.of(), JsonObject.of())))));
        assertNull(nodes.getHomeDistrict(new Account(new Profile(new Organization(List.of(), Map.of(), JsonObject.of())))));
        assertNull(nodes.getJsonArrayValue(JsonArray.of()));
        assertNull(nodes.getLastArrayValue(new String[0]));
        assertNull(nodes.getTypedMapName(Map.of()));
    }

    @Test
    public void primitiveMissingPathThrowsJsonException() {
        GetNodes nodes = CompiledRegistry.of(GetNodes.class);

        assertEquals(7, nodes.getMemberScore(account()));
        assertThrows(JsonException.class, () -> nodes.getMemberScore(null));
        assertThrows(JsonException.class, () -> nodes.getMemberScore(new Account(new Profile(new Organization(List.of(), Map.of(), JsonObject.of())))));
    }

    @Test
    public void supportsPublicFieldBooleanGetterAndTypedContainers() {
        GetNodes nodes = CompiledRegistry.of(GetNodes.class);

        assertEquals("field-value", nodes.getPublicField(new FieldBean("field-value")));
        assertEquals(Boolean.TRUE, nodes.isActive(new FlagBean(true)));
        assertEquals("typed", nodes.getTypedMapName(Map.of("name", "typed")));
        assertEquals("tail", nodes.getLastArrayValue(new String[]{"head", "tail"}));
    }

    @Test
    public void supportsDynamicGetPathParameters() {
        GetNodes nodes = CompiledRegistry.of(GetNodes.class);
        Directory directory = new Directory(
                List.of(new Person("first", 1), new Person("last", 2)),
                Map.of("hz", new City("Hangzhou", "Binjiang")),
                Map.of("east", List.of(new City("Shanghai", "Pudong"), new City("Hangzhou", "Binjiang"))),
                JsonObject.of("value", 123));

        assertEquals("first", nodes.getFriendNameByJsonPathIndex(directory, 0));
        assertEquals("last", nodes.getFriendNameByJsonPathIndex(directory, -1));
        assertEquals("Hangzhou", nodes.getCityNameByBracketParam(directory, "hz"));
        assertEquals("Binjiang", nodes.getRegionCityDistrict(directory, "east", 1));
        assertEquals(123, nodes.getJsonObjectValue(directory, "value"));

        assertNull(nodes.getFriendNameByJsonPathIndex(directory, 99));
        assertThrows(JsonException.class, () -> nodes.getFriendScore(directory, 99));
    }

    private static Account account() {
        Contact first = new Contact("first@example.com", 1);
        Contact last = new Contact("last@example.com", 7);
        Team team = new Team(List.of(new Member(first), new Member(last)));
        Address home = new Address(new City("Hangzhou", "Binjiang"));
        JsonObject metadata = JsonObject.of("owner", JsonObject.of("name", "owner-name"));
        return new Account(new Profile(new Organization(List.of(team), Map.of("home", home), metadata)));
    }

    record Account(Profile profile) {}

    record Profile(Organization organization) {}

    record Organization(List<Team> teams, Map<String, Address> addresses, JsonObject metadata) {}

    record Team(List<Member> members) {}

    record Member(Contact contact) {}

    record Contact(String email, int score) {}

    record Address(City city) {}

    record City(String name, String district) {}

    record Directory(List<Person> friends, Map<String, City> data, Map<String, List<City>> regions, JsonObject json) {}

    record Person(String name, int score) {}

    record FieldBean(String value) {}

    static final class FlagBean {
        private final boolean active;

        FlagBean(boolean active) { this.active = active; }

        public boolean isActive() { return active; }
    }

    @CompiledPath
    interface GetNodes {
        @GetByPath("$.profile.organization.teams[0].members[-1].contact.email")
        String getLastMemberEmail(Account account);

        @GetByPath("$.profile.organization.teams[0].members[-1].contact.score")
        int getMemberScore(Account account);

        @GetByPath("$.profile.organization.addresses.home.city.district")
        String getHomeDistrict(Account account);

        @GetByPath("$.profile.organization.metadata.owner.name")
        Object getMetadataOwnerName(Account account);

        @GetByPath("$[0]")
        Object getJsonArrayValue(JsonArray root);

        @GetByPath("$[-1]")
        String getLastArrayValue(String[] values);

        @GetByPath("$.value")
        String getPublicField(FieldBean root);

        @GetByPath("$.active")
        Boolean isActive(FlagBean root);

        @GetByPath("$.name")
        String getTypedMapName(Map<String, String> root);

        @GetByPath("$.friends[{idx}].name")
        String getFriendNameByJsonPathIndex(Directory root, int idx);

        @GetByPath("$.friends[{idx}].score")
        int getFriendScore(Directory root, int idx);

        @GetByPath("$.data[{name}].name")
        String getCityNameByBracketParam(Directory root, String name);

        @GetByPath("$.regions[{region}][{idx}].district")
        String getRegionCityDistrict(Directory root, String region, int idx);

        @GetByPath("$.json[{name}]")
        Object getJsonObjectValue(Directory root, String name);

    }
}
