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
