package org.sjf4j.testbench.binding.compiled;

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
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Compares Simple's generated switch(String) reader with the runtime reader. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class SimplePojoReadOrderBenchmark {

    private static final String DECLARATION_ORDER = """
            {"id":839201,"createdAt":1700000000123,"updatedAt":1701234567890,
            "reputation":9876543210,"loginCount":421,"age":34,
            "active":true,"verified":true,"admin":false,"suspended":false,
            "score":98.75,"latitude":37.7749,"longitude":-122.4194,
            "username":"alice.builder","email":null,"displayName":"Alice Builder",
            "passwordHash":"$2a$12$abcdef","bio":"Builds fast JSON",
            "website":"https://example.test/a?b=1","department":"Platform Engineering",
            "address":{"street":"1 Main St","city":"San Francisco","state":"CA","zip":"94105","country":"US"},
            "tags":["tag-0-value",null],
            "friends":[{"id":1000,"since":1600000000000,"name":"Bill","close":true},null,
            {"id":1001,"since":1600000000001,"name":"Cindy","close":false}]}
            """;

    private static final String REVERSE_ORDER = """
            {"friends":[{"id":1000,"since":1600000000000,"name":"Bill","close":true},null,
            {"id":1001,"since":1600000000001,"name":"Cindy","close":false}],
            "tags":["tag-0-value",null],
            "address":{"street":"1 Main St","city":"San Francisco","state":"CA","zip":"94105","country":"US"},
            "department":"Platform Engineering","website":"https://example.test/a?b=1",
            "bio":"Builds fast JSON","passwordHash":"$2a$12$abcdef",
            "displayName":"Alice Builder","email":null,"username":"alice.builder",
            "longitude":-122.4194,"latitude":37.7749,"score":98.75,
            "suspended":false,"admin":false,"verified":true,"active":true,
            "age":34,"loginCount":421,"reputation":9876543210,
            "updatedAt":1701234567890,"createdAt":1700000000123,"id":839201}
            """;

    private static final String SHUFFLED_ORDER = """
            {"username":"alice.builder","active":true,
            "address":{"street":"1 Main St","city":"San Francisco","state":"CA","zip":"94105","country":"US"},
            "id":839201,"score":98.75,
            "friends":[{"id":1000,"since":1600000000000,"name":"Bill","close":true},null,
            {"id":1001,"since":1600000000001,"name":"Cindy","close":false}],
            "email":null,"reputation":9876543210,"suspended":false,
            "createdAt":1700000000123,"department":"Platform Engineering",
            "latitude":37.7749,"loginCount":421,"passwordHash":"$2a$12$abcdef",
            "verified":true,"updatedAt":1701234567890,"tags":["tag-0-value",null],
            "age":34,"website":"https://example.test/a?b=1","admin":false,
            "longitude":-122.4194,"displayName":"Alice Builder","bio":"Builds fast JSON"}
            """;

    private static final String UNKNOWN_FIELD = """
            {"id":839201,"createdAt":1700000000123,"updatedAt":1701234567890,
            "reputation":9876543210,"loginCount":421,"age":34,
            "active":true,"verified":true,"admin":false,"suspended":false,
            "score":98.75,"latitude":37.7749,"longitude":-122.4194,
            "username":"alice.builder","email":null,"displayName":"Alice Builder",
            "passwordHash":"$2a$12$abcdef","bio":"Builds fast JSON",
            "website":"https://example.test/a?b=1","department":"Platform Engineering",
            "address":{"street":"1 Main St","city":"San Francisco","state":"CA","zip":"94105","country":"US"},
            "tags":["tag-0-value",null],
            "friends":[{"id":1000,"since":1600000000000,"name":"Bill","close":true},null,
            {"id":1001,"since":1600000000001,"name":"Cindy","close":false}],"ignored":123}
            """;

    private static final SimpleJsonBinder RUNTIME = new SimpleJsonBinder();
    private static final SimpleCompiledBinder COMPILED =
            CompiledInstances.of(SimpleCompiledBinder.class);

    @Setup(Level.Trial)
    public void validate() throws IOException {
        User expected = (User) RUNTIME.readNode(DECLARATION_ORDER, User.class);
        validate("declaration", expected, DECLARATION_ORDER);
        validate("reverse", expected, REVERSE_ORDER);
        validate("shuffled", expected, SHUFFLED_ORDER);
        validate("unknown", expected, UNKNOWN_FIELD);
    }

    private static void validate(String name, User expected, String json) throws IOException {
        User runtime = (User) RUNTIME.readNode(json, User.class);
        User compiled = COMPILED.read(json);
        if (!expected.equals(runtime) || !expected.equals(compiled)) {
            throw new IllegalStateException(name + " fixture produced different User values");
        }
    }

    @Benchmark
    public User declaration_runtime() throws IOException {
        return (User) RUNTIME.readNode(DECLARATION_ORDER, User.class);
    }

    @Benchmark
    public User declaration_compiled() throws IOException {
        return COMPILED.read(DECLARATION_ORDER);
    }

    @Benchmark
    public User reverse_runtime() throws IOException {
        return (User) RUNTIME.readNode(REVERSE_ORDER, User.class);
    }

    @Benchmark
    public User reverse_compiled() throws IOException {
        return COMPILED.read(REVERSE_ORDER);
    }

    @Benchmark
    public User shuffled_runtime() throws IOException {
        return (User) RUNTIME.readNode(SHUFFLED_ORDER, User.class);
    }

    @Benchmark
    public User shuffled_compiled() throws IOException {
        return COMPILED.read(SHUFFLED_ORDER);
    }

    @Benchmark
    public User unknown_runtime() throws IOException {
        return (User) RUNTIME.readNode(UNKNOWN_FIELD, User.class);
    }

    @Benchmark
    public User unknown_compiled() throws IOException {
        return COMPILED.read(UNKNOWN_FIELD);
    }

    @CompiledBinder(backend = BindingBackend.SIMPLE)
    public interface SimpleCompiledBinder {

        @ReadFrom
        User read(String json) throws IOException;
    }
}
