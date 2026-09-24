package org.sjf4j.obnt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.patch.JsonPatch;
import org.sjf4j.patch.Patches;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonContainerPathApiSurfaceTest {

    private Sjf4j sjf4j;

    @BeforeEach
    void setUp() {
        sjf4j = Sjf4j.builder().jsonFacadeProvider(SimpleJsonFacade.provider()).build();
    }

    @Test
    void testJsonContainerPathSurface() {
        JsonObject root = JsonObject.of(
                "string", "12",
                "num", 34,
                "doubleString", "7.5",
                "double", 8.25d,
                "bool", true,
                "boolString", "false",
                "obj", JsonObject.of("k", "v"),
                "strings", JsonArray.of("x", "y"),
                "numbers", JsonArray.of(1, 2, 3),
                "nullable", null,
                "short", (short) 9,
                "byte", (byte) 4,
                "bigInt", new BigInteger("123"),
                "bigDec", new BigDecimal("4.5"),
                "items", JsonArray.of(
                        JsonObject.of("id", 1, "idText", "1"),
                        JsonObject.of("id", 2, "idText", "2")),
                "drop", JsonObject.of("keep", 1, "gone", null)
        );

        assertTrue(root.containsByPath("$.nullable"));
        assertFalse(root.hasNonNullByPath("$.nullable"));
        assertEquals(34, root.getNodeByPath("$.num"));
        assertEquals("fallback", root.getNodeByPath("$.missing", "fallback"));
        assertEquals("12", root.getStringByPath("$.string"));
        assertEquals("fallback", root.getStringByPath("$.missing", "fallback"));
        assertEquals("34", root.getAsStringByPath("$.num"));
        assertEquals(34, root.getNumberByPath("$.num").intValue());
        assertEquals(7, root.getNumberByPath("$.missing", 7).intValue());
        assertEquals(12, root.getAsNumberByPath("$.string").intValue());
        assertEquals(34L, root.getLongByPath("$.num"));
        assertEquals(7L, root.getLongByPath("$.missing", 7L));
        assertEquals(12L, root.getAsLongByPath("$.string"));
        assertEquals(34, root.getIntByPath("$.num"));
        assertEquals(7, root.getIntByPath("$.missing", 7));
        assertEquals(12, root.getAsIntByPath("$.string"));
        assertEquals((short) 9, root.getShortByPath("$.short"));
        assertEquals((short) 7, root.getShortByPath("$.missing", (short) 7));
        assertEquals((short) 12, root.getAsShortByPath("$.string"));
        assertEquals((byte) 4, root.getByteByPath("$.byte"));
        assertEquals((byte) 7, root.getByteByPath("$.missing", (byte) 7));
        assertEquals((byte) 12, root.getAsByteByPath("$.string"));
        assertEquals(8.25d, root.getDoubleByPath("$.double"));
        assertEquals(7.0d, root.getDoubleByPath("$.missing", 7.0d));
        assertEquals(7.5d, root.getAsDoubleByPath("$.doubleString"));
        assertEquals(8.25f, root.getFloatByPath("$.double"));
        assertEquals(7.0f, root.getFloatByPath("$.missing", 7.0f));
        assertEquals(7.5f, root.getAsFloatByPath("$.doubleString"));
        assertEquals(new BigInteger("123"), root.getBigIntegerByPath("$.bigInt"));
        assertEquals(BigInteger.TEN, root.getBigIntegerByPath("$.missing", BigInteger.TEN));
        assertEquals(new BigInteger("12"), root.getAsBigIntegerByPath("$.string"));
        assertEquals(new BigDecimal("4.5"), root.getBigDecimalByPath("$.bigDec"));
        assertEquals(BigDecimal.TEN, root.getBigDecimalByPath("$.missing", BigDecimal.TEN));
        assertEquals(new BigDecimal("7.5"), root.getAsBigDecimalByPath("$.doubleString"));
        assertTrue(root.getBooleanByPath("$.bool"));
        assertTrue(root.getBooleanByPath("$.missing", true));
        assertFalse(root.getAsBooleanByPath("$.boolString"));
        assertEquals("v", root.getMapByPath("$.obj").get("k"));
        assertEquals("v", root.getMapByPath("$.obj", String.class).get("k"));
        assertEquals("v", root.getJsonObjectByPath("$.obj").getString("k"));
        assertEquals(JsonArray.of("x", "y"), root.getJsonArrayByPath("$.strings"));
        assertEquals(Arrays.asList("x", "y"), root.getListByPath("$.strings"));
        assertEquals(Arrays.asList("x", "y"), root.getListByPath("$.strings", String.class));
        assertArrayEquals(new Object[]{"x", "y"}, root.getArrayByPath("$.strings"));
        assertArrayEquals(new Integer[]{1, 2, 3}, root.getArrayByPath("$.numbers", Integer.class));
        assertEquals(Collections.singleton(1), JsonObject.of("set", JsonArray.of(1)).getSetByPath("$.set", Integer.class));
        assertEquals(34, root.getByPath("$.num", Integer.class));
        assertEquals(34, root.<Integer>getByPath("$.num"));
        assertEquals(12, root.getAsByPath("$.string", Integer.class));
        assertEquals(12, root.<Integer>getAsByPath("$.string"));
        assertThrowsExactly(IllegalArgumentException.class, () -> root.getByPath("$.num", 1));
        assertThrowsExactly(IllegalArgumentException.class, () -> root.getAsByPath("$.string", 1));
        assertThrowsExactly(NullPointerException.class, () -> root.getByPath("$.num", (Integer[]) null));
        assertThrowsExactly(NullPointerException.class, () -> root.getAsByPath("$.string", (Integer[]) null));

        root.putByPath("$.obj.added", 1);
        assertEquals(1, root.putIfParentPresentByPath("$.obj.added", 2));
        assertNull(root.putIfParentPresentByPath("$.obj.missing", 3));
        assertEquals(3, root.getIntByPath("$.obj.missing"));
        assertNull(root.putIfParentPresentByPath("$.missing.path", 4));
        root.ensurePutByPath("$.created.path", "x");
        root.ensurePutIfAbsentByPath("$.created.path", "y");
        assertEquals(2, root.computeByPath("$.items[*].id", (parent, current) ->
                Integer.parseInt(((JsonObject) parent).getString("idText")) * 10));
        root.addByPath("$.strings", "z");
        root.replaceByPath("$.obj.k", "vv");
        root.removeIfPresentByPath("$.obj.added");
        assertEquals("vv", root.getStringByPath("$.obj.k"));
        assertFalse(root.containsByPath("$.obj.added"));
        assertEquals("x", root.getStringByPath("$.created.path"));
        assertEquals(Arrays.asList(10, 20), root.findByPath("$.items[*].id", Integer.class));
        assertEquals(Arrays.asList("1", "2"), root.findByPath("$.items[*].idText", String.class));
        assertEquals(Arrays.asList(1, 2), root.findAsByPath("$.items[*].idText", Integer.class));
        assertEquals(2, root.evalByPath("$.items.length()", Number.class).intValue());
        assertEquals(12, root.evalAsByPath("$.string", Integer.class));

        AtomicInteger visits = new AtomicInteger();
        root.walk((path, node) -> {
            visits.incrementAndGet();
            return true;
        });
        root.walk(Nodes.WalkTarget.VALUE, Nodes.WalkOrder.TOP_DOWN, -1, (path, node) -> true);
        root.walk(Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.TOP_DOWN, -1, (path, node) -> true);
        root.walk(Nodes.WalkTarget.VALUE, Nodes.WalkOrder.BOTTOM_UP, 3, (path, node) -> true);
        assertTrue(visits.get() > 0);

        root.apply(sjf4j.fromJson("[{\"op\":\"add\",\"path\":\"/patched\",\"value\":1}]", JsonPatch.class));
        assertEquals(1, root.getInt("patched"));
        root.indexedMerge(JsonObject.of("merged", JsonObject.of("value", 1)), true, false);
        root.indexedMerge(JsonObject.of("merged2", 2), true, false);
        root.indexedMerge(JsonObject.of("copied", JsonObject.of("x", 1)), true, true);
        Patches.mergePatch(root, JsonObject.of("nullable", "set"));
        assertEquals(1, root.getIntByPath("$.merged.value"));
        assertEquals(2, root.getInt("merged2"));
        assertEquals("set", root.getString("nullable"));
        root.deepPruneNulls();
        assertFalse(root.getJsonObject("drop").containsKey("gone"));
    }
}
