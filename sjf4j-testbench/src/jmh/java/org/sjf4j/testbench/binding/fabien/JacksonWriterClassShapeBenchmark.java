package org.sjf4j.testbench.binding.fabien;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.testbench.model.fabien.Users;
import org.sjf4j.testbench.model.fabien.Clients;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Isolate annotation-processor class shape from the generated writeUsers(byte[])
 * implementation. All variants use the same model, signature and JMH call form.
 * Only declared @CompiledBinder methods are varied.
 *
 * Diagnostic only: never change production code based on a single runner.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class JacksonWriterClassShapeBenchmark {

    @Param({"jackson2", "jackson3"})
    public String backend;

    @Param({"runtime", "original", "usersOnly", "writePair", "writeAll", "fullClone"})
    public String variant;

    @Param({"12345", "324"})
    public long seed;

    @Param({"100"})
    public int sizeKb;

    @FunctionalInterface
    private interface Operation {
        byte[] write() throws Exception;
    }

    private Operation operation;

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2UsersOnly {
        @Write byte[] writeUsers(Users value);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2WritePair {
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2WriteAll {
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2FullClone {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3UsersOnly {
        @Write byte[] writeUsers(Users value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3WritePair {
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3WriteAll {
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3FullClone {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture =
                FabienFixtures.create(backend, "users", "pojo", sizeKb, seed);
        Users users = (Users) fixture.value;
        byte[] expected = FabienBindings.writer(backend, "compiled", "users", "pojo").write(users);

        if ("runtime".equals(variant)) {
            if ("jackson2".equals(backend)) {
                Jackson2Binder binder = new Jackson2Binder();
                operation = () -> binder.writeNodeAsBytes(users);
            } else {
                Jackson3Binder binder = new Jackson3Binder();
                operation = () -> binder.writeNodeAsBytes(users);
            }
        } else if ("original".equals(variant)) {
            if ("jackson2".equals(backend)) {
                FabienBindings.Jackson2 binder = CompiledInstances.create(FabienBindings.Jackson2.class);
                operation = () -> binder.writeUsers(users);
            } else {
                FabienBindings.Jackson3 binder = CompiledInstances.create(FabienBindings.Jackson3.class);
                operation = () -> binder.writeUsers(users);
            }
        } else {
            switch (backend) {
            case "jackson2":
                switch (variant) {
                    case "usersOnly": {
                        Jackson2UsersOnly binder = CompiledInstances.create(Jackson2UsersOnly.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    case "writePair": {
                        Jackson2WritePair binder = CompiledInstances.create(Jackson2WritePair.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    case "writeAll": {
                        Jackson2WriteAll binder = CompiledInstances.create(Jackson2WriteAll.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    case "fullClone": {
                        Jackson2FullClone binder = CompiledInstances.create(Jackson2FullClone.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("unknown variant " + variant);
                }
                break;
            case "jackson3":
                switch (variant) {
                    case "usersOnly": {
                        Jackson3UsersOnly binder = CompiledInstances.create(Jackson3UsersOnly.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    case "writePair": {
                        Jackson3WritePair binder = CompiledInstances.create(Jackson3WritePair.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    case "writeAll": {
                        Jackson3WriteAll binder = CompiledInstances.create(Jackson3WriteAll.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    case "fullClone": {
                        Jackson3FullClone binder = CompiledInstances.create(Jackson3FullClone.class);
                        operation = () -> binder.writeUsers(users);
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("unknown variant " + variant);
                }
                break;
                default:
                    throw new IllegalArgumentException("unknown backend " + backend);
            }
        }

        byte[] actual = operation.write();
        FabienFixtures.verifyOutput(backend + "/" + variant, fixture, actual);
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException("Different byte output: " + backend
                    + "/" + variant + "/seed=" + seed);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return operation.write();
    }
}
