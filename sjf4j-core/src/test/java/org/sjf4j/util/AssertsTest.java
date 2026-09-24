package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class AssertsTest {

    @Test
    void notNullReturnsOriginalValue() {
        Object value = new Object();

        assertSame(value, Asserts.notNull(value, "value"));
    }

    @Test
    void notNullRejectsNullWithNullPointerException() {
        NullPointerException exception = assertThrowsExactly(NullPointerException.class,
                () -> Asserts.notNull(null, "value"));

        assertEquals("'value' must not be null", exception.getMessage());
    }

}
