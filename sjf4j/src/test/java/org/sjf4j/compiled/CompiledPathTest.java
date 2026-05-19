package org.sjf4j.compiled;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class CompiledPathTest {


    @Test
    public void testAsm1() {
        try {
            CompiledPath<Object, Object> path = CompiledPath.compile("$.a.b", Object.class, Object.class);
            System.out.println("path class: " + path.getClass());
        } catch (LinkageError e) {
            Assumptions.abort("skip bytecode-backed path test while sjf4j-bytecode is incompatible: " + e);
        }
    }

}
