package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;

import java.io.IOException;
import java.util.Map;

/** Shared deterministic workload, used by both read and write benchmarks. */
final class FabienFixtures {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    private static final SnakeBinder YAML = new SnakeBinder();

    private FabienFixtures() {}

    static Fixture create(String backend, String model, String shape, int sizeKb, long seed)
            throws IOException {
        if (!"pojo".equals(shape) && !"map".equals(shape)) {
            throw new IllegalArgumentException("shape: " + shape);
        }
        int bytes = Math.multiplyExact(sizeKb, 1000);
        Object pojo;
        if ("users".equals(model)) pojo = FabienDataGenerator.users(bytes, seed);
        else if ("clients".equals(model)) pojo = FabienDataGenerator.clients(bytes, seed);
        else throw new IllegalArgumentException("model: " + model);

        byte[] canonicalJson = JSON.writeValueAsBytes(pojo);
        Object value = "map".equals(shape) ? JSON.readValue(canonicalJson, Map.class) : pojo;
        byte[] input = "snake".equals(backend) ? YAML.writeNodeAsBytes(value) : canonicalJson;
        return new Fixture(input, value, backend);
    }

    static void verify(String label, Object expected, Object actual) {
        JsonNode left = JSON.valueToTree(expected);
        JsonNode right = JSON.valueToTree(actual);
        if (!left.equals((a, b) -> {
            if (a.isNumber() && b.isNumber()) {
                return a.decimalValue().compareTo(b.decimalValue());
            }
            return a.equals(b) ? 0 : 1;
        }, right)) {
            throw new IllegalStateException("Fabien binding differs: " + label
                    + "\nexpected: " + left + "\nactual: " + right);
        }
    }

    static void verifyOutput(String label, Fixture fixture, byte[] output) throws IOException {
        Object value = "snake".equals(fixture.backend)
                ? YAML.readNode(output, Map.class)
                : JSON.readValue(output, Map.class);

        // Verify POJO output as the declared Java model, not as the textual
        // intermediate representation. OffsetDateTime may serialize equivalent
        // fractional seconds as .12345678 or .123456780; both deserialize to
        // the same value. Keep Map verification lexical/structural instead.
        if (fixture.value instanceof Clients) {
            Clients actual = JSON.convertValue(value, Clients.class);
            if (!fixture.value.equals(actual)) {
                throw new IllegalStateException("Fabien POJO output differs: " + label
                        + "\nexpected: " + JSON.valueToTree(fixture.value)
                        + "\nactual: " + JSON.valueToTree(actual));
            }
        } else if (fixture.value instanceof Users) {
            Users actual = JSON.convertValue(value, Users.class);
            if (!fixture.value.equals(actual)) {
                throw new IllegalStateException("Fabien POJO output differs: " + label
                        + "\nexpected: " + JSON.valueToTree(fixture.value)
                        + "\nactual: " + JSON.valueToTree(actual));
            }
        } else {
            verify(label, fixture.value, value);
        }
    }

    static final class Fixture {
        final byte[] input;
        final Object value;
        final String backend;

        Fixture(byte[] input, Object value, String backend) {
            this.input = input;
            this.value = value;
            this.backend = backend;
        }
    }
}
