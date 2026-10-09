package org.sjf4j.testbench.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.mapping.NodeMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeMapperExternalInteroperabilityTest {
    @Test
    void convertsGsonToJackson2And3WithoutIntermediateTrees() {
        JsonObject gson = JsonParser.parseString(
                "{\"amount\":0.00120,\"items\":[true,null,\"x\"]}").getAsJsonObject();

        com.fasterxml.jackson.databind.JsonNode j2 = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(gson, com.fasterxml.jackson.databind.JsonNode.class, false);
        tools.jackson.databind.JsonNode j3 = (tools.jackson.databind.JsonNode)
                NodeMapper.convert(gson, tools.jackson.databind.JsonNode.class, false);

        assertEquals(0, j2.get("amount").decimalValue().compareTo(new BigDecimal("0.00120")));
        assertEquals(0, j3.get("amount").decimalValue().compareTo(new BigDecimal("0.00120")));
        assertTrue(j2.get("items").get(1).isNull());
        assertTrue(j3.get("items").get(1).isNull());

        JsonElement gsonAgain = (JsonElement) NodeMapper.convert(j2, JsonElement.class, false);
        assertEquals(0, new BigDecimal("0.00120").compareTo(
                new BigDecimal(gsonAgain.getAsJsonObject().get("amount").getAsString())));
        assertEquals(gson.getAsJsonArray("items"), gsonAgain.getAsJsonObject().getAsJsonArray("items"));
    }

    @Test
    void acceptsJsonpAsSourceButRejectsJsonpAsTarget() {
        jakarta.json.JsonObject jsonp = Json.createObjectBuilder()
                .add("amount", new BigDecimal("0.00120"))
                .add("items", Json.createArrayBuilder().add("x").addNull())
                .build();

        com.fasterxml.jackson.databind.JsonNode j2 = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(jsonp, com.fasterxml.jackson.databind.JsonNode.class, false);
        assertEquals(0, j2.get("amount").decimalValue().compareTo(new BigDecimal("0.00120")));
        assertEquals("x", j2.get("items").get(0).textValue());

        Map<?, ?> raw = (Map<?, ?>) NodeMapper.convertToRaw(jsonp, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList("x", null), raw.get("items"));
        assertThrows(org.sjf4j.exception.BindingException.class,
                () -> NodeMapper.convert(j2, jakarta.json.JsonValue.class, false));
    }
}
