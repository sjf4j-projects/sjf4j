package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
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
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Writes nested Fabien Users or Clients to a per-thread sink, reset for every
 * invocation. Timing includes streaming but not byte materialization.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class FabienWriteBenchmark {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{FabienWriteBenchmark.class.getName()});
    }

    private static final ObjectMapper NATIVE = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ObjectMapper BLACKBIRD =
            new ObjectMapper().registerModule(new JavaTimeModule())
                    .registerModule(new BlackbirdModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final FabienCompiled COMPILED = CompiledInstances.create(FabienCompiled.class);

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private Users users;
    private Clients clients;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        users = Users.sample();

        users_jackson2_native();
        JsonNode expectedUsers = NATIVE.readTree(sink.toByteArray());
        if (expectedUsers.path("users").size() != 2
                || expectedUsers.path("users").get(0).path("friends").size() != 2
                || !expectedUsers.path("users").get(0).path("favoriteFruit").isNull()) {
            throw new IllegalStateException("Fabien Users fixture JSON is incomplete");
        }
        check("Users blackbird", expectedUsers, users_jackson2_blackbird());
        check("Users runtime", expectedUsers, users_jackson2_runtime());
        check("Users compiled", expectedUsers, users_jackson2_compiled());

        clients = Clients.sample();

        clients_jackson2_native();
        JsonNode expectedClients = NATIVE.readTree(sink.toByteArray());
        if (expectedClients.path("clients").size() != 2
                || expectedClients.path("clients").get(0).path("partners").size() != 2
                || !expectedClients.path("clients").get(0).path("registered").isTextual()) {
            throw new IllegalStateException("Fabien Clients fixture JSON is incomplete");
        }
        check("Clients blackbird", expectedClients, clients_jackson2_blackbird());
        check("Clients runtime", expectedClients, clients_jackson2_runtime());
        check("Clients compiled", expectedClients, clients_jackson2_compiled());
    }

    private void check(String label, JsonNode expected, int size) throws IOException {
        if (size == 0 || !expected.equals(NATIVE.readTree(sink.toByteArray()))) {
            throw new IllegalStateException("Fabien JSON differs: " + label);
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

    @Benchmark
    public int clients_jackson2_native() throws IOException {
        sink.reset();
        NATIVE.writeValue(sink, clients);
        return sink.size();
    }

    @Benchmark
    public int clients_jackson2_blackbird() throws IOException {
        sink.reset();
        BLACKBIRD.writeValue(sink, clients);
        return sink.size();
    }

    @Benchmark
    public int clients_jackson2_runtime() {
        sink.reset();
        RUNTIME.writeNode(sink, clients);
        return sink.size();
    }

    @Benchmark
    public int clients_jackson2_compiled() throws IOException {
        sink.reset();
        COMPILED.write(clients, sink);
        return sink.size();
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface FabienCompiled {
        @WriteTo void write(Users value, OutputStream output) throws IOException;
        @WriteTo void write(Clients value, OutputStream output) throws IOException;
    }
}
