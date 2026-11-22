package org.sjf4j.testbench.binding.fabien;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.testbench.model.fabien.Users;

import java.util.concurrent.TimeUnit;

/**
 * Fabien Users POJO Write: native versus APT Compiled versus fully manual
 * Jackson3/Fastjson2 token writers, with runtime or source-fixed null policy where the runtime includeNulls policy is passed as NodeConfig or a captured boolean.
 * All variants return a fresh byte[] and are verified against the same fixture.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class FabienManualUsersBenchmark {
    @Param({"jackson3", "fastjson2"})
    public String backend;

    @Param({"native", "blackbird", "compiled", "manual-runtime", "manual-fixed", "manual-member-runtime", "manual-member-fixed", "manual-bool-runtime", "manual-static-config", "manual-member-static-config"})
    public String implementation;

    @Param({"1", "10", "100"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    @Param({"none", "half", "all"})
    public String nullDensity;

    private Users users;
    private FabienBindings.Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create(backend, "users", "pojo", sizeKb, seed);
        users = (Users) fixture.value;
        introduceNullFields();

        if ("manual-static-config".equals(implementation)) {
            writer = node -> FabienManualUsersWriter.fastjsonStaticConfig((Users) node);
        } else if ("manual-member-static-config".equals(implementation)) {
            writer = node -> FabienManualUsersWriter.jacksonMemberStaticConfig((Users) node);
        } else if ("manual-bool-runtime".equals(implementation)) {
            writer = "jackson3".equals(backend)
                    ? node -> FabienManualUsersWriter.jacksonBoolRuntime((Users) node)
                    : node -> FabienManualUsersWriter.fastjsonBoolRuntime((Users) node);
        } else if ("manual-member-runtime".equals(implementation)) {
            writer = "jackson3".equals(backend)
                    ? node -> FabienManualUsersWriter.jacksonMemberRuntime((Users) node)
                    : node -> FabienManualUsersWriter.fastjsonMemberRuntime((Users) node);
        } else if ("manual-member-fixed".equals(implementation)) {
            writer = "jackson3".equals(backend)
                    ? node -> FabienManualUsersWriter.jacksonMemberFixed((Users) node)
                    : node -> FabienManualUsersWriter.fastjsonMemberFixed((Users) node);
        } else if ("manual-fixed".equals(implementation)) {
            writer = "jackson3".equals(backend)
                    ? node -> FabienManualUsersWriter.jacksonFixed((Users) node)
                    : node -> FabienManualUsersWriter.fastjsonFixed((Users) node);
        } else if ("manual-runtime".equals(implementation)) {
            writer = "jackson3".equals(backend)
                    ? node -> FabienManualUsersWriter.jacksonRuntime((Users) node)
                    : node -> FabienManualUsersWriter.fastjsonRuntime((Users) node);
        } else {
            writer = FabienBindings.writer(backend, implementation, "users", "pojo");
        }

        FabienFixtures.verifyOutput(backend + "/" + implementation,
                fixture, writer.write(users));
    }

    // Deliberately mutate only the JMH setup fixture, never production model
    // code. Every implementation uses this same object, verified on setup.
    private void introduceNullFields() {
        if ("none".equals(nullDensity)) return;
        boolean all = "all".equals(nullDensity);
        for (Users.User user : users.getUsers()) {
            if (all || shouldNull(0)) user.setId(null);
            if (all || shouldNull(1)) user.setGuid(null);
            if (all || shouldNull(2)) user.setBalance(null);
            if (all || shouldNull(3)) user.setPicture(null);
            if (all || shouldNull(4)) user.setEyeColor(null);
            if (all || shouldNull(5)) user.setName(null);
            if (all || shouldNull(6)) user.setGender(null);
            if (all || shouldNull(7)) user.setCompany(null);
            if (all || shouldNull(8)) user.setEmail(null);
            if (all || shouldNull(9)) user.setPhone(null);
            if (all || shouldNull(10)) user.setAddress(null);
            if (all || shouldNull(11)) user.setAbout(null);
            if (all || shouldNull(12)) user.setRegistered(null);
            if (all || shouldNull(13)) user.setGreeting(null);
            if (all || shouldNull(14)) user.setFavoriteFruit(null);
            if (all) {
                user.setTags(null);
                user.setFriends(null);
            } else if (user.getFriends() != null) {
                for (Users.Friend friend : user.getFriends()) {
                    friend.setId(null);
                }
            }
        }
    }

    private boolean shouldNull(int fieldIndex) {
        return "half".equals(nullDensity) && (fieldIndex & 1) == 0;
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(users);
    }
}
