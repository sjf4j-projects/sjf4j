package org.sjf4j.value;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueCodecInitializationTest {

    private static final String VALUE = "org.sjf4j.value.ValueCodec";
    private static final String PATTERNED = "org.sjf4j.value.PatternedValueCodec";
    private static final String[] TEMPORAL_FIELDS = {
            "LOCAL_DATE", "LOCAL_TIME", "LOCAL_DATE_TIME",
            "OFFSET_DATE_TIME", "ZONED_DATE_TIME", "OFFSET_TIME"
    };

    @Test
    void temporalConstantsAreInitializedRegardlessOfFirstAccess() throws Exception {
        for (String first : new String[] {VALUE, PATTERNED,
                VALUE + "$StreamingValueCodec", PATTERNED + "$TemporalValueCodec"}) {
            URL location = ValueCodec.class.getProtectionDomain().getCodeSource().getLocation();
            // Each iteration has independent SJF4J class initialization, while
            // JDK and external dependency classes are shared with the test.
            try (URLClassLoader loader = new URLClassLoader(
                    new URL[] {location}, getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!name.startsWith("org.sjf4j.")) return super.loadClass(name, resolve);
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
            }) {
                Class.forName(first, true, loader);
                Class<?> value = Class.forName(VALUE, true, loader);
                Class<?> patterned = Class.forName(PATTERNED, true, loader);
                for (String field : TEMPORAL_FIELDS) {
                    Object codec = value.getField(field).get(null);
                    assertNotNull(codec, first + ": " + field);
                    assertTrue(patterned.isInstance(codec), first + ": " + field);
                }
                Object date = value.getField("LOCAL_DATE").get(null);
                LocalDate expected = LocalDate.of(2026, 11, 25);
                assertEquals(expected, value.getMethod("rawToValue", Object.class).invoke(date, "2026-11-25"));
                assertEquals("2026-11-25", value.getMethod("valueToRaw", Object.class).invoke(date, expected));
                Object custom = patterned.getMethod("withPattern", String.class).invoke(date, "dd/MM/yyyy");
                assertEquals("25/11/2026", value.getMethod("valueToRaw", Object.class).invoke(custom, expected));
            }
        }
    }
}
