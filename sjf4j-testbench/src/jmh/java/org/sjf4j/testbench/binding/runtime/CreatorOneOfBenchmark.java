package org.sjf4j.testbench.binding.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Creator-parameter @OneOf against Jackson 2 @JsonSubTypes/@JsonTypeInfo.
 *
 * Both implementations read the same JSON into the same record/constructor model
 * and the same mutable subtype classes. Only polymorphic configuration differs:
 * SJF4J selects subtypes on the Creator parameter; Jackson uses type metadata
 * on the interface. The discriminator is either the first or last pet property.
 * Every invocation includes parsing, subtype selection and object construction.
 *
 * Run with -prof gc to compare allocation, as well as latency. No intermediary
 * raw tree is materialized by the CURRENT/key SJF4J fast path.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreatorOneOfBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreatorOneOfBenchmark.class.getName()});
    }

    @Param({"first", "last"})
    public String discriminatorOrder;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
    })
    public interface Pet {}

    public static final class Dog implements Pet {
        public String name;
        public int age;
        public boolean trained;
    }

    public static final class Cat implements Pet {
        public String name;
        public int lives;
    }

    public record RecordEnvelope(
            long id,
            @OneOf(discriminatorKey = "kind", mappings = {
                    @OneOfMapping(type = Dog.class, when = "dog"),
                    @OneOfMapping(type = Cat.class, when = "cat")
            }) Pet pet,
            String label) {}

    public static final class ConstructorEnvelope {
        public final long id;
        public final Pet pet;
        public final String label;

        @NodeCreator
        @JsonCreator
        public ConstructorEnvelope(
                @NodeProperty("id") @JsonProperty("id") long id,
                @NodeProperty("pet") @JsonProperty("pet")
                @OneOf(discriminatorKey = "kind", mappings = {
                        @OneOfMapping(type = Dog.class, when = "dog"),
                        @OneOfMapping(type = Cat.class, when = "cat")
                }) Pet pet,
                @NodeProperty("label") @JsonProperty("label") String label) {
            this.id = id;
            this.pet = pet;
            this.label = label;
        }
    }

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final ObjectMapper BLACKBIRD =
            new ObjectMapper().registerModule(new BlackbirdModule());
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();

    private String json;
    private byte[] utf8;

    @Setup
    public void setup() throws IOException {
        String pet = "first".equals(discriminatorOrder)
                ? "{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7,\"trained\":true}"
                : "{\"name\":\"Rex\",\"age\":7,\"trained\":true,\"kind\":\"dog\"}";
        json = "{\"id\":839201,\"pet\":" + pet + ",\"label\":\"primary\"}";
        utf8 = json.getBytes(StandardCharsets.UTF_8);

        verify((RecordEnvelope) RUNTIME.readNode(json, RecordEnvelope.class));
        verify((RecordEnvelope) RUNTIME.readNode(utf8, RecordEnvelope.class));
        verify(NATIVE.readValue(json, RecordEnvelope.class));
        verify(NATIVE.readValue(utf8, RecordEnvelope.class));
        verify(BLACKBIRD.readValue(json, RecordEnvelope.class));
        verify(BLACKBIRD.readValue(utf8, RecordEnvelope.class));

        verify((ConstructorEnvelope) RUNTIME.readNode(json, ConstructorEnvelope.class));
        verify((ConstructorEnvelope) RUNTIME.readNode(utf8, ConstructorEnvelope.class));
        verify(NATIVE.readValue(json, ConstructorEnvelope.class));
        verify(NATIVE.readValue(utf8, ConstructorEnvelope.class));
        verify(BLACKBIRD.readValue(json, ConstructorEnvelope.class));
        verify(BLACKBIRD.readValue(utf8, ConstructorEnvelope.class));

        // Validate the other subtype in the same parameter-level mapping.
        String other = "{\"id\":1,\"pet\":{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9},\"label\":\"other\"}";
        checkCat(((RecordEnvelope) RUNTIME.readNode(other, RecordEnvelope.class)).pet());
        checkCat(NATIVE.readValue(other, RecordEnvelope.class).pet());
    }

    private static void verify(RecordEnvelope v) {
        if (v.id() != 839201 || !"primary".equals(v.label())) throw new AssertionError("bad record");
        verifyDog(v.pet());
    }

    private static void verify(ConstructorEnvelope v) {
        if (v.id != 839201 || !"primary".equals(v.label)) throw new AssertionError("bad constructor");
        verifyDog(v.pet);
    }

    private static void verifyDog(Pet pet) {
        if (!(pet instanceof Dog)) throw new AssertionError("not a Dog: " + pet);
        Dog dog = (Dog) pet;
        if (!"Rex".equals(dog.name) || dog.age != 7 || !dog.trained)
            throw new AssertionError("incorrect Dog data");
    }

    private static void checkCat(Pet pet) {
        if (!(pet instanceof Cat) || !"Mog".equals(((Cat) pet).name)
                || ((Cat) pet).lives != 9) throw new AssertionError("incorrect Cat data");
    }

    @Benchmark public RecordEnvelope record_string_sjf4j_runtime() {
        return (RecordEnvelope) RUNTIME.readNode(json, RecordEnvelope.class);
    }

    @Benchmark public RecordEnvelope record_string_jackson2_native() throws IOException {
        return NATIVE.readValue(json, RecordEnvelope.class);
    }

    @Benchmark public RecordEnvelope record_string_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(json, RecordEnvelope.class);
    }

    @Benchmark public RecordEnvelope record_utf8_sjf4j_runtime() {
        return (RecordEnvelope) RUNTIME.readNode(utf8, RecordEnvelope.class);
    }

    @Benchmark public RecordEnvelope record_utf8_jackson2_native() throws IOException {
        return NATIVE.readValue(utf8, RecordEnvelope.class);
    }

    @Benchmark public RecordEnvelope record_utf8_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(utf8, RecordEnvelope.class);
    }

    @Benchmark public ConstructorEnvelope constructor_string_sjf4j_runtime() {
        return (ConstructorEnvelope) RUNTIME.readNode(json, ConstructorEnvelope.class);
    }

    @Benchmark public ConstructorEnvelope constructor_string_jackson2_native() throws IOException {
        return NATIVE.readValue(json, ConstructorEnvelope.class);
    }

    @Benchmark public ConstructorEnvelope constructor_string_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(json, ConstructorEnvelope.class);
    }

    @Benchmark public ConstructorEnvelope constructor_utf8_sjf4j_runtime() {
        return (ConstructorEnvelope) RUNTIME.readNode(utf8, ConstructorEnvelope.class);
    }

    @Benchmark public ConstructorEnvelope constructor_utf8_jackson2_native() throws IOException {
        return NATIVE.readValue(utf8, ConstructorEnvelope.class);
    }

    @Benchmark public ConstructorEnvelope constructor_utf8_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(utf8, ConstructorEnvelope.class);
    }
}
