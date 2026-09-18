package org.sjf4j.binding.contract;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Default numeric JDK date bindings from Jackson's DateDeserializationTest. */
public abstract class DateDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Source: DateDeserializationTest#testDateUtil (default root numeric Date input). */
    @Test void testDateUtil() { assertEquals(new Date(123456789L), binding(StreamingContext.EMPTY).readNode("123456789", Date.class)); }
    /** Source: DateDeserializationTest#testCalendar. */
    @Test void testCalendarAsNumber() {
        Calendar value = (Calendar) binding(StreamingContext.EMPTY).readNode("123456789", Calendar.class);
        assertEquals(123456789L, value.getTimeInMillis());
    }
}
