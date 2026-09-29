package org.sjf4j.node.external;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeRegistry;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class ExternalNodeRegistryTest {
    @Test
    void classifiesDiscoveredHierarchyWithoutPojoAnalysis() {
        TestExternalNode object = new TestExternalNode(JsonType.OBJECT);
        TestExternalChildNode array = new TestExternalChildNode(JsonType.ARRAY);

        assertEquals(NodeKind.OBJECT_EXTERNAL, NodeKind.of(object));
        assertEquals(NodeKind.ARRAY_EXTERNAL, NodeKind.of(array));
        assertEquals(JsonType.OBJECT, JsonType.of(object));
        assertEquals(JsonType.ARRAY, JsonType.of(array));

        TypeInfo typeInfo = TypeRegistry.registerTypeInfo(TestExternalChildNode.class);
        assertSame(TestExternalChildNodeProvider.ADAPTER, typeInfo.externalNode);
        assertNull(typeInfo.pojoInfo);
        assertThrowsExactly(BindingException.class, () -> TypeRegistry.registerPojoOrElseThrow(TestExternalChildNode.class));
    }

    @Test
    void mapsExternalScalarTypes() {
        assertEquals(NodeKind.VALUE_STRING_EXTERNAL, NodeKind.of(new TestExternalNode(JsonType.STRING)));
        assertEquals(NodeKind.VALUE_NUMBER_EXTERNAL, NodeKind.of(new TestExternalNode(JsonType.NUMBER)));
        assertEquals(NodeKind.VALUE_BOOLEAN_EXTERNAL, NodeKind.of(new TestExternalNode(JsonType.BOOLEAN)));
        assertEquals(NodeKind.VALUE_NULL, NodeKind.of(new TestExternalNode(JsonType.NULL)));
        assertEquals(NodeKind.UNKNOWN, NodeKind.of(new TestExternalNode(JsonType.UNKNOWN)));
    }

    @Test
    void resolvesStaticExternalTypeWhenAdapterProvidesIt() {
        assertEquals(JsonType.ARRAY, JsonType.rawOf(TestExternalArrayNode.class));
        assertEquals(JsonType.OBJECT, JsonType.rawOf(TestExternalObjectNode.class));
    }

    @Test
    void computesAbsentValueInExternalObject() {
        TestExternalObjectNode object = new TestExternalObjectNode();
        object.values.put("present", "value");

        assertEquals("value", Nodes.computeIfAbsentInObject(object, "present", key -> {
            throw new AssertionError("mapping function should not be called for a present value");
        }));
        assertEquals("created", Nodes.computeIfAbsentInObject(object, "missing", key -> "created"));
        assertEquals("created", object.values.get("missing"));
        assertNull(Nodes.computeIfAbsentInObject(object, "nullResult", key -> null));
        assertFalse(object.values.containsKey("nullResult"));
    }

    @Test
    void resolvesMostSpecificDiscoveredRootType() {
        assertSame(TestExternalNodeProvider.ADAPTER, ExternalNodeRegistry.resolve(TestExternalNode.class));
        assertSame(TestExternalChildNodeProvider.ADAPTER, ExternalNodeRegistry.resolve(TestExternalChildNode.class));
    }

    @Test
    void unavailableProviderDoesNotPreventOtherProviders() {
        assertSame(TestExternalNodeProvider.ADAPTER, ExternalNodeRegistry.resolve(TestExternalNode.class));
    }

    @Test
    void unsupportedOperationsFailFastByDefault() {
        ExternalNode<TestExternalNode> adapter = new ExternalNode<TestExternalNode>() {
            @Override
            public Class<TestExternalNode> nodeType() {
                return TestExternalNode.class;
            }

            @Override
            public JsonType jsonType(TestExternalNode node) {
                return node.getJsonType();
            }
        };
        NodeException exception = assertThrowsExactly(NodeException.class,
                () -> adapter.getInObject(new TestExternalNode(JsonType.OBJECT), "key"));

        assertEquals("unsupported external node operation 'getInObject'", exception.getMessage());
    }

    static class TestExternalNode {
        private final JsonType jsonType;
        final Map<String, Object> values = new LinkedHashMap<>();

        TestExternalNode(JsonType jsonType) {
            this.jsonType = jsonType;
        }

        public JsonType getJsonType() {
            return jsonType;
        }
    }

    static final class TestExternalChildNode extends TestExternalNode {
        TestExternalChildNode(JsonType jsonType) {
            super(jsonType);
        }
    }

    static final class TestExternalArrayNode extends TestExternalNode {
        TestExternalArrayNode() {
            super(JsonType.ARRAY);
        }
    }

    static final class TestExternalObjectNode extends TestExternalNode {
        TestExternalObjectNode() {
            super(JsonType.OBJECT);
        }
    }

    static final class TestExternalAdapter implements ExternalNode<TestExternalNode> {
        @Override
        public Class<TestExternalNode> nodeType() {
            return TestExternalNode.class;
        }

        @Override
        public JsonType jsonType(TestExternalNode node) {
            return node.getJsonType();
        }

        @Override
        public JsonType jsonTypeOfClass(Class<?> nodeType) {
            if (TestExternalArrayNode.class.isAssignableFrom(nodeType)) return JsonType.ARRAY;
            if (TestExternalObjectNode.class.isAssignableFrom(nodeType)) return JsonType.OBJECT;
            return JsonType.UNKNOWN;
        }

        @Override
        public Object getInObject(TestExternalNode node, String key) {
            return node.values.get(key);
        }

        @Override
        public Object putInObject(TestExternalNode node, String key, Object value) {
            return node.values.put(key, value);
        }
    }

    static final class TestExternalChildNodeAdapter implements ExternalNode<TestExternalChildNode> {
        @Override
        public Class<TestExternalChildNode> nodeType() {
            return TestExternalChildNode.class;
        }

        @Override
        public JsonType jsonType(TestExternalChildNode node) {
            return node.getJsonType();
        }
    }
}
