package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.binding.Binder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SJF4J NodeValue contracts structurally adapted from Jackson custom-deserializer cases. */
public abstract class ValueCodecDeserializationContract {
    protected abstract Binder<?, ?> binding(RuntimeContext context);
    /** Structural source: ValueAnnotationsDeserTest#testRootInterfaceUsing; NodeValue replaces JsonDeserialize(using). */
    @Test void testRootInterfaceUsing() {
        Code value = (Code) binding(RuntimeContext.EMPTY).readNode("\"alpha\"", Code.class);
        assertEquals("alpha", value.value); assertEquals("alpha", binding(RuntimeContext.EMPTY).readNode(binding(RuntimeContext.EMPTY).writeNodeAsString(value), String.class));
    }
    /** Structural source: ValueAnnotationsDeserTest#testRootInterfaceUsing; adapts root dispatch to a NodeValue field. */
    @Test void testValueCodecProperty() {
        CodeHolder value = (CodeHolder) binding(RuntimeContext.EMPTY).readNode("{\"code\":\"beta\"}", CodeHolder.class);
        assertEquals("beta", value.code.value);
    }
    /** Structural source: ValueAnnotationsDeserTest#testRootMapAsOld; its list root becomes a NodeValue list codec. */
    @Test void testValueCodecList() {
            assertEquals(List.of("a", "b"), ((ListCode) binding(RuntimeContext.EMPTY).readNode("[\"a\",\"b\"]", ListCode.class)).value);
        }
    /** Structural source: ValueAnnotationsDeserTest#testRootListAsOld; its map root becomes a NodeValue map codec. */
    @Test void testValueCodecMap() {
            assertEquals(Map.of("x", 3), ((MapCode) binding(RuntimeContext.EMPTY).readNode("{\"x\":3}", MapCode.class)).value);
        }
    /** JSON null is passed to the configured NodeValue decoder. */
    @Test void testNullValueCodec() {
        Code root = (Code) binding(RuntimeContext.EMPTY).readNode("null", Code.class);
        CodeHolder field = (CodeHolder) binding(RuntimeContext.EMPTY).readNode("{\"code\":null}", CodeHolder.class);
        CreatorCodeHolder creator = (CreatorCodeHolder) binding(RuntimeContext.EMPTY).readNode("{\"code\":null}", CreatorCodeHolder.class);

        assertEquals("<null>", root.value);
        assertEquals("<null>", field.code.value);
        assertEquals("<null>", creator.code.value);
        }
    @NodeValue static class Code { final String value;
         Code(String value) {
            this.value = value;
        }
         @ValueToRaw String encode() {
             return value;
         }
         @RawToValue static Code decode(String raw) {
            return new Code(raw == null ? "<null>" : raw);
         }
      }
    static class CodeHolder { public Code code; }
    static class CreatorCodeHolder {
        final Code code;

        @NodeCreator CreatorCodeHolder(@NodeProperty("code") Code code) {
            this.code = code;
        }
    }
    @NodeValue static class ListCode { final List<String> value;
         ListCode(List<String> value) {
            this.value = value;
        }
         @ValueToRaw List<String> encode() {
            return value;
        }
         @RawToValue static ListCode decode(List<String> raw) {
            return new ListCode(raw);
        }
     }
    @NodeValue static class MapCode { final Map<String, Integer> value;
         MapCode(Map<String, Integer> value) {
            this.value = value;
        }
         @ValueToRaw Map<String, Integer> encode() {
            return value;
        }
         @RawToValue static MapCode decode(Map<String, Integer> raw) {
            return new MapCode(raw);
        }
     }
}
