package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.backend.jackson2.binding.Jackson2Reader;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.oneof.Cat;
import org.sjf4j.testbench.model.oneof.Dog;
import org.sjf4j.testbench.model.oneof.Envelope;
import org.sjf4j.testbench.model.oneof.Pet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Jackson 2 polymorphic binding vs SJF4J @OneOf runtime subtree fallback.
 *
 * All paths deserialize the same JSON into the same Java classes. Jackson uses
 * @JsonTypeInfo/@JsonSubTypes; SJF4J uses @OneOf on the very same base class.
 * Parsing, object creation and reader lifecycle are included in each operation.
 *
 * The root workload isolates the OneOf subtree. The envelope workload measures
 * the benefit of keeping surrounding fields on the CompiledBinder read path.
 * "last" moves the discriminator behind the subtype's other properties.
 * The raw_baseline methods recreate the pre-refactor readRaw + NodeMapper path
 * for same-JVM comparisons with the streaming OneOf dispatcher.
 * Input bytes are prepared outside the measurement; every read parses anew.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class OneOfReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{OneOfReadBenchmark.class.getName()});
    }

    @Param({"first", "last"})
    public String discriminatorOrder;

    private static final ObjectMapper JACKSON_NATIVE = new ObjectMapper();
    private static final ObjectMapper JACKSON_BLACKBIRD =
            new ObjectMapper().registerModule(new BlackbirdModule());
    private static final Jackson2Binder SJF4J_RUNTIME = new Jackson2Binder();
    private static final Jackson2CompiledBinder SJF4J_COMPILED =
            CompiledInstances.global(Jackson2CompiledBinder.class);

    private static final String PET_FIRST =
            "{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7,\"trained\":true,"
                    + "\"detail\":{\"city\":\"Seattle\",\"zip\":\"98101\",\"score\":99}}";
    private static final String PET_LAST =
            "{\"name\":\"Rex\",\"age\":7,\"trained\":true,"
                    + "\"detail\":{\"city\":\"Seattle\",\"zip\":\"98101\",\"score\":99},\"kind\":\"dog\"}";

    private String petJson;
    private byte[] petUtf8;
    private String envelopeJson;
    private byte[] envelopeUtf8;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        petJson = "first".equals(discriminatorOrder) ? PET_FIRST : PET_LAST;
        petUtf8 = petJson.getBytes(StandardCharsets.UTF_8);
        envelopeJson = "{\"id\":839201,\"createdAt\":1700000000123,"
                + "\"username\":\"alice.builder\",\"verified\":true,"
                + "\"home\":{\"city\":\"San Francisco\",\"zip\":\"94105\",\"score\":8},"
                + "\"roles\":[\"admin\",\"author\",\"reader\"],\"pet\":" + petJson
                + ",\"lastLogin\":1701234567890,\"active\":true}";
        envelopeUtf8 = envelopeJson.getBytes(StandardCharsets.UTF_8);

        Pet expectedPet = JACKSON_NATIVE.readValue(petJson, Pet.class);
        checkPet("root compiled/string", expectedPet, SJF4J_COMPILED.readPet(petJson));
        checkPet("root compiled/utf8", expectedPet, SJF4J_COMPILED.readPet(petUtf8));
        checkPet("root runtime/string", expectedPet, (Pet) SJF4J_RUNTIME.readNode(petJson, Pet.class));
        checkPet("root runtime/utf8", expectedPet, (Pet) SJF4J_RUNTIME.readNode(petUtf8, Pet.class));
        checkPet("root raw baseline/string", expectedPet, root_string_raw_baseline());
        checkPet("root raw baseline/utf8", expectedPet, root_utf8_raw_baseline());
        checkPet("root blackbird/string", expectedPet, JACKSON_BLACKBIRD.readValue(petJson, Pet.class));
        checkPet("root native/utf8", expectedPet, JACKSON_NATIVE.readValue(petUtf8, Pet.class));
        checkPet("root blackbird/utf8", expectedPet, JACKSON_BLACKBIRD.readValue(petUtf8, Pet.class));

        // Validate the second discriminator branch as well; the timed input is Dog.
        String catJson = "{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9}";
        Pet nativeCat = JACKSON_NATIVE.readValue(catJson, Pet.class);
        Pet compiledCat = SJF4J_COMPILED.readPet(catJson);
        Pet runtimeCat = (Pet) SJF4J_RUNTIME.readNode(catJson, Pet.class);
        if (!(nativeCat instanceof Cat) || !(compiledCat instanceof Cat)
                || !(runtimeCat instanceof Cat)
                || ((Cat) compiledCat).lives != ((Cat) nativeCat).lives
                || ((Cat) runtimeCat).lives != ((Cat) nativeCat).lives) {
            throw new IllegalStateException("Cat discriminator differs across implementations");
        }

        Envelope expected = JACKSON_NATIVE.readValue(envelopeJson, Envelope.class);
        checkEnvelope("envelope compiled/string", expected, SJF4J_COMPILED.readEnvelope(envelopeJson));
        checkEnvelope("envelope compiled/utf8", expected, SJF4J_COMPILED.readEnvelope(envelopeUtf8));
        checkEnvelope("envelope runtime/string", expected,
                (Envelope) SJF4J_RUNTIME.readNode(envelopeJson, Envelope.class));
        checkEnvelope("envelope runtime/utf8", expected,
                (Envelope) SJF4J_RUNTIME.readNode(envelopeUtf8, Envelope.class));
        checkEnvelope("envelope blackbird/string", expected,
                JACKSON_BLACKBIRD.readValue(envelopeJson, Envelope.class));
        checkEnvelope("envelope native/utf8", expected,
                JACKSON_NATIVE.readValue(envelopeUtf8, Envelope.class));
        checkEnvelope("envelope blackbird/utf8", expected,
                JACKSON_BLACKBIRD.readValue(envelopeUtf8, Envelope.class));
    }

    private static void checkPet(String caseName, Pet expected, Pet actual) {
        if (!(expected instanceof Dog) || !(actual instanceof Dog)) {
            throw new IllegalStateException(caseName + ": expected Dog subtype");
        }
        Dog left = (Dog) expected;
        Dog right = (Dog) actual;
        if (!left.kind.equals(right.kind) || !left.name.equals(right.name)
                || left.age != right.age || left.trained != right.trained
                || left.detail == null || right.detail == null
                || !left.detail.city.equals(right.detail.city)
                || !left.detail.zip.equals(right.detail.zip)
                || left.detail.score != right.detail.score) {
            throw new IllegalStateException(caseName + ": mismatched OneOf contents");
        }
    }

    private static void checkEnvelope(String caseName, Envelope expected, Envelope actual) {
        if (expected == null || actual == null
                || expected.id != actual.id || expected.createdAt != actual.createdAt
                || !expected.username.equals(actual.username)
                || expected.verified != actual.verified
                || expected.lastLogin != actual.lastLogin
                || expected.active != actual.active
                || !expected.roles.equals(actual.roles)
                || expected.home == null || actual.home == null
                || !expected.home.city.equals(actual.home.city)
                || !expected.home.zip.equals(actual.home.zip)
                || expected.home.score != actual.home.score) {
            throw new IllegalStateException(caseName + ": mismatched outer POJO contents");
        }
        checkPet(caseName, expected.pet, actual.pet);
    }

    // Reproduces the removed StreamingIO implementation: read the full raw
    // tree, resolve CURRENT key, then convert the selected subtype.
    private Pet rawBaseline(Jackson2Reader reader) throws IOException {
        try (reader) {
            reader.startDocument();
            Object raw = reader.readRaw();
            reader.endDocument();
            Object kind = ((Map<?, ?>) raw).get("kind");
            Class<?> subtype = "dog".equals(kind) ? Dog.class : Cat.class;
            return (Pet) NodeMapper.convert(raw, subtype, false, RuntimeContext.EMPTY);
        }
    }

    @Benchmark
    public Pet root_string_raw_baseline() throws IOException {
        return rawBaseline(SJF4J_RUNTIME.createReader(petJson));
    }

    @Benchmark
    public Pet root_utf8_raw_baseline() throws IOException {
        return rawBaseline(SJF4J_RUNTIME.createReader(petUtf8));
    }

    @Benchmark
    public Pet root_string_compiled() throws IOException {
        return SJF4J_COMPILED.readPet(petJson);
    }

    @Benchmark
    public Pet root_string_runtime() throws IOException {
        return (Pet) SJF4J_RUNTIME.readNode(petJson, Pet.class);
    }

    @Benchmark
    public Pet root_string_jackson_native() throws IOException {
        return JACKSON_NATIVE.readValue(petJson, Pet.class);
    }

    @Benchmark
    public Pet root_string_jackson_blackbird() throws IOException {
        return JACKSON_BLACKBIRD.readValue(petJson, Pet.class);
    }

    @Benchmark
    public Pet root_utf8_compiled() throws IOException {
        return SJF4J_COMPILED.readPet(petUtf8);
    }

    @Benchmark
    public Pet root_utf8_runtime() throws IOException {
        return (Pet) SJF4J_RUNTIME.readNode(petUtf8, Pet.class);
    }

    @Benchmark
    public Pet root_utf8_jackson_native() throws IOException {
        return JACKSON_NATIVE.readValue(petUtf8, Pet.class);
    }

    @Benchmark
    public Pet root_utf8_jackson_blackbird() throws IOException {
        return JACKSON_BLACKBIRD.readValue(petUtf8, Pet.class);
    }

    @Benchmark
    public Envelope envelope_string_compiled() throws IOException {
        return SJF4J_COMPILED.readEnvelope(envelopeJson);
    }

    @Benchmark
    public Envelope envelope_string_runtime() throws IOException {
        return (Envelope) SJF4J_RUNTIME.readNode(envelopeJson, Envelope.class);
    }

    @Benchmark
    public Envelope envelope_string_jackson_native() throws IOException {
        return JACKSON_NATIVE.readValue(envelopeJson, Envelope.class);
    }

    @Benchmark
    public Envelope envelope_string_jackson_blackbird() throws IOException {
        return JACKSON_BLACKBIRD.readValue(envelopeJson, Envelope.class);
    }

    @Benchmark
    public Envelope envelope_utf8_compiled() throws IOException {
        return SJF4J_COMPILED.readEnvelope(envelopeUtf8);
    }

    @Benchmark
    public Envelope envelope_utf8_runtime() throws IOException {
        return (Envelope) SJF4J_RUNTIME.readNode(envelopeUtf8, Envelope.class);
    }

    @Benchmark
    public Envelope envelope_utf8_jackson_native() throws IOException {
        return JACKSON_NATIVE.readValue(envelopeUtf8, Envelope.class);
    }

    @Benchmark
    public Envelope envelope_utf8_jackson_blackbird() throws IOException {
        return JACKSON_BLACKBIRD.readValue(envelopeUtf8, Envelope.class);
    }









    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @ReadFrom
        Pet readPet(String json) throws IOException;
        @ReadFrom Pet readPet(byte[] json) throws IOException;
        @ReadFrom Envelope readEnvelope(String json) throws IOException;
        @ReadFrom
        Envelope readEnvelope(byte[] json) throws IOException;
    }
}
