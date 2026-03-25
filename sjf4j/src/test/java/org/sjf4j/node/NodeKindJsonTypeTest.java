package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.AnyOf;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeKindJsonTypeTest {

    enum SampleEnum { A }

    static class SampleJojo extends JsonObject {}

    static class SampleJajo extends JsonArray {}

    static class PlainPojo {
        public String name;
    }

    @AnyOf({@AnyOf.Mapping(value = PolyObj.class), @AnyOf.Mapping(value = PolyArr.class)})
    interface PolymorphicType {}

    static class PolyObj extends JsonObject implements PolymorphicType {}

    static class PolyArr extends JsonArray implements PolymorphicType {}

    @Test
    void testNodeKindPlainClassification() {
        assertEquals(NodeKind.VALUE_NULL, NodeKind.plainOf((Object) null));
        assertEquals(NodeKind.VALUE_NULL, NodeKind.plainOf(void.class));
        assertEquals(NodeKind.VALUE_NULL, NodeKind.plainOf(Void.class));
        assertEquals(NodeKind.VALUE_STRING, NodeKind.plainOf(char.class));
        assertEquals(NodeKind.VALUE_BOOLEAN, NodeKind.plainOf(boolean.class));
        assertEquals(NodeKind.VALUE_NUMBER, NodeKind.plainOf(int.class));
        assertEquals(NodeKind.VALUE_STRING, NodeKind.plainOf(String.class));
        assertEquals(NodeKind.VALUE_STRING, NodeKind.plainOf(Character.class));
        assertEquals(NodeKind.VALUE_STRING, NodeKind.plainOf(SampleEnum.class));
        assertEquals(NodeKind.VALUE_NUMBER, NodeKind.plainOf(Integer.class));
        assertEquals(NodeKind.VALUE_BOOLEAN, NodeKind.plainOf(Boolean.class));
        assertEquals(NodeKind.OBJECT_MAP, NodeKind.plainOf(Collections.emptyMap().getClass()));
        assertEquals(NodeKind.OBJECT_JSON_OBJECT, NodeKind.plainOf(JsonObject.class));
        assertEquals(NodeKind.OBJECT_JOJO, NodeKind.plainOf(SampleJojo.class));
        assertEquals(NodeKind.ARRAY_LIST, NodeKind.plainOf(Arrays.asList(1).getClass()));
        assertEquals(NodeKind.ARRAY_JSON_ARRAY, NodeKind.plainOf(JsonArray.class));
        assertEquals(NodeKind.ARRAY_JAJO, NodeKind.plainOf(SampleJajo.class));
        assertEquals(NodeKind.ARRAY_ARRAY, NodeKind.plainOf(String[].class));
        assertEquals(NodeKind.ARRAY_SET, NodeKind.plainOf(Collections.singleton("x").getClass()));
        assertEquals(NodeKind.UNKNOWN, NodeKind.plainOf(Object.class));
        assertTrue(NodeKind.UNKNOWN.isUnknown());
        assertTrue(NodeKind.VALUE_STRING.isRaw());
        assertTrue(NodeKind.OBJECT_MAP.isRaw());
        assertFalse(NodeKind.OBJECT_POJO.isRaw());
    }

    @Test
    void testNodeKindManagedTypes() {
        assertEquals(NodeKind.VALUE_NULL, NodeKind.of(null));
        assertEquals(NodeKind.OBJECT_POJO, NodeKind.of(new PlainPojo()));
        assertEquals(NodeKind.VALUE_NODE_VALUE, NodeKind.of(URI.create("https://example.com")));
    }

    @Test
    void testJsonTypeClassification() {
        assertEquals(JsonType.OBJECT, JsonType.of(NodeKind.OBJECT_MAP));
        assertEquals(JsonType.ARRAY, JsonType.of(NodeKind.ARRAY_LIST));
        assertEquals(JsonType.STRING, JsonType.of(NodeKind.VALUE_STRING));
        assertEquals(JsonType.NUMBER, JsonType.of(NodeKind.VALUE_NUMBER));
        assertEquals(JsonType.BOOLEAN, JsonType.of(NodeKind.VALUE_BOOLEAN));
        assertEquals(JsonType.NULL, JsonType.of(NodeKind.VALUE_NULL));
        assertEquals(JsonType.UNKNOWN, JsonType.of(NodeKind.UNKNOWN));

        assertEquals(JsonType.OBJECT, JsonType.of(new PlainPojo()));
        assertEquals(JsonType.ARRAY, JsonType.of(Arrays.asList(1, 2)));
        assertEquals(JsonType.STRING, JsonType.of("text"));
        assertEquals(JsonType.NUMBER, JsonType.of(12));
        assertEquals(JsonType.BOOLEAN, JsonType.of(true));
        assertEquals(JsonType.NULL, JsonType.of((Object) null));

        assertEquals(JsonType.OBJECT, JsonType.rawOf(PlainPojo.class));
        assertEquals(JsonType.ARRAY, JsonType.rawOf(String[].class));
        assertEquals(JsonType.STRING, JsonType.rawOf(URI.class));
        assertEquals(JsonType.UNKNOWN, JsonType.rawOf(PolymorphicType.class));
        assertEquals(JsonType.UNKNOWN, JsonType.rawOf(Object.class));

        assertEquals(JsonType.OBJECT, JsonType.ofSchema("object"));
        assertEquals(JsonType.ARRAY, JsonType.ofSchema("array"));
        assertEquals(JsonType.STRING, JsonType.ofSchema("string"));
        assertEquals(JsonType.NUMBER, JsonType.ofSchema("number"));
        assertEquals(JsonType.INTEGER, JsonType.ofSchema("integer"));
        assertEquals(JsonType.BOOLEAN, JsonType.ofSchema("boolean"));
        assertEquals(JsonType.NULL, JsonType.ofSchema("null"));
        assertThrows(Exception.class, () -> JsonType.ofSchema("wat"));

        assertTrue(JsonType.OBJECT.isObject());
        assertTrue(JsonType.ARRAY.isArray());
        assertTrue(JsonType.STRING.isValue());
        assertTrue(JsonType.STRING.isString());
        assertTrue(JsonType.NUMBER.isNumber());
        assertTrue(JsonType.INTEGER.isNumber());
        assertTrue(JsonType.BOOLEAN.isBoolean());
        assertTrue(JsonType.NULL.isNull());
        assertTrue(JsonType.UNKNOWN.isUnknown());
        assertFalse(JsonType.OBJECT.isValue());
    }
}
