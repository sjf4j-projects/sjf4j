package org.sjf4j.jdk17.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.PutByPath;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PutByPathTest {

    @Test
    public void putsDeepPojoMapListAndJsonPaths() {
        PutNodes nodes = CompiledNodes.of(PutNodes.class);
        Account account = account();

        assertEquals("last@example.com", nodes.putLastMemberEmail(account, "new@example.com"));
        assertEquals("new@example.com", account.profile.organization.teams.get(0).members.get(1).contact.getEmail());

        assertEquals("Binjiang", nodes.putHomeDistrict(account, "Xihu"));
        assertEquals("Xihu", account.profile.organization.addresses.get("home").city.district);

        assertEquals("owner-name", nodes.putMetadataOwnerName(account, "new-owner"));
        assertEquals("new-owner", account.profile.organization.metadata.getJsonObject("owner").getString("name"));
    }

    @Test
    public void putsListArrayAppendAndJsonArrayBoundaries() {
        PutNodes nodes = CompiledNodes.of(PutNodes.class);

        List<Integer> values = new ArrayList<>(List.of(1, 2, 3));
        assertEquals(Integer.valueOf(3), nodes.putLast(values, 9));
        assertEquals(List.of(1, 2, 9), values);
        assertNull(nodes.append(values, 10));
        assertEquals(List.of(1, 2, 9, 10), values);
        assertNull(nodes.putAtSize(values, 11));
        assertEquals(List.of(1, 2, 9, 10, 11), values);

        Integer[] array = {1, 2, 3};
        assertEquals(Integer.valueOf(3), nodes.putLastArray(array, 7));
        assertEquals(Integer.valueOf(7), array[2]);
        assertThrows(JsonException.class, () -> nodes.putArrayAtSize(array, 8));

        JsonArray jsonArray = JsonArray.of("old");
        assertEquals("old", nodes.putJsonArrayValue(jsonArray, "new"));
        assertEquals("new", jsonArray.getString(0));
        assertNull(nodes.putJsonArrayAppend(jsonArray, "appended"));
        assertEquals("appended", jsonArray.getString(1));
    }

    @Test
    public void putReturnsOldValueForMapSetterFieldAndPrimitive() {
        PutNodes nodes = CompiledNodes.of(PutNodes.class);

        Map<String, String> map = new HashMap<>();
        map.put("name", "old");
        assertEquals("old", nodes.putMapName(map, "new"));
        assertEquals("new", map.get("name"));

        MutableBean bean = new MutableBean("bean-old", "field-old");
        assertEquals("bean-old", nodes.putBeanValue(bean, "bean-new"));
        assertEquals("bean-new", bean.getValue());
        nodes.putBeanField(bean, "field-new");
        assertEquals("field-new", bean.field);

        Map<String, Integer> typed = new HashMap<>();
        typed.put("value", 1);
        assertEquals(1, nodes.putPrimitiveOld(typed, 2));
        assertEquals(2, typed.get("value"));
    }

    @Test
    public void missingPutParentThrowsJsonException() {
        PutNodes nodes = CompiledNodes.of(PutNodes.class);

        assertThrows(JsonException.class, () -> nodes.putLastMemberEmail(null, "x"));
        assertThrows(JsonException.class, () -> nodes.putLastMemberEmail(new Account(null), "x"));
        assertThrows(JsonException.class, () -> nodes.putLastMemberEmail(new Account(new Profile(new Organization(new ArrayList<>(), new HashMap<>(), JsonObject.of(), new HashMap<>()))), "x"));
        assertThrows(JsonException.class, () -> nodes.putLast(new ArrayList<>(), 1));
    }

    @Test
    public void putsDynamicBracketParams() {
        PutNodes nodes = CompiledNodes.of(PutNodes.class);
        Account account = account();

        assertEquals("Old-District", nodes.putRegionDistrict(account, "east", 1, "New-District"));
        assertEquals("New-District", account.profile.organization.regions.get("east").get(1).district);

        Map<String, String> map = new HashMap<>();
        map.put("name", "old");
        assertEquals("old", nodes.putDynamicMapValue(map, "name", "new"));
        assertEquals("new", map.get("name"));

        List<String> friends = new ArrayList<>(List.of("Ann", "Bob"));
        assertEquals("Bob", nodes.putDynamicListValue(friends, 1, "Bill"));
        assertEquals(List.of("Ann", "Bill"), friends);
        assertEquals("Bill", nodes.putDynamicListValue(friends, -1, "Ben"));
        assertEquals(List.of("Ann", "Ben"), friends);
        assertNull(nodes.putDynamicListValue(friends, 2, "Cara"));
        assertEquals(List.of("Ann", "Ben", "Cara"), friends);

        JsonObject json = JsonObject.of("name", "old");
        assertEquals("old", nodes.putDynamicJsonObjectValue(json, "name", "new"));
        assertEquals("new", json.getString("name"));
    }

    @Test
    public void dynamicIndexMissingParentThrowsJsonException() {
        PutNodes nodes = CompiledNodes.of(PutNodes.class);
        Account account = account();

        assertThrows(JsonException.class, () -> nodes.putRegionDistrict(account, "east", 9, "x"));
        assertThrows(JsonException.class, () -> nodes.putDynamicListValue(new ArrayList<>(List.of("a")), 9, "x"));
    }

    private static Account account() {
        Contact first = new Contact("first@example.com");
        Contact last = new Contact("last@example.com");
        Team team = new Team(new ArrayList<>(List.of(new Member(first), new Member(last))));
        Map<String, Address> addresses = new HashMap<>();
        addresses.put("home", new Address(new City("Hangzhou", "Binjiang")));
        JsonObject metadata = JsonObject.of("owner", JsonObject.of("name", "owner-name"));
        Map<String, List<City>> regions = new HashMap<>();
        regions.put("east", new ArrayList<>(List.of(new City("A", "A-District"), new City("B", "Old-District"))));
        return new Account(new Profile(new Organization(new ArrayList<>(List.of(team)), addresses, metadata, regions)));
    }

    static final class Account {
        private final Profile profile;

        Account(Profile profile) { this.profile = profile; }

        public Profile getProfile() { return profile; }
    }

    record Profile(Organization organization) {}

    record Organization(List<Team> teams, Map<String, Address> addresses, JsonObject metadata, Map<String, List<City>> regions) {}

    record Team(List<Member> members) {}
    record Member(Contact contact) {}

    static final class Contact {
        private String email;

        Contact(String email) { this.email = email; }

        public String getEmail() { return email; }

        public void setEmail(String email) { this.email = email; }
    }

    record Address(City city) {}
    static final class City {
        private final String name;
        public String district;

        City(String name, String district) {
            this.name = name;
            this.district = district;
        }

        public String getName() { return name; }
    }

    static final class MutableBean {
        private String value;
        public String field;

        MutableBean(String value, String field) {
            this.value = value;
            this.field = field;
        }

        public String getValue() { return value; }

        public void setValue(String value) { this.value = value; }
    }

    @CompiledPath
    interface PutNodes {
        @PutByPath("$.profile.organization.teams[0].members[-1].contact.email")
        String putLastMemberEmail(Account root, String value);

        @PutByPath("$.profile.organization.addresses.home.city.district")
        String putHomeDistrict(Account root, String value);

        @PutByPath("$.profile.organization.metadata.owner.name")
        Object putMetadataOwnerName(Account root, String value);

        @PutByPath("$[-1]")
        Integer putLast(List<Integer> root, Integer value);

        @PutByPath("$[+]")
        Integer append(List<Integer> root, Integer value);

        @PutByPath("$[4]")
        Integer putAtSize(List<Integer> root, Integer value);

        @PutByPath("$[-1]")
        Integer putLastArray(Integer[] root, Integer value);

        @PutByPath("$[3]")
        Integer putArrayAtSize(Integer[] root, Integer value);

        @PutByPath("$[0]")
        Object putJsonArrayValue(JsonArray root, String value);

        @PutByPath("$[+]")
        Object putJsonArrayAppend(JsonArray root, String value);

        @PutByPath("$.name")
        String putMapName(Map<String, String> root, String value);

        @PutByPath("$.value")
        String putBeanValue(MutableBean root, String value);

        @PutByPath("$.field")
        void putBeanField(MutableBean root, String value);

        @PutByPath("$.value")
        int putPrimitiveOld(Map<String, Integer> root, int value);

        @PutByPath("$.profile.organization.regions[{region}][{idx}].district")
        String putRegionDistrict(Account root, String region, int idx, String value);

        @PutByPath("$[{name}]")
        String putDynamicMapValue(Map<String, String> root, String name, String value);

        @PutByPath("$[{idx}]")
        String putDynamicListValue(List<String> root, int idx, String value);

        @PutByPath("$[{name}]")
        Object putDynamicJsonObjectValue(JsonObject root, String name, String value);
    }
}
