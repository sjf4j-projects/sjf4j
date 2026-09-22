package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
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
public class Fastjson2JsonBinderBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{Fastjson2JsonBinderBenchmark.class.getName()});
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        JSONReader.Context readerContext;
        JSONWriter.Context writerContext;
        Fastjson2Binder binder;
        String document;
        Document value;

        @Setup(Level.Trial)
        public void setup() {
            readerContext = JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
            writerContext = JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);
            binder = new Fastjson2Binder(readerContext, writerContext);
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
            document = JSON.toJSONString(value, writerContext);
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
    public Document readNative(BenchmarkState state) {
        try (JSONReader reader = JSONReader.of(state.document, state.readerContext)) {
            return reader.read(Document.class);
        }
    }

    @Benchmark
    public Document readBinder(BenchmarkState state) {
        return (Document) state.binder.readNode(state.document, Document.class);
    }

    @Benchmark
    public String writeNative(BenchmarkState state) {
        return JSON.toJSONString(state.value, state.writerContext);
    }

    @Benchmark
    public String writeBinder(BenchmarkState state) {
        return state.binder.writeNodeAsString(state.value);
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
