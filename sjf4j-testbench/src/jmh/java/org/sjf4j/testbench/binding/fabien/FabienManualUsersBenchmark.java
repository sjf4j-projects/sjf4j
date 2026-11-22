package org.sjf4j.testbench.binding.fabien;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.testbench.model.fabien.Users;

import java.util.concurrent.TimeUnit;

/**
 * Fabien Users POJO Write: native versus APT Compiled versus fully manual
 * Jackson3/Fastjson2 token writers, with runtime or source-fixed null policy with inline String members or explicit String-member helpers.
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

    @Param({"native", "blackbird", "compiled", "manual-runtime", "manual-fixed", "manual-member-runtime", "manual-member-fixed"})
    public String implementation;

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

        if ("manual-member-runtime".equals(implementation)) {
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

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(users);
    }
}
