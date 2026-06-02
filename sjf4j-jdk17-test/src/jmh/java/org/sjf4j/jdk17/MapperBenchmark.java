package org.sjf4j.jdk17;

import org.mapstruct.factory.Mappers;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.compiled.CompiledRegistry;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Compares SJF4J generated mappers with MapStruct for the currently supported
 * basic mapper subset, plus direct hand-written baselines.
 *
 * <pre>{@code
 * ./gradlew :sjf4j-jdk17-test:jmh
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class MapperBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{MapperBenchmark.class.getSimpleName()});
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        public FlatSource flatSource;
        public NestedSource nestedSource;
        public Profile profile;
        public Account account;
        public Sjf4jMapper sjf4jMapper;
        public MapStructMapper mapStructMapper;
        public HandMapper handMapper;

        @Setup(Level.Trial)
        public void setup() {
            flatSource = new FlatSource("Ada", "Lovelace", 36, "drop-me");
            nestedSource = new NestedSource(
                    new Profile("Grace", "Hopper", 85),
                    new Account("navy", true),
                    "ignored");
            profile = nestedSource.profile;
            account = nestedSource.account;
            sjf4jMapper = CompiledRegistry.of(Sjf4jMapper.class);
            mapStructMapper = Mappers.getMapper(MapStructMapper.class);
            handMapper = new HandMapper();

            assertFlatEqual(handMapper.flat(flatSource), sjf4jMapper.flat(flatSource), "sjf4j flat");
            assertFlatEqual(handMapper.flat(flatSource), mapStructMapper.flat(flatSource), "mapstruct flat");
            assertNestedEqual(handMapper.nested(nestedSource), sjf4jMapper.nested(nestedSource), "sjf4j nested");
            assertNestedEqual(handMapper.nested(nestedSource), mapStructMapper.nested(nestedSource), "mapstruct nested");
            assertNestedEqual(handMapper.multi(profile, account), sjf4jMapper.multi(profile, account), "sjf4j multi");
            assertNestedEqual(handMapper.multi(profile, account), mapStructMapper.multi(profile, account), "mapstruct multi");

            assertNull(sjf4jMapper.flat(null), "sjf4j flat null");
            assertNull(mapStructMapper.flat(null), "mapstruct flat null");
            assertNull(handMapper.flat(null), "hand flat null");
            assertNull(sjf4jMapper.nested(null), "sjf4j nested null");
            assertNull(mapStructMapper.nested(null), "mapstruct nested null");
            assertNull(handMapper.nested(null), "hand nested null");
            assertNull(sjf4jMapper.multi(null, null), "sjf4j multi null");
            assertNull(mapStructMapper.multi(null, null), "mapstruct multi null");
            assertNull(handMapper.multi(null, null), "hand multi null");
        }
    }

    @Benchmark
    public FlatTarget flat_sjf4j(BenchmarkState state) {
        return state.sjf4jMapper.flat(state.flatSource);
    }

    @Benchmark
    public FlatTarget flat_mapstruct(BenchmarkState state) {
        return state.mapStructMapper.flat(state.flatSource);
    }

    @Benchmark
    public FlatTarget flat_hand(BenchmarkState state) {
        return state.handMapper.flat(state.flatSource);
    }

    @Benchmark
    public NestedTarget nested_sjf4j(BenchmarkState state) {
        return state.sjf4jMapper.nested(state.nestedSource);
    }

    @Benchmark
    public NestedTarget nested_mapstruct(BenchmarkState state) {
        return state.mapStructMapper.nested(state.nestedSource);
    }

    @Benchmark
    public NestedTarget nested_hand(BenchmarkState state) {
        return state.handMapper.nested(state.nestedSource);
    }

    @Benchmark
    public NestedTarget multi_sjf4j(BenchmarkState state) {
        return state.sjf4jMapper.multi(state.profile, state.account);
    }

    @Benchmark
    public NestedTarget multi_mapstruct(BenchmarkState state) {
        return state.mapStructMapper.multi(state.profile, state.account);
    }

    @Benchmark
    public NestedTarget multi_hand(BenchmarkState state) {
        return state.handMapper.multi(state.profile, state.account);
    }

    public static final class FlatSource {
        public String firstName;
        public String lastName;
        public Integer age;
        public String ignored;

        public FlatSource() {}

        public FlatSource(String firstName, String lastName, int age, String ignored) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
            this.ignored = ignored;
        }
    }

    public static final class FlatTarget {
        public String firstName;
        public String surname;
        public String fullName;
        public int age;
        public String ignored;
    }

    public static final class NestedSource {
        public Profile profile;
        public Account account;
        public String ignored;

        public NestedSource() {}

        public NestedSource(Profile profile, Account account, String ignored) {
            this.profile = profile;
            this.account = account;
            this.ignored = ignored;
        }
    }

    public static final class Profile {
        public String firstName;
        public String lastName;
        public Integer age;

        public Profile() {}

        public Profile(String firstName, String lastName, Integer age) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }
    }

    public static final class Account {
        public String type;
        public Boolean active;

        public Account() {}

        public Account(String type, Boolean active) {
            this.type = type;
            this.active = active;
        }
    }

    public static final class NestedTarget {
        public String firstName;
        public String lastName;
        public int age;
        public String accountType;
        public Boolean active;
    }

    @org.sjf4j.annotation.mapper.CompiledMapper
    public interface Sjf4jMapper {
        @org.sjf4j.annotation.mapper.Mapping(target = "surname", source = "lastName")
        @org.sjf4j.annotation.mapper.Mapping(target = "fullName", sources = {"firstName", "lastName"}, compute = "(first, last) -> first + \" \" + last")
        @org.sjf4j.annotation.mapper.Mapping(target = "age", ignore = true)
        @org.sjf4j.annotation.mapper.Mapping(target = "ignored", ignore = true)
        FlatTarget flat(FlatSource source);

        @org.sjf4j.annotation.mapper.Mapping(target = "firstName", source = "$.profile.firstName")
        @org.sjf4j.annotation.mapper.Mapping(target = "lastName", source = "$.profile.lastName")
        @org.sjf4j.annotation.mapper.Mapping(target = "age", sources = {"$.profile.age"}, compute = "(age) -> age == null ? 0 : age")
        @org.sjf4j.annotation.mapper.Mapping(target = "accountType", source = "$.account.type")
        @org.sjf4j.annotation.mapper.Mapping(target = "active", source = "$.account.active")
        NestedTarget nested(NestedSource source);

        @org.sjf4j.annotation.mapper.Mapping(target = "firstName", source = "profile:firstName")
        @org.sjf4j.annotation.mapper.Mapping(target = "lastName", source = "profile:$.lastName")
        @org.sjf4j.annotation.mapper.Mapping(target = "age", sources = {"profile:age"}, compute = "(age) -> age == null ? 0 : age")
        @org.sjf4j.annotation.mapper.Mapping(target = "accountType", source = "account:type")
        @org.sjf4j.annotation.mapper.Mapping(target = "active", source = "account:active")
        NestedTarget multi(Profile profile, Account account);
    }

    @org.mapstruct.Mapper
    public interface MapStructMapper {
        @org.mapstruct.Mapping(target = "surname", source = "lastName")
        @org.mapstruct.Mapping(target = "fullName", expression = "java(source.firstName + \" \" + source.lastName)")
        @org.mapstruct.Mapping(target = "age", ignore = true)
        @org.mapstruct.Mapping(target = "ignored", ignore = true)
        FlatTarget flat(FlatSource source);

        @org.mapstruct.Mapping(target = "firstName", source = "profile.firstName")
        @org.mapstruct.Mapping(target = "lastName", source = "profile.lastName")
        @org.mapstruct.Mapping(target = "age", source = "profile.age")
        @org.mapstruct.Mapping(target = "accountType", source = "account.type")
        @org.mapstruct.Mapping(target = "active", source = "account.active")
        NestedTarget nested(NestedSource source);

        @org.mapstruct.Mapping(target = "firstName", source = "profile.firstName")
        @org.mapstruct.Mapping(target = "lastName", source = "profile.lastName")
        @org.mapstruct.Mapping(target = "age", source = "profile.age")
        @org.mapstruct.Mapping(target = "accountType", source = "account.type")
        @org.mapstruct.Mapping(target = "active", source = "account.active")
        NestedTarget multi(Profile profile, Account account);
    }

    public static final class HandMapper {
        public FlatTarget flat(FlatSource source) {
            if (source == null) return null;
            FlatTarget target = new FlatTarget();
            target.firstName = source.firstName;
            target.surname = source.lastName;
            target.fullName = source.firstName + " " + source.lastName;
            return target;
        }

        public NestedTarget nested(NestedSource source) {
            if (source == null) return null;
            return multi(source.profile, source.account);
        }

        public NestedTarget multi(Profile profile, Account account) {
            if (profile == null && account == null) return null;
            NestedTarget target = new NestedTarget();
            if (profile != null) {
                target.firstName = profile.firstName;
                target.lastName = profile.lastName;
                target.age = profile.age;
            }
            if (account != null) {
                target.accountType = account.type;
                target.active = account.active;
            }
            return target;
        }
    }

    private static void assertFlatEqual(FlatTarget expected, FlatTarget actual, String label) {
        if (!Objects.equals(expected.firstName, actual.firstName)
                || !Objects.equals(expected.surname, actual.surname)
                || !Objects.equals(expected.fullName, actual.fullName)
                || expected.age != actual.age
                || !Objects.equals(expected.ignored, actual.ignored)) {
            throw new AssertionError(label + " mismatch");
        }
    }

    private static void assertNestedEqual(NestedTarget expected, NestedTarget actual, String label) {
        if (!Objects.equals(expected.firstName, actual.firstName)
                || !Objects.equals(expected.lastName, actual.lastName)
                || !Objects.equals(expected.age, actual.age)
                || !Objects.equals(expected.accountType, actual.accountType)
                || !Objects.equals(expected.active, actual.active)) {
            throw new AssertionError(label + " mismatch");
        }
    }

    private static void assertNull(Object value, String label) {
        if (value != null) {
            throw new AssertionError(label + " expected null");
        }
    }
}
