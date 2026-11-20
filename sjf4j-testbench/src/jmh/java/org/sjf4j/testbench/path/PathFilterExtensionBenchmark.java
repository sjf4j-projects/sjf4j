package org.sjf4j.testbench.path;

import org.openjdk.jmh.Main;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.path.JsonPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Prebuilt POJO Filter benchmark: runtime AST vs generated regex, in, functions and indexes. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
@Threads(1)
public class PathFilterExtensionBenchmark {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{PathFilterExtensionBenchmark.class.getName()});
    }

    record Item(String name, int score, List<String> tags) {}
    record Root(List<Item> items) {}

    @CompiledNavigator
    public interface Paths {
        @FindByPath("$.items[?(@.name =~ /^match/)].name")
        List<String> regex(Root root);

        @FindByPath("$.items[?(@.score in [10,20,30])].name")
        List<String> membership(Root root);

        @FindByPath("$.items[?(length(@.tags) >= 2)].name")
        List<String> length(Root root);

        @FindByPath("$.items[?(@.tags[0] == 'a')].name")
        List<String> firstTag(Root root);
    }

    @State(Scope.Thread)
    public static class Data {
        @Param({"16", "256"})
        public int count;

        Root root;
        Paths compiled;
        JsonPath regex;
        JsonPath membership;
        JsonPath length;
        JsonPath firstTag;

        @Setup(Level.Trial)
        public void setup() {
            List<Item> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                items.add(new Item(i % 4 == 0 ? "match" + i : "other" + i,
                        (i % 4) * 10, i % 3 == 0 ? List.of("a", "b") : List.of("a")));
            }
            root = new Root(items);
            compiled = CompiledInstances.create(Paths.class);
            regex = JsonPath.parse("$.items[?(@.name =~ /^match/)].name");
            membership = JsonPath.parse("$.items[?(@.score in [10,20,30])].name");
            length = JsonPath.parse("$.items[?(length(@.tags) >= 2)].name");
            firstTag = JsonPath.parse("$.items[?(@.tags[0] == 'a')].name");
            if (!compiled.regex(root).equals(regex.find(root)) ||
                    !compiled.membership(root).equals(membership.find(root)) ||
                    !compiled.length(root).equals(length.find(root)) ||
                    !compiled.firstTag(root).equals(firstTag.find(root))) {
                throw new IllegalStateException("Compiled/runtime filter mismatch");
            }
        }
    }

    @Benchmark
    public Object regexRuntime(Data data) {
        return data.regex.find(data.root);
    }

    @Benchmark
    public Object regexCompiled(Data data) {
        return data.compiled.regex(data.root);
    }

    @Benchmark
    public Object membershipRuntime(Data data) {
        return data.membership.find(data.root);
    }

    @Benchmark
    public Object membershipCompiled(Data data) {
        return data.compiled.membership(data.root);
    }

    @Benchmark
    public Object lengthRuntime(Data data) {
        return data.length.find(data.root);
    }

    @Benchmark
    public Object lengthCompiled(Data data) {
        return data.compiled.length(data.root);
    }

    @Benchmark
    public Object firstTagRuntime(Data data) {
        return data.firstTag.find(data.root);
    }

    @Benchmark
    public Object firstTagCompiled(Data data) {
        return data.compiled.firstTag(data.root);
    }
}
