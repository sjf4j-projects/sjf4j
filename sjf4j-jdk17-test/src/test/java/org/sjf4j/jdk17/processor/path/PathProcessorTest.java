package org.sjf4j.jdk17.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.compiled.CompiledRegistry;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathProcessorTest {

    @Test
    public void registryCreatesCompiledNodesInterface() {
        BasicNodes nodes = CompiledRegistry.of(BasicNodes.class);

        assertEquals("Hangzhou", nodes.getCityName(new User(new City("Hangzhou"))));
        assertNull(nodes.getCityName(new User(null)));
        assertNull(nodes.getCityName(null));
    }

    @Test
    public void registryRejectsInvalidTargets() {
        assertThrows(JsonException.class, () -> CompiledRegistry.of(NotInterface.class));
        assertThrows(JsonException.class, () -> CompiledRegistry.of(NotCompiled.class));
    }

    record User(City city) {}

    record City(String name) {}

    static final class NotInterface {}

    interface NotCompiled {}

    @CompiledPath
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
