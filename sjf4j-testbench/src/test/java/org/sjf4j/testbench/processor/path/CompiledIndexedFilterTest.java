package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.path.JsonPath;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runtime/compiled parity for singular indexed paths in filters. */
class CompiledIndexedFilterTest {
    record Grade(Integer score) {}
    record Student(String name, List<Grade> grades, Map<String, Object> extra) {}
    record Classroom(List<Student> students, List<Integer> minimums) {}

    @CompiledNavigator
    interface Queries {
        @FindByPath("$.students[?(@.grades[0].score >= 90)].name")
        List<String> firstGrade(Classroom root);

        @FindByPath("$.students[?(@.grades[-1].score >= 90)].name")
        List<String> lastGrade(Classroom root);

        @FindByPath("$.students[?(@.grades[0])].name")
        List<String> firstPresent(Classroom root);

        @FindByPath("$.students[?(!@.grades[0])].name")
        List<String> firstMissing(Classroom root);

        @FindByPath("$.students[?(@.grades[0].score == null)].name")
        List<String> explicitNull(Classroom root);

        @FindByPath("$.students[?(@.grades[0].score >= $.minimums[-1])].name")
        List<String> rootedThreshold(Classroom root);

        @FindByPath("$.students[?(@.extra.scores[1] >= 90)].name")
        List<String> mixedPojoMapList(Classroom root);

        @FindByPath("$.students[?(@.grades[99])].name")
        List<String> outOfBounds(Classroom root);

        @FindByPath("$.students[?(@.grades[-99])].name")
        List<String> negativeOutOfBounds(Classroom root);

        @FindByPath("$.items[?(@.matrix[0][1] == $.target)].name")
        List<Object> nestedIndexes(Map<String, Object> root);

        @FindByPath("$.items[?(@.matrix[-1][-1] == 95)].name")
        List<Object> nestedNegativeIndexes(Map<String, Object> root);

        @FindByPath("$.items[?(@.matrix[0][0])].name")
        List<Object> nullElementPresent(Map<String, Object> root);

        @FindByPath("$.items[?(@.matrix[0][1] == null)].name")
        List<Object> explicitNullInArray(Map<String, Object> root);
    }

    @Test
    void typedPojoPrefixAndIndexedSuffixMatchRuntime() {
        Classroom root = classroom();
        Queries compiled = CompiledInstances.create(Queries.class);

        assertParity(root, "$.students[?(@.grades[0].score >= 90)].name",
                compiled.firstGrade(root), List.of("Alice"));
        assertParity(root, "$.students[?(@.grades[-1].score >= 90)].name",
                compiled.lastGrade(root), List.of("Alice", "Bob"));
        assertParity(root, "$.students[?(@.grades[0])].name",
                compiled.firstPresent(root), List.of("Alice", "Bob", "Eve", "Fay"));
        assertParity(root, "$.students[?(!@.grades[0])].name",
                compiled.firstMissing(root), List.of("Cara", "Dylan"));
        assertParity(root, "$.students[?(@.grades[0].score == null)].name",
                compiled.explicitNull(root), List.of("Fay"));
        assertParity(root, "$.students[?(@.grades[0].score >= $.minimums[-1])].name",
                compiled.rootedThreshold(root), List.of("Alice"));
        assertParity(root, "$.students[?(@.extra.scores[1] >= 90)].name",
                compiled.mixedPojoMapList(root), List.of("Alice", "Fay"));
        assertParity(root, "$.students[?(@.grades[99])].name",
                compiled.outOfBounds(root), List.of());
        assertParity(root, "$.students[?(@.grades[-99])].name",
                compiled.negativeOutOfBounds(root), List.of());
    }

    @Test
    void nestedIndexesAndNonArrayShapesMatchRuntime() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", "Alice");
        a.put("matrix", Arrays.asList(Arrays.asList(null, 95), List.of(70, 99)));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "Bob");
        b.put("matrix", Arrays.asList(List.of(10, 80), List.of(85, 95)));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", "Cara");
        c.put("matrix", Map.of("0", List.of(5, 95))); // index is not an object key
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", "Dylan"); // missing matrix
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("name", "Eve");
        e.put("matrix", List.of());
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", "Fay");
        f.put("matrix", Arrays.asList(Arrays.asList(50, null)));

        Map<String, Object> root = Map.of("items", List.of(a, b, c, d, e, f),
                "target", 95);
        Queries compiled = CompiledInstances.create(Queries.class);
        assertParity(root, "$.items[?(@.matrix[0][1] == $.target)].name",
                compiled.nestedIndexes(root), List.of("Alice"));
        assertParity(root, "$.items[?(@.matrix[-1][-1] == 95)].name",
                compiled.nestedNegativeIndexes(root), List.of("Bob"));
        assertParity(root, "$.items[?(@.matrix[0][0])].name",
                compiled.nullElementPresent(root), List.of("Alice", "Bob", "Fay"));
        assertParity(root, "$.items[?(@.matrix[0][1] == null)].name",
                compiled.explicitNullInArray(root), List.of("Fay"));
    }

    private static Classroom classroom() {
        return new Classroom(Arrays.asList(
                new Student("Alice", Arrays.asList(new Grade(92), null, new Grade(91)),
                        Map.of("scores", List.of(80, 95))),
                new Student("Bob", List.of(new Grade(75), new Grade(95)),
                        Map.of("scores", List.of(90))),
                new Student("Cara", null, Map.of()),
                new Student("Dylan", List.of(), null),
                new Student("Eve", Arrays.asList((Grade) null), Map.of("scores", List.of())),
                new Student("Fay", List.of(new Grade(null)),
                        Map.of("scores", Arrays.asList(null, 90)))
        ), List.of(85, 90));
    }

    private static void assertParity(Object root, String path, List<?> compiled,
                                     List<?> expected) {
        assertEquals(expected, compiled, path);
        assertEquals(JsonPath.parse(path).find(root, Object.class), compiled, path);
    }
}
