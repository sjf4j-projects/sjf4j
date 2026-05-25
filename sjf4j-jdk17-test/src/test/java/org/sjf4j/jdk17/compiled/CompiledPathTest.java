package org.sjf4j.jdk17.compiled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledPathTest {

    @Test
    public void testAsm1() {
        CompiledPath<Root, Integer> path = CompiledPath.compile("$.a", Root.class, Integer.class);
        Assertions.assertInstanceOf(CompiledPath.class, path);
        assertTrue(path.getClass().getName().startsWith("org.sjf4j.bytecode.generated.CompiledPath_"));

        Root root = new Root();
        root.a = 5;
        assertEquals(Integer.valueOf(5), path.get(root));
        assertNull(path.put(root, 7));
        assertEquals(Integer.valueOf(7), root.a);
    }

    @Test
    public void testObjectRootIsRejectedByBytecodeCompiler() {
        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("$.a", Object.class, Integer.class));
        assertTrue(ex.getMessage().contains("Object"));
        assertTrue(ex.getMessage().contains("FallbackCompiledPath"));
    }

    public static class Root {
        public Integer a;
    }

}
