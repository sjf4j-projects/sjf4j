package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldWriterTest {
    static class Person {
        private final String name = "Ada";
        public String getName() {
                return name;
            }
    }

    @Test
    void writesReadablePrivateFieldThroughGetter() {
        assertEquals("{\"name\":\"Ada\"}", new SimpleJsonBinder(StreamingContext.EMPTY).writeNodeAsString(new Person()));
    }

    enum Choice { FIRST }

    static class BoxedValues {
        public String text = "text";
        public Integer integer = 1;
        public Long longer = 2L;
        public Double decimal = 3.5;
        public Float floating = 1.25F;
        public Short shorter = 6;
        public Byte smaller = 7;
        public Boolean flag = true;
        public Character character = 'x';
        public BigInteger bigInteger = BigInteger.TEN;
        public BigDecimal bigDecimal = new BigDecimal("2.5");
        public Number number = 4;
        public Choice choice = Choice.FIRST;
    }

    @Test
    void writesBoxedScalarsWithPreparedNames() {
        assertEquals("{\"text\":\"text\",\"integer\":1,\"longer\":2,\"decimal\":3.5,\"floating\":1.25,"
                        + "\"shorter\":6,\"smaller\":7,\"flag\":true,\"character\":\"x\",\"bigInteger\":10,\"bigDecimal\":2.5,"
                        + "\"number\":4,\"choice\":\"FIRST\"}",
                new SimpleJsonBinder(StreamingContext.EMPTY).writeNodeAsString(new BoxedValues()));
    }

    static class NullableValues {
        public Integer integer;
        public Character character;
        public Choice choice;
    }

    @Test
    void filtersOrWritesNullBoxedScalarsAccordingToContext() {
        NullableValues values = new NullableValues();
        assertEquals("{}", new SimpleJsonBinder(new StreamingContext(Collections.emptyMap(), false))
                .writeNodeAsString(values));
        assertEquals("{\"integer\":null,\"character\":null,\"choice\":null}",
                new SimpleJsonBinder(StreamingContext.EMPTY).writeNodeAsString(values));
    }
}
