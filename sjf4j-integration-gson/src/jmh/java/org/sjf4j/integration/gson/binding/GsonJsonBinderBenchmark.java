package org.sjf4j.integration.gson.binding;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class GsonJsonBinderBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{GsonJsonBinderBenchmark.class.getName()});
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        Gson gson;
        GsonJsonBinder binder;
        String document;
        Document value;

        @Setup(Level.Trial)
        public void setup() {
            gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
            binder = new GsonJsonBinder(gson);
            value = new Document(7, "<Ada & Bob>", new Details(true, "nested"), Arrays.asList("one", "two"), null);
            document = gson.toJson(value);
        }
    }

    @Benchmark
    public Document readNative(BenchmarkState state) {
        return state.gson.fromJson(state.document, Document.class);
    }

    @Benchmark
    public Document readBinder(BenchmarkState state) {
        return (Document) state.binder.readNode(state.document, Document.class);
    }

    @Benchmark
    public String writeNative(BenchmarkState state) {
        return state.gson.toJson(state.value);
    }

    @Benchmark
    public String writeBinder(BenchmarkState state) {
        return state.binder.writeNodeAsString(state.value);
    }

    public static class Document {
        public int id;
        public String title;
        public Details details;
        public List<String> tags;
        public String nullable;

        public Document() {
        }

        Document(int id, String title, Details details, List<String> tags, String nullable) {
            this.id = id;
            this.title = title;
            this.details = details;
            this.tags = tags;
            this.nullable = nullable;
        }
    }

    public static class Details {
        public boolean active;
        public String note;

        public Details() {
        }

        Details(boolean active, String note) {
            this.active = active;
            this.note = note;
        }
    }
}
