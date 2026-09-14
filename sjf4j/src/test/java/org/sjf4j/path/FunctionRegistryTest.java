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
public class FunctionRegistryTest {

    @Test
    public void testRegister1() {
        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("hi", (target, args) -> {
            Object[] values = new Object[args.length + 1];
            values[0] = target;
            System.arraycopy(args, 0, values, 1, args.length);
            return "hi, " + Arrays.toString(values);
        }));

        Set<String> functionNames = FunctionRegistry.getFunctionNames();
        log.info("Registered functions: {}", functionNames);
        assertTrue(functionNames.contains("hi"));

        JsonObject jo = JsonObject.fromJson("{\"aa\":\"bb\"}");
        String hi = JsonPath.parse("$.hi('xixi', 5, \"99\")").eval(jo, String.class);
        log.info("hi = {}", hi);
        assertEquals("hi, [J{aa=bb}, xixi, 5, 99]", hi);

        hi = JsonPath.parse("$.hi()").eval(jo, String.class);
        log.info("hi = {}", hi);
        assertEquals("hi, [J{aa=bb}]", hi);

        assertThrows(Exception.class, () -> JsonPath.parse("$.hi(@.aa)").eval(jo, String.class));
    }

}
