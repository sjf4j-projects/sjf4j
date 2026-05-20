package org.sjf4j.jdk17.compiled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.compiled.FallbackCompiledPath;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompiledPathTest {

    @Test
    public void testAsm1() {
        CompiledPath<Object, Integer> path = CompiledPath.compile("$.a", Object.class, Integer.class);
        System.out.println("path class: " + path.getClass());
        Assertions.assertInstanceOf(FallbackCompiledPath.class, path);
        int v = path.get(Map.of("a", 5));
        assertEquals(5, v);

        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("a", 5);
        assertEquals(Integer.valueOf(5), path.put(root, 7));
        assertEquals(7, root.get("a"));
    }

}
