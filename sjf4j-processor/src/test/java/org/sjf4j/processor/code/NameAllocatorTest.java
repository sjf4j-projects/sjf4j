package org.sjf4j.processor.code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameAllocatorTest {

    @Test
    void usesFirstSuggestionAndNumbersCollisionsFromTwo() {
        NameAllocator names = new NameAllocator();
        assertEquals("name", names.newName("name"));
        assertEquals("name2", names.newName("name"));
        assertEquals("name3", names.newName("name"));
        assertEquals("length", names.newName("length"));
        assertEquals("length2", names.newName("length"));
    }

    @Test
    void reservesExternalNamesBeforeAllocation() {
        NameAllocator names = new NameAllocator()
                .reserve("result")
                .reserve("result2")
                .reserve("parser");
        assertEquals("result3", names.newName("result"));
        assertEquals("parser2", names.newName("parser"));
        assertTrue(names.isUsed("result3"));
        assertFalse(names.isUsed("unrelated"));
    }

    @Test
    void sanitizesInvalidIdentifiersAndKeywords() {
        NameAllocator names = new NameAllocator();
        assertEquals("first_name", names.newName("first-name"));
        assertEquals("first_name2", names.newName("first name"));
        assertEquals("_1value", names.newName("1value"));
        assertEquals("class2", names.newName("class"));
        assertEquals("_2", names.newName("_"));
        assertEquals("value", names.newName(""));
    }

    @Test
    void independentScopesCanReuseNames() {
        NameAllocator left = new NameAllocator();
        NameAllocator right = new NameAllocator();
        assertEquals("item", left.newName("item"));
        assertEquals("item2", left.newName("item"));
        assertEquals("item", right.newName("item"));
    }
}
