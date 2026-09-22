package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class GsonBinderBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{GsonBinderBenchmark.class.getName()});
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        Gson gson;
        GsonBinder binder;
        String document;
        Document value;
        Map<String, Object> mapValue;

        @Setup(Level.Trial)
        public void setup() {
            gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
            binder = new GsonBinder(gson);
            value = new Document(
                    7,
                    20260319L,
                    "<Ada & Bob>",
                    Status.ACTIVE,
                    new Details(true, "nested", Arrays.asList("primary", "visible")),
                    Arrays.asList("one", "two", "three"),
                    new int[]{3, 8, 13, 21, 34},
                    Arrays.asList(
                            new Section("overview", Arrays.asList(
                                    new Entry(1, 12.5d, true),
                                    new Entry(2, 8.75d, false)), attributes("source", "api", "region", "us-east")),
                            new Section("history", Arrays.asList(
                                    new Entry(3, 99.99d, true),
                                    new Entry(4, 0.25d, true),
                                    new Entry(5, 42.0d, false)), attributes("source", "import", "region", "eu-west"))),
                    counters(12, 3, 7),
                    null);
            document = gson.toJson(value);

            mapValue = new LinkedHashMap<>();
            mapValue.put("id", 7);
            mapValue.put("version", 20260319L);
            mapValue.put("title", "<Ada & Bob>");
            mapValue.put("status", "ACTIVE");

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("active", true);
            details.put("note", "nested");
            details.put("labels", Arrays.asList("primary", "visible"));
            mapValue.put("details", details);
            mapValue.put("tags", Arrays.asList("one", "two", "three"));
            mapValue.put("scores", Arrays.asList(3, 8, 13, 21, 34));

            Map<String, Object> overviewEntry1 = new LinkedHashMap<>();
            overviewEntry1.put("id", 1);
            overviewEntry1.put("amount", 12.5d);
            overviewEntry1.put("enabled", true);
            Map<String, Object> overviewEntry2 = new LinkedHashMap<>();
            overviewEntry2.put("id", 2);
            overviewEntry2.put("amount", 8.75d);
            overviewEntry2.put("enabled", false);
            Map<String, Object> overview = new LinkedHashMap<>();
            overview.put("name", "overview");
            overview.put("entries", Arrays.asList(overviewEntry1, overviewEntry2));
            overview.put("attributes", attributes("source", "api", "region", "us-east"));

            Map<String, Object> historyEntry1 = new LinkedHashMap<>();
            historyEntry1.put("id", 3);
            historyEntry1.put("amount", 99.99d);
            historyEntry1.put("enabled", true);
            Map<String, Object> historyEntry2 = new LinkedHashMap<>();
            historyEntry2.put("id", 4);
            historyEntry2.put("amount", 0.25d);
            historyEntry2.put("enabled", true);
            Map<String, Object> historyEntry3 = new LinkedHashMap<>();
            historyEntry3.put("id", 5);
            historyEntry3.put("amount", 42.0d);
            historyEntry3.put("enabled", false);
            Map<String, Object> history = new LinkedHashMap<>();
            history.put("name", "history");
            history.put("entries", Arrays.asList(historyEntry1, historyEntry2, historyEntry3));
            history.put("attributes", attributes("source", "import", "region", "eu-west"));

            mapValue.put("sections", Arrays.asList(overview, history));
            mapValue.put("counters", counters(12, 3, 7));
            mapValue.put("nullable", null);
        }

        private static Map<String, String> attributes(String key1, String value1, String key2, String value2) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(key1, value1);
            values.put(key2, value2);
            return values;
        }

        private static Map<String, Integer> counters(int created, int updated, int deleted) {
            Map<String, Integer> values = new LinkedHashMap<>();
            values.put("created", created);
            values.put("updated", updated);
            values.put("deleted", deleted);
            return values;
        }
    }

    @Benchmark
    public Document pojo_read_native(BenchmarkState state) {
        return state.gson.fromJson(state.document, Document.class);
    }

    @Benchmark
    public Document pojo_read_binder(BenchmarkState state) {
        return (Document) state.binder.readNode(state.document, Document.class);
    }

    @Benchmark
    public String pojo_write_native(BenchmarkState state) {
        return state.gson.toJson(state.value);
    }

    @Benchmark
    public String pojo_write_binder(BenchmarkState state) {
        return state.binder.writeNodeAsString(state.value);
    }

    @Benchmark
    public Map map_read_native(BenchmarkState state) {
        return state.gson.fromJson(state.document, Map.class);
    }

    @Benchmark
    public Map map_read_binder(BenchmarkState state) {
        return (Map) state.binder.readNode(state.document, Map.class);
    }

    @Benchmark
    public String map_write_native(BenchmarkState state) {
        return state.gson.toJson(state.mapValue);
    }

    @Benchmark
    public String map_write_binder(BenchmarkState state) {
        return state.binder.writeNodeAsString(state.mapValue);
    }

    public static class Document {
        public int id;
        public long version;
        public String title;
        public Status status;
        public Details details;
        public List<String> tags;
        public int[] scores;
        public List<Section> sections;
        public Map<String, Integer> counters;
        public String nullable;

        public Document() {
        }

        Document(int id, long version, String title, Status status, Details details, List<String> tags,
                 int[] scores, List<Section> sections, Map<String, Integer> counters, String nullable) {
            this.id = id;
            this.version = version;
            this.title = title;
            this.status = status;
            this.details = details;
            this.tags = tags;
            this.scores = scores;
            this.sections = sections;
            this.counters = counters;
            this.nullable = nullable;
        }
    }

    public static class Details {
        public boolean active;
        public String note;
        public List<String> labels;

        public Details() {
        }

        Details(boolean active, String note, List<String> labels) {
            this.active = active;
            this.note = note;
            this.labels = labels;
        }
    }

    public static class Section {
        public String name;
        public List<Entry> entries;
        public Map<String, String> attributes;

        public Section() {
        }

        Section(String name, List<Entry> entries, Map<String, String> attributes) {
            this.name = name;
            this.entries = entries;
            this.attributes = attributes;
        }
    }

    public static class Entry {
        public int id;
        public double amount;
        public boolean enabled;

        public Entry() {
        }

        Entry(int id, double amount, boolean enabled) {
            this.id = id;
            this.amount = amount;
            this.enabled = enabled;
        }
    }

    public enum Status {
        ACTIVE,
        ARCHIVED
    }
}
