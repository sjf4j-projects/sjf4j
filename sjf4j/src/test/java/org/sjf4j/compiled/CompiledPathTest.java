package org.sjf4j.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledPathTest {


    @Test
    public void testAsm1() {
        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("$.a.b", Root.class, Integer.class));
        assertTrue(ex.getMessage().contains("sjf4j-bytecode"));
    }

    public static class Root {
        public Child a;
    }

    public static class Child {
        public int b;
    }

}
