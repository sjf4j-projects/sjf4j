package org.sjf4j.testbench.model.fabien;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The benchmark fixtures are ordinary testbench models, not JMH-only code. */
class FabienModelsTest {
    @Test
    void usersAreDeterministic() {
        Users first = FabienDataGenerator.users(10_000, 12345);
        assertEquals(first, FabienDataGenerator.users(10_000, 12345));
        assertNotEquals(first, FabienDataGenerator.users(10_000, 324));
        assertFalse(first.getUsers().isEmpty());
    }

    @Test
    void clientsAreDeterministic() {
        Clients first = FabienDataGenerator.clients(10_000, 12345);
        assertEquals(first, FabienDataGenerator.clients(10_000, 12345));
        assertNotEquals(first, FabienDataGenerator.clients(10_000, 324));
        assertFalse(first.getClients().isEmpty());
    }
}
