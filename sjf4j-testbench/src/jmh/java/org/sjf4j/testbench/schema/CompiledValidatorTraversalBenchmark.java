package org.sjf4j.testbench.schema;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Isolates POJO property enumeration vs generated getter traversal. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CompiledValidatorTraversalBenchmark {
    private static final String SCHEMA =
            "{\"type\":\"object\",\"required\":[\"name\",\"age\"],"
            + "\"properties\":{"
            + "\"name\":{\"type\":\"string\",\"minLength\":2},"
            + "\"age\":{\"type\":\"integer\",\"minimum\":1},"
            + "\"city\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    private final Bean bean = new Bean("Alice", 31, "Seattle");
    private final SchemaPlan runtime = new SchemaRegistry().register(
            URI.create("sjf4j:/bench/direct-pojo"),
            JsonSchema.fromJson(SCHEMA));
    private final FastValidator compiled = CompiledInstances.create(FastValidator.class);

    @Benchmark
    public boolean runtime() {
        return runtime.isValid(bean, true);
    }

    @Benchmark
    public boolean compiled() {
        return compiled.isValid(bean);
    }

    public static final class Bean {
        private final String name;
        private final int age;
        private final String city;
        public Bean(String name, int age, String city) {
            this.name = name; this.age = age; this.city = city;
        }
        public String getName() { return name; }
        public int getAge() { return age; }
        public String getCity() { return city; }
    }

    @CompiledValidator
    public interface FastValidator {
        @ValidJsonSchema(SCHEMA)
        boolean isValid(Bean bean);
    }
}
