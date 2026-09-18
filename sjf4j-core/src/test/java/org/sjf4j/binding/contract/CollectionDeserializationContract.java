package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Portable collection cases from Jackson's CollectionDeserializationTest. */
public abstract class CollectionDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Source: CollectionDeserializationTest#testUntypedList. */
    @Test void testUntypedList() {
        List<?> value = assertInstanceOf(List.class, binding(StreamingContext.EMPTY).readNode("[\"text!\",true,null,23]", Object.class));
        assertEquals("text!", value.get(0)); assertEquals(true, value.get(1)); assertNull(value.get(2)); assertEquals(23, ((Number) value.get(3)).intValue());
    }
    /** Source: CollectionDeserializationTest#testExactStringCollection. */
    @Test void testExactStringCollection() { assertEquals(List.of("a", "b"), binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\"]", new TypeReference<List<String>>() {}.getType())); }
}
