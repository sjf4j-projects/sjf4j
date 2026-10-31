package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Parses Fabien Users and Clients JSON with the same byte[] input shape as
 * java-json-benchmark, using generated ~1 KB payloads by default.
 *
 * <p>Timing includes complete UTF-8 byte[] parsing and POJO creation, but not
 * fixture construction, serialization, or correctness checks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class FabienReadBenchmark {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{FabienReadBenchmark.class.getName()});
    }

    // SJF4J preserves the JSON offset; Jackson's default normalizes it to UTC.
    private static final ObjectMapper NATIVE = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ObjectMapper BLACKBIRD = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new BlackbirdModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // Pre-bound readers remove ObjectMapper's per-call type construction.
    private static final ObjectReader NATIVE_USERS_READER_STRICT =
            NATIVE.readerFor(Users.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectReader BLACKBIRD_USERS_READER =
            BLACKBIRD.readerFor(Users.class);
    private static final ObjectReader BLACKBIRD_USERS_READER_STRICT =
            BLACKBIRD_USERS_READER.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectReader NATIVE_CLIENTS_READER_STRICT =
            NATIVE.readerFor(Clients.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectReader BLACKBIRD_CLIENTS_READER =
            BLACKBIRD.readerFor(Clients.class);
    private static final ObjectReader BLACKBIRD_CLIENTS_READER_STRICT =
            BLACKBIRD_CLIENTS_READER.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    // Match Jackson2Binder's parser factory settings for the handwritten baseline.
    @SuppressWarnings("deprecation")
    private static final JsonFactory HAND_FACTORY = new JsonFactory()
            .disable(JsonFactory.Feature.CHARSET_DETECTION)
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final FabienCompiled COMPILED = CompiledInstances.create(FabienCompiled.class);

    // Matches java-json-benchmark's default --size 1 (1,000 target bytes).
    @Param({"1"})
    public int sizeKb;

    // Deterministic counterpart to java-json-benchmark's SEED environment variable.
    @Param({"12345"})
    public long seed;

    private byte[] usersJson;
    private byte[] clientsJson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        int targetBytes = Math.multiplyExact(sizeKb, 1000);
        Users users = FabienDataGenerator.users(targetBytes, seed);
        Clients clients = FabienDataGenerator.clients(targetBytes, seed);

        usersJson = NATIVE.writeValueAsBytes(users);
        clientsJson = NATIVE.writeValueAsBytes(clients);
        System.out.printf("Fabien sizeKb=%d seed=%d users=%d bytes/%d objects "
                        + "clients=%d bytes/%d objects%n",
                sizeKb, seed, usersJson.length, users.getUsers().size(),
                clientsJson.length, clients.getClients().size());

        Users expectedUsers = NATIVE.readValue(usersJson, Users.class);
        Clients expectedClients = NATIVE.readValue(clientsJson, Clients.class);
        checkUsers("generated/native", users, expectedUsers);
        checkClients("generated/native", clients, expectedClients);
        checkUsers("native", expectedUsers, users_jackson2_native());
        checkUsers("blackbird", expectedUsers, users_jackson2_blackbird());
        checkUsers("blackbird reader", expectedUsers, users_jackson2_blackbird_reader());
        checkUsers("blackbird reader strict", expectedUsers, users_jackson2_blackbird_reader_strict());
        checkUsers("native reader strict", expectedUsers, users_jackson2_native_reader_strict());
        checkUsers("runtime", expectedUsers, users_jackson2_runtime());
        checkUsers("compiled", expectedUsers, users_jackson2_compiled());
        checkClients("native", expectedClients, clients_jackson2_native());
        checkClients("blackbird", expectedClients, clients_jackson2_blackbird());
        checkClients("blackbird reader", expectedClients, clients_jackson2_blackbird_reader());
        checkClients("blackbird reader strict", expectedClients, clients_jackson2_blackbird_reader_strict());
        checkClients("native reader strict", expectedClients, clients_jackson2_native_reader_strict());
        checkClients("runtime", expectedClients, clients_jackson2_runtime());
        checkClients("compiled", expectedClients, clients_jackson2_compiled());
        checkClients("handwritten", expectedClients, clients_jackson2_handwritten());
        checkClients("handwritten jdk UUID", expectedClients, clients_jackson2_handwritten_jdk_uuid());
        checkClients("handwritten wrapped scalars", expectedClients,
                clients_jackson2_handwritten_wrapped_scalars());
        checkClients("handwritten wrapped arrays", expectedClients,
                clients_jackson2_handwritten_wrapped_arrays());
        Clients sample = Clients.sample();
        byte[] sampleJson = NATIVE.writeValueAsBytes(sample);
        checkClients("handwritten sample", sample,
                Jackson2HandRead.readClients(HAND_FACTORY, sampleJson));
        checkClients("handwritten jdk UUID sample", sample,
                Jackson2HandRead.readClientsWithJdkUuid(HAND_FACTORY, sampleJson));
        checkClients("handwritten wrapped scalars sample", sample,
                Jackson2HandRead.readClientsWithWrappedScalars(HAND_FACTORY, sampleJson));
        checkClients("handwritten wrapped arrays sample", sample,
                Jackson2HandRead.readClientsWithWrappedArrays(HAND_FACTORY, sampleJson));
    }

    private static void checkUsers(String label, Users expected, Users actual) {
        if (actual == null || actual.getUsers() == null
                || expected.getUsers().size() != actual.getUsers().size()
                || !NATIVE.valueToTree(expected).equals(NATIVE.valueToTree(actual))) {
            throw new IllegalStateException("Fabien Users read differs: " + label);
        }
    }

    private static void checkClients(String label, Clients expected, Clients actual) {
        if (actual == null || actual.getClients() == null
                || expected.getClients().size() != actual.getClients().size()
                || !NATIVE.valueToTree(expected).equals(NATIVE.valueToTree(actual))) {
            throw new IllegalStateException("Fabien Clients read differs: " + label);
        }
    }

    @Benchmark
    public Users users_jackson2_native() throws IOException {
        return NATIVE.readValue(usersJson, Users.class);
    }

    @Benchmark
    public Users users_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(usersJson, Users.class);
    }

    @Benchmark
    public Users users_jackson2_blackbird_reader() throws IOException {
        return BLACKBIRD_USERS_READER.readValue(usersJson);
    }

    @Benchmark
    public Users users_jackson2_blackbird_reader_strict() throws IOException {
        return BLACKBIRD_USERS_READER_STRICT.readValue(usersJson);
    }

    @Benchmark
    public Users users_jackson2_native_reader_strict() throws IOException {
        return NATIVE_USERS_READER_STRICT.readValue(usersJson);
    }

    @Benchmark
    public Users users_jackson2_runtime() {
        return (Users) RUNTIME.readNode(usersJson, Users.class);
    }

    @Benchmark
    public Users users_jackson2_compiled() throws IOException {
        return COMPILED.readUsers(usersJson);
    }

    @Benchmark
    public Clients clients_jackson2_native() throws IOException {
        return NATIVE.readValue(clientsJson, Clients.class);
    }

    @Benchmark
    public Clients clients_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(clientsJson, Clients.class);
    }

    @Benchmark
    public Clients clients_jackson2_blackbird_reader() throws IOException {
        return BLACKBIRD_CLIENTS_READER.readValue(clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_blackbird_reader_strict() throws IOException {
        return BLACKBIRD_CLIENTS_READER_STRICT.readValue(clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_native_reader_strict() throws IOException {
        return NATIVE_CLIENTS_READER_STRICT.readValue(clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_runtime() {
        return (Clients) RUNTIME.readNode(clientsJson, Clients.class);
    }

    @Benchmark
    public Clients clients_jackson2_compiled() throws IOException {
        return COMPILED.readClients(clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_handwritten() throws IOException {
        return Jackson2HandRead.readClients(HAND_FACTORY, clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_handwritten_jdk_uuid() throws IOException {
        return Jackson2HandRead.readClientsWithJdkUuid(HAND_FACTORY, clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_handwritten_wrapped_scalars() throws IOException {
        return Jackson2HandRead.readClientsWithWrappedScalars(HAND_FACTORY, clientsJson);
    }

    @Benchmark
    public Clients clients_jackson2_handwritten_wrapped_arrays() throws IOException {
        return Jackson2HandRead.readClientsWithWrappedArrays(HAND_FACTORY, clientsJson);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface FabienCompiled {
        @ReadFrom Users readUsers(byte[] json) throws IOException;
        @ReadFrom Clients readClients(byte[] json) throws IOException;
    }
}
