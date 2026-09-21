package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.value.ValueRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ValueRegistryTest {

    @NodeValue
    static class ConstructorValue {
        final String value;

        @RawToValue
        ConstructorValue(String value) {
            this.value = value;
        }

        @ValueToRaw
        String encode() {
            return value;
        }
    }

    @Test
    void resolvesRawToValueConstructor() {
        ValueInfo[] infos = ValueRegistry.resolve(ConstructorValue.class);

        assertNotNull(infos);
        assertEquals(1, infos.length);
        assertEquals("value", infos[0].valueToRaw(new ConstructorValue("value")));
        assertEquals("value", ((ConstructorValue) infos[0].rawToValue("value")).value);
    }
}
