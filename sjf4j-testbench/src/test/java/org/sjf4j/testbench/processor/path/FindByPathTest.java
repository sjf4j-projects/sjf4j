package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.CompiledInstances;
import org.sjf4j.path.JsonPath;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@code @FindByPath} generated implementations.
 *
 * <p>Tests wildcard, union, and raw/Object return type scenarios against
 * real POJO/Map data.</p>
 */
public class FindByPathTest {

    // ---- Model types -------------------------------------------------------

    record Item(String name, int age) {}

    record Family(String name, List<Item> children) {}

    record Container(List<Item> items, List<Family> families, Map<String, Object> metadata) {}

    record Registry(Map<String, Item> items) {}

    record Concrete(LinkedList<Item> items) {}

    record FinalConcrete(IteratorOnlyList<Item> items) {}

    static final class IteratorOnlyList<E> extends LinkedList<E> {
        IteratorOnlyList(List<E> values) { super(values); }

        @Override
        public E get(int index) {
            throw new AssertionError("Sequential list must not use get(int)");
        }
    }

    // ---- Compiled interfaces -----------------------------------------------

    @CompiledNavigator
    interface FindNodes {
        @FindByPath("$.items[*].name")
        List<String> itemNames(Container root);

        @FindByPath("$.metadata['version','missing','author','nullable']")
        List<Object> metadataFields(Container root);

        @FindByPath("$.items[2,0].name")
        List<String> itemNamesByIndexUnion(Container root);

        @FindByPath("$.families[*].children[*]")
        List<Item> allChildren(Container root);

        @FindByPath("$.families[*].children[*].name")
        List<String> childNames(Container root);

        @FindByPath("$.items[*]")
        List<Object> allItems(Container root);

        @FindByPath("$.items[*].name")
        List<Object> itemNamesAsObject(Container root);

        @FindByPath("$.items[0:2].name")
        List<String> firstTwoItemNames(Container root);

        @FindByPath("$.items[::2].name")
        List<String> everyOtherItemName(Container root);

        @FindByPath("$.items[-2:].name")
        List<String> lastTwoItemNames(Container root);

        @FindByPath("$.items[:-1].name")
        List<String> allButLastItemNames(Container root);

        @FindByPath("$.items[::-1].name")
        List<String> reverseItemNames(Container root);

        @FindByPath("$.items[-9007199254740991:9007199254740991].name")
        List<String> clampedLongBoundItemNames(Container root);

        @FindByPath("$.items[-1,2:0:-1].name")
        List<String> unionSliceItemNames(Container root);

        @FindByPath(value = "$.families..name")
        List<String> familyDescendantNames(Container root);

        @FindByPath("$.items[*].name")
        List<String> concreteNames(Concrete root);

        @FindByPath("$.items[1::2].name")
        List<String> concreteStrideNames(Concrete root);

        @FindByPath("$.items[::-2].name")
        List<String> concreteReverseNames(Concrete root);

        @FindByPath("$.items[3:0:-2].name")
        List<String> reverseBoundedStrideNames(Concrete root);

        @FindByPath("$.items[-1:-4:-2].name")
        List<String> reverseNegativeBoundedStrideNames(Concrete root);

        @FindByPath("$.items[0:0].name")
        List<String> zeroWidthNames(Concrete root);

        @FindByPath("$.items[1:2].name")
        List<String> singleName(Concrete root);

        @FindByPath("$.items[::-1].name")
        List<String> finalConcreteReverseNames(FinalConcrete root);
    }

    @CompiledNavigator
    interface BasicFilterNodes {
        @FindByPath("$.items[?(@.age >= 18)].name")
        List<String> adultNames(Container root);

        @FindByPath("$.items[?(@.age >= 18 && @.age < 30)].name")
        List<String> ageRange(Container root);

        @FindByPath("$.items[?(@.age < 18 || @.name == 'Charlie')].name")
        List<String> youngOrCharlie(Container root);

        @FindByPath("$.items[?(!@.disabled)].name")
        List<String> withoutDisabled(Container root);

        @FindByPath("$.items[?(@.flag)].name")
        List<Object> flagPresent(Map<String, Object> root);

        @FindByPath("$.items[?(!@.flag)].name")
        List<Object> flagMissing(Map<String, Object> root);

        @FindByPath("$.items[?(@.value == null)].name")
        List<Object> valueNull(Map<String, Object> root);

        @FindByPath("$.items[?(@.missingA == @.missingB)].name")
        List<Object> bothMissing(Map<String, Object> root);

        @FindByPath("$.items[?(@.score.math >= $.minimum)].name")
        List<Object> nestedScore(Map<String, Object> root);

        @FindByPath("$.items[?(@ == null)]")
        List<Object> nullItems(Map<String, Object> root);
    }

    /** Unsupported predicates still require explicit fallback. */
    @CompiledNavigator(fallback = true)
    interface FilterNodes {
        @FindByPath(value = "$.items[?(@.age > 18)].name")
        List<String> adultItemNames(Container root);

        @FindByPath(value = "$.items[?(@.name == \"Bob\")].age")
        List<Integer> bobAges(Container root);

        @FindByPath(value = "$.items[?(@.age > 18)].name")
        List<String> adultRegistryNames(Registry root);

        @FindByPath(value = "$.items[?(@ == null)]")
        List<Item> nullItems(Concrete root);

        @FindByPath(value = "$.items[?(@.age > 18)].name")
        List<String> concreteAdultNames(Concrete root);

        @FindByPath("$.items[?(@.name =~ /B/)].name")
        List<String> regexNames(Container root);
    }

    // ---- Tests -------------------------------------------------------------

    @Test
    public void wildcardReturnsAllItemNames() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        List<String> names = nodes.itemNames(root);
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test
    public void unionReturnsValuesForSpecifiedKeysInOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        List<Object> fields = nodes.metadataFields(root);
        assertEquals(3, fields.size());
        assertEquals("1.0", fields.get(0));
        assertEquals("test", fields.get(1));
        assertEquals(null, fields.get(2));
    }

    @Test
    public void indexUnionReturnsPathOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        assertEquals(List.of("Charlie", "Alice"), nodes.itemNamesByIndexUnion(root));
    }

    @Test
    public void nestedWildcardReturnsValuesInNestedOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        assertEquals(List.of(root.items().get(0), root.items().get(1), root.items().get(2)), nodes.allChildren(root));
        assertEquals(List.of("Alice", "Bob", "Charlie"), nodes.childNames(root));
    }

    @Test
    public void rawObjectReturnReturnsItems() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        List<Object> items = nodes.allItems(root);
        assertEquals(3, items.size());
        // Each item should be an Item record
        for (Object item : items) {
            assertTrue(item instanceof Item, "each element should be an Item, got " + item.getClass());
        }
        // Verify order
        assertEquals(root.items().get(0), items.get(0));
        assertEquals(root.items().get(1), items.get(1));
        assertEquals(root.items().get(2), items.get(2));
    }

    @Test
    public void wildcardWithObjectReturnReturnsAllItemNames() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        List<Object> names = nodes.itemNamesAsObject(root);
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test
    public void sliceReturnsMatchingValuesInArrayOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        assertEquals(List.of("Alice", "Bob"), nodes.firstTwoItemNames(root));
        assertEquals(List.of("Alice", "Charlie"), nodes.everyOtherItemName(root));
        assertEquals(List.of("Bob", "Charlie"), nodes.lastTwoItemNames(root));
        assertEquals(List.of("Alice", "Bob"), nodes.allButLastItemNames(root));
        assertEquals(List.of("Charlie", "Bob", "Alice"), nodes.reverseItemNames(root));
        assertEquals(List.of("Alice", "Bob", "Charlie"), nodes.clampedLongBoundItemNames(root));
        assertEquals(List.of("Charlie", "Charlie", "Bob"), nodes.unionSliceItemNames(root));
    }

    @Test
    public void filterReturnsMatchingValuesInArrayOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        FilterNodes filters = CompiledInstances.create(FilterNodes.class);
        Container root = container();

        assertEquals(List.of("Bob", "Charlie"), filters.adultItemNames(root));
        assertEquals(List.of(25), filters.bobAges(root));

        Map<String, Item> registryItems = new LinkedHashMap<>();
        registryItems.put("a", root.items().get(0));
        registryItems.put("b", root.items().get(1));
        registryItems.put("c", root.items().get(2));
        assertEquals(List.of("Bob", "Charlie"), filters.adultRegistryNames(new Registry(registryItems)));
    }

    @Test
    public void sequentialListsPreserveWildcardSliceFilterAndNestedOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        FilterNodes filters = CompiledInstances.create(FilterNodes.class);
        Container random = container();
        IteratorOnlyList<Item> items = new IteratorOnlyList<>(random.items());
        Container sequential = new Container(items,
                new IteratorOnlyList<>(List.of(
                        new Family("one", new IteratorOnlyList<>(items.subList(0, 2))),
                        new Family("two", new IteratorOnlyList<>(items.subList(2, 3))))),
                random.metadata());

        assertEquals(List.of("Alice", "Bob", "Charlie"), nodes.itemNames(sequential));
        assertEquals(List.of("Alice", "Bob", "Charlie"), nodes.childNames(sequential));
        assertEquals(List.of("Alice", "Charlie"), nodes.everyOtherItemName(sequential));
        assertEquals(List.of("Bob", "Charlie"), nodes.lastTwoItemNames(sequential));
        assertEquals(List.of("Charlie", "Bob", "Alice"), nodes.reverseItemNames(sequential));
        assertEquals(List.of("Bob", "Charlie"), filters.adultItemNames(sequential));

        Concrete concrete = new Concrete(items);
        assertEquals(List.of("Alice", "Bob", "Charlie"), nodes.concreteNames(concrete));
        assertEquals(List.of("Bob"), nodes.concreteStrideNames(concrete));
        assertEquals(List.of("Charlie", "Alice"), nodes.concreteReverseNames(concrete));
        assertEquals(List.of("Charlie"), nodes.reverseBoundedStrideNames(concrete));
        assertEquals(List.of("Charlie", "Alice"), nodes.reverseNegativeBoundedStrideNames(concrete));
        assertTrue(nodes.zeroWidthNames(concrete).isEmpty());
        assertEquals(List.of("Bob"), nodes.singleName(concrete));
        assertEquals(List.of("Bob", "Charlie"), filters.concreteAdultNames(concrete));
        assertEquals(List.of("Charlie", "Bob", "Alice"),
                nodes.finalConcreteReverseNames(new FinalConcrete(items)));

        assertTrue(nodes.concreteReverseNames(new Concrete(new IteratorOnlyList<>(List.of())))
                .isEmpty());
        IteratorOnlyList<Item> withNull = new IteratorOnlyList<>(java.util.Arrays.asList(random.items().get(0), null, random.items().get(2)));
        assertEquals(java.util.Collections.singletonList(null), filters.nullItems(new Concrete(withNull)));
    }

    @Test
    public void compiledDescendantReturnsMatchingValuesInOrder() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container root = container();

        assertEquals(List.of("one", "Alice", "Bob", "empty", "two", "Charlie"),
                nodes.familyDescendantNames(root));
    }

    @Test
    public void emptyResultsReturnEmptyList() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);
        Container empty = new Container(List.of(), List.of(), Map.of());

        List<String> names = nodes.itemNames(empty);
        assertTrue(names.isEmpty());

        List<Object> items = nodes.allItems(empty);
        assertTrue(items.isEmpty());

        List<Object> metadata = nodes.metadataFields(empty);
        assertTrue(metadata.isEmpty());
    }

    @Test
    public void nullRootThrowsNullPointerExceptionForFind() {
        FindNodes nodes = CompiledInstances.create(FindNodes.class);

        assertThrows(NullPointerException.class, () -> nodes.itemNames(null));
    }

    @Test
    public void basicCompiledFiltersMatchRuntimeJsonPath() {
        BasicFilterNodes compiled = CompiledInstances.create(BasicFilterNodes.class);
        FilterNodes fallback = CompiledInstances.create(FilterNodes.class);
        Container root = container();

        assertEquals(List.of("Bob", "Charlie"), compiled.adultNames(root));
        assertEquals(List.of("Bob"), compiled.ageRange(root));
        assertEquals(List.of("Alice", "Charlie"), compiled.youngOrCharlie(root));
        assertEquals(List.of("Alice", "Bob", "Charlie"), compiled.withoutDisabled(root));
        assertEquals(List.of("Bob"), fallback.regexNames(root));

        assertEquals(JsonPath.parse("$.items[?(@.age >= 18)].name")
                .find(root, Object.class), compiled.adultNames(root));
        assertEquals(JsonPath.parse("$.items[?(@.age >= 18 && @.age < 30)].name")
                .find(root, Object.class), compiled.ageRange(root));
        assertEquals(JsonPath.parse("$.items[?(@.age < 18 || @.name == 'Charlie')].name")
                .find(root, Object.class), compiled.youngOrCharlie(root));
        assertEquals(JsonPath.parse("$.items[?(!@.disabled)].name")
                .find(root, Object.class), compiled.withoutDisabled(root));
    }

    @Test
    public void compiledFiltersDistinguishMissingFromNullAndUseRootContext() {
        BasicFilterNodes compiled = CompiledInstances.create(BasicFilterNodes.class);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "first");
        first.put("flag", false);
        first.put("value", null);
        first.put("score", Map.of("math", 91));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "second");
        second.put("score", Map.of("math", 89));
        Map<String, Object> third = new LinkedHashMap<>();
        third.put("name", "third");
        third.put("flag", null);
        third.put("value", 42);
        third.put("score", Map.of("math", 95));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", List.of(first, second, third));
        root.put("minimum", 90);

        assertEquals(List.of("first", "third"), compiled.flagPresent(root));
        assertEquals(List.of("second"), compiled.flagMissing(root));
        assertEquals(List.of("first"), compiled.valueNull(root));
        assertEquals(List.of("first", "second", "third"), compiled.bothMissing(root));
        assertEquals(List.of("first", "third"), compiled.nestedScore(root));

        for (String query : List.of(
                "$.items[?(@.flag)].name",
                "$.items[?(!@.flag)].name",
                "$.items[?(@.value == null)].name",
                "$.items[?(@.missingA == @.missingB)].name",
                "$.items[?(@.score.math >= $.minimum)].name")) {
            List<Object> runtime = JsonPath.parse(query).find(root, Object.class);
            List<Object> actual;
            switch (query) {
                case "$.items[?(@.flag)].name": actual = compiled.flagPresent(root); break;
                case "$.items[?(!@.flag)].name": actual = compiled.flagMissing(root); break;
                case "$.items[?(@.value == null)].name": actual = compiled.valueNull(root); break;
                case "$.items[?(@.missingA == @.missingB)].name": actual = compiled.bothMissing(root); break;
                default: actual = compiled.nestedScore(root);
            }
            assertEquals(runtime, actual, query);
        }

        Map<String, Object> withNull = Map.of("items",
                java.util.Arrays.asList(first, null, second));
        assertEquals(java.util.Collections.singletonList(null), compiled.nullItems(withNull));
        assertEquals(JsonPath.parse("$.items[?(@ == null)]").find(withNull, Object.class),
                compiled.nullItems(withNull));
    }

    // ---- Helpers -----------------------------------------------------------

    private static Container container() {
        List<Item> items = List.of(
                new Item("Alice", 10),
                new Item("Bob", 25),
                new Item("Charlie", 30)
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("author", "test");
        metadata.put("nullable", null);
        metadata.put("redundant", "ignored");
        metadata.put("version", "1.0");
        List<Family> families = List.of(
                new Family("one", List.of(items.get(0), items.get(1))),
                new Family("empty", List.of()),
                new Family("two", List.of(items.get(2)))
        );
        return new Container(items, families, metadata);
    }


}
