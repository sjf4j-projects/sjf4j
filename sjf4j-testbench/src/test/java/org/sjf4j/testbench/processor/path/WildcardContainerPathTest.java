package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.path.PutByPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Runtime coverage for nested paths through generic and wildcard-typed containers. */
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
    public void nestedExtendsContainersRemainReadableAtEveryLevel() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);
        User first = new User("first");
        User second = new User("second");

        List<List<User>> groups = new ArrayList<>();
        groups.add(List.of(first));
        groups.add(List.of(second));
        NestedListUsers root = new NestedListUsers(groups);

        assertEquals("first", nodes.getNestedListUserName(root));
        assertEquals(List.of("first", "second"), nodes.findNestedListUserNames(root));
        assertEquals("first", nodes.putNestedListUserName(root, "updated"));
        assertEquals("updated", first.getName());
    }

    @Test
    public void unboundedListAndMapSupportObjectReads() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);
        UnknownUsers listRoot = new UnknownUsers(Arrays.asList("first", 2, null));

        assertEquals("first", nodes.getUnknownListValue(listRoot));
        assertEquals(Arrays.asList("first", 2, null), nodes.findUnknownListValues(listRoot));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("first", "value");
        values.put("second", 2);
        values.put("none", null);
        UnknownMap mapRoot = new UnknownMap(values);

        assertEquals("value", nodes.getUnknownMapValue(mapRoot));
        assertEquals(Arrays.asList("value", 2, null), nodes.findUnknownMapValues(mapRoot));
    }

    @Test
    public void inheritedGenericPropertiesResolveToConcreteTypes() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);
        User first = new User("first");
        UserHolder root = new UserHolder();
        root.users.add(first);

        assertEquals("first", nodes.getInheritedUserName(root));
        assertEquals("first", nodes.putInheritedUserName(root, "updated"));
        assertEquals("updated", first.getName());
    }

    @Test
    public void inheritedContainerSubtypesRemainReadableAndWritable() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);

        UserList list = new UserList();
        User first = new User("first");
        list.add(first);
        ListSubtypeRoot listRoot = new ListSubtypeRoot(list);

        assertSame(first, nodes.getSubtypeListUser(listRoot));
        User replacement = new User("replacement");
        assertSame(first, nodes.putSubtypeListUser(listRoot, replacement));
        assertSame(replacement, list.get(0));

        UserMap map = new UserMap();
        User primary = new User("primary");
        map.put("primary", primary);
        MapSubtypeRoot mapRoot = new MapSubtypeRoot(map);

        assertSame(primary, nodes.getSubtypeMapUser(mapRoot));
        User mapReplacement = new User("replacement-map");
        assertSame(primary, nodes.putSubtypeMapUser(mapRoot, mapReplacement));
        assertSame(mapReplacement, map.get("primary"));
    }

    @Test
    public void nullWildcardLeavesFollowNormalReferencePathSemantics() {
        WildcardNodes nodes = CompiledInstances.of(WildcardNodes.class);

        ListUsers listRoot = new ListUsers(Arrays.asList((User) null));
        assertNull(nodes.getListUserName(listRoot));
        assertEquals(List.of(), nodes.findListUserNames(listRoot));

        Map<String, User> values = new LinkedHashMap<>();
        values.put("primary", null);
        MapUsers mapRoot = new MapUsers(values);
        assertNull(nodes.getMapUserName(mapRoot));
        assertEquals(List.of(), nodes.findMapUserNames(mapRoot));
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

    record ListUsers(List<? extends User> users) {
    }

    record MapUsers(Map<String, ? extends User> users) {
    }

    record NestedListUsers(List<? extends List<? extends User>> users) {
    }

    record UnknownUsers(List<?> users) {
    }

    record UnknownMap(Map<String, ?> values) {
    }

    static class GenericHolder<T> {
        public final List<T> users = new ArrayList<>();
    }

    static final class UserHolder extends GenericHolder<User> {
    }

    static final class UserList extends ArrayList<User> {
    }

    static final class UserMap extends LinkedHashMap<String, User> {
    }

    record ListSubtypeRoot(UserList users) {
    }

    record MapSubtypeRoot(UserMap users) {
    }

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

        @GetByPath("$.users[0][0].name")
        String getNestedListUserName(NestedListUsers root);

        @FindByPath("$.users[*][*].name")
        List<String> findNestedListUserNames(NestedListUsers root);

        @PutByPath("$.users[0][0].name")
        String putNestedListUserName(NestedListUsers root, String value);

        @GetByPath("$.users[0]")
        Object getUnknownListValue(UnknownUsers root);

        @FindByPath("$.users[*]")
        List<Object> findUnknownListValues(UnknownUsers root);

        @GetByPath("$.values.first")
        Object getUnknownMapValue(UnknownMap root);

        @FindByPath("$.values[*]")
        List<Object> findUnknownMapValues(UnknownMap root);

        @GetByPath("$.users[0].name")
        String getInheritedUserName(UserHolder root);

        @PutByPath("$.users[0].name")
        String putInheritedUserName(UserHolder root, String value);

        @GetByPath("$.users[0]")
        User getSubtypeListUser(ListSubtypeRoot root);

        @PutByPath("$.users[0]")
        User putSubtypeListUser(ListSubtypeRoot root, User value);

        @GetByPath("$.users.primary")
        User getSubtypeMapUser(MapSubtypeRoot root);

        @PutByPath("$.users.primary")
        User putSubtypeMapUser(MapSubtypeRoot root, User value);

        /*
         * Direct list/map value replacement, append, and ensure-materialization
         * through wildcard containers are intentionally absent: ? extends T
         * contributes a read type only and must not be used as a writable T.
         */
    }
}
