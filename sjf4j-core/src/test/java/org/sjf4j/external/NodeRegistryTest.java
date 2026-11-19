package org.sjf4j.external;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.NodeRegistries;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Explicit interface registry combines codec and external node declarations. */
class NodeRegistryTest {
    public static final class OrderId {
        public final String id;
        public OrderId(String id) { this.id = id; }
    }

    public static final class CustomNode {
        public final String text;
        CustomNode(String text) { this.text = text; }
    }

    public static final class Adapter implements ExternalNode<CustomNode> {
        @Override
        public Class<CustomNode> nodeType() { return CustomNode.class; }

        @Override
        public JsonType jsonType(CustomNode node) { return JsonType.STRING; }

        @Override
        public String toString(CustomNode node) { return node.text; }
    }

    @NodeRegistry
    public interface AppNodes {
        ValueCodec<OrderId, String> ORDER_IDS = new ValueCodec.SimpleValueCodec<>(
                OrderId.class, String.class, value -> value.id, OrderId::new);
        ExternalNode<CustomNode> CUSTOM = new Adapter();
    }

    @Test
    void registersCodecAndExternalNodeFromTheSameInterface() {
        NodeRegistries.register(AppNodes.class);
        NodeRegistries.register(AppNodes.class);

        assertNotNull(ValueRegistry.resolve(OrderId.class));
        assertEquals("\"order-1\"", new Sjf4j().toJsonString(new OrderId("order-1")));
        assertSame(AppNodes.CUSTOM, ExternalRegistry.resolve(CustomNode.class));
        assertEquals(NodeKind.VALUE_STRING_EXTERNAL, NodeKind.of(new CustomNode("example")));
    }
}
