package org.sjf4j.jdk17.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.GetByPath;
import org.sjf4j.compiled.CompiledNodesRegistry;
import org.sjf4j.exception.JsonException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GetByPathTest {

    @Test
    public void getsDeepRecordListMapAndJsonPaths() {
        GetNodes nodes = CompiledNodesRegistry.of(GetNodes.class);
        Account account = account();

        assertEquals("last@example.com", nodes.getLastMemberEmail(account));
        assertEquals("Binjiang", nodes.getHomeDistrict(account));
        assertEquals("owner-name", nodes.getMetadataOwnerName(account));
        assertEquals("array-value", nodes.getJsonArrayValue(JsonArray.of("array-value")));
    }

    @Test
    public void missingReferencePathReturnsNull() {
        GetNodes nodes = CompiledNodesRegistry.of(GetNodes.class);

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
        GetNodes nodes = CompiledNodesRegistry.of(GetNodes.class);

        assertEquals(7, nodes.getMemberScore(account()));
        assertThrows(JsonException.class, () -> nodes.getMemberScore(null));
        assertThrows(JsonException.class, () -> nodes.getMemberScore(new Account(new Profile(new Organization(List.of(), Map.of(), JsonObject.of())))));
    }

    @Test
    public void supportsPublicFieldBooleanGetterAndTypedContainers() {
        GetNodes nodes = CompiledNodesRegistry.of(GetNodes.class);

        assertEquals("field-value", nodes.getPublicField(new FieldBean("field-value")));
        assertEquals(Boolean.TRUE, nodes.isActive(new FlagBean(true)));
        assertEquals("typed", nodes.getTypedMapName(Map.of("name", "typed")));
        assertEquals("tail", nodes.getLastArrayValue(new String[]{"head", "tail"}));
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

    record FieldBean(String value) {}

    static final class FlagBean {
        private final boolean active;

        FlagBean(boolean active) { this.active = active; }

        public boolean isActive() { return active; }
    }

    @CompiledNodes
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
    }
}
