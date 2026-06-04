package org.sjf4j.jdk17.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathProcessorTest {

    @Test
    public void registryCreatesInterface() {
        BasicNodes nodes = CompiledNodes.of(BasicNodes.class);

        assertEquals("Hangzhou", nodes.getCityName(new User(new City("Hangzhou"))));
        assertNull(nodes.getCityName(new User(null)));
        assertNull(nodes.getCityName(null));
    }

    @Test
    public void registryRejectsInvalidTargets() {
        JsonException nullType = assertThrows(JsonException.class, () -> CompiledNodes.of(null));
        assertTrue(nullType.getMessage().contains("non-null interface type"), nullType.getMessage());

        JsonException notInterface = assertThrows(JsonException.class, () -> CompiledNodes.of(NotInterface.class));
        assertTrue(notInterface.getMessage().contains("requires an interface type"), notInterface.getMessage());
        assertTrue(notInterface.getMessage().contains(NotInterface.class.getName()), notInterface.getMessage());

        JsonException notCompiled = assertThrows(JsonException.class, () -> CompiledNodes.of(NotCompiled.class));
        assertTrue(notCompiled.getMessage().contains("Cannot find generated SJF4J implementation"), notCompiled.getMessage());
        assertTrue(notCompiled.getMessage().contains("@CompiledPath or @CompiledMapper"), notCompiled.getMessage());
        assertTrue(notCompiled.getMessage().contains(NotCompiled.class.getName() + "_Impl"), notCompiled.getMessage());
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
