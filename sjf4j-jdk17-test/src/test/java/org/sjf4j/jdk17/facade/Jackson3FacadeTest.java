package org.sjf4j.jdk17.facade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.facade.FacadeFactory;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;
import org.sjf4j.node.NodeKind;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3FacadeTest {

    private Sjf4jConfig previousConfig;

    @BeforeEach
    void saveGlobalConfig() {
        previousConfig = Sjf4jConfig.global();
    }

    @AfterEach
    void restoreGlobalConfig() {
        if (previousConfig != null) {
            Sjf4jConfig.global(previousConfig);
        }
    }

    static class Book extends JsonObject {
        int id;

        @NodeProperty("user_name")
        String userName;
    }

    private static Stream<StreamingFacade.StreamingMode> allModes() {
        return Stream.of(
                StreamingFacade.StreamingMode.SHARED_IO,
                StreamingFacade.StreamingMode.EXCLUSIVE_IO,
                StreamingFacade.StreamingMode.PLUGIN_MODULE
        );
    }

    private static Jackson3JsonFacade newFacade(StreamingFacade.StreamingMode mode) {
        return new Jackson3JsonFacade(new ObjectMapper(), mode);
    }

    @TestFactory
    Stream<DynamicTest> testJsonObjectRoundTripAcrossModes() {
        return allModes().map(mode -> DynamicTest.dynamicTest("mode=" + mode, () -> {
            Jackson3JsonFacade facade = newFacade(mode);
            String json = "{\"id\":123,\"user_name\":\"han\"}";

            Book book = (Book) facade.readNode(json, Book.class);
            assertEquals(123, book.id);
            assertEquals("han", book.userName);
            assertEquals("han", book.getString("user_name"));
            assertEquals(json, facade.writeNodeAsString(book));
        }));
    }

    @Test
    void testUseJackson3AsGlobal() {
        Sjf4jConfig.useJackson3AsGlobal(StreamingFacade.StreamingMode.PLUGIN_MODULE);
        Book book = Sjf4j.fromJson("{\"id\":7,\"user_name\":\"jack\"}", Book.class);
        assertEquals(7, book.id);
        assertEquals("jack", book.userName);
    }

    @Test
    void testDefaultFacadePrefersJackson3() {
        assertTrue(FacadeFactory.getDefaultJsonFacade() instanceof Jackson3JsonFacade);
    }

    @Test
    void testFacadeNodesDispatchJackson3() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode objectNode = mapper.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        ArrayNode arrayNode = mapper.createArrayNode().add("x").add(2).add(false);

        assertTrue(FacadeNodes.isJackson3NodesPresent());
        assertTrue(FacadeNodes.isNode(objectNode));
        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(objectNode));
        assertEquals(NodeKind.ARRAY_FACADE, FacadeNodes.kindOf(arrayNode));
        assertEquals(NodeKind.VALUE_STRING_FACADE, FacadeNodes.kindOf(StringNode.valueOf("x")));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, FacadeNodes.kindOf(BooleanNode.TRUE));
        assertEquals(NodeKind.VALUE_NULL, FacadeNodes.kindOf(JsonNodeFactory.instance.nullNode()));
        assertEquals("han", FacadeNodes.toString(objectNode.get("name")));
        assertEquals(18, FacadeNodes.toNumber(objectNode.get("age")).intValue());
        assertEquals("x", FacadeNodes.toJsonArray(arrayNode).getString(0));
    }
}
