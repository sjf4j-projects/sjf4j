package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeReferenceTest {
    @Test
    @SuppressWarnings("rawtypes")
    void rawTypeReferenceIsABindingDefinitionError() {
        assertThrows(BindingException.class, () -> new TypeReference() {});
    }
}
