package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.value.ValueRegistry;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @NodeValue
    static class MissingValueToRaw {
        @RawToValue
        static MissingValueToRaw decode(String value) {
            return new MissingValueToRaw();
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

    @Test
    void rejectsObjectRawTypeWhenRegisteringValueInfoDirectly() {
        ValueInfo valueInfo = new ValueInfo(null, ConstructorValue.class, Object.class,
                null, null, null, null);

        assertThrows(BindingException.class, () -> ValueRegistry.register(valueInfo, false));
    }

    @Test
    void rejectsOptionalCodecValueType() {
        ValueCodec<Optional, String> codec = new ValueCodec.SimpleValueCodec<>(Optional.class, String.class,
                Optional::toString, Optional::of);

        assertThrows(BindingException.class, () -> ValueRegistry.registerByCodec(codec, null, false));
    }

    @Test
    void missingValueToRawReportsAnnotationFullyQualifiedName() {
        BindingException error = assertThrows(BindingException.class,
                () -> ValueRegistry.resolve(MissingValueToRaw.class));

        assertEquals("missing @" + ValueToRaw.class.getName() + " method in " +
                MissingValueToRaw.class.getName(), error.getMessage());
    }
}
