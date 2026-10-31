package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

class FastLocalDateTest {

    @Test
    void canonicalDatesAndBufferOffsets() {
        for (String date : new String[]{"2026-10-31", "2000-02-29",
                "1900-03-01", "0000-01-01", "9999-12-31"}) {
            LocalDate expected = LocalDate.parse(date);
            assertEquals(expected, FastLocalDate.parse(date));
            char[] chars = ("xx" + date + "yy").toCharArray();
            assertEquals(expected, FastLocalDate.parse(chars, 2, date.length()));
        }
    }

    @Test
    void nonCanonicalIsoDatesFallback() {
        for (String date : new String[]{"+10000-01-01", "-0001-12-31",
                "2026-1-01", "2026-10-3"}) {
            // Invalid alternative spellings must fail exactly as in the JDK;
            // valid extended years remain supported.
            try {
                LocalDate expected = LocalDate.parse(date);
                assertEquals(expected, FastLocalDate.parse(date));
                char[] chars = date.toCharArray();
                assertEquals(expected, FastLocalDate.parse(chars, 0, chars.length));
            } catch (DateTimeParseException ignored) {
                assertThrows(DateTimeParseException.class, () -> FastLocalDate.parse(date));
                assertThrows(DateTimeParseException.class,
                        () -> FastLocalDate.parse(date.toCharArray(), 0, date.length()));
            }
        }
    }

    @Test
    void invalidDatesMatchJdkExceptionType() {
        for (String date : new String[]{"2023-02-29", "2026-13-01",
                "2026-00-10", "2026-01-00", "2026-0x-31", "abcd-12-01",
                "2026-10-3x", "", "2026/10/31"}) {
            assertThrows(DateTimeParseException.class, () -> FastLocalDate.parse(date), date);
            assertThrows(DateTimeParseException.class,
                    () -> FastLocalDate.parse(date.toCharArray(), 0, date.length()), date);
        }
        assertThrows(NullPointerException.class, () -> FastLocalDate.parse((String) null));
    }
}
