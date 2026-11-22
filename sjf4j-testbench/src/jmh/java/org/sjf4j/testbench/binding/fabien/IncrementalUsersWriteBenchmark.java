package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;
import java.util.concurrent.TimeUnit;

/** Full byte[] write, including fresh buffer lifecycle, for staged Users POJOs. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class IncrementalUsersWriteBenchmark {
    @Param({"1", "2", "3", "4", "5", "6", "7", "8", "9"})
    public int stage;
    @Param({"native", "blackbird", "compiled"})
    public String implementation;
    @Param({"1", "10", "100"})
    public int sizeKb;
    @Param({"12345", "324"})
    public long seed;

    private static final JsonMapper NATIVE = new JsonMapper();
    private static final JsonMapper BLACKBIRD = JsonMapper.builder().addModule(new BlackbirdModule()).build();
    private static final ObjectMapper EXPECTED = new ObjectMapper();

    @FunctionalInterface
    private interface Writer { byte[] write(Object value) throws Exception; }
    private Object value;
    private Writer writer;

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Compiled {
        @Write byte[] write01(IncrementalUsersModel.Stage01 value);
        @Write byte[] write02(IncrementalUsersModel.Stage02 value);
        @Write byte[] write03(IncrementalUsersModel.Stage03 value);
        @Write byte[] write04(IncrementalUsersModel.Stage04 value);
        @Write byte[] write05(IncrementalUsersModel.Stage05 value);
        @Write byte[] write06(IncrementalUsersModel.Stage06 value);
        @Write byte[] write07(IncrementalUsersModel.Stage07 value);
        @Write byte[] write08(IncrementalUsersModel.Stage08 value);
        @Write byte[] write09(IncrementalUsersModel.Stage09 value);
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        Users original = FabienDataGenerator.users(Math.multiplyExact(sizeKb, 1000), seed);
        value = IncrementalUsersModel.project(stage, original);
        if ("native".equals(implementation)) writer = NATIVE::writeValueAsBytes;
        else if ("blackbird".equals(implementation)) writer = BLACKBIRD::writeValueAsBytes;
        else if ("compiled".equals(implementation)) {
            Compiled binder = CompiledInstances.create(Compiled.class);
            switch (stage) {
                case 1: writer = v -> binder.write01((IncrementalUsersModel.Stage01) v); break;
                case 2: writer = v -> binder.write02((IncrementalUsersModel.Stage02) v); break;
                case 3: writer = v -> binder.write03((IncrementalUsersModel.Stage03) v); break;
                case 4: writer = v -> binder.write04((IncrementalUsersModel.Stage04) v); break;
                case 5: writer = v -> binder.write05((IncrementalUsersModel.Stage05) v); break;
                case 6: writer = v -> binder.write06((IncrementalUsersModel.Stage06) v); break;
                case 7: writer = v -> binder.write07((IncrementalUsersModel.Stage07) v); break;
                case 8: writer = v -> binder.write08((IncrementalUsersModel.Stage08) v); break;
                case 9: writer = v -> binder.write09((IncrementalUsersModel.Stage09) v); break;
                default: throw new IllegalArgumentException("stage: " + stage);
            }
        } else throw new IllegalArgumentException("implementation: " + implementation);
        JsonNode reference = EXPECTED.readTree(NATIVE.writeValueAsBytes(value));
        JsonNode candidate = EXPECTED.readTree(writer.write(value));
        if (!reference.equals(candidate)) {
            throw new IllegalStateException("JSON mismatch stage=" + stage + " impl=" + implementation
                    + " seed=" + seed + " sizeKb=" + sizeKb + " reference=" + reference + " actual=" + candidate);
        }
        if (stage == 9 && !reference.equals(EXPECTED.readTree(NATIVE.writeValueAsBytes(original)))) {
            throw new IllegalStateException("Stage09 is not the full Users projection");
        }
    }

    @Benchmark
    public byte[] write() throws Exception { return writer.write(value); }
}
