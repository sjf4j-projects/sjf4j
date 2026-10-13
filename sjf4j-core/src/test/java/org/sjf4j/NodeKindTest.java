package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeKind;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeKindTest {

    enum SampleEnum { A }

    static class SampleJojo extends JsonObject {}

    static class SampleJajo extends JsonArray {}

    static class PlainPojo {
        public String name;
    }

    @Test
    void classifiesPlainJavaTypes() {
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
    void classifiesManagedValues() {
        assertEquals(NodeKind.VALUE_NULL, NodeKind.of(null));
        assertEquals(NodeKind.OBJECT_POJO, NodeKind.of(new PlainPojo()));
        assertEquals(NodeKind.VALUE_BINDING, NodeKind.of(URI.create("https://example.com")));
    }

}
