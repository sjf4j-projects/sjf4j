package org.sjf4j;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
@State(Scope.Thread)
public class FromNodeBenchmark {

    private static final JsonObject JSON_PERSON = new JsonObject(
            "name", "Alice",
            "age", 30,
            "address", new JsonObject("city", "SG", "street", "Main"),
            "tags", Arrays.asList("a", "b", "c")
    );

    private static final JsonObject JSON_CTOR_ONLY = new JsonObject(
            "id", "u-1",
            "level", 7,
            "note", "hello",
            "tags", Arrays.asList("x", "y", "z")
    );

    private Person person;

    @Setup
    public void setup() {
        person = Sjf4j.fromNode(JSON_PERSON, Person.class);
    }

    @Benchmark
    public Object fromNode_jsonobject_to_person() {
        return Sjf4j.fromNode(JSON_PERSON, Person.class);
    }

    @Benchmark
    public Object fromNode_person_to_person() {
        return Sjf4j.fromNode(person, Person.class);
    }

    @Benchmark
    public Object fromNode_jsonobject_to_ctor_only() {
        return Sjf4j.fromNode(JSON_CTOR_ONLY, CtorOnlyPojo.class);
    }

    public static class Address extends JsonObject {
        public String city;
        public String street;
    }

    public static class Person extends JsonObject {
        public String name;
        public int age;
        public Address address;
        public List<String> tags;
    }

    public static class CtorOnlyPojo extends JsonObject {
        public final String id;
        public final int level;
        public String note;
        public List<String> tags;

        @NodeCreator
        public CtorOnlyPojo(@NodeProperty("id") String id, @NodeProperty("level") int level) {
            this.id = id;
            this.level = level;
        }
    }
}
