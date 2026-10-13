package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.NodeException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class NodeApiValidationTest {

    enum State { ON, OFF }

    @Test
    void directClassTargetsAreRequiredEvenForNullNodes() {
        assertThrowsExactly(NullPointerException.class, () -> Nodes.to(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.as(null, (Class<Object>) null));
        assertNull(Nodes.toMap(null, null));
        assertNull(Nodes.toList(null, null));
        assertNull(Nodes.toArray(null, null));
        assertNull(Nodes.toSet(null, null));

        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        assertThrowsExactly(NullPointerException.class, () -> object.get("missing", (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> object.getMap("missing", null));
        assertThrowsExactly(NullPointerException.class, () -> array.get(0, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> array.getList(0, null));
        assertArrayEquals(new Object[0], array.toArray(null));

        assertThrowsExactly(NullPointerException.class,
                () -> object.getByPath("$.missing", (Class<Object>) null));
    }

    @Test
    void enumAndFactoryDefinitionErrorsUseTheirDefinedTypes() {
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toEnum(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.asEnum(null, null));
        assertNull(Nodes.toEnum(null, State.class));
        assertEquals(State.ON, Nodes.toEnum(State.ON, State.class));
        assertNull(Nodes.asEnum("missing", State.class));

        NodeException invalid = assertThrowsExactly(NodeException.class,
                () -> Nodes.toEnum("missing", State.class));
        assertInstanceOf(IllegalArgumentException.class, invalid.getCause());

        assertThrowsExactly(IllegalArgumentException.class, () -> JsonObject.of("key"));
        assertThrowsExactly(IllegalArgumentException.class, () -> JsonObject.of(1, "value"));
        assertEquals(0, JsonObject.of((Object[]) null).size());
        assertThrowsExactly(NullPointerException.class, () -> CompiledInstances.of(null));
        assertThrowsExactly(IllegalArgumentException.class, () -> CompiledInstances.of(String.class));
    }

}
