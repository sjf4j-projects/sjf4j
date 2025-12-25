package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.node.NodeStream;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class NodeStreamTest {

    @Test
    public void testFind() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        NodeStream<JsonObject> js = NodeStream.of(jo1);
        String[] abc = js.findAllAs("$.book.*", JsonObject.class)
                .filter(n -> n.hasNonNull("tags"))
                .findAs("$.title", String.class)
                .toList().toArray(new String[0]);
        assertArrayEquals(new String[]{"A", "B"}, abc);
    }


    @Test
    public void testFind2() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"price\": 88,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        List<Integer> prices = JsonObject.fromJson(json1).stream()
                .findAll("$..price", Integer.class)
                .filter(Objects::nonNull)
                .toList();

        assertEquals(2, prices.size());
        assertEquals(88, prices.get(1));

        int priceSum = JsonObject.fromJson(json1).stream()
                .findAll("$..price", Integer.class)
                .filter(Objects::nonNull)
                .collect(Collectors.summingInt(x -> x));
        assertEquals(98, priceSum);
    }

}
