package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldWriterTest {
    static class Person {
        private final String name = "Ada";
        public String getName() { return name; }
    }

    @Test
    void writesReadablePrivateFieldThroughGetter() {
        assertEquals("{\"name\":\"Ada\"}", new SimpleJsonBinder().writeNodeAsString(new Person()));
    }
}
