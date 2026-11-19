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

/** Runtime/compiled parity for statically typed filter member paths. */
class CompiledFilterPojoAccessTest {
    record Scores(Integer math) {}

    static final class Student {
        public String alias;
        private final String name;
        private final Scores scores;
        private final Map<String, Object> extra;
        private int scoreReads;

        Student(String name, Scores scores, Map<String, Object> extra, String alias) {
            this.name = name;
            this.scores = scores;
            this.extra = extra;
            this.alias = alias;
        }

        public String getName() {
            return name;
        }

        public Scores getScores() {
            scoreReads++;
            return scores;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }
    }

    record Classroom(List<Student> students, int minimum) {}

    static class BaseScore {
        public int rank = 50;
    }

    static final class HiddenScore extends BaseScore {
        public int rank = 99;
    }

    record MixedScores(List<BaseScore> items) {}

    @CompiledNavigator
    interface Queries {
        @FindByPath("$.students[?(@.scores.math >= $.minimum)].name")
        List<String> passing(Classroom root);

        @FindByPath("$.students[?(@.scores.math == null)].name")
        List<String> explicitNull(Classroom root);

        @FindByPath("$.students[?(!@.scores.math)].name")
        List<String> absentMath(Classroom root);

        @FindByPath("$.students[?(@.scores.math >= 90 && @.name != 'Bob')].name")
        List<String> composite(Classroom root);

        @FindByPath("$.students[?(@.extra.math >= 90)].name")
        List<String> mixedPojoAndMap(Classroom root);

        @FindByPath("$.students[?(@.alias == 'VIP')].name")
        List<String> publicField(Classroom root);

        @FindByPath("$.items[?(@.rank >= 90)]")
        List<Object> inheritedField(MixedScores root);
    }

    @Test
    void directGetterAndRecordPathsMatchRuntime() {
        Classroom root = fixture();
        Queries compiled = CompiledInstances.create(Queries.class);

        assertEquals(List.of("Alice"), compiled.passing(root));
        assertEquals(List.of("Charlie"), compiled.explicitNull(root));
        assertEquals(List.of("Bob"), compiled.absentMath(root));
        assertEquals(List.of("Alice"), compiled.composite(root));
        assertEquals(List.of("Alice", "Bob"), compiled.mixedPojoAndMap(root));
        assertEquals(List.of("Alice"), compiled.publicField(root));

        assertEquals(JsonPath.parse("$.students[?(@.scores.math >= $.minimum)].name")
                .find(root, Object.class), compiled.passing(root));
        assertEquals(JsonPath.parse("$.students[?(@.scores.math == null)].name")
                .find(root, Object.class), compiled.explicitNull(root));
        assertEquals(JsonPath.parse("$.students[?(!@.scores.math)].name")
                .find(root, Object.class), compiled.absentMath(root));
        assertEquals(JsonPath.parse("$.students[?(@.scores.math >= 90 && @.name != 'Bob')].name")
                .find(root, Object.class), compiled.composite(root));
        assertEquals(JsonPath.parse("$.students[?(@.extra.math >= 90)].name")
                .find(root, Object.class), compiled.mixedPojoAndMap(root));
        assertEquals(JsonPath.parse("$.students[?(@.alias == 'VIP')].name")
                .find(root, Object.class), compiled.publicField(root));
    }

    @Test
    void shadowedFieldsOnSubclassesUseRuntimePropertySemantics() {
        MixedScores root = new MixedScores(Arrays.asList(new BaseScore(), new HiddenScore()));
        Queries compiled = CompiledInstances.create(Queries.class);
        String path = "$.items[?(@.rank >= 90)]";
        assertEquals(JsonPath.parse(path).find(root, Object.class),
                compiled.inheritedField(root));
    }

    @Test
    void directGettersAreReadOncePerPredicateEvaluation() {
        Classroom root = fixture();
        Queries compiled = CompiledInstances.create(Queries.class);
        assertEquals(List.of("Alice"), compiled.passing(root));
        for (Student student : root.students()) {
            assertEquals(1, student.scoreReads);
        }
    }

    private static Classroom fixture() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("math", 92);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("math", 99);
        return new Classroom(Arrays.asList(
                new Student("Alice", new Scores(95), first, "VIP"),
                new Student("Bob", null, second, null),
                new Student("Charlie", new Scores(null), null, null),
                new Student("Daisy", new Scores(80), null, null)
        ), 90);
    }
}
