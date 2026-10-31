package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Parses Fabien Users and Clients JSON with the same byte[] input shape as
 * java-json-benchmark. Defaults to generated ~1 KB payloads, with the original
 * hand-written edge-case sample available via -p fixture=sample.
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
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final FabienCompiled COMPILED = CompiledInstances.create(FabienCompiled.class);

    @Param({"generated"})
    public String fixture;

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
        Users users;
        Clients clients;
        if ("generated".equals(fixture)) {
            int targetBytes = Math.multiplyExact(sizeKb, 1000);
            users = FabienDataGenerator.users(targetBytes, seed);
            clients = FabienDataGenerator.clients(targetBytes, seed);
        } else if ("sample".equals(fixture)) {
            users = Users.sample();
            clients = Clients.sample();
        } else {
            throw new IllegalArgumentException("Unknown Fabien fixture: " + fixture);
        }

        usersJson = NATIVE.writeValueAsBytes(users);
        clientsJson = NATIVE.writeValueAsBytes(clients);
        System.out.printf("Fabien fixture=%s sizeKb=%d seed=%d users=%d bytes/%d objects "
                        + "clients=%d bytes/%d objects%n",
                fixture, sizeKb, seed, usersJson.length, users.getUsers().size(),
                clientsJson.length, clients.getClients().size());

        Users expectedUsers = NATIVE.readValue(usersJson, Users.class);
        Clients expectedClients = NATIVE.readValue(clientsJson, Clients.class);
        checkUsers("fixture/native", users, expectedUsers);
        checkClients("fixture/native", clients, expectedClients);
        checkUsers("native", expectedUsers, users_jackson2_native());
        checkUsers("blackbird", expectedUsers, users_jackson2_blackbird());
        checkUsers("runtime", expectedUsers, users_jackson2_runtime());
        checkUsers("compiled", expectedUsers, users_jackson2_compiled());
        checkClients("native", expectedClients, clients_jackson2_native());
        checkClients("blackbird", expectedClients, clients_jackson2_blackbird());
        checkClients("runtime", expectedClients, clients_jackson2_runtime());
        checkClients("compiled", expectedClients, clients_jackson2_compiled());
    }

    private void checkUsers(String label, Users expected, Users actual) {
        if (actual == null || actual.getUsers() == null
                || expected.getUsers().size() != actual.getUsers().size()
                || !NATIVE.valueToTree(expected).equals(NATIVE.valueToTree(actual))) {
            throw new IllegalStateException("Fabien Users read differs: " + label);
        }

        if ("sample".equals(fixture)) {
            Users.User first = actual.getUsers().get(0);
            Users.User second = actual.getUsers().get(1);
            if (actual.getUsers().size() != 2 || first.getFriends().size() != 2
                    || first.getTags().size() != 3 || first.getTags().get(2) != null
                    || first.getFavoriteFruit() != null || second.getEmail() != null) {
                throw new IllegalStateException("Fabien Users sample differs: " + label);
            }
        }
    }

    private void checkClients(String label, Clients expected, Clients actual) {
        if (actual == null || actual.getClients() == null
                || expected.getClients().size() != actual.getClients().size()
                || !NATIVE.valueToTree(expected).equals(NATIVE.valueToTree(actual))) {
            throw new IllegalStateException("Fabien Clients read differs: " + label);
        }

        if ("sample".equals(fixture)) {
            Clients.Client first = actual.getClients().get(0);
            Clients.Client second = actual.getClients().get(1);
            Clients.Client expectedFirst = expected.getClients().get(0);
            if (actual.getClients().size() != 2
                    || !expectedFirst.getPartners().get(1).getSince()
                            .equals(first.getPartners().get(1).getSince())
                    || !expectedFirst.getRegistered().equals(first.getRegistered())
                    || !expectedFirst.getGuid().equals(first.getGuid())
                    || first.getEyeColor() != expectedFirst.getEyeColor()
                    || first.getBalance().compareTo(expectedFirst.getBalance()) != 0
                    || !Arrays.equals(expectedFirst.getEmails(), first.getEmails())
                    || !Arrays.equals(expectedFirst.getPhones(), first.getPhones())
                    || first.getTags().size() != 3 || first.getTags().get(2) != null
                    || second.getPicture() != null) {
                throw new IllegalStateException("Fabien Clients sample differs: " + label);
            }
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
    public Clients clients_jackson2_runtime() {
        return (Clients) RUNTIME.readNode(clientsJson, Clients.class);
    }

    @Benchmark
    public Clients clients_jackson2_compiled() throws IOException {
        return COMPILED.readClients(clientsJson);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface FabienCompiled {
        @ReadFrom Users readUsers(byte[] json) throws IOException;
        @ReadFrom Clients readClients(byte[] json) throws IOException;
    }
}
