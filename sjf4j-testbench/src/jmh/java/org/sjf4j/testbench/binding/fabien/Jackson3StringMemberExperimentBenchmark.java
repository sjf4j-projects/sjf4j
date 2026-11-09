package org.sjf4j.testbench.binding.fabien;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.testbench.model.fabien.Users;
import org.sjf4j.testbench.model.fabien.Clients;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Original Users, unchanged Pooled byte[] path. Each variant has identical
 * binder API shape; only the @CompiledBinder diagnostic string helper differs.
 * This JMH class is only for experiments, never production.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class Jackson3StringMemberExperimentBenchmark {

    @Param({"original","sibling","baselineClone","compact","staticFlag","direct"})
    public String variant;
    @Param({"12345","324"})
    public long seed;
    @Param({"100"})
    public int sizeKb;

    @FunctionalInterface
    private interface Operation { byte[] write() throws Exception; }
    private Operation operation;

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface BaselineClone {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Compact {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface StaticFlag {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Direct {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture = FabienFixtures.create("jackson3","users","pojo",sizeKb,seed);
        Users value = (Users) fixture.value;
        byte[] expected = FabienBindings.writer("jackson3","compiled","users","pojo").write(value);
        switch (variant) {
            case "original": {
                FabienBindings.Jackson3 binder = CompiledInstances.create(FabienBindings.Jackson3.class);
                operation = () -> binder.writeUsers(value);
                break;
            }
            case "sibling": {
                JacksonOutputSinkBenchmark.Jackson3Stream binder = CompiledInstances.create(JacksonOutputSinkBenchmark.Jackson3Stream.class);
                operation = () -> binder.usersBytes(value);
                break;
            }
            case "baselineClone": {
                BaselineClone binder = CompiledInstances.create(BaselineClone.class);
                operation = () -> binder.writeUsers(value);
                break;
            }
            case "compact": {
                Compact binder = CompiledInstances.create(Compact.class);
                operation = () -> binder.writeUsers(value);
                break;
            }
            case "staticFlag": {
                StaticFlag binder = CompiledInstances.create(StaticFlag.class);
                operation = () -> binder.writeUsers(value);
                break;
            }
            case "direct": {
                Direct binder = CompiledInstances.create(Direct.class);
                operation = () -> binder.writeUsers(value);
                break;
            }
            default: throw new IllegalArgumentException("variant=" + variant);
        }
        byte[] actual = operation.write();
        FabienFixtures.verifyOutput("string-member " + variant, fixture, actual);
        if (!Arrays.equals(expected,actual)) {
            throw new IllegalStateException("byte mismatch " + variant + " seed=" + seed
                    + " expected=" + expected.length + " actual=" + actual.length);
        }
        System.out.println("STRING_MEMBER_CHECK variant=" + variant + " seed=" + seed
                + " bytes=" + actual.length + " byteIdentical=true");
    }

    @Benchmark
    public byte[] write() throws Exception {
        return operation.write();
    }
}
