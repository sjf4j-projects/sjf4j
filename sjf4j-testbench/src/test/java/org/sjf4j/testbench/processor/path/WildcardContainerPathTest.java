package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.path.PutByPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Runtime coverage for nested paths through wildcard-typed containers. */
public class WildcardContainerPathTest {

    @Test
    public void listExtendsUserSupportsNestedReadsAndPropertyPut() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);
        User first = new User("first");
        List<User> users = new ArrayList<>(List.of(first, new User("second")));
        ListUsers root = new ListUsers(users);

        assertEquals("first", nodes.getListUserName(root));
        assertEquals(List.of("first", "second"), nodes.findListUserNames(root));
        assertEquals("first", nodes.putListUserName(root, "updated"));
        assertSame(first, root.users().get(0));
        assertEquals("updated", first.getName());
    }

    @Test
    public void mapExtendsUserSupportsNestedReadsAndPropertyPut() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);
        User primary = new User("primary");
        Map<String, User> users = new LinkedHashMap<>();
        users.put("primary", primary);
        MapUsers root = new MapUsers(users);

        assertEquals("primary", nodes.getMapUserName(root));
        assertEquals(List.of("primary"), nodes.findMapUserNames(root));
        assertEquals("primary", nodes.putMapUserName(root, "updated"));
        assertSame(primary, root.users().get("primary"));
        assertEquals("updated", primary.getName());
    }

    @Test
    public void unboundedListSupportsObjectReads() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);
        UnknownUsers root = new UnknownUsers(List.of("first", 2));

        assertEquals("first", nodes.getUnknownListValue(root));
        assertEquals(List.of("first", 2), nodes.findUnknownListValues(root));
    }

    static final class User {
        private String name;

        User(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    record ListUsers(List<? extends User> users) {}

    record MapUsers(Map<String, ? extends User> users) {}

    record UnknownUsers(List<?> users) {}

    @CompiledNavigator
    interface WildcardNodes {
        @GetByPath("$.users[0].name")
        String getListUserName(ListUsers root);

        @FindByPath("$.users[*].name")
        List<String> findListUserNames(ListUsers root);

        @PutByPath("$.users[0].name")
        String putListUserName(ListUsers root, String value);

        @GetByPath("$.users.primary.name")
        String getMapUserName(MapUsers root);

        @FindByPath("$.users[*].name")
        List<String> findMapUserNames(MapUsers root);

        @PutByPath("$.users.primary.name")
        String putMapUserName(MapUsers root, String value);

        @GetByPath("$.users[0]")
        Object getUnknownListValue(UnknownUsers root);

        @FindByPath("$.users[*]")
        List<Object> findUnknownListValues(UnknownUsers root);

        /*
         * Direct list/map value replacement, append, and ensure-materialization
         * through wildcard containers are intentionally absent: they must fail
         * annotation-processor compilation.
         */
    }
}
