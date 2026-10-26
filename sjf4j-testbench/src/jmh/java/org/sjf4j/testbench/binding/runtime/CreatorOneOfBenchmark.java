package org.sjf4j.testbench.binding.runtime;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Runtime creator-parameter OneOf vs Jackson2 JsonTypeInfo subtype binding.
 * Both use the same record subtype classes and identical JSON inputs.
 * Parsing, subtype resolution and record construction are measured.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreatorOneOfBenchmark {
    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreatorOneOfBenchmark.class.getName()});
    }

    interface Animal {}
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
            property = "kind", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
    })
    public interface JacksonAnimal {}

    public record Dog(String kind, String name, int age) implements Animal, JacksonAnimal {}
    public record Cat(String kind, String name, int lives) implements Animal, JacksonAnimal {}

    public record RuntimeZoo(
            @OneOf(discriminatorKey = "kind", mappings = {
                    @OneOfMapping(type = Dog.class, when = "dog"),
                    @OneOfMapping(type = Cat.class, when = "cat")
            }) Animal pet, int after) {}

    public record JacksonZoo(JacksonAnimal pet, int after) {}

    @Param({"first", "last"})
    public String discriminatorOrder;

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final ObjectMapper BLACKBIRD =
            new ObjectMapper().registerModule(new BlackbirdModule());
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final CompiledReader COMPILED = CompiledInstances.global(CompiledReader.class);

    private String json;
    private byte[] utf8;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        String pet = "first".equals(discriminatorOrder)
                ? "{\"kind\":\"dog\",\"name\":\"Rex\",\"age\":7}"
                : "{\"name\":\"Rex\",\"age\":7,\"kind\":\"dog\"}";
        json = "{\"pet\":" + pet + ",\"after\":19}";
        utf8 = json.getBytes(StandardCharsets.UTF_8);
        verify((RuntimeZoo) RUNTIME.readNode(json, RuntimeZoo.class),
                NATIVE.readValue(json, JacksonZoo.class));
        verify(COMPILED.read(json), NATIVE.readValue(json, JacksonZoo.class));
        verify((RuntimeZoo) RUNTIME.readNode(utf8, RuntimeZoo.class),
                BLACKBIRD.readValue(utf8, JacksonZoo.class));
        verify(COMPILED.read(utf8), BLACKBIRD.readValue(utf8, JacksonZoo.class));

        // Exercise the second subtype outside the timed section.
        String cat = "{\"pet\":{\"kind\":\"cat\",\"name\":\"Mog\",\"lives\":9},\"after\":19}";
        RuntimeZoo actual = (RuntimeZoo) RUNTIME.readNode(cat, RuntimeZoo.class);
        JacksonZoo expected = NATIVE.readValue(cat, JacksonZoo.class);
        if (!(actual.pet() instanceof Cat) || !(expected.pet() instanceof Cat)
                || !(COMPILED.read(cat).pet() instanceof Cat)) {
            throw new IllegalStateException("Cat subtype resolution differs");
        }
    }

    private static void verify(RuntimeZoo actual, JacksonZoo expected) {
        if (!(actual.pet() instanceof Dog) || !(expected.pet() instanceof Dog)) {
            throw new IllegalStateException("Dog subtype resolution differs");
        }
        Dog left = (Dog) actual.pet();
        Dog right = (Dog) expected.pet();
        if (actual.after() != expected.after() || !left.equals(right)) {
            throw new IllegalStateException("Runtime creator binding differs from Jackson2");
        }
    }

    @Benchmark
    public RuntimeZoo string_compiled() throws IOException {
        return COMPILED.read(json);
    }

    @Benchmark
    public RuntimeZoo utf8_compiled() throws IOException {
        return COMPILED.read(utf8);
    }

    @Benchmark
    public RuntimeZoo string_runtime() throws IOException {
        return (RuntimeZoo) RUNTIME.readNode(json, RuntimeZoo.class);
    }

    @Benchmark
    public JacksonZoo string_jackson2_native() throws IOException {
        return NATIVE.readValue(json, JacksonZoo.class);
    }

    @Benchmark
    public JacksonZoo string_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(json, JacksonZoo.class);
    }

    @Benchmark
    public RuntimeZoo utf8_runtime() throws IOException {
        return (RuntimeZoo) RUNTIME.readNode(utf8, RuntimeZoo.class);
    }

    @Benchmark
    public JacksonZoo utf8_jackson2_native() throws IOException {
        return NATIVE.readValue(utf8, JacksonZoo.class);
    }

    @Benchmark
    public JacksonZoo utf8_jackson2_blackbird() throws IOException {
        return BLACKBIRD.readValue(utf8, JacksonZoo.class);
    }
    @CompiledBinder(backend = Backend.JACKSON2)
    public interface CompiledReader {
        @ReadFrom RuntimeZoo read(String json) throws IOException;
        @ReadFrom RuntimeZoo read(byte[] json) throws IOException;
    }

}
