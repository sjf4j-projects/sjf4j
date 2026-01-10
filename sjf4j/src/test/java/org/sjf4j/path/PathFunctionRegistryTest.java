package org.sjf4j.path;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class PathFunctionRegistryTest {

    @Test
    public void testRegister1() {
        PathFunctionRegistry.register(new PathFunctionRegistry.FunctionDescriptor("hi", args -> {
            return "hi, " + Arrays.toString(args);
        }));

        Set<String> functionNames = PathFunctionRegistry.getFunctionNames();
        log.info("Registered functions: {}", functionNames);
        assertTrue(functionNames.contains("hi"));

        JsonObject jo = JsonObject.fromJson("{\"aa\":\"bb\"}");
        String hi = JsonPath.compile("$.hi('xixi', 5, \"99\")").eval(jo, String.class);
        log.info("hi = {}", hi);
        assertEquals("hi, [J{aa=bb}, xixi, 5, 99]", hi);

        hi = JsonPath.compile("$.hi()").eval(jo, String.class);
        log.info("hi = {}", hi);
        assertEquals("hi, [J{aa=bb}]", hi);

        assertThrows(Exception.class, () -> JsonPath.compile("$.hi(@.aa)").eval(jo, String.class));
    }

}
