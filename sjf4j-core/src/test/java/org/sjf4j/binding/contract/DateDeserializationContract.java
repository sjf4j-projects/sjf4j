package org.sjf4j.binding.contract;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.node.NodeValueRegistry;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.NodeValueCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in epoch-millis date bindings using named SJF4J value codecs, not Jackson defaults. */
public abstract class DateDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    private static final StreamingContext EPOCH_MILLIS = new StreamingContext(java.util.Map.of(
            Date.class, "epochMillis", Calendar.class, "epochMillis"));

    private static final class EpochMillisCodecs {
        static {
            NodeValueRegistry.registerByCodec(
                    new NodeValueCodec.SimpleValueCodec<>(Date.class, Long.class, Date::getTime, Date::new),
                    "epochMillis", false);
            NodeValueRegistry.registerByCodec(
                    new NodeValueCodec.SimpleValueCodec<>(Calendar.class, Long.class, Calendar::getTimeInMillis,
                            raw -> {
                        Calendar value = Calendar.getInstance();
                        value.setTimeInMillis(raw);
                        return value;
                    }
                    ), "epochMillis", false);
        }

        static void ensureRegistered() { }
    }

    private static StreamingContext epochMillisContext() {
        EpochMillisCodecs.ensureRegistered();
        return EPOCH_MILLIS;
    }

    /** Source: DateDeserializationTest#testDateUtil, expressed through opt-in valueFormat. */
    @Test void testDateUtil() { assertEquals(new Date(123456789L), binding(epochMillisContext()).readNode("123456789", Date.class)); }
    /** Source: DateDeserializationTest#testCalendar, expressed through opt-in valueFormat. */
    @Test void testCalendarAsNumber() {
        Calendar value = (Calendar) binding(epochMillisContext()).readNode("123456789", Calendar.class);
        assertEquals(123456789L, value.getTimeInMillis());
    }
}
