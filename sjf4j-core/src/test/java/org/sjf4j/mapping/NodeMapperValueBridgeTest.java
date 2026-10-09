package org.sjf4j.mapping;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class NodeMapperValueBridgeTest {
    @Test
    void convertsDifferentNodeValuesThroughCompatibleRawWithoutCoercion() {
        A source = new A("hello");
        B result = (B) NodeMapper.convert(source, B.class, false);
        assertEquals("hello", result.text);
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, LongValue.class, false));
    }

    @Test
    void sameTypeRetainsIdentityUnlessDeepCopyWasRequested() {
        A source = new A("hello");
        assertSame(source, NodeMapper.convert(source, A.class, false));
        A copy = (A) NodeMapper.convert(source, A.class, true);
        assertNotSame(source, copy);
        assertEquals("hello", copy.text);
    }

    @Test
    void nullRawValuesCanBeDecodedIntoNonNullNodeValues() {
        assertNull(NodeMapper.convert(null, String.class, false));
        assertEquals("<null>", ((B) NodeMapper.convert(null, B.class, false)).text);
        assertEquals("<null>", ((B) NodeMapper.convert(new A(null), B.class, false)).text);
        TargetHolder holder = (TargetHolder) NodeMapper.convert(
                JsonObject.of("value", null), TargetHolder.class, false);
        assertEquals("<null>", holder.value.text);
    }

    @Test
    void mapRawCodecTransfersWithoutMaterializingAnotherTree() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("nested", Arrays.asList(1, 2));
        MapB target = (MapB) NodeMapper.convert(new MapA(raw), MapB.class, false);
        assertSame(raw, target.raw);
        assertThrows(BindingException.class, () -> NodeMapper.convert(new MapA(raw), B.class, false));
    }

    @Test
    void convertsValueElementsInTypedListAndMap() {
        List<B> list = (List<B>) NodeMapper.convert(
                Arrays.asList(new A("a"), new A(null)),
                new TypeReference<List<B>>() {}.getType(), false);
        assertEquals("a", list.get(0).text);
        assertEquals("<null>", list.get(1).text);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("first", new A("x"));
        source.put("second", null);
        Map<String, B> map = (Map<String, B>) NodeMapper.convert(
                source, new TypeReference<Map<String, B>>() {}.getType(), false);
        assertEquals("x", map.get("first").text);
        assertEquals("<null>", map.get("second").text);
    }

    @Test
    void convertsConstructorArgumentsFromPojoAndMap() {
        SourceHolder source = new SourceHolder();
        source.value = new A("pojo");
        source.count = 3;
        TargetCreator fromPojo = (TargetCreator) NodeMapper.convert(source, TargetCreator.class, false);
        assertEquals("pojo", fromPojo.value.text);
        assertEquals(3, fromPojo.count);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", new A("map"));
        map.put("count", 4);
        TargetCreator fromMap = (TargetCreator) NodeMapper.convert(map, TargetCreator.class, false);
        assertEquals("map", fromMap.value.text);
        assertEquals(4, fromMap.count);
    }

    @NodeValue
    static class A {
        final String text;
        A(String text) { this.text = text; }
        @ValueToRaw String encode() { return text; }
        @RawToValue static A decode(String raw) { return new A(raw); }
        @ValueCopy A copy() { return new A(text); }
    }

    @NodeValue
    static class B {
        final String text;
        B(String text) { this.text = text; }
        @ValueToRaw String encode() { return text; }
        @RawToValue static B decode(String raw) { return new B(raw == null ? "<null>" : raw); }
    }

    @NodeValue
    static class LongValue {
        final Long value;
        LongValue(Long value) { this.value = value; }
        @ValueToRaw Long encode() { return value; }
        @RawToValue static LongValue decode(Long raw) { return new LongValue(raw); }
    }

    @NodeValue
    static class MapA {
        final Map<String, Object> raw;
        MapA(Map<String, Object> raw) { this.raw = raw; }
        @ValueToRaw Map<String, Object> encode() { return raw; }
        @RawToValue static MapA decode(Map<String, Object> raw) { return new MapA(raw); }
    }

    @NodeValue
    static class MapB {
        final Map<String, Object> raw;
        MapB(Map<String, Object> raw) { this.raw = raw; }
        @ValueToRaw Map<String, Object> encode() { return raw; }
        @RawToValue static MapB decode(Map<String, Object> raw) { return new MapB(raw); }
    }

    static class SourceHolder {
        public A value;
        public int count;
    }

    static class TargetHolder {
        public B value;
    }

    static class TargetCreator {
        final B value;
        final int count;

        @NodeCreator
        TargetCreator(@NodeProperty("value") B value, @NodeProperty("count") int count) {
            this.value = value;
            this.count = count;
        }
    }
}
