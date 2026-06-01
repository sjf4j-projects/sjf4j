package org.sjf4j.jdk17.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.PutByPath;
import org.sjf4j.annotation.path.PutIfParentPresentByPath;
import org.sjf4j.compiled.CompiledRegistry;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PutIfParentPresentByPathTest {

    @Test
    public void existingParentMutatesContainersAndPojo() {
        PutIfNodes nodes = CompiledRegistry.of(PutIfNodes.class);

        Map<String, String> map = new HashMap<>();
        map.put("name", "old-map");
        assertEquals("old-map", nodes.putMapName(map, "new-map"));
        assertEquals("new-map", map.get("name"));

        List<String> list = new ArrayList<>(List.of("zero", "one"));
        assertEquals("one", nodes.putListValue(list, "new-one"));
        assertEquals(List.of("zero", "new-one"), list);

        JsonObject json = JsonObject.of("name", "old-json");
        assertEquals("old-json", nodes.putJsonName(json, "new-json"));
        assertEquals("new-json", json.getString("name"));

        Bean bean = new Bean("old-bean", "old-field");
        assertEquals("old-bean", nodes.putBeanValue(bean, "new-bean"));
        assertEquals("new-bean", bean.getValue());
        assertEquals("old-field", nodes.putBeanField(bean, "new-field"));
        assertEquals("new-field", bean.field);
    }

    @Test
    public void missingIntermediateParentReturnsNullAndDoesNotCreate() {
        PutIfNodes nodes = CompiledRegistry.of(PutIfNodes.class);
        Account missingProfile = new Account(null);
        assertNull(nodes.putMemberEmail(missingProfile, "x"));
        assertNull(missingProfile.profile);

        Account missingTeams = new Account(new Profile(new Organization(null, new HashMap<>())));
        assertNull(nodes.putMemberEmail(missingTeams, "x"));
        assertNull(missingTeams.profile.organization.teams);

        assertNull(nodes.putMemberEmail(null, "x"));
    }

    @Test
    public void voidReturnMissingParentIsNoop() {
        PutIfNodes nodes = CompiledRegistry.of(PutIfNodes.class);
        Account account = new Account(null);
        nodes.putMemberEmailVoid(account, "x");
        assertNull(account.profile);
    }

    @Test
    public void dynamicNestedParamsMutateWhenParentExists() {
        PutIfNodes nodes = CompiledRegistry.of(PutIfNodes.class);
        Account account = account();

        assertEquals("old-east", nodes.putRegionDistrict(account, "east", 0, "new-east"));
        assertEquals("new-east", account.profile.organization.regions.get("east").get(0).district);

        assertNull(nodes.putRegionDistrict(account, "west", 0, "x"));
        assertNull(account.profile.organization.regions.get("west"));
    }

    @Test
    public void finalInvalidIndexWithExistingParentStillThrows() {
        PutIfNodes nodes = CompiledRegistry.of(PutIfNodes.class);
        assertThrows(JsonException.class, () -> nodes.putListValue(new ArrayList<>(), "x"));
    }

    @Test
    public void testPutMissing() {
        PutIfNodes nodes = CompiledRegistry.of(PutIfNodes.class);
        Account account = new Account(null);
        nodes.putIfParentMissing(account, "x");
        assertThrows(JsonException.class, () -> nodes.putMissing(account, "x"));
    }

    private static Account account() {
        Contact contact = new Contact("old@example.com");
        Team team = new Team(new ArrayList<>(List.of(new Member(contact))));
        Map<String, List<City>> regions = new HashMap<>();
        regions.put("east", new ArrayList<>(List.of(new City("old-east"))));
        return new Account(new Profile(new Organization(new ArrayList<>(List.of(team)), regions)));
    }

    static final class Account {
        final Profile profile;
        Account(Profile profile) { this.profile = profile; }
        public Profile getProfile() { return profile; }
    }

    record Profile(Organization organization) {}
    record Organization(List<Team> teams, Map<String, List<City>> regions) {}
    record Team(List<Member> members) {}
    record Member(Contact contact) {}

    static final class Contact {
        private String email;
        Contact(String email) { this.email = email; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    static final class City {
        public String district;
        City(String district) { this.district = district; }
    }

    static final class Bean {
        private String value;
        public String field;
        Bean(String value, String field) { this.value = value; this.field = field; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @CompiledPath
    interface PutIfNodes {
        @PutIfParentPresentByPath("$.name")
        String putMapName(Map<String, String> root, String value);

        @PutIfParentPresentByPath("$[1]")
        String putListValue(List<String> root, String value);

        @PutIfParentPresentByPath("$.name")
        Object putJsonName(JsonObject root, String value);

        @PutIfParentPresentByPath("$.value")
        String putBeanValue(Bean root, String value);

        @PutIfParentPresentByPath("$.field")
        String putBeanField(Bean root, String value);

        @PutIfParentPresentByPath("$.profile.organization.teams[0].members[0].contact.email")
        String putMemberEmail(Account root, String value);

        @PutIfParentPresentByPath("$.profile.organization.teams[0].members[0].contact.email")
        void putMemberEmailVoid(Account root, String value);

        @PutIfParentPresentByPath("$.profile.organization.regions[{region}][{idx}].district")
        String putRegionDistrict(Account root, String region, int idx, String value);

        @PutByPath("$.profile.organization.teams[5].members[0].contact.email")
        String putMissing(Account root, String value);

        @PutIfParentPresentByPath("$.profile.organization.teams[5].members[0].contact.email")
        String putIfParentMissing(Account root, String value);
    }
}
