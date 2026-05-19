package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.compiled.FallbackCompiledPath;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledPathTest {

    @Test
    void testFallbackBackedGetAndPut() {
        FallbackCompiledPath<Map<String, Object>, String> path = FallbackCompiledPath.compile("$.user.name");
 
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", "Ada");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("user", user);

        assertEquals("$.user.name", path.expr());
        assertEquals("$.user.name", path.toString());
        assertEquals("Ada", path.get(root));
        assertTrue(path.contains(root));
        assertTrue(path.hasNonNull(root));
        assertEquals("Ada", path.put(root, "Bob"));
        assertEquals("Bob", path.get(root));
    }

    @Test
    void testEnsurePutCreatesMissingContainers() {
        FallbackCompiledPath<Map<String, Object>, String> path = FallbackCompiledPath.compile("$.user.name");

        Map<String, Object> root = new LinkedHashMap<>();
        assertNull(path.ensurePut(root, "Ada"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) root.get("user");
        assertEquals("Ada", user.get("name"));
        assertEquals("Ada", path.get(root));
    }

    @Test
    void testRejectUnsupportedCompiledPathShapes() {
        assertThrows(JsonException.class, () -> FallbackCompiledPath.compile("$"));
        assertThrows(JsonException.class, () -> FallbackCompiledPath.compile("@.name"));
        assertThrows(JsonException.class, () -> FallbackCompiledPath.compile("$.users[*].name"));
        assertThrows(JsonException.class, () -> FallbackCompiledPath.compile("$..name"));
    }

    @Test
    void testAppendPathWriteOnlySemantics() {
        FallbackCompiledPath<List<String>, String> path = FallbackCompiledPath.compile("$[+]");
        List<String> root = new ArrayList<>();

        path.add(root, "a");
        path.put(root, "b");
        assertEquals(List.of("a", "b"), root);
        assertTrue(path.isAppend());
        assertFalse(path.contains(root));
        assertThrows(JsonException.class, () -> path.get(root));
        assertThrows(JsonException.class, () -> path.hasNonNull(root));
        assertThrows(JsonException.class, () -> path.replace(root, "c"));
        assertThrows(JsonException.class, () -> path.remove(root));
    }

    @Test
    void testCustomSubclassCanOverrideHotPath() {
        FallbackCompiledPath<ManualUser, String> path = new ManualUserNameCompiledPath();
        ManualUser user = new ManualUser();
        user.name = "Ada";

        assertEquals("$.name", path.expr());
        assertEquals("Ada", path.get(user));
        assertEquals("Ada", path.put(user, "Bob"));
        assertEquals("Bob", user.name);
    }
}

class ManualUser {
    String name;
}

final class ManualUserNameCompiledPath extends FallbackCompiledPath<ManualUser, String> {
    ManualUserNameCompiledPath() {
        super("$.name", JsonPath.parse("$.name"));
    }

    @Override
    public String get(ManualUser root) {
        return _requireRoot(root).name;
    }

    @Override
    public String put(ManualUser root, String value) {
        _requireRoot(root);
        String old = root.name;
        root.name = value;
        return old;
    }
}
