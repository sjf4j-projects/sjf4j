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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
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

        Clients.Client client = new Clients.Client();
        client.setId(1001L);
        client.setIndex(1);
        client.setGuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        client.setIsActive(true);
        client.setBalance(new BigDecimal("1234.50"));
        client.setPicture("https://example.test/avatar/1");
        client.setAge(29);
        client.setEyeColor(Clients.EyeColor.BLUE);
        client.setName("Zoë \"Z\" 李");
        client.setGender("female");
        client.setCompany("Acme & Co.");
        client.setEmails(new String[]{"zoe@example.test", "work@example.test"});
        client.setPhones(new long[]{1234567890L, 9876543210L});
        client.setAddress("42 Main St\nApt 2");
        client.setAbout("Hello\\world\tこんにちは");
        client.setRegistered(LocalDate.of(2024, 2, 3));
        client.setLatitude(37.7749);
        client.setLongitude(-122.4194);
        client.setTags(Arrays.asList("coffee", "雪", null));
        client.setPartners(Arrays.asList(
                Clients.Partner.create(2001L, "Renée", OffsetDateTime.parse("2024-02-03T10:20:30Z")),
                Clients.Partner.create(2002L, "Sam \"S\"", OffsetDateTime.parse("2023-12-01T08:00:07+02:00"))));

        Clients.Client secondClient = new Clients.Client();
        secondClient.setId(1002L);
        secondClient.setIndex(2);
        secondClient.setGuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));
        secondClient.setIsActive(false);
        secondClient.setBalance(new BigDecimal("0.00"));
        secondClient.setAge(34);
        secondClient.setEyeColor(Clients.EyeColor.GREEN);
        secondClient.setName("Miyuki 美雪");
        secondClient.setEmails(new String[]{"miyuki@example.test"});
        secondClient.setPhones(new long[]{5550102L});
        secondClient.setRegistered(LocalDate.of(2020, 1, 1));
        secondClient.setTags(Arrays.asList("music", "hiking"));
        secondClient.setPartners(Arrays.asList(
                Clients.Partner.create(2003L, "Zoë", OffsetDateTime.parse("2022-06-01T12:00:09Z"))));
        clients = new Clients();
        clients.setClients(Arrays.asList(client, secondClient));

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
