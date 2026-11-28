package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.exception.NodeException;
import org.sjf4j.path.FunctionRegistry;
import org.sjf4j.path.JsonPath;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompiledFilterExtensionsTest {
    record Item(String name, List<String> tags, int age, String pattern) {}
    record Root(List<Item> items) {}

    @CompiledNavigator
    interface Queries {
        @FindByPath("$.items[?(@.name =~ /^a/i)].name")
        List<String> regex(Root root);

        @FindByPath("$.items[?(@.tags =~ /GO/i)].name")
        List<String> arrayRegex(Root root);

        @FindByPath("$.items[?(@.name in ['Alice','Bob'])].name")
        List<String> inLiteral(Root root);

        @FindByPath("$.items[?(@.name nin ['Alice','Bob'])].name")
        List<String> notInLiteral(Root root);

        @FindByPath("$.items[?(@.age in [17,20])].name")
        List<String> numericMembership(Root root);

        @FindByPath("$.items[?(@.age in [])].name")
        List<String> emptyMembership(Root root);

        @FindByPath("$.items[?(length(@.tags) >= 2)].name")
        List<String> length(Root root);

        @FindByPath("$.items[?(count(@.tags) > 0)].name")
        List<String> count(Root root);

        @FindByPath("$.items[?(match(@.name, '^A.*'))].name")
        List<String> match(Root root);

        @FindByPath("$.items[?(search(@.name, 'a'))].name")
        List<String> search(Root root);

        @FindByPath("$.items[?(search(@.name, @.pattern))].name")
        List<String> dynamicSearch(Root root);

        @FindByPath("$.items[?(value(@.age) >= 18)].name")
        List<String> value(Root root);

        @FindByPath("$.items[?(@.name =~ /^a/i && @.age in [17,20])].name")
        List<String> mixed(Root root);

        @FindByPath("$.items[?(phase3CustomCheck(@.name))].name")
        List<String> customFunction(Root root);

        @FindByPath("$.items[?(phase3CustomCheck(value(@.name)))].name")
        List<String> nestedFunction(Root root);

        @FindByPath("$.items[?(@.score in [null,2])].name")
        List<Object> nullMembership(Map<String, Object> root);

        @FindByPath("$.items[?(@.score in [null])].name")
        List<Object> singletonNullMembership(Map<String, Object> root);

        @FindByPath("$.items[?(@.score nin [null])].name")
        List<Object> singletonNullNonMembership(Map<String, Object> root);

        @FindByPath("$.items[?(@.score nin [1,2])].name")
        List<Object> notInWithMissing(Map<String, Object> root);
    }

    @Test
    void regexMembershipAndFunctionsMatchRuntime() {
        Queries compiled = CompiledInstances.create(Queries.class);
        Root root = fixture();

        assertMatches(root, "$.items[?(@.name =~ /^a/i)].name", compiled.regex(root),
                List.of("Alice"));
        assertMatches(root, "$.items[?(@.tags =~ /GO/i)].name", compiled.arrayRegex(root),
                List.of("Alice"));
        assertMatches(root, "$.items[?(@.name in ['Alice','Bob'])].name", compiled.inLiteral(root),
                List.of("Alice", "Bob"));
        assertMatches(root, "$.items[?(@.name nin ['Alice','Bob'])].name", compiled.notInLiteral(root),
                List.of("Cara", "Dylan"));
        assertMatches(root, "$.items[?(@.age in [17,20])].name", compiled.numericMembership(root),
                List.of("Alice", "Bob"));
        assertMatches(root, "$.items[?(@.age in [])].name", compiled.emptyMembership(root),
                List.of());
        assertMatches(root, "$.items[?(length(@.tags) >= 2)].name", compiled.length(root),
                List.of("Alice"));
        assertMatches(root, "$.items[?(count(@.tags) > 0)].name", compiled.count(root),
                List.of("Alice", "Bob"));
        assertMatches(root, "$.items[?(match(@.name, '^A.*'))].name", compiled.match(root),
                List.of("Alice"));
        assertMatches(root, "$.items[?(search(@.name, 'a'))].name", compiled.search(root),
                List.of("Cara", "Dylan"));
        assertMatches(root, "$.items[?(search(@.name, @.pattern))].name",
                compiled.dynamicSearch(root), List.of("Alice", "Bob", "Cara", "Dylan"));
        assertMatches(root, "$.items[?(value(@.age) >= 18)].name", compiled.value(root),
                List.of("Alice", "Cara"));
        assertMatches(root, "$.items[?(@.name =~ /^a/i && @.age in [17,20])].name",
                compiled.mixed(root), List.of("Alice"));
    }

    @Test
    void missingAndNullPreserveMembershipSemantics() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", "missing");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "null");
        b.put("score", null);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", "two");
        c.put("score", 2);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", "ten");
        d.put("score", 10);
        Map<String, Object> root = Map.of("items", Arrays.asList(a, b, c, d));
        Queries compiled = CompiledInstances.create(Queries.class);
        assertMatches(root, "$.items[?(@.score in [null,2])].name",
                compiled.nullMembership(root), List.of("null", "two"));
        assertMatches(root, "$.items[?(@.score in [null])].name",
                compiled.singletonNullMembership(root), List.of("null"));
        assertMatches(root, "$.items[?(@.score nin [null])].name",
                compiled.singletonNullNonMembership(root), List.of("two", "ten"));
        assertMatches(root, "$.items[?(@.score nin [1,2])].name",
                compiled.notInWithMissing(root), List.of("null", "ten"));
    }

    @Test
    void customFunctionRegistrationIsUnique() {
        Queries compiled = CompiledInstances.create(Queries.class);
        Root root = fixture();
        String query = "$.items[?(phase3CustomCheck(@.name))].name";
        String nested = "$.items[?(phase3CustomCheck(value(@.name)))].name";

        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("phase3CustomCheck",
                (target, args) -> "Alice".equals(target)));
        assertMatches(root, query, compiled.customFunction(root), List.of("Alice"));
        assertMatches(root, nested, compiled.nestedFunction(root), List.of("Alice"));

        assertThrows(NodeException.class, () -> FunctionRegistry.register(
                new FunctionRegistry.FunctionDescriptor("phase3CustomCheck",
                        (target, args) -> "Bob".equals(target))));
        assertMatches(root, query, compiled.customFunction(root), List.of("Alice"));
        assertMatches(root, nested, compiled.nestedFunction(root), List.of("Alice"));
    }

    private static Root fixture() {
        return new Root(Arrays.asList(
                new Item("Alice", List.of("go", "fast"), 20, "^A"),
                new Item("Bob", List.of("slow"), 17, "o"),
                new Item("Cara", null, 25, "a"),
                new Item("Dylan", List.of(), 0, "y")));
    }

    private static void assertMatches(Object root, String path,
                                      List<?> actual, List<?> expected) {
        assertEquals(expected, actual, path);
        assertEquals(JsonPath.parse(path).find(root, Object.class), actual, path);
    }
}
