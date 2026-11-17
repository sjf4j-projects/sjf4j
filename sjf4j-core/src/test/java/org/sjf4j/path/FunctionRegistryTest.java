package org.sjf4j.path;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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


    @Test
    public void testRegexMatchAndSearch() {
        assertTrue(regex("match", "A.*", "Alice"));
        assertFalse(regex("match", "A.*", "Hello Alice"));
        assertTrue(regex("search", "A.*", "Hello Alice"));
        assertFalse(regex("search", "A.*", "Hello Bob"));
        assertTrue(regex("match", "", ""));
        assertTrue(regex("search", "", "abc"));
        assertFalse(regex("search", "a", null));
    }

    @Test
    public void testRegexJavaSemantics() {
        assertTrue(regex("match", "^A", "A"));
        assertFalse(regex("match", "^A", "^A"));
        assertTrue(regex("match", "A$", "A"));
        assertFalse(regex("match", ".", "\u2028"));
        assertTrue(regex("match", "\\d+", "123"));
        assertTrue(regex("match", "(cat|dog)s?", "dogs"));
        assertTrue(regex("match", "\\p{L}+", "中文"));
        assertTrue(regex("match", "[a-z&&[^aeiou]]+", "bcdf"));
        assertTrue(regex("match", "(?=A)A.*", "Alice"));
        assertTrue(regex("match", Pattern.quote("\\") + "\\$", "\\$"));
    }

    @Test
    public void testRegexInvalidPattern() {
        assertFalse(regex("match", "[abc", "a"));
        assertFalse(regex("search", "(", "a"));
        assertThrows(org.sjf4j.exception.NodeException.class,
                () -> FunctionRegistry.invoke("match", "a", new Object[]{null}));
    }

    @Test
    public void testRegexJsonPathFilters() {
        Map<String, Object> alice = new HashMap<>();
        alice.put("name", "Alice");
        Map<String, Object> bob = new HashMap<>();
        bob.put("name", "Bob");
        Map<String, Object> amy = new HashMap<>();
        amy.put("name", "Amy");
        Map<String, Object> root = new HashMap<>();
        root.put("users", Arrays.asList(alice, bob, amy));

        assertEquals(Arrays.asList("Alice", "Amy"),
                JsonPath.parse("$.users[?match(@.name, 'A.*')].name")
                        .find(root, String.class));
        assertEquals(Arrays.asList("Alice", "Amy"),
                JsonPath.parse("$.users[?search(@.name, 'A')].name")
                        .find(root, String.class));
    }

    private static boolean regex(String function, String pattern, String text) {
        return Boolean.TRUE.equals(FunctionRegistry.invoke(function, text, new Object[]{pattern}));
    }

}
