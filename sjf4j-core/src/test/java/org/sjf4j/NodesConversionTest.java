package org.sjf4j;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;
import org.sjf4j.fixture.NodesBeanFixture.Bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodesConversionTest {
    enum SwitchState { ON, OFF }

    @Test
    void convertsEnumCharNumberAndBooleanScalars() {
        assertNull(Nodes.toEnum(null, SwitchState.class));
        assertEquals(SwitchState.ON, Nodes.toEnum(SwitchState.ON, SwitchState.class));
        assertEquals(SwitchState.OFF, Nodes.toEnum("OFF", SwitchState.class));

        assertNull(Nodes.asEnum(null, SwitchState.class));
        assertEquals(SwitchState.ON, Nodes.asEnum(SwitchState.ON, SwitchState.class));
        assertEquals(SwitchState.OFF, Nodes.asEnum("OFF", SwitchState.class));
        assertNull(Nodes.asEnum("missing", SwitchState.class));

        assertEquals("ON", Nodes.toString(SwitchState.ON));

        assertNull(Nodes.toChar(null));
        assertEquals(Character.valueOf('z'), Nodes.toChar('z'));
        assertThrows(NodeException.class, () -> Nodes.toChar("abc"));
        assertThrows(NodeException.class, () -> Nodes.toChar(""));
        assertEquals(Character.valueOf('b'), Nodes.asChar("bar"));
        assertNull(Nodes.asChar(""));

        assertEquals(1, Nodes.asNumber(true));
        assertEquals(0, Nodes.asNumber(false));
        assertEquals(1, Nodes.asNumber(SwitchState.OFF));
        assertThrows(NodeException.class, () -> Nodes.asNumber(TextNode.valueOf("12")));
        assertThrows(NodeException.class, () -> Nodes.asNumber(new Object()));

        assertThrows(NodeException.class, () -> Nodes.toBoolean(BooleanNode.TRUE));
        assertTrue(Nodes.asBoolean("yes"));
        assertTrue(Nodes.asBoolean("on"));
        assertTrue(Nodes.asBoolean("1"));
        assertFalse(Nodes.asBoolean("no"));
        assertFalse(Nodes.asBoolean("off"));
        assertFalse(Nodes.asBoolean("0"));
        assertTrue(Nodes.asBoolean(1));
        assertFalse(Nodes.asBoolean(0));
        assertThrows(NodeException.class, () -> Nodes.asBoolean(BooleanNode.TRUE));
        assertThrows(NodeException.class, () -> Nodes.asBoolean(TextNode.valueOf("maybe")));
        assertThrows(NodeException.class, () -> Nodes.asBoolean(2));
        assertThrows(NodeException.class, () -> Nodes.asBoolean(new Object()));
    }

    @Test
    void convertsPojoAndCollectionsToStructuralRepresentations() {
        Bean bean = new Bean();
        bean.setName("han");
        bean.setCount(2);

        Map<String, Object> pojoMap = Nodes.toMap(bean);
        assertEquals("han", pojoMap.get("name"));
        assertEquals(2, pojoMap.get("count"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "han");
        assertSame(map, Nodes.toMap(map));

        List<Object> list = new ArrayList<>();
        list.add("a");
        assertSame(list, Nodes.toList(list));
        assertEquals(Arrays.asList(1, 2), Nodes.toList(new int[]{1, 2}));
        assertEquals(Arrays.asList("a", "b"), Nodes.toList(new LinkedHashSet<>(Arrays.asList("a", "b"))));
        assertArrayEquals(new Object[]{1, 2}, Nodes.toArray(new int[]{1, 2}));
        assertArrayEquals(new Object[]{"a", "b"}, Nodes.toArray(new String[]{"a", "b"}));
        assertArrayEquals(new Object[]{"a", "b"}, Nodes.toArray(new LinkedHashSet<>(Arrays.asList("a", "b"))));
        assertArrayEquals(new Object[]{1, 2}, Nodes.toArray(new int[]{1, 2}, Object.class));
        assertArrayEquals(new String[]{"a", "b"}, Nodes.toArray(Arrays.asList("a", "b"), String.class));

        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)), Nodes.toSet(new int[]{1, 2}));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "b")), Nodes.toSet(Arrays.asList("a", "b")));

        Set<Object> set = new LinkedHashSet<>();
        set.add("x");
        assertSame(set, Nodes.toSet(set));
    }
}
