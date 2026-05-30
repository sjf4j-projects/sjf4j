package org.sjf4j.jdk17.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.GetByPath;
import org.sjf4j.compiled.CompiledNodesRegistry;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathProcessorTest {

    @Test
    public void registryCreatesCompiledNodesInterface() {
        BasicNodes nodes = CompiledNodesRegistry.of(BasicNodes.class);

        assertEquals("Hangzhou", nodes.getCityName(new User(new City("Hangzhou"))));
        assertNull(nodes.getCityName(new User(null)));
        assertNull(nodes.getCityName(null));
    }

    @Test
    public void registryRejectsInvalidTargets() {
        assertThrows(JsonException.class, () -> CompiledNodesRegistry.of(NotInterface.class));
        assertThrows(JsonException.class, () -> CompiledNodesRegistry.of(NotCompiled.class));
    }

    record User(City city) {}

    record City(String name) {}

    static final class NotInterface {}

    interface NotCompiled {}

    @CompiledNodes
    interface BasicNodes {
        @GetByPath("$.city.name")
        String getCityName(User user);

        static void printMe() {
            System.out.println("me");
        }

        default void printYou() {
            System.out.println("you");
        }
    }
}
