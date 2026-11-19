package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Codec registration works with a package-private registry in another package. */
class NodeRegistryVisibilityTest {

    static final class FieldValue {
        final String value;
        FieldValue(String value) { this.value = value; }
    }

    @NodeRegistry
    interface HiddenCodecs {
        ValueCodec<FieldValue, String> FIELD =
                new ValueCodec.SimpleValueCodec<>(
                        FieldValue.class, String.class, value -> value.value, FieldValue::new);

    }

    @Test
    void registersCodecFieldsOnPackagePrivateInterface() {
        ValueRegistry.registerAnnotated(HiddenCodecs.class);
        assertNotNull(ValueRegistry.resolve(FieldValue.class));
    }
}
