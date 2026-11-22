package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.NodeConfig;
import org.sjf4j.testbench.model.fabien.Users;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Actual APT-produced Users POJO binder vs native controls.
 * The benchmark jar is rebuilt with the APT diagnostic policy: dynamic/include/omit.
 * The exact same Java source and benchmark harness are used in all three jars.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(1)
public class FabienFixedConfigUsersBenchmark {

    private static final ObjectMapper JSON = new ObjectMapper();
    @Param({"jackson3", "fastjson2"})
    public String backend;

    @Param({"compiled", "blackbird", "native"})
    public String implementation;

    @Param({"include", "omit"})
    public String nullPolicy;

    @Param({"none", "half", "all"})
    public String nullDensity;

    @Param({"1", "10", "100"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    private Users users;
    private FabienBindings.Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create(backend, "users", "pojo", sizeKb, seed);
        users = (Users) fixture.value;
        introduceNulls();
        if ("compiled".equals(implementation)) {
            NodeConfig config = new NodeConfig("include".equals(nullPolicy));
            switch (backend) {
                case "jackson3": {
                    FabienBindings.Jackson3 compiled =
                            CompiledInstances.create(FabienBindings.Jackson3.class, config);
                    writer = node -> compiled.writeUsers((Users) node);
                    break;
                }
                case "fastjson2": {
                    FabienBindings.Fastjson2 compiled =
                            CompiledInstances.create(FabienBindings.Fastjson2.class, config);
                    writer = node -> compiled.writeUsers((Users) node);
                    break;
                }
                default:
                    throw new IllegalArgumentException(backend);
            }
        } else {
            if (!"include".equals(nullPolicy))
                throw new IllegalArgumentException("native reference only tests include policy");
            writer = FabienBindings.writer(backend, implementation, "users", "pojo");
        }

        byte[] output = writer.write(users);
        JsonNode expected = JSON.valueToTree(users);
        if ("omit".equals(nullPolicy)) stripNullObjectMembers(expected);
        JsonNode actual = JSON.readTree(output);
        FabienFixtures.verify(
                backend + "/" + implementation + "/" + nullPolicy + "/" + nullDensity,
                expected, actual);
    }

    /** Remove only object properties with null values; retain null array elements. */
    private static void stripNullObjectMembers(JsonNode node) {
        if (node.isObject()) {
            ArrayList<String> nullNames = new ArrayList<>();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                JsonNode child = node.get(field);
                if (child.isNull()) nullNames.add(field);
                else stripNullObjectMembers(child);
            }
            ObjectNode object = (ObjectNode) node;
            for (String name : nullNames) object.remove(name);
        } else if (node.isArray()) {
            for (JsonNode element : node) stripNullObjectMembers(element);
        }
    }

    private void introduceNulls() {
        if ("none".equals(nullDensity)) return;
        boolean all = "all".equals(nullDensity);
        for (Users.User user : users.getUsers()) {
            if (all || half(0)) user.setId(null);
            if (all || half(1)) user.setGuid(null);
            if (all || half(2)) user.setBalance(null);
            if (all || half(3)) user.setPicture(null);
            if (all || half(4)) user.setEyeColor(null);
            if (all || half(5)) user.setName(null);
            if (all || half(6)) user.setGender(null);
            if (all || half(7)) user.setCompany(null);
            if (all || half(8)) user.setEmail(null);
            if (all || half(9)) user.setPhone(null);
            if (all || half(10)) user.setAddress(null);
            if (all || half(11)) user.setAbout(null);
            if (all || half(12)) user.setRegistered(null);
            if (all || half(13)) user.setGreeting(null);
            if (all || half(14)) user.setFavoriteFruit(null);
            if (all) {
                user.setTags(null);
                user.setFriends(null);
            } else if (user.getFriends() != null) {
                for (Users.Friend friend : user.getFriends()) friend.setId(null);
            }
        }
    }

    private boolean half(int fieldIndex) {
        return "half".equals(nullDensity) && (fieldIndex & 1) == 0;
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(users);
    }
}
