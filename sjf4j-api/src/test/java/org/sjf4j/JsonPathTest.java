package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class JsonPathTest {

    @Test
    public void testCompile1() {
        String s1 = "$.a.b[0].c";
        JsonPath path1 = JsonPath.compile(s1);
        log.info("path1: {}", path1);
        assertEquals("$.a.b[0].c", path1.toString());

        String s2 = "$.a[*].b['c'].d";
        JsonPath path2 = JsonPath.compile(s2);
        log.info("path2: {}", path2);
        assertEquals("$.a[*].b.c.d", path2.toString());

        String s3 = "$['x.y'].a['[['][-1].*[*]";
        JsonPath path3 = JsonPath.compile(s3);
        log.info("path3: {}", path3);
        assertEquals("$['x.y'].a['[['][-1].*[*]", path3.toString());

        String s4 = "$.a[''].b['\\''].c";
        JsonPath path4 = JsonPath.compile(s4);
        log.info("path4: {}", path4);
        assertEquals("$.a[''].b['\\''].c", path4.toString());
    }

    @Test
    public void testFindOne1() {
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

        assertEquals("B", JsonPath.compile("$.book[1]['title']").findOne(jo1));
        assertEquals(10, JsonPath.compile("$.book[0].price").findOne(jo1));
        assertEquals("classic", JsonPath.compile("$.book[0].tags[0]").findOne(jo1));
        assertEquals(new JsonArray(), JsonPath.compile("$.emptyArray").findOne(jo1));
        assertEquals(new JsonObject(), JsonPath.compile("$['emptyObject']").findOne(jo1));
        assertNull(JsonPath.compile("$.nullValue").findOne(jo1));
        assertEquals("v1", JsonPath.compile("$['weird.keys']['key with spaces']").findOne(jo1));

        log.info("$: {}", JsonPath.compile("$").findOne(jo1));
        assertEquals(JsonObject.class, JsonPath.compile("$").findOne(jo1).getClass());

        assertThrows(JsonException.class, () -> JsonPath.compile("$.book[*].price").findOne(jo1));

    }

    @Test
    public void testFindAll1() {
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

        JsonArray result1 = JsonPath.compile("$.book[*].title").findAll(jo1);
        log.info("result1: {}", result1);
        assertEquals(3, result1.size());
        assertEquals("B", result1.getString(1));

        JsonArray result2 = JsonPath.compile("$.book[*].tags").findAll(jo1);
        log.info("result2: {}", result2);
        assertEquals(3, result2.size());
        assertEquals("classic", result2.getJsonArray(0).getString(0));
        assertEquals(new JsonArray(), result2.getJsonArray(1));
        assertNull(result2.getJsonArray(2));

    }

    @Test
    public void testAutofillContainers1() {
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

        Object container1 = JsonPath.compile("$.book[0].box[0].gg")._autoCreateContainers(jo1);
        log.info("container1={} jo1={}", container1, jo1);
        assertEquals(JsonObject.class, container1.getClass());
        assertEquals(1, jo1.getJsonArrayByPath("$.book[0].box").size());

        Object container2 = JsonPath.compile("$.book[2].tags['gg mm'][0]")._autoCreateContainers(jo1);
        log.info("container2={} jo1={}", container2, jo1);
        assertEquals(JsonArray.class, container2.getClass());
        assertEquals(0, jo1.getJsonArrayByPath("$.book[2].tags['gg mm']").size());
    }

}
