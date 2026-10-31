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

/**
 * Inspect generated implementation in:
 * build/generated/sources/annotationProcessor/java/jmh/
 * org/sjf4j/testbench/schema/DirectValidatorBenchmark$Validator_Impl.java
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 20, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DirectValidatorBenchmark {
    static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":20}}}";
    final Bean bean = new Bean("Alice");
    final SchemaPlan runtime = new SchemaRegistry().register(
            URI.create("sjf4j:/bench/direct"), JsonSchema.fromJson(SCHEMA));
    final Validator compiled = CompiledInstances.create(Validator.class);

    @Benchmark public boolean runtime() { return runtime.isValid(bean, true); }
    @Benchmark public boolean compiled() { return compiled.isValid(bean); }

    public static final class Bean {
        private String name;
        public Bean() {}
        public Bean(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @CompiledValidator
    public interface Validator {
        @ValidJsonSchema(SCHEMA)
        boolean isValid(Bean bean);
    }
}
