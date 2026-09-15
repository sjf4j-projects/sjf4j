package org.sjf4j.node.external;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.TypeInfo;
import org.sjf4j.node.TypeRegistry;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalNodeRegistryTest {
    private static final TestExternalAdapter ADAPTER = new TestExternalAdapter();

    static {
        ExternalNodeRegistry.register(ADAPTER);
    }

    @Test
    void classifiesRegisteredHierarchyWithoutPojoAnalysis() {
        TestExternalNode object = new TestExternalNode(JsonType.OBJECT);
        TestExternalChildNode array = new TestExternalChildNode(JsonType.ARRAY);

        assertEquals(NodeKind.OBJECT_EXTERNAL, NodeKind.of(object));
        assertEquals(NodeKind.ARRAY_EXTERNAL, NodeKind.of(array));
        assertEquals(JsonType.OBJECT, JsonType.of(object));
        assertEquals(JsonType.ARRAY, JsonType.of(array));

        TypeInfo typeInfo = TypeRegistry.registerTypeInfo(TestExternalChildNode.class);
        assertSame(ADAPTER, typeInfo.externalNode);
        assertNull(typeInfo.pojoInfo);
        assertThrows(JsonException.class, () -> TypeRegistry.registerPojoOrElseThrow(TestExternalChildNode.class));
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
    }

    @Test
    void rejectsDuplicateRootRegistration() {
        assertThrows(JsonException.class, () -> ExternalNodeRegistry.register(new TestExternalAdapter()));
        assertNotNull(ExternalNodeRegistry.resolve(TestExternalChildNode.class));
    }

    @Test
    void rejectsNativeObntRootTypes() {
        assertThrows(JsonException.class, () -> ExternalNodeRegistry.register(new ExternalNode<ArrayList<?>>() {
            @SuppressWarnings("unchecked")
            @Override public Class<ArrayList<?>> rootType() { return (Class<ArrayList<?>>) (Class<?>) ArrayList.class; }
            @Override public JsonType jsonType(ArrayList<?> node) { return JsonType.ARRAY; }
        }));
    }

    static class TestExternalNode {
        private final JsonType jsonType;

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

    static final class TestExternalAdapter implements ExternalNode<TestExternalNode> {
        @Override
        public Class<TestExternalNode> rootType() {
            return TestExternalNode.class;
        }

        @Override
        public JsonType jsonType(TestExternalNode node) {
            return node.getJsonType();
        }

        @Override
        public JsonType jsonTypeOfClass(Class<?> nodeType) {
            return TestExternalArrayNode.class.isAssignableFrom(nodeType) ? JsonType.ARRAY : JsonType.UNKNOWN;
        }
    }
}
