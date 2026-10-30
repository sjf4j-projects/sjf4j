package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
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
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.fabien.Users;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Writes the same nested Fabien Users POJO to a per-thread sink, reset for
 * every invocation. Timing includes streaming but not byte materialization.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class FabienWriteBenchmark {

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final ObjectMapper BLACKBIRD =
            new ObjectMapper().registerModule(new BlackbirdModule());
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final FabienCompiled COMPILED = CompiledInstances.create(FabienCompiled.class);

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private Users users;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Users.User first = new Users.User();
        first.setId("user-001");
        first.setIndex(1);
        first.setGuid("b4c1-001");
        first.setIsActive(true);
        first.setBalance("$1,234.50");
        first.setPicture("https://example.test/avatar/1");
        first.setAge(29);
        first.setEyeColor("blue");
        first.setName("Zoë \"Z\" 李");
        first.setGender("female");
        first.setCompany("Acme & Co.");
        first.setEmail("zoe@example.test");
        first.setPhone("+1-555-0101");
        first.setAddress("42 Main St\nApt 2");
        first.setAbout("Hello\\world\tこんにちは");
        first.setRegistered("2024-02-03T10:20:30Z");
        first.setLatitude(37.7749);
        first.setLongitude(-122.4194);
        first.setTags(Arrays.asList("coffee", "雪", null));
        first.setFriends(Arrays.asList(
                Users.Friend.create("friend-1", "Renée"),
                Users.Friend.create("friend-2", "Sam \"S\"")));
        first.setGreeting("Hi, Zoë!\n");
        first.setFavoriteFruit(null);

        Users.User second = new Users.User();
        second.setId("user-002");
        second.setIndex(2);
        second.setIsActive(false);
        second.setName("Miyuki 美雪");
        second.setAge(34);
        second.setEmail(null);
        second.setTags(Arrays.asList("music", "hiking"));
        second.setFriends(Arrays.asList(Users.Friend.create("friend-3", "Zoë")));
        second.setGreeting("Welcome 🌿");

        users = new Users();
        users.setUsers(Arrays.asList(first, second));

        write_jackson2_native();
        JsonNode expected = NATIVE.readTree(sink.toByteArray());
        if (expected.path("users").size() != 2
                || expected.path("users").get(0).path("friends").size() != 2
                || !expected.path("users").get(0).path("favoriteFruit").isNull()) {
            throw new IllegalStateException("Fabien Users fixture JSON is incomplete");
        }
        check("blackbird", expected, write_jackson2_blackbird());
        check("runtime", expected, write_jackson2_runtime());
        check("compiled", expected, write_jackson2_compiled());
    }

    private void check(String label, JsonNode expected, int size) throws IOException {
        if (size == 0 || !expected.equals(NATIVE.readTree(sink.toByteArray()))) {
            throw new IllegalStateException("Fabien Users JSON differs: " + label);
        }
    }

    @Benchmark
    public int users_jackson2_native() throws IOException {
        sink.reset();
        NATIVE.writeValue(sink, users);
        return sink.size();
    }

    @Benchmark
    public int users_jackson2_blackbird() throws IOException {
        sink.reset();
        BLACKBIRD.writeValue(sink, users);
        return sink.size();
    }

    @Benchmark
    public int users_jackson2_runtime() {
        sink.reset();
        RUNTIME.writeNode(sink, users);
        return sink.size();
    }

    @Benchmark
    public int users_jackson2_compiled() throws IOException {
        sink.reset();
        COMPILED.write(users, sink);
        return sink.size();
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface FabienCompiled {
        @WriteTo void write(Users value, OutputStream output) throws IOException;
    }
}
