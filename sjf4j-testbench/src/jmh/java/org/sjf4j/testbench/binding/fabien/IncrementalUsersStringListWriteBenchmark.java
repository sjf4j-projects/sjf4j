package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolates only List<String> serialization from the Users.tags fixture.
 * Both bare root list and a single-property POJO are measured, separately.
 * The same prebuilt object is passed to Native, Blackbird and Compiled.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class IncrementalUsersStringListWriteBenchmark {

    @Param({"root", "property"})
    public String shape;

    @Param({"1", "10", "100", "1000"})
    public int count;

    @Param({"native", "blackbird", "compiled"})
    public String implementation;

    @Param({"12345", "324"})
    public long seed;

    private static final JsonMapper NATIVE = new JsonMapper();
    private static final JsonMapper BLACKBIRD =
            JsonMapper.builder().addModule(new BlackbirdModule()).build();
    private static final ObjectMapper VERIFY = new ObjectMapper();

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Compiled {
        @Write byte[] root(List<String> values);
        @Write byte[] property(TagsOnly values);
    }

    public static final class TagsOnly {
        private final List<String> tags;
        public TagsOnly(List<String> tags) { this.tags = tags; }
        public List<String> getTags() { return tags; }
    }

    @FunctionalInterface
    private interface Writer {
        byte[] write(Object value) throws Exception;
    }

    private Object value;
    private Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Users source = FabienDataGenerator.users(10000, seed);
        List<String> pool = new ArrayList<>();
        for (Users.User user : source.getUsers()) {
            if (user.getTags() != null) pool.addAll(user.getTags());
        }
        if (pool.isEmpty()) throw new IllegalStateException("Users tags fixture is empty");
        List<String> tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) tags.add(pool.get(i % pool.size()));
        if ("root".equals(shape)) value = tags;
        else if ("property".equals(shape)) value = new TagsOnly(tags);
        else throw new IllegalArgumentException("shape: " + shape);

        if ("native".equals(implementation)) writer = NATIVE::writeValueAsBytes;
        else if ("blackbird".equals(implementation)) writer = BLACKBIRD::writeValueAsBytes;
        else if ("compiled".equals(implementation)) {
            Compiled binder = CompiledInstances.create(Compiled.class);
            if ("root".equals(shape)) writer = node -> binder.root((List<String>) node);
            else writer = node -> binder.property((TagsOnly) node);
        } else throw new IllegalArgumentException("implementation: " + implementation);

        JsonNode expected = VERIFY.readTree(NATIVE.writeValueAsBytes(value));
        JsonNode actual = VERIFY.readTree(writer.write(value));
        if (!expected.equals(actual)) {
            throw new IllegalStateException("List<String> JSON mismatch shape=" + shape
                    + " count=" + count + " seed=" + seed + " implementation=" + implementation);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(value);
    }
}
