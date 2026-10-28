package org.sjf4j.schema;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.Nodes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end schema validation for the Evaluator paths that walk objects and arrays.
 *
 * <p>Run the same class and parameters on commits before/after a traversal refactor.
 * Schemas, plans and nodes are created outside the timed methods. "first"/"last"
 * refer to the failing element (or matching element for contains); "none" is a
 * successful input for properties/items/unevaluated, but a missing match for contains.
 *
 * <p>Do not compare this class's scores directly with raw NodeWalker benchmarks:
 * these methods also measure child-schema evaluation, error handling and tracking.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class EvaluatorTraversalBenchmark {

    private static final String NON_NEGATIVE = "{\"type\":\"integer\",\"minimum\":0}";

    // Exercises explicit properties, regex matching and additionalProperties in one pass.
    private static final String PROPERTIES_SCHEMA =
            "{\"type\":\"object\","
                    + "\"properties\":{\"p0\":" + NON_NEGATIVE + ",\"p1\":" + NON_NEGATIVE + "},"
                    + "\"patternProperties\":{\"^metric\":" + NON_NEGATIVE + "},"
                    + "\"additionalProperties\":" + NON_NEGATIVE + "}";

    // Some members are evaluated by properties/patternProperties; others are left
    // for unevaluatedProperties (a separate second traversal).
    private static final String UNEVALUATED_PROPERTIES_SCHEMA =
            "{\"type\":\"object\","
                    + "\"properties\":{\"p0\":" + NON_NEGATIVE + ",\"p1\":" + NON_NEGATIVE + "},"
                    + "\"patternProperties\":{\"^metric\":" + NON_NEGATIVE + "},"
                    + "\"unevaluatedProperties\":" + NON_NEGATIVE + "}";

    private static final String ITEMS_SCHEMA =
            "{\"type\":\"array\","
                    + "\"prefixItems\":[" + NON_NEGATIVE + "," + NON_NEGATIVE + "],"
                    + "\"items\":" + NON_NEGATIVE + "}";

    private static final String UNEVALUATED_ITEMS_SCHEMA =
            "{\"type\":\"array\","
                    + "\"prefixItems\":[" + NON_NEGATIVE + "," + NON_NEGATIVE + "],"
                    + "\"unevaluatedItems\":" + NON_NEGATIVE + "}";

    private static final String CONTAINS_SCHEMA =
            "{\"type\":\"array\",\"contains\":{\"const\":42}}";

    // unevaluatedItems forces contains to scan all items to preserve evaluated indices.
    private static final String CONTAINS_TRACKED_SCHEMA =
            "{\"type\":\"array\",\"contains\":{\"const\":42},"
                    + "\"unevaluatedItems\":" + NON_NEGATIVE + "}";

    private static SchemaPlan plan(String schemaJson) {
        return JsonSchema.fromJson(schemaJson).createPlan();
    }

    private static void check(String label, SchemaPlan plan, Object node,
                              boolean failFast, boolean expected) {
        boolean actual = plan.validate(node, failFast, false).isValid();
        if (actual != expected) {
            throw new IllegalStateException(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /** Ordinary POJO, to exercise the POJO property-access branch of NodeWalker. */
    public static class Bean {
        public int p0 = 1;
        public int p1 = 1;
        public int p2 = 1;
        public int p3 = 1;
        public int metric0 = 1;
        public int metric1 = 1;
        public int metric2 = 1;
        public int metric3 = 1;
        public int extra0 = 1;
        public int extra1 = 1;
        public int extra2 = 1;
        public int extra3 = 1;
    }

    @State(Scope.Thread)
    public static class ObjectState {
        @Param({"map", "pojo"})
        public String representation;

        @Param({"none", "first", "last"})
        public String scenario;

        @Param({"true", "false"})
        public boolean failFast;

        public SchemaPlan propertiesPlan;
        public SchemaPlan unevaluatedPlan;
        public Object propertiesNode;
        public Object unevaluatedNode;

        @Setup(Level.Trial)
        public void setup() throws ReflectiveOperationException {
            propertiesPlan = plan(PROPERTIES_SCHEMA);
            unevaluatedPlan = plan(UNEVALUATED_PROPERTIES_SCHEMA);
            propertiesNode = newNode(false);
            unevaluatedNode = newNode(true);
            boolean expected = "none".equals(scenario);
            check("properties", propertiesPlan, propertiesNode, failFast, expected);
            check("unevaluatedProperties", unevaluatedPlan, unevaluatedNode, failFast, expected);
        }

        private Object newNode(boolean onlyUnevaluated) throws ReflectiveOperationException {
            Bean bean = new Bean();
            Object node;
            if ("map".equals(representation)) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(bean)) {
                    map.put(entry.getKey(), entry.getValue());
                }
                node = map;
            } else if ("pojo".equals(representation)) {
                node = bean;
            } else {
                throw new IllegalArgumentException(representation);
            }

            if (!"none".equals(scenario)) {
                List<String> candidates = new ArrayList<>();
                for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(node)) {
                    if (!onlyUnevaluated || !entry.getKey().startsWith("p")
                            && !entry.getKey().startsWith("metric")) {
                        candidates.add(entry.getKey());
                    }
                }
                if (candidates.isEmpty()) throw new IllegalStateException("No candidate fields");
                String key = "first".equals(scenario)
                        ? candidates.get(0) : candidates.get(candidates.size() - 1);
                if (node instanceof Map) {
                    ((Map<String, Object>) node).put(key, -1);
                } else {
                    Field field = Bean.class.getField(key);
                    field.setInt(bean, -1);
                }
            }
            return node;
        }
    }

    @State(Scope.Thread)
    public static class ArrayState {
        @Param({"list", "array"})
        public String representation;

        @Param({"8", "32"})
        public int length;

        @Param({"none", "first", "last"})
        public String scenario;

        @Param({"true", "false"})
        public boolean failFast;

        public SchemaPlan itemsPlan;
        public SchemaPlan unevaluatedPlan;
        public SchemaPlan containsPlan;
        public SchemaPlan containsTrackedPlan;

        public Object itemsNode;
        public Object unevaluatedNode;
        public Object containsNode;

        @Setup(Level.Trial)
        public void setup() {
            itemsPlan = plan(ITEMS_SCHEMA);
            unevaluatedPlan = plan(UNEVALUATED_ITEMS_SCHEMA);
            containsPlan = plan(CONTAINS_SCHEMA);
            containsTrackedPlan = plan(CONTAINS_TRACKED_SCHEMA);

            Object[] items = filled(1);
            if ("first".equals(scenario)) items[0] = -1;
            if ("last".equals(scenario)) items[length - 1] = -1;
            itemsNode = represent(items);

            Object[] unevaluated = filled(1);
            if ("first".equals(scenario)) unevaluated[2] = -1;
            if ("last".equals(scenario)) unevaluated[length - 1] = -1;
            unevaluatedNode = represent(unevaluated);

            Object[] contains = filled(1);
            if ("first".equals(scenario)) contains[0] = 42;
            if ("last".equals(scenario)) contains[length - 1] = 42;
            containsNode = represent(contains);

            boolean expectedValid = "none".equals(scenario);
            boolean expectedContains = !expectedValid;
            check("items", itemsPlan, itemsNode, failFast, expectedValid);
            check("unevaluatedItems", unevaluatedPlan, unevaluatedNode, failFast, expectedValid);
            check("contains", containsPlan, containsNode, failFast, expectedContains);
            check("contains+unevaluatedItems", containsTrackedPlan, containsNode,
                    failFast, expectedContains);
        }

        private Object[] filled(int value) {
            Object[] values = new Object[length];
            Arrays.fill(values, value);
            return values;
        }

        private Object represent(Object[] values) {
            if ("array".equals(representation)) return values;
            if ("list".equals(representation)) return new ArrayList<>(Arrays.asList(values));
            throw new IllegalArgumentException(representation);
        }
    }

    @Benchmark
    public ValidationResult properties(ObjectState state) {
        return state.propertiesPlan.validate(state.propertiesNode, state.failFast, false);
    }

    @Benchmark
    public ValidationResult unevaluated_properties(ObjectState state) {
        return state.unevaluatedPlan.validate(state.unevaluatedNode, state.failFast, false);
    }

    @Benchmark
    public ValidationResult items(ArrayState state) {
        return state.itemsPlan.validate(state.itemsNode, state.failFast, false);
    }

    @Benchmark
    public ValidationResult unevaluated_items(ArrayState state) {
        return state.unevaluatedPlan.validate(state.unevaluatedNode, state.failFast, false);
    }

    @Benchmark
    public ValidationResult contains(ArrayState state) {
        return state.containsPlan.validate(state.containsNode, state.failFast, false);
    }

    @Benchmark
    public ValidationResult contains_with_unevaluated_items(ArrayState state) {
        return state.containsTrackedPlan.validate(state.containsNode, state.failFast, false);
    }
}
