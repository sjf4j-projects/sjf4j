package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Focused coverage of segment composition in generated find methods. */
public class FindCompositionTest {

    record Book(String title, int pages) {}
    record Group(List<Book> books) {}
    record User(String name, int age) {}
    record Root(List<Group> groups, User user) {}

    @CompiledNavigator
    interface Compiled {
        @FindByPath("$.groups[*].books[0,2].title")
        List<String> wildcardThenUnion(Root root);

        @FindByPath("$.groups[0,1].books[*].title")
        List<String> unionThenWildcard(Root root);

        @FindByPath("$.groups[*].books[0:2,2].title")
        List<String> wildcardThenSliceUnion(Root root);

        @FindByPath("$.user['name','missing','age']")
        List<Object> pojoNameUnion(Root root);

        @FindByPath("$.groups[*].books[0:2].title")
        List<String> wildcardThenSlice(Root root);
    }

    @CompiledNavigator(fallback = true)
    interface Dynamic {
        @FindByPath("$.books[*].title")
        List<String> books(Map<String, Object> root);
    }

    @Test
    public void combinableSelectorsKeepOrderAndDuplicates() {
        Compiled paths = CompiledInstances.create(Compiled.class);
        Root root = new Root(Arrays.asList(
                new Group(Arrays.asList(new Book("A", 1), new Book("B", 2), new Book("C", 3))),
                new Group(Arrays.asList(new Book("D", 4), new Book("E", 5), new Book("F", 6)))),
                new User("Ada", 35));

        assertEquals(List.of("A", "C", "D", "F"), paths.wildcardThenUnion(root));
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), paths.unionThenWildcard(root));
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), paths.wildcardThenSliceUnion(root));
        assertEquals(List.of("A", "B", "D", "E"), paths.wildcardThenSlice(root));
        assertEquals(List.of("Ada", 35), paths.pojoNameUnion(root));
    }

    @Test
    public void dynamicUnknownShapeRequiresExplicitFallback() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("books", Arrays.asList(Map.of("title", "First"), Map.of("title", "Second")));
        assertEquals(List.of("First", "Second"), CompiledInstances.create(Dynamic.class).books(root));
    }
}
