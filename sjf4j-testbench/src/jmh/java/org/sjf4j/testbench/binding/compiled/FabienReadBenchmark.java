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
import org.sjf4j.testbench.model.fabien.Users;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Parses the nested Fabien Users and Clients sample JSON.
 * Timing includes complete UTF-8 byte[] parsing and POJO creation, but not
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

    private byte[] usersJson;
    private byte[] clientsJson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Users users = Users.sample();
        Clients clients = Clients.sample();
        usersJson = NATIVE.writeValueAsBytes(users);
        clientsJson = NATIVE.writeValueAsBytes(clients);

        Users expectedUsers = NATIVE.readValue(usersJson, Users.class);
        Clients expectedClients = NATIVE.readValue(clientsJson, Clients.class);
        checkUsers("sample/native", users, expectedUsers);
        checkClients("sample/native", clients, expectedClients);
        checkUsers("native", expectedUsers, users_jackson2_native());
        checkUsers("blackbird", expectedUsers, users_jackson2_blackbird());
        checkUsers("runtime", expectedUsers, users_jackson2_runtime());
        checkUsers("compiled", expectedUsers, users_jackson2_compiled());
        checkClients("native", expectedClients, clients_jackson2_native());
        checkClients("blackbird", expectedClients, clients_jackson2_blackbird());
        checkClients("runtime", expectedClients, clients_jackson2_runtime());
        checkClients("compiled", expectedClients, clients_jackson2_compiled());
    }

    private static void checkUsers(String label, Users expected, Users actual) {
        if (actual == null || actual.getUsers() == null || actual.getUsers().size() != 2) {
            throw new IllegalStateException("Fabien Users read differs: " + label);
        }
        Users.User first = actual.getUsers().get(0);
        Users.User second = actual.getUsers().get(1);
        if (first == null || second == null || first.getFriends() == null
                || first.getFriends().size() != 2
                || first.getFriends().get(0) == null
                || !expected.getUsers().get(0).getFriends().get(0).getName()
                        .equals(first.getFriends().get(0).getName())
                || first.getTags() == null || first.getTags().size() != 3
                || first.getTags().get(2) != null || first.getFavoriteFruit() != null
                || second.getEmail() != null
                || !NATIVE.valueToTree(expected).equals(NATIVE.valueToTree(actual))) {
            throw new IllegalStateException("Fabien Users read differs: " + label);
        }
    }

    private static void checkClients(String label, Clients expected, Clients actual) {
        if (actual == null || actual.getClients() == null || actual.getClients().size() != 2) {
            throw new IllegalStateException("Fabien Clients read differs: " + label);
        }
        Clients.Client first = actual.getClients().get(0);
        Clients.Client second = actual.getClients().get(1);
        Clients.Client expectedFirst = expected.getClients().get(0);
        // Compare every field via JSON; BigDecimal scale is not semantic here.
        if (first == null || second == null || first.getPartners() == null
                || first.getPartners().size() != 2
                || first.getPartners().get(1) == null
                || !expectedFirst.getPartners().get(1).getSince()
                        .equals(first.getPartners().get(1).getSince())
                || !expectedFirst.getRegistered().equals(first.getRegistered())
                || !expectedFirst.getGuid().equals(first.getGuid())
                || first.getEyeColor() != expectedFirst.getEyeColor()
                || first.getBalance() == null
                || first.getBalance().compareTo(expectedFirst.getBalance()) != 0
                || !Arrays.equals(expectedFirst.getEmails(), first.getEmails())
                || !Arrays.equals(expectedFirst.getPhones(), first.getPhones())
                || first.getTags() == null || first.getTags().size() != 3
                || first.getTags().get(2) != null || second.getPicture() != null
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
