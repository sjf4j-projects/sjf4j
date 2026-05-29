package org.sjf4j.jdk17.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.CompiledPath;
import org.sjf4j.compiled.CompiledNodesRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CompiledPathProcessorTest {

    @Test
    public void getFromRecordPath() {
        UserNodes nodes = CompiledNodesRegistry.of(UserNodes.class);

        assertEquals("Hangzhou", nodes.getCityName(new User(new City("Hangzhou"))));
        assertNull(nodes.getCityName(new User(null)));
        assertNull(nodes.getCityName(null));
    }

    record User(City city) {}

    record City(String name) {}

    @CompiledNodes
    interface UserNodes {
        @CompiledPath(expr = "$.city.name", method = CompiledPath.MethodKind.GET)
        String getCityName(CompiledPathProcessorTest.User user);
    }

}
