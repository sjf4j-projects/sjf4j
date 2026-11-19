package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalRegistry;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Registries in a different package from both runtime registries need not be public. */
class NodeRegistryVisibilityTest {

    static final class FieldValue {
        final String value;
        FieldValue(String value) { this.value = value; }
    }

    static final class FactoryValue {
        final String value;
        FactoryValue(String value) { this.value = value; }
    }

    static final class FieldNode {}
    static final class FactoryNode {}

    private static <N> ExternalNode<N> external(Class<N> nodeType) {
        return new ExternalNode<N>() {
            @Override public Class<N> nodeType() { return nodeType; }
            @Override public JsonType jsonType(N node) { return JsonType.STRING; }
        };
    }

    @NodeRegistry
    interface HiddenNodes {
        ValueCodec<FieldValue, String> FIELD_CODEC =
                new ValueCodec.SimpleValueCodec<>(
                        FieldValue.class, String.class, value -> value.value, FieldValue::new);

        static ValueCodec<FactoryValue, String> factoryCodec() {
            return new ValueCodec.SimpleValueCodec<>(
                    FactoryValue.class, String.class, value -> value.value, FactoryValue::new);
        }

        ExternalNode<FieldNode> FIELD_EXTERNAL = external(FieldNode.class);

        static ExternalNode<FactoryNode> factoryExternal() {
            return external(FactoryNode.class);
        }
    }

    @NodeRegistry
    interface EmptyNodes {}

    @Test
    void registersFieldsAndFactoriesOnPackagePrivateInterface() {
        ValueRegistry.registerAnnotated(HiddenNodes.class);
        ExternalRegistry.registerAnnotated(HiddenNodes.class);
        assertNotNull(ValueRegistry.resolve(FieldValue.class));
        assertNotNull(ValueRegistry.resolve(FactoryValue.class));
        assertSame(HiddenNodes.FIELD_EXTERNAL, ExternalRegistry.resolve(FieldNode.class));
        assertNotNull(ExternalRegistry.resolve(FactoryNode.class));
    }

    @Test
    void emptyRegistryIsAccepted() {
        ValueRegistry.registerAnnotated(EmptyNodes.class);
        ExternalRegistry.registerAnnotated(EmptyNodes.class);
    }
}
