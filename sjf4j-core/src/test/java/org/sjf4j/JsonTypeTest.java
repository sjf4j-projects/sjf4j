package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;

import java.net.URI;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTypeTest {
    static class PlainPojo { public String name; }

    @OneOf(mappings = {@OneOfMapping(type = PolyObj.class), @OneOfMapping(type = PolyArr.class)})
    interface PolymorphicType {}

    static class PolyObj extends JsonObject implements PolymorphicType {}
    static class PolyArr extends JsonArray implements PolymorphicType {}

    @Test
    void classifiesNodeKindsValuesAndSchemaTypes() {
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
