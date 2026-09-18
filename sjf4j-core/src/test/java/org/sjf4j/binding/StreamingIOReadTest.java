package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonReader;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingIOReadTest {
    @Test
    void readsUntypedNestedObjectAndArray() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"items\":[1,true]}"))) {
            Map<?, ?> value = (Map<?, ?>) StreamingIO.readNode(reader, Object.class, StreamingContext.EMPTY);
            assertEquals(List.of(1, true), value.get("items"));
        }
    }

    @Test
    void readsPrimitiveArrayIntoExactLength() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[3,4]"))) {
            int[] value = (int[]) StreamingIO.readNode(reader, int[].class, StreamingContext.EMPTY);
            assertEquals(2, value.length);
            assertEquals(4, value[1]);
        }
    }
}
