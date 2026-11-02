package org.sjf4j.testbench;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.value.ValueRegistry;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classification uses the declared default codec type, not valueToRaw().
 * In particular a codec-backed Map/List is still an atomic value node.
 */
class NodeKindCodecClassificationTest {

    @NodeValue
    public static final class Text {
        @ValueToRaw public String encode() { throw new AssertionError("unexpected conversion"); }
        @RawToValue public static Text decode(String raw) { return new Text(); }
    }

    @NodeValue
    public static final class Numeric {
        @ValueToRaw public Long encode() { throw new AssertionError("unexpected conversion"); }
        @RawToValue public static Numeric decode(Long raw) { return new Numeric(); }
    }

    @NodeValue
    public static final class Logical {
        @ValueToRaw public Boolean encode() { throw new AssertionError("unexpected conversion"); }
        @RawToValue public static Logical decode(Boolean raw) { return new Logical(); }
    }

    @NodeValue
    public static final class ObjectEncoding {
        @ValueToRaw public Map<String, Object> encode() { throw new AssertionError("unexpected conversion"); }
        @RawToValue public static ObjectEncoding decode(Map<String, Object> raw) { return new ObjectEncoding(); }
    }

    @NodeValue
    public static final class ArrayEncoding {
        @ValueToRaw public List<Object> encode() { throw new AssertionError("unexpected conversion"); }
        @RawToValue public static ArrayEncoding decode(List<Object> raw) { return new ArrayEncoding(); }
    }

    @NodeValue
    public static final class InvalidCharacterEncoding {
        @ValueToRaw public Character encode() { return 'x'; }
        @RawToValue public static InvalidCharacterEncoding decode(Character raw) {
            return new InvalidCharacterEncoding();
        }
    }

    @NodeValue
    public static final class InvalidJsonObjectEncoding {
        @ValueToRaw public JsonObject encode() { return new JsonObject(); }
        @RawToValue public static InvalidJsonObjectEncoding decode(JsonObject raw) {
            return new InvalidJsonObjectEncoding();
        }
    }

    @Test
    void codecRegistryRejectsNonWireRawTypes() {
        assertThrows(BindingException.class, () -> NodeKind.of(new InvalidCharacterEncoding()));
        assertThrows(BindingException.class, () -> NodeKind.of(new InvalidJsonObjectEncoding()));

        // Direct codec registration follows the same five-shape contract.
        for (Class<?> raw : new Class<?>[]{Object.class, Character.class, JsonObject.class, String[].class}) {
            ValueInfo invalid = new ValueInfo(null, Text.class, raw, null, null, null, null);
            assertThrows(BindingException.class, () -> ValueRegistry.register(invalid, false));
        }
    }

    @Test
    void codecShapeIsStableAndDoesNotInvokeEncoder() {
        Object[] values = {new Text(), new Numeric(), new Logical(), new ObjectEncoding(), new ArrayEncoding()};
        NodeKind[] kinds = {
                NodeKind.VALUE_CODEC_STRING, NodeKind.VALUE_CODEC_NUMBER,
                NodeKind.VALUE_CODEC_BOOLEAN, NodeKind.VALUE_CODEC_MAP, NodeKind.VALUE_CODEC_LIST
        };
        JsonType[] types = {
                JsonType.STRING, JsonType.NUMBER, JsonType.BOOLEAN,
                JsonType.UNKNOWN, JsonType.UNKNOWN
        };
        for (int i = 0; i < values.length; i++) {
            assertEquals(kinds[i], NodeKind.of(values[i]));
            assertEquals(types[i], JsonType.of(values[i]));
            assertTrue(kinds[i].isValue());
            assertTrue(kinds[i].isValueCodec());
            assertFalse(kinds[i].isRaw());
        }
        assertFalse(NodeKind.VALUE_CODEC_MAP.isObject());
        assertFalse(NodeKind.VALUE_CODEC_LIST.isArray());
        assertEquals(JsonType.OBJECT, JsonType.rawOf(ObjectEncoding.class));
        assertEquals(JsonType.ARRAY, JsonType.rawOf(ArrayEncoding.class));
    }
}
