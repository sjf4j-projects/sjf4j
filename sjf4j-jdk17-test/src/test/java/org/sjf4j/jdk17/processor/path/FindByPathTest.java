package org.sjf4j.jdk17.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.compiled.CompiledNodes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime tests for {@code @FindByPath} generated implementations.
 *
 * <p>Tests wildcard, union, and raw/Object return type scenarios against
 * real POJO/Map data.</p>
 */
public class FindByPathTest {

    // ---- Model types -------------------------------------------------------

    record Item(String name, int age) {}

    record Container(List<Item> items, Map<String, Object> metadata) {}

    // ---- Compiled interfaces -----------------------------------------------

    @CompiledPath
    interface FindNodes {
        @FindByPath("$.items[*].name")
        List<String> itemNames(Container root);

        @FindByPath("$.metadata['version','author']")
        List<Object> metadataFields(Container root);

        @FindByPath("$.items[*]")
        List<Object> allItems(Container root);

        @FindByPath("$.items[*].name")
        List<Object> itemNamesAsObject(Container root);
    }

    // ---- Tests -------------------------------------------------------------

    @Test
    public void wildcardReturnsAllItemNames() {
        FindNodes nodes = CompiledNodes.of(FindNodes.class);
        Container root = container();

        List<String> names = nodes.itemNames(root);
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test
    public void unionReturnsValuesForSpecifiedKeysInOrder() {
        FindNodes nodes = CompiledNodes.of(FindNodes.class);
        Container root = container();

        List<Object> fields = nodes.metadataFields(root);
        // metadata has version=1.0 and author=test in LinkedHashMap insertion order
        assertEquals(List.of("1.0", "test"), fields);
    }

    @Test
    public void rawObjectReturnReturnsItems() {
        FindNodes nodes = CompiledNodes.of(FindNodes.class);
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
        FindNodes nodes = CompiledNodes.of(FindNodes.class);
        Container root = container();

        List<Object> names = nodes.itemNamesAsObject(root);
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test
    public void emptyResultsReturnEmptyList() {
        FindNodes nodes = CompiledNodes.of(FindNodes.class);
        Container empty = new Container(List.of(), Map.of());

        List<String> names = nodes.itemNames(empty);
        assertTrue(names.isEmpty());

        List<Object> items = nodes.allItems(empty);
        assertTrue(items.isEmpty());
    }

    @Test
    public void nullRootThrowsNullPointerExceptionForFind() {
        FindNodes nodes = CompiledNodes.of(FindNodes.class);

        assertThrows(NullPointerException.class, () -> nodes.itemNames(null));
    }

    // ---- Helpers -----------------------------------------------------------

    private static Container container() {
        List<Item> items = List.of(
                new Item("Alice", 10),
                new Item("Bob", 25),
                new Item("Charlie", 30)
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("version", "1.0");
        metadata.put("author", "test");
        metadata.put("redundant", "ignored");
        return new Container(items, metadata);
    }


}
