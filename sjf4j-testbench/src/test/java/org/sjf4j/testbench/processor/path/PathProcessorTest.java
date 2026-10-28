package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.CompiledInstances;
import org.sjf4j.exception.NodeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathProcessorTest {

    @Test
    public void registryCreatesInterface() {
        BasicNodes nodes = CompiledInstances.create(BasicNodes.class);

        assertEquals("Hangzhou", nodes.getCityName(new User(new City("Hangzhou"))));
        assertNull(nodes.getCityName(new User(null)));
        assertNull(nodes.getCityName(null));
    }

    @Test
    public void registryRejectsInvalidTargets() {
        assertThrows(NullPointerException.class, () -> CompiledInstances.create(null));

        IllegalArgumentException notInterface = assertThrows(IllegalArgumentException.class,
                () -> CompiledInstances.create(NotInterface.class));
        assertTrue(notInterface.getMessage().contains("expected interface type"), notInterface.getMessage());
        assertTrue(notInterface.getMessage().contains(NotInterface.class.getName()), notInterface.getMessage());

        NodeException notCompiled = assertThrows(NodeException.class, () -> CompiledInstances.create(NotCompiled.class));
        assertTrue(notCompiled.getMessage().contains("cannot find generated implementation"), notCompiled.getMessage());
        assertTrue(notCompiled.getMessage().contains("annotation processing is enabled"), notCompiled.getMessage());
        assertTrue(notCompiled.getMessage().contains(NotCompiled.class.getName() + "_Impl"), notCompiled.getMessage());
    }

    record User(City city) {}

    record City(String name) {}

    static final class NotInterface {}

    interface NotCompiled {}

    @CompiledNavigator
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
