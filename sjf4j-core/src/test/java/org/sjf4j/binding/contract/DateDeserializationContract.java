package org.sjf4j.binding.contract;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.value.ValueRegistry;
import org.sjf4j.value.ValueCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in epoch-millis date bindings using named SJF4J value codecs, not Jackson defaults. */
public abstract class DateDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(RuntimeContext context);
    private static final RuntimeContext EPOCH_MILLIS = new RuntimeContext(java.util.Map.of(
            Date.class, "epochMillis", Calendar.class, "epochMillis"));

    private static final class EpochMillisCodecs {
        static {
            ValueRegistry.registerByCodec(
                    new ValueCodec.SimpleValueCodec<>(Date.class, Long.class, Date::getTime, Date::new),
                    "epochMillis", false);
            ValueRegistry.registerByCodec(
                    new ValueCodec.SimpleValueCodec<>(Calendar.class, Long.class, Calendar::getTimeInMillis,
                            raw -> {
                        Calendar value = Calendar.getInstance();
                        value.setTimeInMillis(raw);
                        return value;
                    }
                    ), "epochMillis", false);
        }

        static void ensureRegistered() { }
    }

    private static RuntimeContext epochMillisContext() {
        EpochMillisCodecs.ensureRegistered();
        return EPOCH_MILLIS;
    }

    /** Source: DateDeserializationTest#testDateUtil, expressed through opt-in valueFormat. */
    @Test void testDateUtil() {
            assertEquals(new Date(123456789L), binding(epochMillisContext()).readNode("123456789", Date.class));
        }
    /** Source: DateDeserializationTest#testCalendar, expressed through opt-in valueFormat. */
    @Test void testCalendarAsNumber() {
        Calendar value = (Calendar) binding(epochMillisContext()).readNode("123456789", Calendar.class);
        assertEquals(123456789L, value.getTimeInMillis());
    }
}
