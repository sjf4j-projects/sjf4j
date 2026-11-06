package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for YAML temporal lexical normalization:
 * .13684323Z and .136843230Z are equivalent OffsetDateTime instances.
 */
class SnakeFabienRoundTripTest {
    @Test
    void clientsWithNanosecondPrecisionRoundTripAsTypedModel() {
        Clients expected = FabienDataGenerator.clients(10_000, 12345);
        SnakeBinder snake = new SnakeBinder();
        byte[] output = snake.writeNodeAsBytes(expected);
        Object decoded = snake.readNode(output, Map.class);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clients actual = mapper.convertValue(decoded, Clients.class);
        assertEquals(expected, actual);
    }
}
