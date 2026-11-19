package org.sjf4j.external;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.exception.NodeException;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime registration of external-only and mixed registry interfaces. */
public class NodeRegistryExternalTest {
    static final class NativeNode {
        final String text;
        NativeNode(String text) { this.text = text; }
    }

    static final class FactoryNode {
        final String text;
        FactoryNode(String text) { this.text = text; }
    }

    static final class MixedNode {
        final String text;
        MixedNode(String text) { this.text = text; }
    }

    static final class Tag {
        final String value;
        Tag(String value) { this.value = value; }
    }

    private static <T> ExternalNode<T> adapter(Class<T> root) {
        return new ExternalNode<T>() {
            @Override public Class<T> nodeType() { return root; }
            @Override public JsonType jsonType(T node) { return JsonType.STRING; }
        };
    }

    @NodeRegistry
    public interface ExternalOnly {
        ExternalNode<NativeNode> FIELD = adapter(NativeNode.class);

        static ExternalNode<FactoryNode> factory() {
            return adapter(FactoryNode.class);
        }
    }

    @NodeRegistry
    public interface Mixed {
        ValueCodec<Tag, String> TAG =
                new ValueCodec.SimpleValueCodec<>(Tag.class, String.class, t -> t.value, Tag::new);
        ExternalNode<MixedNode> EXTERNAL = adapter(MixedNode.class);
    }

    @NodeRegistry
    public interface Mismatch {
        @SuppressWarnings({"rawtypes", "unchecked"})
        ExternalNode<NativeNode> WRONG = (ExternalNode) adapter(FactoryNode.class);
    }

    @Test
    void externalOnlyRegistrySupportsFieldAndFactory() {
        ExternalRegistry.registerAnnotated(ExternalOnly.class);
        assertSame(ExternalOnly.FIELD, ExternalRegistry.resolve(NativeNode.class));
        assertNotNull(ExternalRegistry.resolve(FactoryNode.class));
        assertEquals(NodeKind.VALUE_STRING_EXTERNAL, NodeKind.of(new NativeNode("n")));
        assertThrows(NodeException.class, () -> ExternalRegistry.registerAnnotated(ExternalOnly.class));
    }

    @Test
    void mixedRegistryRegistersBothKinds() {
        ValueRegistry.registerAnnotated(Mixed.class);
        ExternalRegistry.registerAnnotated(Mixed.class);
        assertSame(Mixed.EXTERNAL, ExternalRegistry.resolve(MixedNode.class));
        assertNotNull(ValueRegistry.resolve(Tag.class));
        assertEquals(NodeKind.VALUE_STRING_EXTERNAL, NodeKind.of(new MixedNode("m")));
    }

    @Test
    void rejectsMismatchedGenericRoot() {
        NodeException e = assertThrows(NodeException.class,
                () -> ExternalRegistry.registerAnnotated(Mismatch.class));
        assertTrue(e.getMessage().contains("does not match declaration"), e.getMessage());
    }
}
