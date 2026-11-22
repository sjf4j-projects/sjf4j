package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Targeted friends-list ablation of Stage09. The POJO class and generated
 * writer are unchanged for every mode: only the number of Friend values
 * traversed at runtime differs. All timed operations materialize byte[].
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class IncrementalUsersFriendWriteBenchmark {

    @Param({"none", "one", "all"})
    public String friends;

    @Param({"native", "blackbird", "compiled"})
    public String implementation;

    @Param({"12345", "324"})
    public long seed;

    @Param({"1"})
    public int sizeKb;

    private static final JsonMapper NATIVE = new JsonMapper();
    private static final JsonMapper BLACKBIRD =
            JsonMapper.builder().addModule(new BlackbirdModule()).build();
    private static final ObjectMapper VERIFY = new ObjectMapper();

    @FunctionalInterface
    private interface Writer {
        byte[] write(Object value) throws Exception;
    }

    private Object value;
    private Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Users source = FabienDataGenerator.users(Math.multiplyExact(sizeKb, 1000), seed);
        int before = 0;
        int after = 0;
        for (Users.User user : source.getUsers()) {
            if (user.getFriends() == null) continue;
            int n = user.getFriends().size();
            before += n;
            if ("none".equals(friends)) {
                user.setFriends(new ArrayList<>());
            } else if ("one".equals(friends)) {
                user.setFriends(new ArrayList<>(user.getFriends().subList(0, Math.min(1, n))));
            } else if (!"all".equals(friends)) {
                throw new IllegalArgumentException("friends: " + friends);
            }
            after += user.getFriends().size();
        }
        if (before < 2) {
            throw new IllegalStateException("Need >=2 friends to distinguish one/all: seed=" + seed
                    + " sizeKb=" + sizeKb + " found=" + before);
        }
        System.out.println("Users friends setup: mode=" + friends + " before=" + before
                + " after=" + after + " seed=" + seed + " sizeKb=" + sizeKb);
        value = IncrementalUsersModel.project(9, source);
        if ("native".equals(implementation)) {
            writer = NATIVE::writeValueAsBytes;
        } else if ("blackbird".equals(implementation)) {
            writer = BLACKBIRD::writeValueAsBytes;
        } else if ("compiled".equals(implementation)) {
            IncrementalUsersWriteBenchmark.Compiled compiled =
                    CompiledInstances.create(IncrementalUsersWriteBenchmark.Compiled.class);
            writer = node -> compiled.write09((IncrementalUsersModel.Stage09) node);
        } else {
            throw new IllegalArgumentException("implementation: " + implementation);
        }
        JsonNode expected = VERIFY.readTree(NATIVE.writeValueAsBytes(value));
        JsonNode actual = VERIFY.readTree(writer.write(value));
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Users friends JSON mismatch: " + implementation
                    + " friends=" + friends + " seed=" + seed);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(value);
    }
}
