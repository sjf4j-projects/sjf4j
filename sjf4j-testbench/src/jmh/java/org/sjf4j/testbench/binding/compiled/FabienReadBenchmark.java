package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Focused comparison of SJF4J compiled binding, native-structure hybrid,
 * handwritten Jackson Core and Blackbird.
 *
 * Blackbird uses Jackson's native scalar deserializers without SJF4J overrides.
 * The handwritten reader retains the fast scalar decoders for a structural
 * parsing reference, not as a codec-equalized Blackbird comparison.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 7, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@State(Scope.Thread)
public class FabienReadBenchmark {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{FabienReadBenchmark.class.getName()});
    }

    private static final ObjectMapper NATIVE = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final ObjectMapper BLACKBIRD = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new BlackbirdModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @SuppressWarnings("deprecation")
    private static final JsonFactory HAND_FACTORY = new JsonFactory()
            .disable(JsonFactory.Feature.CHARSET_DETECTION)
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

    private static final FabienCompiled COMPILED = CompiledInstances.create(FabienCompiled.class);

    @Param({"10"})
    public int sizeKb;

    @Param({"12345", "324", "43201"})
    public long seed;

    private byte[] clientsJson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Clients input = FabienDataGenerator.clients(Math.multiplyExact(sizeKb, 1000), seed);
        clientsJson = NATIVE.writeValueAsBytes(input);
        Clients expected = NATIVE.readValue(clientsJson, Clients.class);

        checkClients("generated/native", input, expected);
        checkClients("compiled", expected, clients_jackson2_compiled());
        checkClients("hybrid", expected, clients_jackson2_hybrid_handwritten());
        checkClients("handwritten", expected, clients_jackson2_handwritten_fast_temporal());
        checkClients("blackbird", expected, clients_jackson2_blackbird());

        Clients sample = Clients.sample();
        byte[] sampleJson = NATIVE.writeValueAsBytes(sample);
        checkClients("hybrid sample", sample,
                Jackson2HybridHandRead.readClients(HAND_FACTORY, sampleJson));
    }

    private static void checkClients(String label, Clients expected, Clients actual) {
        if (actual == null || actual.getClients() == null
                || expected.getClients().size() != actual.getClients().size()
                || !NATIVE.valueToTree(expected).equals(NATIVE.valueToTree(actual))) {
            throw new IllegalStateException("Fabien Clients read differs: " + label);
        }
    }

    /** Current production Compiled binder: the baseline we want to improve. */
    @Benchmark
    public Clients clients_jackson2_compiled() throws IOException {
        return COMPILED.readClients(clientsJson);
    }

    /** Candidate: native object/array traversal, wrapper scalar reads. */
    @Benchmark
    public Clients clients_jackson2_hybrid_handwritten() throws IOException {
        return Jackson2HybridHandRead.readClients(HAND_FACTORY, clientsJson);
    }

    /** Performance ceiling: direct handwritten Jackson Core reader. */
    @Benchmark
    public Clients clients_jackson2_handwritten_fast_temporal() throws IOException {
        return Jackson2HandReadFast.readClients(HAND_FACTORY, clientsJson);
    }

    /** Native Jackson Blackbird baseline, with its standard scalar codecs. */
    @Benchmark
    public Clients clients_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(clientsJson, Clients.class);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface FabienCompiled {
        @ReadFrom Clients readClients(byte[] json) throws IOException;
    }
}
