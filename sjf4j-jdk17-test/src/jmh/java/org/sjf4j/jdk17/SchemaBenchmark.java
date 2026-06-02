package org.sjf4j.jdk17;

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
import org.sjf4j.annotation.schema.CompiledSchemaValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaValidator;
import org.sjf4j.schema.ValidationResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compares annotation-processor generated schema validation with the runtime
 * {@link SchemaPlan} and {@link SchemaValidator} paths for a JDK 17 benchmark model.
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
public class SchemaBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{SchemaBenchmark.class.getSimpleName()});
    }

    private static final String ORDER_SCHEMA_JSON = """
            {
              "type":"object",
              "required":["id","customer","items","priority","gift"],
              "properties":{
                "id":{"type":"string","minLength":8,"maxLength":32,"pattern":"^ORD-[0-9]+$"},
                "customer":{
                  "type":"object",
                  "required":["name","email"],
                  "properties":{
                    "name":{"type":"string","minLength":2,"maxLength":40},
                    "email":{"type":"string","minLength":5,"maxLength":80,"pattern":"^[a-z0-9._%+-]+@[a-z0-9.-]+\\\\.[a-z]{2,}$"}
                  },
                  "additionalProperties":false
                },
                "items":{
                  "type":"array",
                  "minItems":3,
                  "maxItems":16,
                  "items":{
                    "type":"object",
                    "required":["sku","qty","price"],
                    "properties":{
                      "sku":{"type":"string","minLength":4,"maxLength":20,"pattern":"^[A-Z0-9-]+$"},
                      "qty":{"type":"integer","minimum":1,"maximum":100},
                      "price":{"type":"number","minimum":0}
                    },
                    "additionalProperties":false
                  }
                },
                "priority":{"type":"integer","minimum":1,"maximum":5},
                "coupon":{
                  "oneOf":[
                    {"type":"string","minLength":4,"maxLength":12,"pattern":"^[A-Z0-9]+$"},
                    {"type":"number","minimum":1000,"maximum":9999}
                  ]
                },
                "gift":{"type":"boolean"}
              },
              "additionalProperties":false
            }
            """;

    @ValidJsonSchema(ORDER_SCHEMA_JSON)
    public static final class Order {
        public String id;
        public Customer customer;
        public List<LineItem> items;
        public Integer priority;
        public Object coupon;
        public boolean gift;

        public Order() {}

        public Order(String id, Customer customer, List<LineItem> items, Integer priority, Object coupon, Boolean gift) {
            this.id = id;
            this.customer = customer;
            this.items = items;
            this.priority = priority;
            this.coupon = coupon;
            this.gift = gift;
        }
    }

    public static final class Customer {
        public String name;
        public String email;

        public Customer() {}

        public Customer(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    public static final class LineItem {
        public String sku;
        public Integer qty;
        public double price;

        public LineItem() {}

        public LineItem(String sku, Integer qty, Double price) {
            this.sku = sku;
            this.qty = qty;
            this.price = price;
        }
    }

    @CompiledSchemaValidator
    public interface OrderValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Order order);
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        public Order order;
        public OrderValidator compiledValidator;
        public SchemaPlan schemaPlan;
        public SchemaValidator schemaValidator;

        @Setup(Level.Trial)
        public void setup() {
            order = new Order(
                    "ORD-10001",
                    new Customer("Ada Lovelace", "ada@example.com"),
                    List.of(
                            new LineItem("BOOK-1", 2, 19.95d),
                            new LineItem("PEN-2", 5, 3.5d),
                            new LineItem("BAG-3", 1, 42.0d)),
                    3,
                    "SAVE10",
                    Boolean.FALSE);
            compiledValidator = CompiledNodes.of(OrderValidator.class);
            schemaPlan = JsonSchema.fromJson(ORDER_SCHEMA_JSON).createPlan();
            schemaValidator = new SchemaValidator();

            if (!compiledValidator.isValid(order)) throw new AssertionError("compiled schema validator mismatch");
            ValidationResult planResult = schemaPlan.validate(order, true, true);
            if (!planResult.isValid()) throw new AssertionError("schema plan mismatch: " + planResult);
            ValidationResult validatorResult = schemaValidator.validate(order);
            if (!validatorResult.isValid()) throw new AssertionError("schema validator mismatch: " + validatorResult);
        }
    }

    @Benchmark
    public boolean compiled_schema_validator(BenchmarkState state) {
        return state.compiledValidator.isValid(state.order);
    }

    @Benchmark
    public boolean schema_plan_is_valid(BenchmarkState state) {
        return state.schemaPlan.isValid(state.order, true);
    }

    @Benchmark
    public boolean schema_validator_is_valid(BenchmarkState state) {
        return state.schemaValidator.validate(state.order).isValid();
    }
}
